from pathlib import Path
import runpy
from types import SimpleNamespace
import pytest

from preprocessing_agent.parsers.pdf import PdfDocumentParser, _classify_text_detection_hypothesis, _default_extractor, _find_native_tables, _is_reliable_native_table, _native_table_candidate_diagnostic, _reconcile_native_table_candidates, _reconstruct_native_table, _without_consumed_header_blocks
from preprocessing_agent.structure import TableStructureDetector
from preprocessing_agent.adapters.docling_pdf import docling_document_to_pages
from preprocessing_agent.adapters.pymupdf4llm_pdf import PyMuPdf4LlmPdfAdapter
from preprocessing_agent.adapters.hybrid_pdf import HybridPdfAdapter
from preprocessing_agent.pipeline.extraction_service import ExtractionApplicationService, _extract_vision_candidate_region
from preprocessing_agent.pipeline.extraction_service import (
    PyMuPdfNativePdfAdapter, _merge_vision_table_blocks, _merge_vision_tables_into_page,
)
from preprocessing_agent.adapters.llm_table_extractor import AiProviderTableExtractor, page_table_blocks


def test_pymupdf_blocks_have_page_scoped_ids_for_extraction_service(tmp_path, monkeypatch, caplog):
    fitz = pytest.importorskip("fitz")
    import logging

    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level(logging.INFO)
    source = tmp_path / "identified-blocks.pdf"
    document = fitz.open()
    page = document.new_page(width=300, height=400)
    page.insert_text((30, 40), "First paragraph")
    page.insert_text((30, 70), "Second paragraph")
    document.save(source)
    document.close()

    pages = list(_default_extractor(source))
    blocks = pages[0]["blocks"]

    assert len(pages) == 1
    assert len(blocks) == 2
    assert [block["block_id"] for block in blocks] == ["p1-b0", "p1-b1"]
    assert "stage=completed pageNumber=1 expectedIdentifiedBlockCount=2 actualBlockCount=2" in caplog.text
    assert "generatedBlockIdCount=2 route=page_and_source_block_ordinal outcome=success" in caplog.text


def test_parser_excludes_only_geometry_confirmed_forms_and_records_original_page_numbers(tmp_path):
    pages = [
        {"page_number": 1, "page_classification": "form", "form_diagnostics": {
            "drawing_count": 180, "aligned_line_count": 466,
            "long_horizontal_line_count": 154, "text_line_count": 15},
         "blocks": [{"block_id": "field", "text": "Strength", "bbox": (10, 10, 40, 20)}]},
        {"page_number": 2, "blocks": [{"block_id": "rule", "text": "Rule text", "bbox": (10, 10, 90, 20)}]},
    ]

    document = PdfDocumentParser(extractor=lambda _source: pages).parse(tmp_path / "source.pdf")

    assert [page.page_number for page in document.pages] == [2]
    assert document.source_text == "Rule text"
    assert document.metadata["source_page_count"] == 2
    assert document.metadata["excluded_form_pages"] == (1,)
    assert document.metadata["excluded_form_evidence"][0]["form_diagnostics"]["drawing_count"] == 180


def test_parser_keeps_filled_or_uncertain_forms_searchable():
    diagnostics = {"drawing_count": 180, "aligned_line_count": 466,
                   "long_horizontal_line_count": 154, "text_line_count": 15}
    pages = [
        {"page_number": 1, "page_classification": "form", "form_content_state": "filled",
         "form_diagnostics": diagnostics,
         "blocks": [{"block_id": "value", "text": "Strength 16", "bbox": (10, 10, 40, 20)}]},
        {"page_number": 2, "page_classification": "form", "form_content_state": "unknown",
         "form_diagnostics": diagnostics,
         "blocks": [{"block_id": "label", "text": "Character Name", "bbox": (10, 10, 40, 20)}]},
    ]

    document = PdfDocumentParser(extractor=lambda _source: pages, analyze_structure=False).parse(Path("source.pdf"))

    assert [page.page_number for page in document.pages] == [1, 2]
    assert "Strength 16" in document.source_text
    assert "Character Name" in document.source_text
    assert document.metadata["excluded_form_pages"] == ()


def test_docling_items_map_to_shared_page_block_contract():
    class Box:
        l, t, r, b = 10, 20, 90, 40

        def to_top_left_origin(self, _height):
            return self

    provenance = SimpleNamespace(page_no=1, bbox=Box())
    item = SimpleNamespace(text="Spell description", label=SimpleNamespace(value="text"), prov=(provenance,))
    document = SimpleNamespace(
        pages={1: SimpleNamespace(size=SimpleNamespace(width=612, height=792))},
        iterate_items=lambda: iter(((item, 0),)),
    )

    pages = docling_document_to_pages(document)

    assert pages == [{
        "page_number": 1,
        "geometry": {"width": 612.0, "height": 792.0},
        "blocks": [{"block_id": "p1-b0", "text": "Spell description", "bbox": (10.0, 20.0, 90.0, 40.0),
                    "kind": "text", "docling_label": "text",
                    "lines": [{"text": "Spell description", "bbox": (10.0, 20.0, 90.0, 40.0),
                               "line_index": 0, "spans": []}]}],
        "image_regions": [], "page_classification": "text-native", "docling_extraction": True,
        "docling_pipeline": "standard_with_models",
    }]


def test_docling_table_items_keep_cell_content_and_row_column_provenance():
    class Box:
        l, t, r, b = 10, 20, 90, 40

        def to_top_left_origin(self, _height):
            return self

    provenance = SimpleNamespace(page_no=1, bbox=Box())
    cells = [SimpleNamespace(text="Weapon", bbox=Box(), start_row_offset_idx=0, start_col_offset_idx=0),
             SimpleNamespace(text="Range", bbox=Box(), start_row_offset_idx=0, start_col_offset_idx=1)]
    table = SimpleNamespace(text="", label=SimpleNamespace(value="table"), prov=(provenance,),
                            data=SimpleNamespace(table_cells=cells))
    document = SimpleNamespace(
        pages={1: SimpleNamespace(size=SimpleNamespace(width=612, height=792))},
        iterate_items=lambda: iter(((table, 0),)),
    )

    blocks = docling_document_to_pages(document)[0]["blocks"]

    assert [(block["text"], block["row"], block["column"], block["table_id"])
            for block in blocks] == [("Weapon", 0, 0, "p1-table-0"), ("Range", 0, 1, "p1-table-0")]


def test_pdf_provider_can_be_switched_without_changing_defaults(monkeypatch):
    monkeypatch.delenv("PREPROCESSING_PDF_PROVIDER", raising=False)
    assert isinstance(ExtractionApplicationService().native_pdf, HybridPdfAdapter)
    assert PdfDocumentParser()._provider_name == "hybrid"

    monkeypatch.setenv("PREPROCESSING_PDF_PROVIDER", "pymupdf")
    assert isinstance(ExtractionApplicationService().native_pdf, PyMuPdfNativePdfAdapter)
    assert PdfDocumentParser()._provider_name == "pymupdf"

    monkeypatch.setenv("PREPROCESSING_PDF_PROVIDER", "docling")
    assert ExtractionApplicationService().native_pdf.provider_name == "docling"
    assert PdfDocumentParser()._extractor.__self__.provider_name == "docling"

    monkeypatch.setenv("PREPROCESSING_PDF_PROVIDER", "unsupported")
    with pytest.raises(ValueError, match="unsupported PREPROCESSING_PDF_PROVIDER"):
        ExtractionApplicationService()


def test_hybrid_provider_keeps_pymupdf_text_and_uses_docling_cells_in_table_region(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    import logging
    caplog.set_level(logging.INFO)
    pymupdf_pages = [{"page_number": 1, "blocks": [
        {"block_id": "before", "text": "Before table", "bbox": (10, 10, 90, 20)},
        {"block_id": "native-table-text", "text": "Sword 1d8", "bbox": (20, 40, 100, 60)},
        {"block_id": "after", "text": "After table", "bbox": (10, 80, 90, 90)},
    ]}]
    docling_pages = [{"page_number": 1, "blocks": [
        {"block_id": "table-r0-c0", "table_id": "p1-table-0", "kind": "table_cell",
         "row": 0, "column": 0, "text": "Sword", "bbox": (20, 40, 55, 50),
         "table_bbox": (15, 35, 110, 65)},
        {"block_id": "table-r0-c1", "table_id": "p1-table-0", "kind": "table_cell",
         "row": 0, "column": 1, "text": "1d8", "bbox": (60, 40, 95, 50),
         "table_bbox": (15, 35, 110, 65)},
    ]}]
    class FakeDocling:
        def extract(self, _source):
            return docling_pages

    pages = HybridPdfAdapter(lambda _source: pymupdf_pages, FakeDocling()).extract(Path("source.pdf"))

    assert [block["text"] for block in pages[0]["blocks"]] == ["Before table", "After table", "Sword", "1d8"]
    assert pages[0]["docling_merge_diagnostics"] == {
        "status": "tables_merged", "table_count": 1, "cell_count": 2,
        "replaced_pymupdf_block_count": 1,
        "table_regions": {"docling:p1-table-0": (15.0, 35.0, 110.0, 65.0)},
        "table_decisions": [],
    }
    assert "stage=completed provider=hybrid" in caplog.text
    assert "actualMergedTableCount=1 actualMergedCellCount=2" in caplog.text


def test_hybrid_keeps_the_more_complete_native_table_when_table_regions_conflict(monkeypatch, caplog):
    import logging
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level(logging.INFO)
    native_cells = [
        {"block_id": "native-r0-c0", "table_id": "native-table", "kind": "table_cell",
         "row": 0, "column": 0, "text": "Sword", "bbox": (20, 40, 55, 50)},
        {"block_id": "native-r0-c1", "table_id": "native-table", "kind": "table_cell",
         "row": 0, "column": 1, "text": "1d8+2", "bbox": (60, 40, 95, 50)},
    ]
    docling_cells = [{"block_id": "docling-r0-c0", "table_id": "docling-table", "kind": "table_cell",
                      "row": 0, "column": 0, "text": "Sword", "bbox": (20, 40, 55, 50),
                      "table_bbox": (15, 35, 110, 65)}]

    class FakeDocling:
        def extract(self, _source):
            return [{"page_number": 1, "blocks": docling_cells}]

    page = HybridPdfAdapter(lambda _source: [{"page_number": 1, "blocks": native_cells}], FakeDocling()).extract(Path("source.pdf"))[0]

    assert [block["text"] for block in page["blocks"]] == ["Sword", "1d8+2"]
    decision = page["docling_merge_diagnostics"]["table_decisions"][0]
    assert decision["selected_provider"] == "pymupdf"
    assert decision["pymupdf_text_fidelity"] > decision["docling_text_fidelity"]
    assert page["docling_merge_diagnostics"]["cell_count"] == 2
    assert "pageNumber=1" in caplog.text
    assert "selectedProvider=pymupdf" in caplog.text
    assert "outcome=resolved" in caplog.text


def test_hybrid_uses_docling_when_it_preserves_more_native_source_text():
    native_cells = [{"block_id": "native-r0-c0", "table_id": "native-table", "kind": "table_cell",
                     "row": 0, "column": 0, "text": "Sword", "bbox": (20, 40, 55, 50)}]
    docling_cells = [
        {"block_id": "docling-r0-c0", "table_id": "docling-table", "kind": "table_cell",
         "row": 0, "column": 0, "text": "Sword", "bbox": (20, 40, 55, 50),
         "table_bbox": (15, 35, 110, 65)},
        {"block_id": "docling-r0-c1", "table_id": "docling-table", "kind": "table_cell",
         "row": 0, "column": 1, "text": "1d8+2", "bbox": (60, 40, 95, 50),
         "table_bbox": (15, 35, 110, 65)},
    ]

    class FakeDocling:
        def extract(self, _source):
            return [{"page_number": 1, "blocks": docling_cells}]

    source_blocks = [*native_cells, {"block_id": "native-text", "text": "1d8+2", "bbox": (60, 40, 95, 50)}]
    page = HybridPdfAdapter(lambda _source: [{"page_number": 1, "blocks": source_blocks}], FakeDocling()).extract(Path("source.pdf"))[0]

    assert [block["text"] for block in page["blocks"]] == ["Sword", "1d8+2"]
    assert {block.get("table_id") for block in page["blocks"]} == {"docling:docling-table"}
    decision = page["docling_merge_diagnostics"]["table_decisions"][0]
    assert decision["selected_provider"] == "docling"
    assert decision["docling_text_fidelity"] > decision["pymupdf_text_fidelity"]


def test_hybrid_keeps_provider_ids_distinct_for_non_overlapping_tables():
    native_cells = [{"block_id": "cell", "table_id": "table-0", "kind": "table_cell",
                     "row": 0, "column": 0, "text": "Native", "bbox": (10, 10, 30, 20)}]
    docling_cells = [{"block_id": "cell", "table_id": "table-0", "kind": "table_cell",
                      "row": 0, "column": 0, "text": "Docling", "bbox": (100, 100, 130, 110),
                      "table_bbox": (95, 95, 135, 115)}]

    class FakeDocling:
        def extract(self, _source):
            return [{"page_number": 1, "blocks": docling_cells}]

    page = HybridPdfAdapter(lambda _source: [{"page_number": 1, "blocks": native_cells}], FakeDocling()).extract(Path("source.pdf"))[0]

    assert {block["table_id"] for block in page["blocks"]} == {"pymupdf:table-0", "docling:table-0"}
    assert set(page["docling_merge_diagnostics"]["table_regions"]) == {"pymupdf:table-0", "docling:table-0"}


def test_hybrid_keeps_native_tables_when_one_docling_region_overlaps_multiple_native_tables():
    native_cells = [
        {"block_id": "native-a", "table_id": "native-a", "kind": "table_cell", "row": 0,
         "column": 0, "text": "First", "bbox": (20, 40, 55, 50)},
        {"block_id": "native-b", "table_id": "native-b", "kind": "table_cell", "row": 0,
         "column": 0, "text": "Second", "bbox": (60, 40, 95, 50)},
    ]
    docling_cells = [{"block_id": "docling", "table_id": "docling-table", "kind": "table_cell",
                      "row": 0, "column": 0, "text": "First Second", "bbox": (20, 40, 95, 50),
                      "table_bbox": (15, 35, 110, 65)}]

    class FakeDocling:
        def extract(self, _source):
            return [{"page_number": 1, "blocks": docling_cells}]

    page = HybridPdfAdapter(lambda _source: [{"page_number": 1, "blocks": native_cells}], FakeDocling()).extract(Path("source.pdf"))[0]

    assert [block["text"] for block in page["blocks"]] == ["First", "Second"]
    decision = page["docling_merge_diagnostics"]["table_decisions"][0]
    assert decision["selected_provider"] == "pymupdf"
    assert decision["reason"] == "ambiguous_overlapping_regions_preserve_native_table"


def test_hybrid_asks_ai_to_resolve_only_ambiguous_table_regions(monkeypatch):
    import preprocessing_agent.adapters.hybrid_pdf as hybrid
    native_cells = [
        {"block_id": "native-a", "table_id": "native-a", "kind": "table_cell", "row": 0,
         "column": 0, "text": "First", "bbox": (20, 40, 55, 50)},
        {"block_id": "native-b", "table_id": "native-b", "kind": "table_cell", "row": 0,
         "column": 0, "text": "Second", "bbox": (60, 40, 95, 50)},
    ]
    docling_cells = [{"block_id": "docling", "table_id": "docling-table", "kind": "table_cell",
                      "row": 0, "column": 0, "text": "First Second", "bbox": (20, 40, 95, 50),
                      "table_bbox": (15, 35, 110, 65)}]
    calls = []

    def resolve(source, page_number, bbox, candidates, source_blocks, resolver):
        calls.append((page_number, {candidate["provider"] for candidate in candidates}, resolver))
        return ([{"block_id": "resolved-cell", "table_id": "llm:p1-llm-table-0", "kind": "table_cell",
                  "row": 0, "column": 0, "text": "First Second", "bbox": bbox}],
                {"llm:p1-llm-table-0": bbox})

    monkeypatch.setattr(hybrid, "_resolve_with_ai", resolve)
    resolver = object()

    class FakeDocling:
        def extract(self, _source):
            return [{"page_number": 1, "blocks": docling_cells}]

    page = HybridPdfAdapter(lambda _source: [{"page_number": 1, "blocks": native_cells}],
                            FakeDocling(), resolver).extract(Path("source.pdf"))[0]

    assert calls == [(1, {"pymupdf", "docling"}, resolver)]
    assert [block["text"] for block in page["blocks"]] == ["First Second"]
    assert page["docling_merge_diagnostics"]["table_decisions"][-1]["selected_provider"] == "llm_resolution"


def test_ai_resolution_renders_only_conflict_crop_and_maps_corrected_cells_to_page(tmp_path):
    import fitz
    from preprocessing_agent.adapters.hybrid_pdf import _resolve_with_ai
    source = tmp_path / "one-page.pdf"
    document = fitz.open()
    document.new_page(width=400, height=400)
    document.save(source)
    document.close()

    class FakeResolver:
        def resolve(self, image, *, candidate_tables):
            assert image.startswith(bytes((137, 80, 78, 71)))
            assert len(candidate_tables) == 2
            return [{"bbox": [0, 0, 1000, 1000], "cells": [
                {"row": 0, "column": 0, "text": "Native", "bbox": [0, 0, 1000, 1000]},
            ]}]

    cells, regions = _resolve_with_ai(
        source, 1, (100, 100, 300, 200),
        [{"provider": "pymupdf", "cells": [{"text": "Native"}]},
         {"provider": "docling", "cells": [{"text": "Native"}]}],
        [{"text": "Native", "bbox": (100, 100, 300, 200)}], FakeResolver())

    assert cells[0]["bbox"] == (100.0, 100.0, 300.0, 200.0)
    assert cells[0]["table_id"].startswith("llm:")
    assert regions == {cells[0]["table_id"]: (100.0, 100.0, 300.0, 200.0)}


def test_hybrid_keeps_overlapping_pymupdf_prose_when_docling_cells_do_not_cover_its_text():
    pymupdf_pages = [{"page_number": 1, "blocks": [
        {"block_id": "body", "text": "A paragraph beside the table", "bbox": (20, 40, 100, 60)},
    ]}]
    docling_pages = [{"page_number": 1, "blocks": [
        {"block_id": "cell", "table_id": "p1-table-0", "kind": "table_cell", "row": 0,
         "column": 0, "text": "Sword", "bbox": (20, 40, 55, 50), "table_bbox": (15, 35, 110, 65)},
    ]}]
    class FakeDocling:
        def extract(self, _source):
            return docling_pages

    pages = HybridPdfAdapter(lambda _source: pymupdf_pages, FakeDocling()).extract(Path("source.pdf"))
    assert [block["text"] for block in pages[0]["blocks"]] == ["A paragraph beside the table", "Sword"]


def test_hybrid_provider_falls_back_to_pymupdf_pages_when_docling_fails(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    import logging
    caplog.set_level(logging.INFO)
    class BrokenDocling:
        def extract(self, _source):
            raise RuntimeError("model unavailable")

    source_pages = [{"page_number": 1, "blocks": [{"block_id": "text", "text": "Kept", "bbox": (1, 1, 10, 10)}]}]
    pages = HybridPdfAdapter(lambda _source: source_pages, BrokenDocling()).extract(Path("source.pdf"))

    assert pages[0]["blocks"] == source_pages[0]["blocks"]
    assert pages[0]["capability_error"] == "DOCLING_TABLE_EXTRACTION_FAILED"
    assert pages[0]["docling_merge_diagnostics"]["status"] == "failed"
    assert "stage=docling_failed" in caplog.text
    assert "pymupdfPageCount=1" in caplog.text
    assert "recoveryAction=preserve_pymupdf_pages" in caplog.text


def test_pymupdf4llm_provider_returns_page_markdown_with_page_provenance(tmp_path, monkeypatch):
    fitz = pytest.importorskip("fitz")
    source = tmp_path / "markdown.pdf"
    doc = fitz.open()
    page = doc.new_page(width=300, height=400)
    page.insert_text((30, 40), "Heading")
    doc.save(source)
    doc.close()

    import pymupdf4llm
    monkeypatch.setattr(pymupdf4llm, "to_markdown", lambda *_args, **_kwargs: [{
        "metadata": {"page": 1}, "text": "# Heading\n\nBody text",
    }])
    pages = PyMuPdf4LlmPdfAdapter().extract(source)

    assert pages[0]["markdown_output"] is True
    assert pages[0]["blocks"] == [{
        "block_id": "p1-markdown", "text": "# Heading\n\nBody text",
        "bbox": (0.0, 0.0, 300.0, 400.0), "kind": "markdown", "extraction_method": "native",
    }]


def test_llm_table_cells_are_inserted_by_page_geometry_and_replace_matching_native_text(tmp_path, monkeypatch, caplog):
    fitz = __import__("pytest").importorskip("fitz")
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    source = tmp_path / "table-layout.pdf"
    doc = fitz.open()
    page = doc.new_page(width=300, height=400)
    page.insert_text((30, 40), "Before table")
    page.insert_text((60, 112), "Name")
    page.insert_text((180, 112), "Value")
    page.insert_text((60, 150), "Dexterity")
    page.insert_text((180, 150), "14")
    page.insert_text((30, 300), "After table")
    doc.save(source)
    doc.close()

    class TableModel:
        def detect_candidates(self, _image, *, page_numbers):
            assert page_numbers == [1]
            return [1]

        def extract(self, _image, *, page_width, page_height):
            assert page_width == 300 and page_height < 400
            return [{"bbox": [150, 200, 850, 650], "cells": [
                {"row": 0, "column": 0, "text": "Name", "bbox": [180, 280, 400, 360]},
                {"row": 0, "column": 1, "text": "Value", "bbox": [580, 280, 800, 360]},
                {"row": 1, "column": 0, "text": "Dexterity", "bbox": [180, 420, 400, 500]},
                {"row": 1, "column": 1, "text": "14", "bbox": [580, 420, 800, 500]},
            ]}, {"bbox": [100, 100, 200, 200], "cells": [
                {"row": 0, "column": 0, "text": "Bad table cell", "bbox": [800, 800, 900, 900]},
            ]}]

    extracted = PyMuPdfNativePdfAdapter(analyze_structure=False, table_extractor=TableModel()).extract(source)
    blocks = extracted[0]["blocks"]
    table_text = [block["text"] for block in blocks if block.get("kind") == "table_cell"]
    ordered_text = [block["text"] for block in blocks]
    assert table_text == ["Name", "Value", "Dexterity", "14"]
    assert "Before table" in ordered_text[0]
    assert "After table" in ordered_text[-1]
    assert sum(block.get("kind") == "table_cell" for block in blocks) == 4
    assert extracted[0]["table_extraction_diagnostics"]["status"] == "partial_tables"
    assert extracted[0]["table_extraction_diagnostics"]["native_blocks_replaced"] == 2
    rejected = extracted[0]["table_extraction_diagnostics"]["rejected_tables"]
    assert len(rejected) == 1
    assert rejected[0]["failure_code"] == "TABLE_LLM_CELL_OUTSIDE_TABLE"
    assert rejected[0]["table_bbox"][0] == 30.0 and rejected[0]["table_bbox"][2] == 60.0
    assert rejected[0]["cell_bbox"][0] == 240.0 and rejected[0]["cell_bbox"][2] == 270.0
    parsed = PdfDocumentParser(extractor=lambda _source: extracted, analyze_structure=False).parse(source)
    assert len(parsed.pages[0].tables) == 1
    assert parsed.source_text.index("Before table") < parsed.source_text.index("Name")
    assert parsed.source_text.index("14") < parsed.source_text.index("After table")
    assert "stage=explicit_cells pageNumber=1 expectedCellBlockCount=4 actualStructuredTableCount=1 route=table_cells_without_heading_analysis outcome=success" in caplog.text
    assert "stage=candidate_screen_completed expectedPageCount=1 actualCandidateCount=1" in caplog.text
    assert "stage=page_merge_completed pageNumber=1 expectedTableCount=2 actualTableCount=1 actualCellCount=4" in caplog.text
    assert "stage=table_cell_rejected pageNumber=1 tableIndex=1 cellIndex=0 row=0 column=0" in caplog.text
    assert "actualFailureCode=TABLE_LLM_CELL_OUTSIDE_TABLE" in caplog.text
    assert "route=cropped_region_provider" in caplog.text


def test_low_resolution_screen_extracts_only_selected_pages_and_skips_low_score_pages(tmp_path):
    fitz = pytest.importorskip("fitz")
    source = tmp_path / "screen-selected-page.pdf"
    document = fitz.open()
    for index in range(2):
        page = document.new_page(width=300, height=400)
        page.insert_text((30, 50), "Ordinary prose page one variation 0." if index == 0 else "Page 2 prose")
        if index == 1:
            for y, values in ((180, ("Name", "Value")), (205, ("Dex", "14"))):
                page.insert_text((40, y), values[0])
                page.insert_text((190, y), values[1])
    document.save(source)
    document.close()

    class TableModel:
        def __init__(self):
            self.extracted = []

        def detect_candidates(self, image, *, page_numbers):
            assert image.startswith(b"\x89PNG")
            assert page_numbers == [2]
            return [2]

        def extract(self, _image, *, page_width, page_height):
            self.extracted.append((page_width, page_height))
            return [{"bbox": [100, 100, 900, 900], "cells": [
                {"row": 0, "column": 0, "text": "Table cell", "bbox": [150, 150, 400, 300]},
            ]}]

    model = TableModel()
    pages = PyMuPdfNativePdfAdapter(analyze_structure=False, table_extractor=model).extract(source)

    assert len(model.extracted) == 1
    assert model.extracted[0][0] <= 300 and model.extracted[0][1] <= 400
    assert pages[0]["table_extraction_diagnostics"]["candidate_screen"] == "below_threshold_not_sampled"
    assert not any(block.get("kind") == "table_cell" for block in pages[0]["blocks"])
    assert [block["text"] for block in pages[1]["blocks"] if block.get("kind") == "table_cell"] == ["Table cell"]


def test_candidate_screen_failure_does_not_trigger_full_resolution_for_low_score_pages(tmp_path):
    fitz = pytest.importorskip("fitz")
    source = tmp_path / "screen-fallback.pdf"
    document = fitz.open()
    for index in range(2):
        page = document.new_page(width=300, height=400)
        page.insert_text((30, 50), "Ordinary prose page one variation 0." if index == 0 else "Page 2 prose")
        if index == 1:
            for y, values in ((180, ("Name", "Value")), (205, ("Dex", "14"))):
                page.insert_text((40, y), values[0])
                page.insert_text((190, y), values[1])
    document.save(source)
    document.close()

    class TableModel:
        def __init__(self):
            self.extracted = 0

        def detect_candidates(self, _image, *, page_numbers):
            assert page_numbers == [2]
            raise RuntimeError("TABLE_LLM_CANDIDATE_SCREEN_FAILED")

        def extract(self, _image, *, page_width, page_height):
            self.extracted += 1
            return []

    model = TableModel()
    pages = PyMuPdfNativePdfAdapter(analyze_structure=False, table_extractor=model).extract(source)
    assert model.extracted == 0
    assert [page["table_extraction_diagnostics"]["candidate_screen"] for page in pages] == [
        "below_threshold_not_sampled", "failed_native_candidates_only"]
    assert pages[0]["table_extraction_diagnostics"]["status"] == "not_screened_low_score"
    assert pages[1]["table_extraction_diagnostics"]["status"] == "candidate_screen_failed"
    assert pages[1]["table_extraction_diagnostics"]["failure_code"] == "TABLE_LLM_CANDIDATE_SCREEN_FAILED"


def test_table_extractor_does_not_fall_back_to_full_page_when_no_local_region_exists(tmp_path):
    fitz = pytest.importorskip("fitz")
    source = tmp_path / "no-local-region.pdf"
    document = fitz.open()
    document.new_page(width=300, height=400)
    document.save(source)
    document.close()

    class TableModel:
        def extract(self, *_args, **_kwargs):
            raise AssertionError("must not transcribe a full page when no crop exists")

    document = fitz.open(source)
    page_result = {"table_extraction_diagnostics": {"status": "pending"}}
    assessment = {"score": 12, "region_bbox": [0, 0, 300, 400], "extraction_regions": []}
    try:
        result = _extract_vision_candidate_region(
            page_result, document[0], assessment, 1, 300, 400, TableModel(), fitz)
    finally:
        document.close()

    assert result == {"image_bytes": 0}
    assert page_result["table_extraction_diagnostics"]["status"] == "candidate_region_unresolved"
    assert page_result["table_extraction_diagnostics"]["failure_code"] == "TABLE_CANDIDATE_REGION_UNRESOLVED"


def test_high_score_text_grid_skips_visual_screen_and_extracts_only_the_scored_region(tmp_path, caplog, monkeypatch):
    fitz = pytest.importorskip("fitz")
    import logging

    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level(logging.INFO)
    source = tmp_path / "high-score-grid.pdf"
    document = fitz.open()
    page = document.new_page(width=300, height=400)
    for y, values in ((120, ("Level", "Cost")), (145, ("1", "2")), (170, ("2", "3"))):
        page.insert_text((50, y), values[0])
        page.insert_text((190, y), values[1])
    document.save(source)
    document.close()

    class TableModel:
        def __init__(self):
            self.extracted = []

        def detect_candidates(self, *_args, **_kwargs):
            raise AssertionError("high-confidence candidate must bypass visual screening")

        def extract(self, image, *, page_width, page_height):
            self.extracted.append((len(image), page_width, page_height))
            return [{"bbox": [100, 100, 900, 900], "cells": [
                {"row": 0, "column": 0, "text": "Level", "bbox": [120, 150, 450, 300]},
                {"row": 0, "column": 1, "text": "Cost", "bbox": [550, 150, 850, 300]},
            ]}]

    model = TableModel()
    pages = PyMuPdfNativePdfAdapter(analyze_structure=False, table_extractor=model).extract(source)
    page_result = pages[0]
    diagnostics = page_result["table_extraction_diagnostics"]
    assessment = diagnostics["candidate_assessment"]
    assert assessment["score"] >= 55
    assert assessment["route"] == "extract"
    assert diagnostics["candidate_screen"] == "high_score_direct"
    assert diagnostics["extraction_resolution_dpi"] == 108
    assert len(model.extracted) == 1
    assert model.extracted[0][1] < 300 and model.extracted[0][2] < 400
    assert [block["text"] for block in page_result["blocks"] if block.get("kind") == "table_cell"] == ["Level", "Cost"]
    assert "expectedExtractionRegionCount=local_scored_regions actualExtractionRegionCount=1" in caplog.text


def test_llm_table_merge_preserves_overlapping_native_text_when_cells_do_not_cover_it():
    blocks, regions, rejected = page_table_blocks([{"bbox": [100, 100, 900, 900], "cells": [
        {"row": 0, "column": 0, "text": "Ability", "bbox": [100, 100, 400, 300]},
    ]}], page_number=1, page_width=100, page_height=100)
    source = [{"block_id": "native", "text": "Ability score point cost", "bbox": (20, 20, 80, 30)}]

    merged, replaced, preserved = _merge_vision_table_blocks(source, blocks, regions)

    assert replaced == 0
    assert preserved == 1
    assert source[0] in merged
    assert merged[-1]["table_extraction_method"] == "llm_vision"
    assert rejected == []


def test_llm_rejects_only_table_with_outside_cell_and_reports_geometry():
    blocks, regions, rejected = page_table_blocks([
        {"bbox": [100, 100, 500, 500], "cells": [
            {"row": 0, "column": 0, "text": "Cell", "bbox": [600, 200, 800, 300]},
        ]},
        {"bbox": [100, 600, 500, 900], "cells": [
            {"row": 0, "column": 0, "text": "Good cell", "bbox": [120, 620, 300, 700]},
        ]},
    ], page_number=1, page_width=100, page_height=100)

    assert [block["text"] for block in blocks] == ["Good cell"]
    assert regions == [(10.0, 60.0, 50.0, 90.0)]
    assert rejected == [{"table_index": 0, "cell_index": 0, "row": 0, "column": 0,
                         "failure_code": "TABLE_LLM_CELL_OUTSIDE_TABLE",
                         "table_bbox": [10.0, 10.0, 50.0, 50.0],
                         "cell_bbox": [60.0, 20.0, 80.0, 30.0]}]


def test_llm_table_merge_deduplicates_same_table_returned_from_overlapping_regions():
    table = {"bbox": [100, 100, 500, 400], "cells": [
        {"row": 0, "column": 0, "text": "Ability score", "bbox": [110, 110, 280, 180]},
        {"row": 0, "column": 1, "text": "Cost", "bbox": [300, 110, 480, 180]},
    ]}
    duplicate = {"bbox": [105, 105, 505, 405], "cells": [
        {"row": 0, "column": 0, "text": " ability   score ", "bbox": [110, 110, 280, 180]},
        {"row": 0, "column": 1, "text": "COST", "bbox": [300, 110, 480, 180]},
    ]}

    blocks, regions, rejected = page_table_blocks([table, duplicate], page_number=1,
                                                   page_width=100, page_height=100)

    assert [block["text"] for block in blocks] == ["Ability score", "Cost"]
    assert len(regions) == 1
    assert rejected == [{"table_index": 1, "failure_code": "TABLE_LLM_DUPLICATE_OVERLAPPING_REGION",
                         "duplicate_of_table_index": 0}]


def test_duplicate_table_merge_records_the_count_and_discard_reason(caplog, monkeypatch):
    import logging

    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level(logging.INFO)
    table = {"bbox": [100, 100, 500, 400], "cells": [
        {"row": 0, "column": 0, "text": "Ability score", "bbox": [110, 110, 280, 180]},
        {"row": 0, "column": 1, "text": "Cost", "bbox": [300, 110, 480, 180]},
    ]}
    duplicate = {"bbox": [105, 105, 505, 405], "cells": [
        {"row": 0, "column": 0, "text": "ability score", "bbox": [110, 110, 280, 180]},
        {"row": 0, "column": 1, "text": "cost", "bbox": [300, 110, 480, 180]},
    ]}
    page = {"blocks": [], "table_extraction_diagnostics": {}}

    _merge_vision_tables_into_page(page, [table, duplicate], 1, 100, 100,
                                   type("Extractor", (), {"model": "test"})())

    assert page["table_extraction_diagnostics"]["table_count"] == 1
    assert page["table_extraction_diagnostics"]["rejected_tables"][0]["failure_code"] == \
        "TABLE_LLM_DUPLICATE_OVERLAPPING_REGION"
    assert "expectedTableCount=2 actualTableCount=1" in caplog.text
    assert "stage=duplicate_table_discarded" in caplog.text


def test_table_extractor_uses_internal_ai_provider_route_with_page_image(monkeypatch):
    import json
    from preprocessing_agent.adapters import llm_table_extractor

    response = {"tables": [{"bbox": [100, 100, 900, 900], "cells": [
        {"row": 0, "column": 0, "text": "Name", "bbox": [100, 100, 400, 300]}]}]}
    seen = {}

    class HttpResponse:
        def __enter__(self): return self
        def __exit__(self, *_): pass
        def read(self): return json.dumps(response).encode()

    def fake_urlopen(request, timeout):
        seen["url"] = request.full_url
        seen["timeout"] = timeout
        seen["payload"] = json.loads(request.data)
        seen["token"] = request.get_header("X-internal-token")
        return HttpResponse()

    monkeypatch.setattr(llm_table_extractor, "urlopen", fake_urlopen)
    result = AiProviderTableExtractor("http://ai-service/", "internal-secret", "00000000-0000-0000-0000-000000000001",
        provider="openai", model="vision-model", reasoning="medium", endpoint_id="endpoint-id").extract(
        b"png-bytes", page_width=600, page_height=800)

    assert seen["url"] == "http://ai-service/internal/gm/preprocessing/table-extraction"
    assert seen["token"] == "internal-secret"
    assert seen["payload"]["provider"] == "openai"
    assert seen["payload"]["model"] == "vision-model"
    assert seen["payload"]["imageDataUri"].startswith("data:image/png;base64,")
    assert result[0]["cells"][0]["text"] == "Name"


def test_provider_comparison_counts_table_source_identifiers_in_emitted_chunks(tmp_path):
    script = Path(__file__).resolve().parents[2] / "scripts/compare_pdf_providers.py"
    counts = runpy.run_path(str(script))["_table_counts"]
    chunks = tmp_path / "chunks.jsonl"
    chunks.write_text(
        '{"source_spans": []}\n'
        '{"source_spans": [{"table_id": "t1", "table_cell": "r0:c0"}, '
        '{"table_id": "t1", "table_cell": "r0:c1"}]}\n'
        '{"source_spans": [{"table_id": "t2", "table_cell": "r0:c0"}]}\n',
        encoding="utf-8",
    )

    assert counts(chunks) == (2, 2, 3)


def test_pymupdf_native_table_cells_are_projected_when_supported(tmp_path, monkeypatch):
    monkeypatch.setenv("PREPROCESSING_PDF_PROVIDER", "pymupdf")
    fitz = __import__("pytest").importorskip("fitz")
    document = fitz.open()
    page = document.new_page(width=240, height=160)
    for x in (20, 120, 220):
        page.draw_line((x, 30), (x, 100), color=(0, 0, 0))
    for y in (30, 60, 100):
        page.draw_line((20, y), (220, y), color=(0, 0, 0))
    page.insert_text((35, 50), "Name")
    page.insert_text((135, 50), "Value")
    page.insert_text((35, 85), "Dex")
    page.insert_text((135, 85), "12")
    source = tmp_path / "table.pdf"
    document.save(source)
    document.close()
    if not hasattr(page, "find_tables"):
        __import__("pytest").skip("PyMuPDF table finder is unavailable")
    parsed = PdfDocumentParser().parse(source)
    tables = parsed.pages[0].tables
    assert tables, "native table finder must expose a structured table"
    assert tables[0].table_id
    assert {cell.text for cell in tables[0].cells} >= {"Name", "Value", "Dex", "12"}


def test_weak_native_table_detection_is_rejected_before_cell_projection():
    weak_one_row = SimpleNamespace(rows=(SimpleNamespace(cells=((0, 0, 20, 10), (20, 0, 40, 10))),))
    weak_missing_cell = SimpleNamespace(rows=(
        SimpleNamespace(cells=((0, 0, 20, 10), (20, 0, 40, 10))),
        SimpleNamespace(cells=((0, 10, 20, 20), None)),
    ))

    assert _is_reliable_native_table(weak_one_row) is False
    assert _is_reliable_native_table(weak_missing_cell) is True
    weak_missing_cell.bbox = (0, 0, 40, 20)
    _, missing_diagnostic = _reconstruct_native_table(weak_missing_cell, [])
    assert missing_diagnostic["decision"] == "strong_unresolved"


def test_partial_native_grid_geometry_reaches_reconstruction_but_stays_unresolved():
    table = SimpleNamespace(bbox=(0, 0, 100, 30), rows=(
        SimpleNamespace(cells=((0, 0, 50, 10), (50, 0, 100, 10))),
        SimpleNamespace(cells=((0, 10, 50, 20), None)),
        SimpleNamespace(cells=((0, 20, 50, 30), (50, 20, 100, 30))),
    ), extract=lambda: [["A", "B"], ["C", None], ["D", "E"]])
    projected, diagnostic = _reconstruct_native_table(table, [])
    assert projected == []
    assert diagnostic["decision"] == "strong_unresolved"
    assert diagnostic["rejection_reason"] == "GRID_RECONSTRUCTION_AMBIGUOUS"


def test_native_detection_compares_strategies_before_candidate_reconstruction(monkeypatch, caplog):
    def table(boxes):
        rows = tuple(SimpleNamespace(cells=(box, (box[0] + 50, box[1], box[2] + 50, box[3])))
                     for box in boxes)
        return SimpleNamespace(bbox=(0, 0, 100, 40), rows=rows)

    default_candidate = table(((0, 0, 50, 20), (0, 20, 50, 40)))
    text_candidate = table(((0, 0, 50, 20), (0, 20, 50, 40), (0, 40, 50, 60)))
    class Page:
        def find_tables(self, **kwargs):
            return SimpleNamespace(tables=(text_candidate,) if kwargs else (default_candidate,))

    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    candidates = _find_native_tables(Page(), 7)
    assert candidates == [(default_candidate, "lines"), (text_candidate, "text")]
    reconstructed = []
    for candidate, strategy in candidates:
        cells, diagnostic = _reconstruct_native_table(candidate, [])
        diagnostic.update(detection_strategies=[strategy], detection_deduplicated=False)
        _classify_text_detection_hypothesis(diagnostic, strategy)
        reconstructed.append((cells, diagnostic))
    selected, diagnostics = _reconcile_native_table_candidates(candidates, reconstructed, 7)
    assert len(selected) == 1
    assert selected[0][1] == "lines+text"
    assert selected[0][3]["decision"] == "strong_unresolved"
    assert len(diagnostics) == 2
    assert diagnostics[1]["decision"] == "weak_candidate"
    logs = " ".join(record.getMessage() for record in caplog.records)
    assert "strategy=lines" in logs and "strategy=text" in logs
    assert "overlap_deduplicated" in logs and "keep_best_reconstruction" in logs


def test_contained_text_candidate_does_not_replace_precise_line_candidate():
    def table(bbox, rows):
        return SimpleNamespace(bbox=bbox, rows=tuple(SimpleNamespace(cells=cells) for cells in rows),
                               extract=lambda: [["A", "B"], ["C", "D"]])

    precise = table((10, 40, 90, 60), (((10, 40, 50, 50), (50, 40, 90, 50)),
                                       ((10, 50, 50, 60), (50, 50, 90, 60))))
    broad = table((0, 0, 100, 100), (((0, 0, 50, 50), (50, 0, 100, 50)),
                                     ((0, 50, 50, 100), (50, 50, 100, 100))))
    class Page:
        def find_tables(self, **kwargs):
            return SimpleNamespace(tables=(broad,) if kwargs else (precise,))

    candidates = _find_native_tables(Page(), 23)
    assert candidates == [(precise, "lines"), (broad, "text")]


def test_unresolved_text_candidate_remains_diagnostic_without_becoming_confirmed():
    candidate = SimpleNamespace(bbox=(0, 0, 100, 100), rows=(
        SimpleNamespace(cells=((0, 0, 50, 50), (50, 0, 100, 50))),
        SimpleNamespace(cells=((0, 50, 50, 100), (50, 50, 100, 100))),
    ), extract=lambda: [["A", "B"], ["C", "D"]])
    _, diagnostic = _reconstruct_native_table(candidate, [])
    assert diagnostic["decision"] == "strong_unresolved"
    _classify_text_detection_hypothesis(diagnostic, "text")
    assert diagnostic["decision"] == "weak_candidate"
    assert diagnostic["candidate_kind"] == "unresolved_text_detection"
    assert diagnostic["reconstruction_decision"] == "strong_unresolved"
    assert diagnostic["reconstruction_rejection_reason"] == "TABLE_TEXT_MISMATCH"
    assert "TEXT_DETECTION_RECONSTRUCTION_UNRESOLVED" in diagnostic["signals"]

    unresolved = {"decision": "strong_unresolved", "rejection_reason": "GRID_RECONSTRUCTION_AMBIGUOUS",
                  "signals": []}
    _classify_text_detection_hypothesis(unresolved, "lines")
    assert unresolved["decision"] == "strong_unresolved"


def test_text_candidate_with_unsubstantiated_empty_rows_is_deferred_and_logged(caplog, monkeypatch):
    table = SimpleNamespace(bbox=(0, 0, 100, 60), rows=(
        SimpleNamespace(cells=((0, 0, 50, 20), (50, 0, 100, 20))),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
        SimpleNamespace(cells=((0, 40, 50, 60), (50, 40, 100, 60))),
    ), extract=lambda: [["A", "B"], ["", ""], ["C", "D"]])
    source = [{"text": value, "bbox": (x + 5, y + 5, x + 15, y + 15),
               "spans": [{"text": value, "bbox": (x + 5, y + 5, x + 15, y + 15)}]}
              for y, values in ((0, ("A", "B")), (40, ("C", "D")))
              for x, value in zip((0, 50), values)]
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")

    projected, diagnostic = _reconstruct_native_table(table, source)
    assert diagnostic["decision"] == "confirmed_structured"
    _classify_text_detection_hypothesis(diagnostic, "text", 119)

    assert diagnostic["decision"] == "weak_candidate"
    assert diagnostic["reconstruction_decision"] == "strong_unresolved"
    assert diagnostic["rejection_reason"] == "TEXT_DETECTION_EMPTY_ROWS_REQUIRE_GEOMETRIC_CONFIRMATION"
    assert diagnostic["extracted_empty_row_count"] == 1
    assert len(projected) == 6
    log = " ".join(record.getMessage() for record in caplog.records)
    assert "pageNumber=119" in log
    assert "expectedEmptyRowCount=0" in log and "actualEmptyRowCount=1" in log
    assert "preserve_source_blocks_and_defer_text_grid" in log


def test_line_candidate_recovers_a_complete_aligned_row_below_its_detected_bounds(caplog, monkeypatch):
    table = SimpleNamespace(bbox=(0, 0, 100, 40), rows=(
        SimpleNamespace(cells=((0, 0, 50, 20), (50, 0, 100, 20))),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
    ), extract=lambda: [["A", "B"], ["C", "D"]])
    source = [{"text": value, "bbox": (x + 5, y + 5, x + 15, y + 15),
               "spans": [{"text": value, "bbox": (x + 5, y + 5, x + 15, y + 15)}]}
              for y, values in ((0, ("A", "B")), (20, ("C", "D")), (44, ("E", "F")))
              for x, value in zip((0, 50), values)]

    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    projected, diagnostic = _reconstruct_native_table(table, source, 10)
    _classify_text_detection_hypothesis(diagnostic, "lines", 10)
    assert diagnostic["decision"] == "confirmed_structured"
    assert diagnostic["effective_bbox"] == [0.0, 0.0, 100.0, 59.0]
    assert diagnostic["aligned_following_span_count"] == 2
    assert diagnostic["continued_row_projected"] is True
    assert diagnostic["reconstructed_row_count"] == 3
    assert [(cell["row"], cell["column"], cell["text"]) for cell in projected][-2:] == [
        (2, 0, "E"), (2, 1, "F"),
    ]
    log = " ".join(record.getMessage() for record in caplog.records)
    assert "stage=native_table_continuation pageNumber=10" in log
    assert "expectedColumnCount=2 actualProjectedColumnCount=2" in log
    assert "recoveryAction=include_aligned_source_row_and_expand_table_region" in log


def test_empty_pdf_spans_do_not_make_valid_table_cells_unmappable():
    table = SimpleNamespace(bbox=(0, 0, 100, 40), rows=(
        SimpleNamespace(cells=((0, 0, 50, 20), (50, 0, 100, 20))),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
    ), extract=lambda: [["A", "B"], ["C", "D"]])
    source = [{"text": value, "bbox": (x + 5, y + 5, x + 15, y + 15),
               "spans": [{"text": value, "bbox": (x + 5, y + 5, x + 15, y + 15)},
                         {"text": "", "bbox": (x + 2, y + 2, x + 4, y + 4)}]}
              for y, values in ((0, ("A", "B")), (20, ("C", "D")))
              for x, value in zip((0, 50), values)]

    projected, diagnostic = _reconstruct_native_table(table, source, 119)

    assert diagnostic["decision"] == "confirmed_structured"
    assert diagnostic["source_span_count"] == diagnostic["mapped_span_count"] == 4
    assert [(cell["row"], cell["column"], cell["text"]) for cell in projected] == [
        (0, 0, "A"), (0, 1, "B"), (1, 0, "C"), (1, 1, "D"),
    ]


def test_reported_partial_native_grid_shapes_are_strong_but_not_reconstructed():
    def candidate(rows, columns, missing):
        cells = []
        index = 0
        for row in range(rows):
            row_cells = []
            for column in range(columns):
                if index < missing:
                    row_cells.append(None)
                else:
                    x0, y0 = 10 + column * 5, 10 + row * 3
                    row_cells.append((x0, y0, x0 + 4, y0 + 2))
                index += 1
            cells.append(SimpleNamespace(cells=tuple(row_cells)))
        return SimpleNamespace(bbox=(10, 10, 200, 300), rows=tuple(cells))

    for rows, columns, missing in ((19, 13, 108), (5, 5, 8), (41, 7, 132), (33, 2, 16), (27, 2, 13)):
        diagnostic = _native_table_candidate_diagnostic(candidate(rows, columns, missing))
        assert diagnostic["decision"] == "strong_unresolved"
        assert diagnostic["row_count"] == rows and diagnostic["column_count"] == columns
        assert diagnostic["missing_cell_count"] == missing
        assert diagnostic["bbox"] == [10, 10, 200, 300]
        assert diagnostic["rejection_reason"] == "MISSING_CELL_BBOXES"
        assert any(signal.startswith("DISTINCT_ROW_CENTER_COUNT_") and int(signal.rsplit("_", 1)[1]) >= 3
                   for signal in diagnostic["signals"])
        assert any(signal.startswith("X_EDGE_SUPPORT_ACROSS_ROWS_COUNT_")
                   and int(signal.rsplit("_", 1)[1]) >= min(columns + 1, 3)
                   for signal in diagnostic["signals"])


def test_native_grid_reconstructs_merged_full_row_and_standard_row():
    table = SimpleNamespace(bbox=(0, 0, 100, 40), rows=(
        SimpleNamespace(cells=((0, 0, 100, 20), None)),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
    ), extract=lambda: [["Title", None], ["A", "B"]])
    projected, diagnostic = _reconstruct_native_table(table, [
        {"text": "Title", "bbox": (10, 5, 40, 15)},
        {"text": "A", "bbox": (10, 25, 20, 35)},
        {"text": "B", "bbox": (60, 25, 70, 35)},
    ])
    assert diagnostic["decision"] == "confirmed_structured"
    assert [(cell["row"], cell["column"], cell["column_span"], cell["merged"]) for cell in projected] == [
        (0, 0, 2, True), (1, 0, 1, False), (1, 1, 1, False),
    ]


def test_native_grid_text_mismatch_stays_unresolved():
    table = SimpleNamespace(bbox=(0, 0, 100, 40), rows=(
        SimpleNamespace(cells=((0, 0, 50, 20), (50, 0, 100, 20))),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
    ), extract=lambda: [["A", "B"], ["C", "D"]])
    projected, diagnostic = _reconstruct_native_table(table, [
        {"text": "A", "bbox": (2, 2, 10, 10)}, {"text": "B", "bbox": (52, 2, 60, 10)},
        {"text": "C", "bbox": (2, 22, 10, 30)}, {"text": "wrong", "bbox": (52, 22, 70, 30)},
    ])
    assert projected == []
    assert diagnostic["decision"] == "strong_unresolved"
    assert diagnostic["rejection_reason"] == "TABLE_TEXT_MISMATCH"


def test_native_wide_box_splits_only_when_source_spans_map_to_columns():
    table = SimpleNamespace(bbox=(0, 0, 100, 40), rows=(
        SimpleNamespace(cells=((0, 0, 100, 20), None)),
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40))),
    ), extract=lambda: [["Name Value", None], ["A", "B"]])
    source = [
        {"text": "Name Value", "bbox": (0, 0, 100, 20), "spans": [
            {"text": "Name", "bbox": (10, 5, 25, 15)},
            {"text": "Value", "bbox": (60, 5, 80, 15)},
        ]},
        {"text": "A", "bbox": (0, 20, 50, 40), "spans": [{"text": "A", "bbox": (10, 25, 20, 35)}]},
        {"text": "B", "bbox": (50, 20, 100, 40), "spans": [{"text": "B", "bbox": (60, 25, 70, 35)}]},
    ]
    projected, diagnostic = _reconstruct_native_table(table, source)
    assert diagnostic["decision"] == "confirmed_structured"
    assert [(cell["row"], cell["column"], cell["text"]) for cell in projected] == [
        (0, 0, "Name"), (0, 1, "Value"), (1, 0, "A"), (1, 1, "B"),
    ]


def test_native_header_spans_map_to_columns_and_shift_data_rows():
    table = SimpleNamespace(bbox=(0, 20, 200, 60), rows=(
        SimpleNamespace(cells=tuple((column * 50, 20, column * 50 + 50, 40) for column in range(4))),
        SimpleNamespace(cells=tuple((column * 50, 40, column * 50 + 50, 60) for column in range(4))),
    ), extract=lambda: [["1", "2", "3", "4"], ["5", "6", "7", "8"]])
    labels = ("Level", "Proficiency Bonus", "Features", "Cantrips Known")
    label_lines = ((0, "Level", 5, 15), (1, "Proficiency", 3, 9), (1, "Bonus", 11, 17),
                   (2, "Features", 5, 15), (3, "Cantrips", 3, 9), (3, "Known", 11, 17))
    source = [{"text": label, "bbox": (column * 50 + 5, y0, column * 50 + 40, y1),
               "spans": [{"text": label, "bbox": (column * 50 + 5, y0, column * 50 + 40, y1)}]}
              for column, label, y0, y1 in label_lines]
    source += [{"text": value, "bbox": (column * 50 + 5, row * 20 + 5, column * 50 + 15, row * 20 + 15)}
               for row, values in enumerate((("1", "2", "3", "4"), ("5", "6", "7", "8")), start=1)
               for column, value in enumerate(values)]
    projected, diagnostic = _reconstruct_native_table(table, source)
    assert diagnostic["decision"] == "confirmed_structured"
    headers = [cell for cell in projected if cell["is_header"]]
    assert [(cell["column"], cell["text"]) for cell in headers] == list(enumerate(labels))
    assert all(cell["row"] == 0 for cell in headers)
    assert {cell["row"] for cell in projected if not cell["is_header"]} == {1, 2}
    structures = TableStructureDetector().detect(
        [{**cell, "table_id": "p31-table-0", "block_id": f"c{index}"}
         for index, cell in enumerate(projected)])
    assert [cell.text for cell in structures[0].header_rows[0].cells] == list(labels)
    remaining = _without_consumed_header_blocks(source, projected)
    assert len(remaining) == 8
    assert all(block["text"] not in labels for block in remaining)


def test_native_table_geometry_scales_with_text_and_row_heights():
    for scale in (0.5, 2.0):
        table = SimpleNamespace(bbox=(0, 20 * scale, 100 * scale, 60 * scale), rows=(
            SimpleNamespace(cells=((0, 20 * scale, 50 * scale, 40 * scale),
                                   (50 * scale, 20 * scale, 100 * scale, 40 * scale))),
            SimpleNamespace(cells=((0, 40 * scale, 50 * scale, 60 * scale),
                                   (50 * scale, 40 * scale, 100 * scale, 60 * scale))),
        ), extract=lambda: [["A", "B"], ["C", "D"]])
        source = []
        for column, text, y0, y1 in ((0, "Left", 5, 9), (1, "Right", 5, 9)):
            box = (column * 50 * scale + 5 * scale, y0 * scale,
                   column * 50 * scale + 20 * scale, y1 * scale)
            source.append({"text": text, "bbox": box, "spans": [{"text": text, "bbox": box}]})
        source.extend({"text": text, "bbox": (column * 50 * scale + 5 * scale, y * scale,
                                                  column * 50 * scale + 15 * scale, (y + 4) * scale)}
                      for y, row in ((22, ("A", "B")), (42, ("C", "D")))
                      for column, text in enumerate(row))
        projected, diagnostic = _reconstruct_native_table(table, source)
        assert diagnostic["decision"] == "confirmed_structured"
        assert diagnostic["mapping_tolerance"] > 0
        assert [(cell["row"], cell["column"], cell["text"]) for cell in projected] == [
            (0, 0, "Left"), (0, 1, "Right"), (1, 0, "A"), (1, 1, "B"),
            (2, 0, "C"), (2, 1, "D"),
        ]


def test_p57_style_neighboring_tables_stay_independent_without_header_evidence():
    tables = [SimpleNamespace(bbox=(x, 20, x + 100, 60), rows=(
        SimpleNamespace(cells=((x, 20, x + 50, 40), (x + 50, 20, x + 100, 40))),
        SimpleNamespace(cells=((x, 40, x + 50, 60), (x + 50, 40, x + 100, 60))),
    ), extract=lambda: [["A", "B"], ["C", "D"]]) for x in (0, 150)]
    structures = []
    for index, table in enumerate(tables):
        x = table.bbox[0]
        source = [{"text": value, "bbox": (left + 5, top + 5, left + 15, top + 15)}
                  for top, values in ((20, ("A", "B")), (40, ("C", "D")))
                  for left, value in zip((x, x + 50), values)]
        projected, diagnostic = _reconstruct_native_table(table, source)
        assert diagnostic["decision"] == "confirmed_structured"
        assert not any(cell["is_header"] for cell in projected)
        structures.extend({**cell, "table_id": f"p57-table-{index}", "block_id": f"t{index}-{cell['row']}-{cell['column']}"}
                          for cell in projected)
    detected = TableStructureDetector().detect(structures)
    assert [table.table_id for table in detected] == ["p57-table-0", "p57-table-1"]
    assert all(not table.header_rows and len(table.rows) == 2 for table in detected)


def test_external_header_keeps_unlabeled_inferred_columns_structural():
    table = SimpleNamespace(bbox=(0, 20, 150, 60), rows=(
        SimpleNamespace(cells=((0, 20, 50, 40), (50, 20, 100, 40), (100, 20, 150, 40))),
        SimpleNamespace(cells=((0, 40, 50, 60), (50, 40, 100, 60), (100, 40, 150, 60))),
    ), extract=lambda: [["A", "B", "C"], ["D", "E", "F"]])
    source = [{"text": value, "bbox": (x, 5, x + 15, 15),
               "spans": [{"text": value, "bbox": (x, 5, x + 15, 15)}]}
              for x, value in ((5, "Name"), (55, "Cost"))]
    source += [{"text": value, "bbox": (x + 5, y + 5, x + 15, y + 15)}
               for y, values in ((20, ("A", "B", "C")), (40, ("D", "E", "F")))
               for x, value in zip((0, 50, 100), values)]
    projected, diagnostic = _reconstruct_native_table(table, source)
    assert diagnostic["decision"] == "confirmed_structured"
    structures = TableStructureDetector().detect(
        [{**cell, "table_id": "p48-table-0", "block_id": f"c{index}"}
         for index, cell in enumerate(projected)])
    assert len(structures[0].header_rows[0].cells) == 3
    assert not structures[0].header_rows[0].cells[2].text.strip()
    assert "IRREGULAR_TABLE" not in structures[0].findings
def test_one_row_or_invalid_bbox_native_candidates_are_weak():
    one_row = SimpleNamespace(bbox=(0, 0, 100, 20), rows=(SimpleNamespace(cells=((0, 0, 50, 20), (50, 0, 100, 20))),))
    invalid_bbox = SimpleNamespace(bbox=None, rows=tuple(
        SimpleNamespace(cells=(None, None))
        for row in range(4)))
    assert _native_table_candidate_diagnostic(one_row)["decision"] == "weak_candidate"
    assert _native_table_candidate_diagnostic(invalid_bbox)["decision"] == "weak_candidate"
    no_repeated_columns = SimpleNamespace(bbox=(0, 0, 100, 100), rows=tuple(
        SimpleNamespace(cells=((row * 20, row * 20, row * 20 + 8, row * 20 + 8), None))
        for row in range(3)))
    diagnostic = _native_table_candidate_diagnostic(no_repeated_columns)
    assert diagnostic["decision"] == "weak_candidate"
    assert "X_EDGE_SUPPORT_ACROSS_ROWS_COUNT_0" in diagnostic["signals"]


def test_pdf_parser_preserves_order_layout_and_source_spans():
    parser = PdfDocumentParser(lambda _: [{"page_number": 1, "blocks": [
        {"text": "Part I", "bbox": [1, 2, 3, 4], "font_size": 18, "font": "Bold"},
        {"text": "A rule.", "bbox": [1, 20, 3, 30], "font_size": 10},
    ]}])
    parsed = parser.parse(Path("fixture.pdf"))
    assert [block.source_text for block in parsed.pages[0].blocks] == ["Part I", "A rule."]
    assert parsed.pages[0].blocks[0].bbox == (1.0, 2.0, 3.0, 4.0)
    assert parsed.pages[0].blocks[0].font_weight == "bold"
    assert parsed.pages[0].blocks[1].source_span.block_index == 1
    assert parsed.source_text == "Part I\nA rule."


def test_pdf_parser_retains_all_native_table_candidate_diagnostics():
    candidates = (
        {"bbox": [10, 20, 30, 40], "detection_strategies": ["lines"],
         "decision": "strong_unresolved", "rejection_reason": "GRID_RECONSTRUCTION_AMBIGUOUS"},
        {"bbox": [8, 18, 32, 42], "detection_strategies": ["text"],
         "decision": "weak_candidate", "rejection_reason": "TEXT_DETECTION_NOT_CONFIRMED"},
    )
    parsed = PdfDocumentParser(lambda _: [{"page_number": 4, "blocks": [
        {"block_id": "body", "text": "Source remains available", "bbox": [10, 50, 90, 60]},
    ], "native_table_diagnostics": candidates}]).parse(Path("fixture.pdf"))

    assert parsed.pages[0].native_table_diagnostics == candidates
    assert parsed.pages[0].blocks[0].source_text == "Source remains available"


def test_pdf_parser_carries_exact_table_cell_location_on_source_span():
    parser = PdfDocumentParser(lambda _: [{"page_number": 3, "blocks": [
        {"block_id": "p3-table-0-r1-c2", "text": "12", "bbox": [10, 20, 30, 40],
         "kind": "table_cell", "table_id": "p3-table-0", "row": 1, "column": 2},
    ]}])

    span = parser.parse(Path("fixture.pdf")).pages[0].blocks[0].source_span

    assert span.bbox == (10.0, 20.0, 30.0, 40.0)
    assert span.table_id == "p3-table-0"
    assert span.table_cell == "r1:c2"
    assert (span.char_start, span.char_end) == (0, 2)


def test_pdf_parser_assigns_detector_default_table_id_to_unlabeled_table_cells():
    parsed = PdfDocumentParser(lambda _: [{"blocks": [
        {"block_id": "cell-a", "text": "A", "bbox": [10, 20, 30, 30], "kind": "table_cell"},
        {"block_id": "cell-b", "text": "B", "bbox": [30, 20, 50, 30], "kind": "cell"},
    ]}]).parse(Path("fixture.pdf"))
    assert {block.source_span.table_id for block in parsed.pages[0].blocks} == {"table-1"}
    assert parsed.pages[0].layout_plan.table_zones[0].zone_id == "table-1"


def test_pdf_parser_orders_strong_two_column_page_left_to_right():
    parser = PdfDocumentParser(lambda _: [{"page_number": 4, "blocks": [
        {"block_id": "right-top", "text": "Right top", "bbox": [309, 100, 535, 140]},
        {"block_id": "right-bottom", "text": "Right bottom", "bbox": [309, 180, 535, 220]},
        {"block_id": "left-top", "text": "Left top", "bbox": [54, 100, 280, 140]},
        {"block_id": "left-bottom", "text": "Left bottom", "bbox": [54, 180, 280, 220]},
        {"block_id": "footer", "text": "Footer", "bbox": [54, 700, 535, 720]},
    ]}])

    parsed = parser.parse(Path("fixture.pdf"))
    page = parsed.pages[0]

    assert [block.block_id for block in page.blocks] == [
        "left-top", "left-bottom", "right-top", "right-bottom", "footer",
    ]
    assert [block.source_span.block_index for block in page.blocks] == [0, 1, 2, 3, 4]
    assert page.blocks[0].bbox == (54.0, 100.0, 280.0, 140.0)
    assert parsed.source_text == "Left top\nLeft bottom\nRight top\nRight bottom\nFooter"


def test_pdf_parser_preserves_line_boxes_as_analysis_metadata():
    source_lines = [{"bbox": [10, y, 40, y + 8]} for y in (10, 20, 30)]
    parsed = PdfDocumentParser(lambda _: [{"page_number": 1, "blocks": [{
        "block_id": "paragraph", "text": "Original paragraph text", "bbox": [10, 10, 40, 38],
        "lines": source_lines,
    }]}]).parse(Path("fixture.pdf"))

    block = parsed.pages[0].blocks[0]
    assert block.source_text == "Original paragraph text"
    assert block.line_boxes == ((10.0, 10.0, 40.0, 18.0), (10.0, 20.0, 40.0, 28.0),
                                (10.0, 30.0, 40.0, 38.0))


def test_heading_candidate_diagnostic_records_counts_and_document_style(monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    page = {"page_number": 1, "geometry": {"width": 300, "height": 400}, "blocks": [
        {"block_id": "body", "text": "Body words that establish the document style.", "bbox": [20, 100, 200, 120],
         "lines": [{"text": "Body words that establish the document style.", "bbox": [20, 100, 200, 120],
                    "spans": [{"text": "Body words that establish the document style.", "size": 10}]}]},
        {"block_id": "heading", "text": "RULES", "bbox": [20, 150, 100, 160], "font_size": 12,
         "lines": [{"text": "RULES", "bbox": [20, 150, 100, 160],
                    "spans": [{"text": "RULES", "size": 12, "font": "Bold"}]}]},
    ]}
    parsed = PdfDocumentParser(lambda _source: [page]).parse(Path("fixture.pdf"))
    log = next(record.message for record in caplog.records if "dev_preprocessing_heading stage=candidate_analysis" in record.message)

    assert parsed.pages[0].heading_candidates
    assert "pageNumber=1" in log
    assert "actualReferenceFontSize=10.0" in log
    assert "candidateCount=2" in log and "acceptedCount=1" in log and "rejectedCount=1" in log
    assert "route=line_and_span_candidates" in log and "outcome=success" in log


def test_form_page_uses_geometric_order_without_prose_column_profiles():
    parsed = PdfDocumentParser(lambda _: [{"page_number": 1, "page_classification": "form", "blocks": [
        {"block_id": "right", "text": "Field", "bbox": [60, 10, 90, 20]},
        {"block_id": "left", "text": "Label", "bbox": [10, 10, 40, 20]},
    ]}]).parse(Path("fixture.pdf"))

    page = parsed.pages[0]
    assert page.page_classification == "form"
    assert page.layout_plan.profiles == ()
    assert [block.block_id for block in page.blocks] == ["left", "right"]


def test_pdf_parser_keeps_single_column_table_like_page_order():
    parser = PdfDocumentParser(lambda _: [{"page_number": 1, "blocks": [
        {"block_id": "row-2-right", "text": "B2", "bbox": [180, 130, 250, 145]},
        {"block_id": "row-1-left", "text": "A1", "bbox": [54, 100, 124, 115]},
        {"block_id": "row-2-left", "text": "A2", "bbox": [54, 130, 124, 145]},
        {"block_id": "row-1-far-right", "text": "C1", "bbox": [306, 100, 376, 115]},
        {"block_id": "row-1-right", "text": "B1", "bbox": [180, 100, 250, 115]},
        {"block_id": "row-2-far-right", "text": "C2", "bbox": [306, 130, 376, 145]},
    ]}])

    parsed = parser.parse(Path("fixture.pdf"))

    assert [block.block_id for block in parsed.pages[0].blocks] == [
        "row-1-far-right", "row-1-left", "row-1-right", "row-2-far-right",
        "row-2-left", "row-2-right",
    ]


def test_pdf_parser_joins_spans_by_bbox_without_merging_table_cells():
    parser = PdfDocumentParser(lambda _: [{"page_number": 31, "blocks": [
        {"block_id": "wizard-table", "bbox": [54, 401, 535, 660], "lines": [
            {"spans": [
                {"text": "Level", "bbox": [58, 411, 76, 421]},
                {"text": "Proficiency", "bbox": [85, 411, 124, 421]},
                {"text": "Bonus", "bbox": [127, 411, 163, 421]},
            ]},
            {"spans": [
                {"text": "1st", "bbox": [59, 430, 75, 440]},
                {"text": "+2", "bbox": [93, 430, 106, 440]},
                {"text": "Spellcasting", "bbox": [128, 430, 188, 440]},
            ]},
        ]},
    ]}])

    parsed = parser.parse(Path("fixture.pdf"))

    assert parsed.pages[0].blocks[0].source_text == "Level Proficiency Bonus\n1st +2 Spellcasting"
    assert parsed.source_text == parsed.pages[0].blocks[0].source_text
