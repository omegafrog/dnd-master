#!/usr/bin/env python3
"""Build a human-review bundle for possible tables in any PDF."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import html
import json
import logging
import os
from pathlib import Path
import sys
import tempfile
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

_LOGGER = logging.getLogger(__name__)


def _text_grid_signal(page: object, clip: object | None = None) -> dict[str, int | float] | None:
    payload = page if isinstance(page, dict) else page.get_text("dict")  # type: ignore[attr-defined]
    baseline_anchors: dict[float, list[float]] = defaultdict(list)
    for block in payload.get("blocks", ()):
        for line in block.get("lines", ()):
            spans = [span for span in line.get("spans", ()) if str(span.get("text", "")).strip()]
            if not spans:
                continue
            if clip and not (clip.y0 <= (float(line["bbox"][1]) + float(line["bbox"][3])) / 2 <= clip.y1
                             and clip.x0 <= (float(line["bbox"][0]) + float(line["bbox"][2])) / 2 <= clip.x1):
                continue
            y = sum((float(span["bbox"][1]) + float(span["bbox"][3])) / 2 for span in spans) / len(spans)
            baseline = next((key for key in baseline_anchors if abs(key - y) <= 2.5), round(y, 1))
            baseline_anchors[baseline].extend(float(span["bbox"][0]) for span in spans)

    repeated: Counter[int] = Counter()
    multi_anchor_rows = 0
    for anchors in baseline_anchors.values():
        columns: list[float] = []
        for x in sorted(anchors):
            if not columns or x - columns[-1] > 24:
                columns.append(x)
        if len(columns) >= 2:
            multi_anchor_rows += 1
            repeated.update(round(x / 12) for x in columns)
    supported_columns = sum(count >= 3 for count in repeated.values())
    row_count = len(baseline_anchors)
    ratio = multi_anchor_rows / row_count if row_count else 0.0
    if clip is not None:
        width, height = float(clip.width), float(clip.height)
    elif isinstance(page, dict):
        width = max((float(span["bbox"][2]) for block in payload.get("blocks", ())
                     for line in block.get("lines", ()) for span in line.get("spans", ())), default=0.0)
        height = max((float(span["bbox"][3]) for block in payload.get("blocks", ())
                      for line in block.get("lines", ()) for span in line.get("spans", ())), default=0.0)
    else:
        rect = getattr(page, "rect", None)
        if rect is None:
            boxes = [span["bbox"] for block in payload.get("blocks", ())
                     for line in block.get("lines", ()) for span in line.get("spans", ())]
            width = max((float(box[2]) for box in boxes), default=0.0) - min(
                (float(box[0]) for box in boxes), default=0.0)
            height = max((float(box[3]) for box in boxes), default=0.0) - min(
                (float(box[1]) for box in boxes), default=0.0)
        else:
            width, height = float(rect.width), float(rect.height)
    aspect_ratio = height / width if width else 0.0
    if ((supported_columns >= 3 and row_count >= 4)
            or (supported_columns >= 2 and row_count >= 5 and ratio >= 0.8)) and aspect_ratio <= 2:
        return {"text_baseline_count": row_count, "multi_anchor_row_count": multi_anchor_rows,
                "repeated_column_anchor_count": supported_columns, "multi_anchor_row_ratio": round(ratio, 3),
                "region_aspect_ratio": round(aspect_ratio, 3)}
    return None


def _vector_grid_signal(drawings: list[dict[str, object]]) -> dict[str, int] | None:
    horizontal: Counter[int] = Counter()
    vertical: Counter[int] = Counter()
    for drawing in drawings:
        for item in drawing.get("items", ()):
            if item[0] == "l":
                start, end = item[1], item[2]
                if abs(start.y - end.y) <= 1:
                    horizontal[round((start.y + end.y) / 4)] += 1
                elif abs(start.x - end.x) <= 1:
                    vertical[round((start.x + end.x) / 4)] += 1
            elif item[0] == "re":
                rect = item[1]
                horizontal.update((round(rect.y0 / 2), round(rect.y1 / 2)))
                vertical.update((round(rect.x0 / 2), round(rect.x1 / 2)))
    repeated_horizontal = sum(count >= 3 for count in horizontal.values())
    repeated_vertical = sum(count >= 3 for count in vertical.values())
    if repeated_horizontal >= 2 and repeated_vertical >= 2:
        return {"repeated_horizontal_edge_count": repeated_horizontal,
                "repeated_vertical_edge_count": repeated_vertical}
    return None


def _scan_pdf(pdf_path: Path) -> tuple[list[dict[str, object]], list[dict[str, object]]]:
    import fitz

    pages: list[dict[str, object]] = []
    candidates: list[dict[str, object]] = []
    with fitz.open(pdf_path) as document:
        for number, page in enumerate(document, 1):
            signals: list[dict[str, object]] = []
            text_payload = page.get_text("dict")
            words = page.get_text("words")
            drawings = page.get_drawings()
            vector_grid = _vector_grid_signal(drawings)
            if vector_grid and len(words) >= 80:
                signals.append({"kind": "repeated_vector_grid", **vector_grid})
            for drawing in drawings:
                rect = drawing.get("rect")
                if (not drawing.get("fill") or rect is None or rect.width < 60 or rect.height < 35
                        or rect.y1 >= page.rect.height - 12):
                    continue
                grid = _text_grid_signal(text_payload, rect)
                if grid:
                    signals.append({"kind": "filled_region_repeated_text_grid",
                                    "bbox": [round(value, 2) for value in rect], **grid})
            image_area = sum(float(box[2] - box[0]) * float(box[3] - box[1])
                             for image in page.get_image_info(xrefs=True)
                             if len(box := image.get("bbox", ())) == 4)
            if (image_area >= page.rect.width * page.rect.height * 0.12
                    and len(words) < 35):
                signals.append({"kind": "large_image_region", "covered_page_area_ratio": round(
                    image_area / (page.rect.width * page.rect.height), 3)})
            text_blocks = [{"text": str(block[4]).strip(),
                            "bbox": [float(value) for value in block[:4]]}
                           for block in page.get_text("blocks") if str(block[4]).strip()]
            record = {"page_number": number, "pymupdf_text": page.get_text("text"),
                      "pymupdf_blocks": text_blocks}
            pages.append(record)
            if signals:
                candidates.append({**record, "signals": signals,
                                   "image": f"{pdf_path.stem}-review-p{number:04d}.png"})
    return pages, candidates


def _docling_candidate_tables(pdf_path: Path, candidate_pages: list[dict[str, object]]) -> dict[int, list[dict[str, object]]]:
    """Run Docling only on preselected pages, then map its cells back to source page numbers."""
    import fitz
    from preprocessing_agent.adapters.docling_pdf import DoclingPdfAdapter

    started = time.monotonic()
    page_numbers = [int(candidate["page_number"]) for candidate in candidate_pages]
    tables_by_page: dict[int, list[dict[str, object]]] = {number: [] for number in page_numbers}
    _dev_log("dev_pdf_table_review stage=docling_started expectedPageCount=%d actualPageCount=%d route=candidate_page_subset recoveryAction=run_model_table_extraction outcome=started",
             len(page_numbers), len(page_numbers))
    with tempfile.TemporaryDirectory(prefix="pdf-table-candidates-") as temporary:
        subset_path = Path(temporary) / "candidate-pages.pdf"
        with fitz.open(pdf_path) as source, fitz.open() as subset:
            for number in page_numbers:
                subset.insert_pdf(source, from_page=number - 1, to_page=number - 1)
            subset.save(subset_path)
        extracted_pages = DoclingPdfAdapter().extract(subset_path)
    for extracted in extracted_pages:
        subset_number = int(extracted["page_number"])
        if not 1 <= subset_number <= len(page_numbers):
            continue
        source_number = page_numbers[subset_number - 1]
        table_ids: dict[str, str] = {}
        for block in extracted.get("blocks", ()):
            if block.get("kind") != "table_cell":
                continue
            original_table_id = str(block.get("table_id") or "table-0")
            mapped_table_id = table_ids.setdefault(
                original_table_id, f"p{source_number}-table-{len(table_ids)}")
            tables_by_page[source_number].append({**{key: block.get(key) for key in
                                                   ("row", "column", "text", "bbox")},
                                                  "table_id": mapped_table_id})
    _dev_log("dev_pdf_table_review stage=docling_tables_ready expectedCandidatePageCount=%d actualProcessedPageCount=%d tableCellCount=%d elapsedMs=%d route=candidate_pages_only recoveryAction=merge_cells_by_geometry outcome=success",
             len(page_numbers), len(extracted_pages), sum(len(cells) for cells in tables_by_page.values()),
             round((time.monotonic() - started) * 1000))
    return tables_by_page


def _merged_page_text(text_blocks: list[dict[str, object]], cells: list[dict[str, object]]) -> str:
    """Keep PyMuPDF prose, replacing text inside Docling table bounds with structured rows."""
    tables: dict[str, list[dict[str, object]]] = defaultdict(list)
    for index, cell in enumerate(cells):
        tables[str(cell.get("table_id") or f"table-{index}")].append(cell)
    table_regions: list[tuple[float, float, float, float]] = []
    table_text: list[tuple[float, str]] = []
    for table_id, table_cells in tables.items():
        boxes = [cell.get("bbox") for cell in table_cells if isinstance(cell.get("bbox"), (list, tuple)) and len(cell["bbox"]) == 4]
        if not boxes:
            continue
        region = (min(float(box[0]) for box in boxes), min(float(box[1]) for box in boxes),
                  max(float(box[2]) for box in boxes), max(float(box[3]) for box in boxes))
        table_regions.append(region)
        rows: dict[int, dict[int, str]] = defaultdict(dict)
        for cell in table_cells:
            if cell.get("row") is not None and cell.get("column") is not None:
                rows[int(cell["row"])][int(cell["column"])] = str(cell.get("text") or "")
        rendered = [" | ".join(row[column] for column in sorted(row)) for _, row in sorted(rows.items())]
        table_text.append((region[1], f"[Docling 표 {table_id}]\n" + "\n".join(rendered)))

    pieces: list[tuple[float, float, str]] = []
    for block in text_blocks:
        box = block.get("bbox")
        if not isinstance(box, (list, tuple)) or len(box) != 4:
            continue
        center_x = (float(box[0]) + float(box[2])) / 2
        center_y = (float(box[1]) + float(box[3])) / 2
        if any(x0 <= center_x <= x1 and y0 <= center_y <= y1 for x0, y0, x1, y1 in table_regions):
            continue
        pieces.append((float(box[1]), float(box[0]), str(block.get("text") or "")))
    pieces.extend((y, -1.0, text) for y, text in table_text)
    return "\n\n".join(text for _, _, text in sorted(pieces) if text.strip())


def _selected_table_candidates(review_path: Path, candidates: list[dict[str, object]]) -> list[dict[str, object]]:
    decisions = json.loads(review_path.read_text(encoding="utf-8"))
    if not isinstance(decisions, list):
        raise ValueError("TABLE_REVIEW_MUST_BE_A_LIST")
    candidate_by_page = {int(candidate["page_number"]): candidate for candidate in candidates}
    candidate_pages = set(candidate_by_page)
    seen: set[int] = set()
    selected = []
    labels = {"TABLE_PRESENT": "TABLE_PRESENT", "NO_TABLE": "NO_TABLE", "UNSURE": "UNSURE",
              "표 있음": "TABLE_PRESENT", "표 없음": "NO_TABLE", "판단 보류": "UNSURE"}
    for row in decisions:
        if not isinstance(row, dict) or not isinstance(row.get("page_number"), int):
            raise ValueError("INVALID_TABLE_REVIEW_ROW")
        number = row["page_number"]
        if number not in candidate_pages or number in seen:
            raise ValueError("UNKNOWN_OR_DUPLICATE_REVIEW_PAGE")
        seen.add(number)
        review = labels.get(row.get("review"))
        if review is None and row.get("review") is not None:
            raise ValueError("INVALID_TABLE_REVIEW_DECISION")
        candidate_by_page[number]["review"] = review
        if review == "TABLE_PRESENT":
            selected.append(candidate_by_page[number])
    if seen != candidate_pages:
        raise ValueError("TABLE_REVIEW_PAGE_SET_MISMATCH")
    return selected


def _write_review(output: Path, pdf_path: Path, candidates: list[dict[str, object]],
                  pages: list[dict[str, object]]) -> None:
    output.mkdir(parents=True, exist_ok=True)
    import fitz

    with fitz.open(pdf_path) as source:
        source_page_count = len(source)
        for candidate in candidates:
            page = source[int(candidate["page_number"]) - 1]
            page.get_pixmap(matrix=fitz.Matrix(1.0, 1.0), alpha=False).save(output / str(candidate["image"]))
    sections = []
    for candidate in candidates:
        number = int(candidate["page_number"])
        image_name = str(candidate["image"])
        signals = html.escape(json.dumps(candidate["signals"], ensure_ascii=False, indent=2))
        extracted_text = html.escape(str(candidate["pymupdf_text"]))
        docling_tables = candidate.get("docling_tables", [])
        docling_text = (html.escape(json.dumps(docling_tables, ensure_ascii=False, indent=2))
                        if candidate.get("docling_processed") else "Docling 실행 전: 사람이 ‘표 있음’을 선택해야 처리됩니다.")
        merged_text = html.escape(str(candidate.get("merged_text", candidate["pymupdf_text"])))
        review = candidate.get("review")
        options = [("", "선택"), ("TABLE_PRESENT", "표 있음"),
                   ("NO_TABLE", "표 없음"), ("UNSURE", "판단 보류")]
        select = "".join(f'<option value="{value}"{" selected" if review == value else ""}>{label}</option>'
                         for value, label in options)
        sections.append(
            f'<article data-page="{number}"><h2>PDF {number}쪽</h2>'
            f'<label>검수 결과 <select>{select}</select></label>'
            f'<img loading="lazy" src="{html.escape(image_name)}" alt="PDF {number}쪽 원본">'
            f'<h3>후보 근거</h3><pre>{signals}</pre><h3>PyMuPDF 텍스트 추출</h3><pre>{extracted_text}</pre>'
            f'<h3>Docling 표 셀</h3><pre>{docling_text}</pre><h3>통합 추출본</h3><pre>{merged_text}</pre></article>'
        )
    document = """<!doctype html><html lang="ko"><meta charset="utf-8"><title>PDF 표 후보 검수</title>
<style>body{font:15px system-ui;max-width:1200px;margin:2rem auto;color:#222}article{border-top:2px solid #555;padding:1rem 0}img{display:block;max-width:100%;height:auto;margin:1rem 0}pre{white-space:pre-wrap;background:#f4f4f4;padding:1rem;max-height:24rem;overflow:auto}select,button{font:inherit;padding:.4rem}</style>
<h1>PDF 표 후보 검수</h1><p>후보 페이지를 원본과 대조해 ‘표 있음’을 선택하고 검수 JSON을 저장하세요. 두 번째 실행에서는 선택한 페이지만 Docling으로 표를 인식합니다.</p>
<button id="download">검수 결과 JSON 다운로드</button>
""" + "\n".join(sections) + """
<script>document.querySelector('#download').onclick=()=>{const rows=[...document.querySelectorAll('article')].map(a=>({page_number:+a.dataset.page,review:a.querySelector('select').value||null}));const b=new Blob([JSON.stringify(rows,null,2)],{type:'application/json'});const u=URL.createObjectURL(b);const x=document.createElement('a');x.href=u;x.download='table-review.json';x.click();URL.revokeObjectURL(u)}</script></html>"""
    (output / "review.html").write_text(document, encoding="utf-8")
    manifest = {"source_pdf": pdf_path.name, "source_page_count": source_page_count,
                "sha256": hashlib.sha256(pdf_path.read_bytes()).hexdigest(),
                "candidate_page_count": len(candidates), "candidates": candidates}
    (output / "candidates.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    candidate_by_page = {int(candidate["page_number"]): candidate for candidate in candidates}
    extracted = {"source_pdf": pdf_path.name, "source_page_count": source_page_count,
                 "pages": [
        {"page_number": page["page_number"],
         "pymupdf_text": page["pymupdf_text"],
         "docling_table_cells": candidate_by_page.get(int(page["page_number"]), {}).get("docling_tables", []),
         "merged_text": _merged_page_text(
             page["pymupdf_blocks"],
             candidate_by_page.get(int(page["page_number"]), {}).get("docling_tables", [])),
         "table_candidate": int(page["page_number"]) in candidate_by_page}
        for page in pages]}
    (output / "merged_extraction.json").write_text(
        json.dumps(extracted, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--docling-review", type=Path,
                        help="process only pages marked TABLE_PRESENT in the review JSON from the first run")
    args = parser.parse_args()
    pdf_path = args.pdf.expanduser().resolve(strict=True)
    if pdf_path.suffix.casefold() != ".pdf":
        parser.error("--pdf must point to a PDF")
    output = args.output.expanduser().resolve()
    if args.docling_review:
        review_path = args.docling_review.expanduser().resolve(strict=True)
        manifest_path = output / "candidates.json"
        if not manifest_path.is_file():
            parser.error("--docling-review requires a first-run output directory")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        current_hash = hashlib.sha256(pdf_path.read_bytes()).hexdigest()
        if manifest.get("sha256") != current_hash:
            parser.error("review output belongs to a different PDF")
    elif output.exists() and any(output.iterdir()):
        parser.error("--output must be a new or empty directory")
    output.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    _dev_log("dev_pdf_table_review stage=started expectedInput=pdf actualFilename=%s route=pymupdf_candidate_scan outcome=started", pdf_path.name)
    try:
        pages, candidates = _scan_pdf(pdf_path)
        _dev_log("dev_pdf_table_review stage=candidates_ready expectedPageCount=from_pdf actualCandidatePageCount=%d route=fast_page_signals recoveryAction=render_candidates_only outcome=success",
                 len(candidates))
        if args.docling_review:
            decisions = _selected_table_candidates(review_path, candidates)
            _dev_log("dev_pdf_table_review stage=human_selection_loaded expectedCandidateCount=%d actualSelectedTablePageCount=%d route=docling_selected_pages_only recoveryAction=exclude_unselected_pages outcome=ready",
                     len(candidates), len(decisions))
            if decisions:
                tables = _docling_candidate_tables(pdf_path, decisions)
                for candidate in decisions:
                    candidate["docling_processed"] = True
                    candidate["review"] = "TABLE_PRESENT"
                    cells = tables[int(candidate["page_number"])]
                    candidate["docling_tables"] = cells
                    candidate["merged_text"] = _merged_page_text(candidate["pymupdf_blocks"], cells)
        else:
            for candidate in candidates:
                candidate["docling_processed"] = False
                candidate["docling_tables"] = []
                candidate["merged_text"] = candidate["pymupdf_text"]
        _write_review(output, pdf_path, candidates, pages)
    except Exception as exc:
        _dev_log("dev_pdf_table_review stage=failed expectedOutput=review_bundle actualFailure=%s route=none recoveryAction=inspect_pdf_and_adapter elapsedMs=%d outcome=failed",
                 type(exc).__name__, round((time.monotonic() - started) * 1000))
        raise
    _dev_log("dev_pdf_table_review stage=completed expectedPageCount=from_pdf actualCandidatePageCount=%d actualRenderedPageCount=%d elapsedMs=%d route=human_table_review outcome=success",
             len(candidates), len(candidates), round((time.monotonic() - started) * 1000))
    print(json.dumps({"pdf": pdf_path.name, "candidate_page_count": len(candidates),
                      "extracted_page_count": len(pages),
                      "docling_processed_page_count": sum(bool(candidate.get("docling_processed")) for candidate in candidates),
                      "docling_table_cell_count": sum(len(candidate.get("docling_tables", ())) for candidate in candidates),
                      "review_html": str(args.output.resolve() / "review.html")}, ensure_ascii=False))
    return 0


def _dev_log(message: str, *args: object) -> None:
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
        _LOGGER.info(message, *args)


if __name__ == "__main__":
    raise SystemExit(main())
