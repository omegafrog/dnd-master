from __future__ import annotations

import runpy
from pathlib import Path
import logging
import json


def test_table_review_candidate_scan_flags_repeated_multi_column_rows():
    script = Path(__file__).resolve().parents[2] / "scripts/review_pdf_table_candidates.py"
    text_grid_signal = runpy.run_path(str(script))["_text_grid_signal"]

    class Page:
        def get_text(self, mode):
            assert mode == "dict"
            return {"blocks": [{"lines": [
                {"spans": [{"text": f"left {row}", "bbox": [10, y, 45, y + 8]},
                           {"text": f"right {row}", "bbox": [150, y, 190, y + 8]}]}
                for row, y in enumerate((20, 40, 60, 80, 100))
            ]}]}

    result = text_grid_signal(Page())

    assert result == {
        "text_baseline_count": 5,
        "multi_anchor_row_count": 5,
        "repeated_column_anchor_count": 2,
        "multi_anchor_row_ratio": 1.0,
        "region_aspect_ratio": 0.489,
    }


def test_table_review_candidate_scan_ignores_single_column_prose():
    script = Path(__file__).resolve().parents[2] / "scripts/review_pdf_table_candidates.py"
    text_grid_signal = runpy.run_path(str(script))["_text_grid_signal"]

    class Page:
        def get_text(self, mode):
            assert mode == "dict"
            return {"blocks": [{"lines": [
                {"spans": [{"text": f"paragraph line {row}", "bbox": [10, y, 240, y + 8]}]}
                for row, y in enumerate((20, 40, 60, 80))
            ]}]}

    assert text_grid_signal(Page()) is None


def test_table_review_detects_repeated_vector_grid_edges():
    script = Path(__file__).resolve().parents[2] / "scripts/review_pdf_table_candidates.py"
    vector_signal = runpy.run_path(str(script))["_vector_grid_signal"]

    class Point:
        def __init__(self, x, y):
            self.x, self.y = x, y

    drawing = {"items": [
        *(('l', Point(start, y), Point(end, y))
          for y in (20, 40, 60, 80) for start, end in ((10, 40), (40, 70), (70, 100))),
        *(('l', Point(x, y), Point(x, y + 20))
          for x in (10, 50, 100) for y in (20, 40, 60, 80)),
    ]}

    assert vector_signal([drawing]) == {
        "repeated_horizontal_edge_count": 4,
        "repeated_vertical_edge_count": 3,
    }


def test_merged_output_replaces_native_text_inside_docling_table():
    script = Path(__file__).resolve().parents[2] / "scripts/review_pdf_table_candidates.py"
    merge = runpy.run_path(str(script))["_merged_page_text"]
    result = merge(
        [
            {"text": "Before table", "bbox": [10, 10, 100, 20]},
            {"text": "Name  Value", "bbox": [10, 30, 100, 40]},
            {"text": "After table", "bbox": [10, 80, 100, 90]},
        ],
        [
            {"table_id": "p1-table-0", "row": 0, "column": 0, "text": "Name", "bbox": [10, 30, 50, 40]},
            {"table_id": "p1-table-0", "row": 0, "column": 1, "text": "Value", "bbox": [50, 30, 100, 40]},
        ],
    )

    assert "Before table" in result
    assert "[Docling 표 p1-table-0]\nName | Value" in result
    assert "Name  Value" not in result
    assert "After table" in result


def test_docling_selection_contains_only_human_confirmed_candidate_pages(tmp_path):
    script = Path(__file__).resolve().parents[2] / "scripts/review_pdf_table_candidates.py"
    select = runpy.run_path(str(script))["_selected_table_candidates"]
    review = tmp_path / "table-review.json"
    review.write_text(json.dumps([
        {"page_number": 12, "review": "TABLE_PRESENT"},
        {"page_number": 17, "review": "NO_TABLE"},
        {"page_number": 19, "review": "UNSURE"},
    ]))
    candidates = [{"page_number": number} for number in (12, 17, 19)]

    selected = select(review, candidates)

    assert [candidate["page_number"] for candidate in selected] == [12]
    assert [candidate["review"] for candidate in candidates] == ["TABLE_PRESENT", "NO_TABLE", "UNSURE"]


def test_table_review_completion_log_records_candidate_counts(tmp_path, monkeypatch, caplog):
    fitz = __import__("pytest").importorskip("fitz")
    script = Path(__file__).resolve().parents[2] / "scripts/review_pdf_table_candidates.py"
    main = runpy.run_path(str(script))["main"]
    source = tmp_path / "plain.pdf"
    with fitz.open() as document:
        document.new_page()
        document.save(source)
    output = tmp_path / "review"
    monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
    monkeypatch.setattr("sys.argv", [str(script), "--pdf", str(source), "--output", str(output)])
    caplog.set_level(logging.INFO)

    assert main() == 0
    assert "stage=completed expectedPageCount=from_pdf actualCandidatePageCount=0 actualRenderedPageCount=0" in caplog.text
    assert (output / "review.html").is_file()
    assert (output / "candidates.json").is_file()
    assert (output / "merged_extraction.json").is_file()
    extracted = json.loads((output / "merged_extraction.json").read_text())
    assert len(extracted["pages"]) == 1
    assert extracted["pages"][0]["page_number"] == 1
