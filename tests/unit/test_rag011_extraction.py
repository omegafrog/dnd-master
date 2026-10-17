import hashlib
import json
from pathlib import Path

from preprocessing_agent.pipeline.extraction_service import ExtractionApplicationService, PyMuPdfNativePdfAdapter
from preprocessing_agent.adapters.hybrid_pdf import HybridPdfAdapter
from preprocessing_agent.adapters.ocr import TesseractOcrAdapter
from preprocessing_agent.ports.extraction import RenderedPage, normalize_ocr_blocks


class Native:
    def __init__(self, blocks, **extra):
        self.blocks, self.extra = blocks, extra

    def extract(self, _source):
        return [{"page_number": 1, "geometry": {"width": 100, "height": 100}, "blocks": self.blocks, **self.extra}]


class Render:
    def available(self): return True
    def render(self, _source, page_number, region=None): return RenderedPage(page_number, 100, 100, b"png")


class Ocr:
    def available(self): return True
    def recognize(self, _rendered, region=None): return [{"text": "scanned", "bbox": (5, 5, 40, 15), "text_confidence": .91}]


def request(tmp_path, name):
    source = tmp_path / f"{name}.pdf"
    source.write_bytes(b"pdf")
    return {"request_id": name, "source_path": str(source), "source_sha256": hashlib.sha256(b"pdf").hexdigest(), "policy_version": "p1", "output_dir": str(tmp_path / name)}


def test_image_only_page_uses_ocr_and_preserves_provenance(tmp_path):
    result = ExtractionApplicationService(Native([]), Render(), Ocr()).preprocess(request(tmp_path, "image"))
    assert result["status"] == "READY"
    page = result["pages"][0]
    assert page["status"] == "VALIDATED"


def test_image_only_page_is_blocked_when_ocr_capability_missing(tmp_path):
    result = ExtractionApplicationService(Native([]), Render(), None).preprocess(request(tmp_path, "missing"))
    assert result["status"] == "NEEDS_REVIEW"
    assert "OCR_UNAVAILABLE" in result["pages"][0]["findings"]
    assert "chunks" not in result["artifacts"]


def test_retry_reuses_ocr_path_and_can_recover_image_only_page(tmp_path):
    class RecoveringOcr(Ocr):
        def __init__(self):
            self.availability_checks = 0

        def available(self):
            self.availability_checks += 1
            return self.availability_checks > 1

    source = tmp_path / "retry.pdf"
    source.write_bytes(b"pdf")
    digest = hashlib.sha256(b"pdf").hexdigest()
    output = tmp_path / "retry-out"
    service = ExtractionApplicationService(Native([]), Render(), RecoveringOcr())
    request = {"request_id": "retry", "source_path": str(source), "source_sha256": digest,
               "policy_version": "p1", "output_dir": str(output), "version_id": "retry-v1"}

    first = service.preprocess(request)
    assert first["status"] == "NEEDS_REVIEW"
    recovered = service.retry_pages("retry-v1", output, [1], request_id="retry-request")
    assert recovered["status"] == "READY"
    assert recovered["pages"][0]["status"] == "VALIDATED"


def test_mixed_page_keeps_native_and_adds_targeted_ocr(tmp_path):
    result = ExtractionApplicationService(Native([{"block_id": "n", "text": "native", "bbox": (1, 1, 30, 10)}], image_regions=[(40, 1, 90, 30)]), Render(), Ocr()).preprocess(request(tmp_path, "mixed"))
    assert result["status"] == "READY"
    evidence = json.loads((tmp_path / "mixed" / "versions" / result["version_id"] / "version.json").read_text())
    page = evidence["pages"][0]
    assert page["page_classification"] == "mixed"
    assert {block["extraction_method"] for block in page["blocks"]} == {"native", "ocr"}
    assert next(block["bbox"] for block in page["blocks"] if block["extraction_method"] == "ocr") == [45.0, 6.0, 80.0, 16.0]
    assert next(block["text_confidence"] for block in page["blocks"] if block["extraction_method"] == "ocr") == .91


def test_confirmed_form_is_preserved_but_excluded_from_chunks(tmp_path, monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    class NativePages:
        def extract(self, _source):
            return [
                {"page_number": 1, "geometry": {"width": 100, "height": 100},
                 "blocks": [
                     {"block_id": "label", "text": "Strength", "bbox": (10, 10, 40, 20)},
                     {"block_id": "field", "text": "Field", "bbox": (50, 10, 90, 20)},
                 ], "page_classification": "form", "form_diagnostics": {
                     "drawing_count": 180, "aligned_line_count": 466,
                     "long_horizontal_line_count": 154, "text_line_count": 15}},
                {"page_number": 2, "geometry": {"width": 100, "height": 100},
                 "blocks": [{"block_id": "rule", "text": "A validated rule. " * 120, "bbox": (10, 70, 90, 80)}]},
            ]

    result = ExtractionApplicationService(NativePages(), Render(), None).preprocess(request(tmp_path, "form"))

    form_page = result["pages"][0]
    assert result["status"] == "READY"
    assert form_page["status"] == "EXCLUDED_FORM"
    assert form_page["page_classification"] == "form"
    assert form_page["form_diagnostics"]["drawing_count"] == 180
    assert "chunks" in result["artifacts"]
    version = json.loads((tmp_path / "form" / "versions" / result["version_id"] / "version.json").read_text())
    assert version["page_count"] == 2
    assert version["excluded_form_pages"] == [1]
    assert version["pages"][0]["blocks"][0]["block_id"] == "label"
    manifest = json.loads(Path(result["artifacts"]["manifest"]["path"]).read_text())
    assert manifest["statistics"]["pages"] == 2
    assert manifest["page_statistics"] == {"count": 2, "excluded_form_pages": [1]}
    chunks = [json.loads(line) for line in Path(result["artifacts"]["chunks"]["path"]).read_text().splitlines()]
    assert chunks and all(span["page_number"] == 2 for chunk in chunks for span in chunk["source_spans"])
    assert "dev_preprocessing_form stage=excluded pageNumber=1" in caplog.text
    assert "actualExcludedFormCount=1 recoveryAction=preserve_source_skip_parser outcome=excluded" in caplog.text
    assert "A validated rule" not in caplog.text


def test_verified_blank_form_is_excluded_without_becoming_a_review_page(tmp_path):
    class BlankForm:
        def extract(self, _source):
            return [{"page_number": 1, "geometry": {"width": 100, "height": 100}, "blocks": [],
                     "page_classification": "form", "form_diagnostics": {
                         "drawing_count": 180, "aligned_line_count": 466,
                         "long_horizontal_line_count": 154, "text_line_count": 0}}]

    result = ExtractionApplicationService(BlankForm(), Render(), None).preprocess(request(tmp_path, "blank-form"))

    assert result["status"] == "READY"
    assert result["pages"][0]["status"] == "EXCLUDED_FORM"
    assert result["page_summary"]["excluded_forms"] == 1
    assert result["page_summary"]["needs_review"] == 0


def test_filled_form_values_remain_searchable_and_log_the_selected_route(tmp_path, monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")

    result = ExtractionApplicationService(Native([
        {"block_id": "entry", "text": "Strength 16", "bbox": (10, 10, 40, 20), "kind": "form_value"},
    ], page_classification="form", form_content_state="filled", populated_form_field_count=1,
       form_diagnostics={"drawing_count": 180, "aligned_line_count": 466,
                         "long_horizontal_line_count": 154, "text_line_count": 15}),
       Render(), None).preprocess(request(tmp_path, "filled-form"))

    assert result["status"] == "PARTIAL"
    assert result["pages"][0]["status"] == "USABLE_WITH_WARNINGS"
    assert result["pages"][0]["form_content_state"] == "filled"
    chunks = [json.loads(line) for line in Path(result["artifacts"]["chunks"]["path"]).read_text().splitlines()]
    assert any("Strength 16" in chunk["embedding_text"] for chunk in chunks)
    assert "dev_preprocessing_form stage=content_assessment pageNumber=1" in caplog.text
    assert "actualPopulatedFieldCount=1 route=searchable_form_content" in caplog.text


def test_uncertain_form_with_image_uses_ocr_and_stays_searchable_with_warning(tmp_path, monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")

    result = ExtractionApplicationService(Native([
        {"block_id": "label", "text": "Character Name", "bbox": (5, 5, 40, 15)},
    ], page_classification="form", form_content_state="unknown", image_regions=[(40, 10, 90, 50)],
       form_diagnostics={"drawing_count": 180, "aligned_line_count": 466,
                         "long_horizontal_line_count": 154, "text_line_count": 15}),
       Render(), Ocr()).preprocess(request(tmp_path, "uncertain-form-image"))

    assert result["status"] == "PARTIAL"
    assert result["pages"][0]["status"] == "USABLE_WITH_WARNINGS"
    assert "FORM_CONTENT_UNVERIFIED" in result["pages"][0]["findings"]
    assert "scanned" in Path(result["artifacts"]["chunks"]["path"]).read_text()
    assert "actualState=unknown populatedFieldCount=0 imageRegionCount=1 route=ocr_and_preserve_page_asset" in caplog.text


def test_unverified_form_classification_stays_needs_review(tmp_path):
    result = ExtractionApplicationService(Native([
        {"block_id": "label", "text": "Strength", "bbox": (10, 10, 40, 20)},
    ], page_classification="form", form_diagnostics={"drawing_count": 180,
       "aligned_line_count": 466, "long_horizontal_line_count": 5, "text_line_count": 15}), Render(), None).preprocess(
           request(tmp_path, "uncertain-form"))

    assert result["status"] == "PARTIAL"
    assert result["pages"][0]["status"] == "USABLE_WITH_WARNINGS"
    assert "FORM_CLASSIFICATION_UNVERIFIED" in result["pages"][0]["findings"]
    assert "chunks" in result["artifacts"]


def test_searchable_pages_are_published_when_other_pages_need_review(tmp_path, monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    text = "Page one contains a reliable rule with page coordinates and source provenance. " * 25
    second_text = "Page two contains a different usable rule with its own source coordinates. " * 25
    unresolved = {"bbox": [10, 10, 90, 90], "row_count": 5, "column_count": 5,
                  "missing_cell_count": 8, "observed_cell_geometry_count": 17,
                  "expected_cell_count": 25, "signals": ["IRREGULAR_GRID"],
                  "decision": "strong_unresolved", "rejection_reason": "MISSING_CELL_BBOXES"}

    class MixedPages:
        def extract(self, _source):
            return [
                {"page_number": 1, "geometry": {"width": 100, "height": 100},
                 "blocks": [{"block_id": "rule", "text": text, "bbox": (5, 5, 90, 95)}],
                 "native_table_candidate_count": 1, "native_table_diagnostics": [unresolved]},
                {"page_number": 2, "geometry": {"width": 100, "height": 100},
                 "blocks": [{"block_id": "usable", "text": second_text, "bbox": (5, 5, 90, 95)}]},
                {"page_number": 3, "geometry": {"width": 100, "height": 100}, "blocks": [],
                 "page_classification": "image-only"},
            ]

    service = ExtractionApplicationService(MixedPages(), Render(), None)
    result = service.preprocess(request(tmp_path, "partial-pages"))

    assert result["status"] == "PARTIAL"
    jsonschema = __import__("pytest").importorskip("jsonschema")
    response_schema = json.loads((Path(__file__).parents[2] / "schemas" / "extraction-response.schema.json").read_text())
    jsonschema.Draft202012Validator(response_schema).validate(result)
    assert [page["status"] for page in result["pages"]] == [
        "VALIDATED", "VALIDATED", "NEEDS_REVIEW"]
    assert result["pages"][0]["findings"] == []
    assert "OCR_UNAVAILABLE" in result["pages"][2]["findings"]
    assert result["page_summary"]["usable_with_warnings"] == 0
    assert result["page_summary"]["needs_review"] == 1
    assert result["page_summary"]["ready"] == 2

    chunks = [json.loads(line) for line in Path(result["artifacts"]["chunks"]["path"]).read_text().splitlines()]
    source_pages = {span["page_number"] for chunk in chunks for span in chunk["source_spans"]}
    assert source_pages == {1, 2}
    assert all(span["page_number"] != 3 for chunk in chunks for span in chunk["source_spans"])

    version = json.loads(Path(result["artifacts"]["version"]["path"]).read_text())
    assert version["page_count"] == 3
    assert [page["status"] for page in version["pages"]] == [
        "VALIDATED", "VALIDATED", "NEEDS_REVIEW"]
    assert version["pages"][0]["blocks"][0]["text"] == text
    reread = service.get_status(result["version_id"], tmp_path / "partial-pages")
    assert reread["status"] == "PARTIAL"
    assert reread["page_summary"] == result["page_summary"]
    jsonschema.Draft202012Validator(response_schema).validate(reread)
    assert "dev_preprocessing_page stage=completed pageNumber=1" in caplog.text
    assert "actualStatus=VALIDATED" in caplog.text
    assert "dev_preprocessing_run stage=completed status=PARTIAL" in caplog.text
    assert text not in caplog.text


def test_default_search_path_keeps_reading_order_without_heading_or_publication_analysis(tmp_path, monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    text = "This page has usable text and page coordinates even if structural analysis crashes. " * 20

    calls = []
    def fail_structure_analysis(*_args, **_kwargs):
        calls.append("called")
        raise RuntimeError("structure analysis must be disabled")
    for target in (
        "preprocessing_agent.pipeline.extraction_service.HeadingAssociator.analyze",
        "preprocessing_agent.pipeline.extraction_service.TableStructureDetector.detect",
        "preprocessing_agent.pipeline.extraction_service.LayoutValidationService.validate",
        "preprocessing_agent.structure.tree_builder.HeadingDetector.detect",
    ):
        monkeypatch.setattr(target, fail_structure_analysis)
    result = ExtractionApplicationService(
        Native([{"block_id": "body", "text": text, "bbox": (5, 5, 90, 95)}]), Render(), None,
    ).preprocess(request(tmp_path, "structure-failure"))

    assert result["status"] == "READY"
    assert result["pages"][0]["status"] == "VALIDATED"
    assert result["pages"][0]["findings"] == []
    assert calls == []
    chunks = [json.loads(line) for line in Path(result["artifacts"]["chunks"]["path"]).read_text().splitlines()]
    assert chunks and all(span["page_number"] == 1 for chunk in chunks for span in chunk["source_spans"])
    assert all(span["bbox"] == [5.0, 5.0, 90.0, 95.0]
               for chunk in chunks for span in chunk["source_spans"])
    assert "route=geometry_reading_order_only" in caplog.text
    assert "dev_preprocessing_heading" not in caplog.text
    assert "dev_preprocessing_page stage=completed pageNumber=1" in caplog.text
    assert text not in caplog.text


def test_text_without_page_source_geometry_is_quarantined(tmp_path, monkeypatch, caplog):
    monkeypatch.delenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", raising=False)
    caplog.set_level("INFO")
    result = ExtractionApplicationService(
        Native([{"block_id": "unlocated", "text": "Text without a page coordinate must not be published."}]),
        Render(), None,
    ).preprocess(request(tmp_path, "missing-source-location"))

    assert result["status"] == "NEEDS_REVIEW"
    assert result["pages"][0]["status"] == "NEEDS_REVIEW"
    assert "chunks" not in result["artifacts"]
    assert "dev_preprocessing_page" not in caplog.text


def test_default_search_extraction_does_not_run_table_candidate_diagnostics(tmp_path, monkeypatch, caplog):
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    caplog.set_level("INFO")
    result = ExtractionApplicationService(
        Native([{"block_id": "body", "text": "source words", "bbox": (1, 1, 30, 10)}],
               native_table_candidate_count=1, native_table_diagnostics=[{
                   "bbox": None, "row_count": 1, "column_count": 1, "missing_cell_count": 1,
                   "observed_cell_geometry_count": 0, "expected_cell_count": 1,
                   "signals": ["INVALID_TABLE_BBOX"], "decision": "weak_candidate",
                   "rejection_reason": "INSUFFICIENT_GEOMETRIC_EVIDENCE"}]), Render(), None,
    ).preprocess(request(tmp_path, "table-gap"))
    assert result["status"] == "READY"
    assert result["pages"][0]["status"] == "VALIDATED"
    assert "dev_preprocessing_layout stage=table_classification" not in caplog.text
    assert "source words" not in caplog.text


def test_unresolved_table_structure_does_not_block_default_search_extraction(tmp_path):
    diagnostic = {"bbox": [10, 10, 90, 90], "row_count": 5, "column_count": 5,
                  "missing_cell_count": 8, "observed_cell_geometry_count": 17,
                  "expected_cell_count": 25,
                  "signals": ["VALID_TABLE_BBOX", "ROW_COUNT_5", "COLUMN_COUNT_5",
                              "CELL_GEOMETRY_COVERAGE_0.680", "IRREGULAR_GRID", "MISSING_CELL_BBOXES"],
                  "decision": "strong_unresolved", "rejection_reason": "MISSING_CELL_BBOXES"}
    blocks = [
        {"block_id": "a", "text": "A", "bbox": (0, 0, 10, 10)},
        {"block_id": "b", "text": "B", "bbox": (20, 0, 30, 10)},
        {"block_id": "c", "text": "C", "bbox": (70, 0, 80, 10)},
        {"block_id": "d", "text": "D", "bbox": (90, 0, 100, 10)},
    ]
    result = ExtractionApplicationService(
        Native(blocks, native_table_candidate_count=1, native_table_diagnostics=[diagnostic]), Render(), None,
    ).preprocess(request(tmp_path, "unresolved-table"))
    page = result["pages"][0]
    assert result["status"] == "READY"
    assert page["status"] == "VALIDATED"
    assert page["findings"] == []
    version = json.loads((tmp_path / "unresolved-table" / "versions" / result["version_id"] / "version.json").read_text())
    assert [block["text"] for block in version["pages"][0]["blocks"]] == ["A", "B", "C", "D"]
    assert "layout" not in version["pages"][0]
    assert "tables" not in version["pages"][0]


def test_service_wires_optional_tesseract_adapter_by_default():
    service = ExtractionApplicationService()
    assert isinstance(service.ocr, TesseractOcrAdapter)
    assert isinstance(service.native_pdf, HybridPdfAdapter)


def test_search_native_pdf_adapter_skips_table_reconstruction(tmp_path, monkeypatch):
    fitz = __import__("pytest").importorskip("fitz")
    source = tmp_path / "raw-text.pdf"
    document = fitz.open()
    page = document.new_page(width=300, height=200)
    page.insert_text((30, 40), "Text stays in its original PDF block.")
    document.save(source)
    document.close()

    def fail_table_finding(*_args, **_kwargs):
        raise AssertionError("search extraction must not run table reconstruction")
    monkeypatch.setattr("preprocessing_agent.pipeline.extraction_service._find_native_tables", fail_table_finding)

    raw_page = PyMuPdfNativePdfAdapter(analyze_structure=False).extract(source)[0]

    assert any("Text stays in its original PDF block." in block["text"] for block in raw_page["blocks"])
    assert all("table_id" not in block for block in raw_page["blocks"])
    assert "native_table_diagnostics" not in raw_page


def test_tesseract_availability_requires_a_runnable_binary(monkeypatch):
    monkeypatch.setattr("preprocessing_agent.adapters.ocr.shutil.which", lambda _name: "/broken/tesseract")
    monkeypatch.setattr("preprocessing_agent.adapters.ocr.subprocess.run", lambda *args, **kwargs: (_ for _ in ()).throw(OSError("missing shared library")))
    assert not TesseractOcrAdapter().available()


def test_ocr_normalization_rejects_out_of_bounds_and_keeps_confidence():
    blocks = normalize_ocr_blocks([{"text": "x", "bbox": (0, 0, 10, 10), "confidence": .8}], page_number=2, width=20, height=20)
    assert blocks[0]["extraction_method"] == "ocr"
    assert blocks[0]["text_confidence"] == .8
    regional = normalize_ocr_blocks([{"text": "x", "bbox": (1, 2, 4, 6)}], page_number=2, width=10, height=10, offset=(30, 40, 50, 60))
    assert regional[0]["bbox"] == (31.0, 42.0, 34.0, 46.0)


def test_pymupdf_renderer_uses_local_dimensions_for_region(tmp_path):
    fitz = __import__("pytest").importorskip("fitz")
    document = fitz.open()
    document.new_page(width=200, height=100)
    source = tmp_path / "render.pdf"
    document.save(source)
    document.close()
    from preprocessing_agent.adapters.ocr import PyMuPdfPageRenderAdapter
    rendered = PyMuPdfPageRenderAdapter().render(source, 1, (40, 10, 140, 60))
    assert (rendered.width, rendered.height) == (100.0, 50.0)
    assert rendered.pixel_width and rendered.pixel_height


def test_tesseract_tsv_adapter_projects_crop_coordinates_to_page():
    from preprocessing_agent.adapters.ocr import _parse_tsv
    rendered = RenderedPage(1, 100.0, 50.0, b"", pixel_width=200, pixel_height=100, region_origin=(40.0, 10.0))
    tsv = "level\tpage\tblock\tpar\tline\tword\tleft\ttop\twidth\theight\tconf\ttext\n5\t1\t1\t1\t1\t1\t10\t20\t30\t10\t90\tword"
    block = _parse_tsv(tsv, rendered, 200, 100)[0]
    assert block["bbox"] == (45.0, 20.0, 60.0, 25.0)


def test_parsed_page_rejects_unknown_classification():
    from preprocessing_agent.domain import ParsedPage
    import pytest
    with pytest.raises(ValueError, match="classification"):
        ParsedPage(1, (), "", page_classification="unknown")
