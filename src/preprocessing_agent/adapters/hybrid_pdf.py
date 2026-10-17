"""Combine PyMuPDF's positioned text with Docling's table-cell extraction."""
from __future__ import annotations

import logging
import os
import re
import time
from collections import Counter
from pathlib import Path
from typing import Any, Mapping

_LOGGER = logging.getLogger(__name__)
_AUTO_RESOLVER = object()


class HybridPdfAdapter:
    provider_name = "hybrid"

    def __init__(self, pymupdf_extractor: Any | None = None, docling_extractor: Any | None = None,
                 table_resolver: Any = _AUTO_RESOLVER) -> None:
        if pymupdf_extractor is None:
            from preprocessing_agent.parsers.pdf import _default_extractor
            pymupdf_extractor = _default_extractor
        if docling_extractor is None:
            from preprocessing_agent.adapters.docling_pdf import DoclingPdfAdapter
            docling_extractor = DoclingPdfAdapter()
        self._pymupdf_extractor = pymupdf_extractor
        self._docling_extractor = docling_extractor
        if table_resolver is _AUTO_RESOLVER:
            from preprocessing_agent.adapters.llm_table_extractor import AiProviderTableExtractor
            table_resolver = AiProviderTableExtractor.from_environment()
        self._table_resolver = table_resolver

    def extract(self, source: Path) -> list[Mapping[str, Any]]:
        started = time.monotonic()
        source = Path(source)
        _dev_log("dev_preprocessing_pdf_provider stage=started provider=hybrid expectedProviders=pymupdf_and_docling actualProviders=pymupdf_and_docling route=positioned_text_plus_table_cells outcome=started")
        try:
            pymupdf_pages = list(self._pymupdf_extractor(source))
        except Exception as exc:
            _dev_log("dev_preprocessing_pdf_provider stage=pymupdf_failed provider=hybrid expectedBaseTextProvider=pymupdf actualFailure=%s route=none recoveryAction=fail_extraction elapsedMs=%d outcome=failed",
                     type(exc).__name__, round((time.monotonic() - started) * 1000))
            raise
        try:
            docling_pages = self._docling_extractor.extract(source)
        except Exception as exc:
            _dev_log("dev_preprocessing_pdf_provider stage=docling_failed provider=hybrid expectedTableProvider=docling actualFailure=%s pymupdfPageCount=%d route=pymupdf_text_fallback recoveryAction=preserve_pymupdf_pages elapsedMs=%d outcome=partial",
                     type(exc).__name__, len(pymupdf_pages), round((time.monotonic() - started) * 1000))
            return [{**page, "capability_error": "DOCLING_TABLE_EXTRACTION_FAILED",
                     "docling_merge_diagnostics": {"status": "failed", "failure_type": type(exc).__name__}}
                    for page in pymupdf_pages]

        docling_by_page = {int(page["page_number"]): page for page in docling_pages}
        merged: list[Mapping[str, Any]] = []
        table_count = cell_count = replaced_block_count = 0
        for source_page in pymupdf_pages:
            page_number = int(source_page["page_number"])
            docling_page = docling_by_page.get(page_number, {})
            pymupdf_cells = [block for block in source_page.get("blocks", ())
                             if block.get("kind") == "table_cell" and block.get("table_id")]
            table_cells = [block for block in docling_page.get("blocks", ())
                           if block.get("kind") == "table_cell" and block.get("table_id")]
            pymupdf_regions = _table_regions(pymupdf_cells)
            docling_regions = _table_regions(table_cells)
            selected_docling_ids: set[str] = set()
            replaced_pymupdf_ids: set[str] = set()
            table_decisions = []
            ambiguous_conflicts = []
            for docling_id, (docling_bbox, docling_text) in docling_regions.items():
                overlaps = [
                    (pymupdf_id, region)
                    for pymupdf_id, region in pymupdf_regions.items()
                    if _bbox_overlap_ratio(docling_bbox, region[0]) >= 0.5
                ]
                if not overlaps:
                    selected_docling_ids.add(docling_id)
                    continue
                if len(overlaps) > 1 or any(
                    sum(_bbox_overlap_ratio(other_bbox, region[0]) >= 0.5
                        for other_bbox, _ in docling_regions.values()) > 1
                    for _, region in overlaps
                ):
                    decision = {
                        "docling_table_id": docling_id,
                        "pymupdf_table_ids": [table_id for table_id, _ in overlaps],
                        "selected_provider": "pymupdf",
                        "reason": "ambiguous_overlapping_regions_preserve_native_table",
                    }
                    table_decisions.append(decision)
                    ambiguous_conflicts.append({
                        "bbox": _bbox_union(docling_bbox, *[region[0] for _, region in overlaps]),
                        "docling_ids": {docling_id},
                        "pymupdf_ids": {table_id for table_id, _ in overlaps},
                    })
                    _dev_log("dev_preprocessing_table_resolution stage=conflict pageNumber=%d doclingTableId=%s pymupdfTableIds=%s expectedOneToOneOverlap=true actualOverlapCount=%d selectedProvider=pymupdf reason=ambiguous_overlapping_regions recoveryAction=preserve_native_cells outcome=resolved_conservatively",
                             page_number, docling_id,
                             ",".join(table_id for table_id, _ in overlaps), len(overlaps))
                    continue

                pymupdf_id, (pymupdf_bbox, pymupdf_text) = overlaps[0]
                comparison_bbox = _bbox_union(docling_bbox, pymupdf_bbox)
                source_text = _region_source_text(source_page.get("blocks", ()), comparison_bbox)
                pymupdf_score = _text_fidelity_score(source_text, pymupdf_text)
                docling_score = _text_fidelity_score(source_text, docling_text)
                selected_provider = "docling" if docling_score > pymupdf_score else "pymupdf"
                if selected_provider == "docling":
                    selected_docling_ids.add(docling_id)
                    replaced_pymupdf_ids.add(pymupdf_id)
                decision = {
                    "docling_table_id": docling_id,
                    "pymupdf_table_id": pymupdf_id,
                    "selected_provider": selected_provider,
                    "source_token_count": len(_tokens(source_text)),
                    "pymupdf_text_fidelity": round(pymupdf_score, 4),
                    "docling_text_fidelity": round(docling_score, 4),
                }
                table_decisions.append(decision)
                _dev_log("dev_preprocessing_table_resolution stage=conflict pageNumber=%d doclingTableId=%s pymupdfTableId=%s selectionRule=max_source_text_fidelity sourceTokenCount=%d pymupdfFidelity=%.4f doclingFidelity=%.4f selectedProvider=%s reason=%s recoveryAction=keep_one_table_representation outcome=resolved",
                         page_number, docling_id, pymupdf_id, len(_tokens(source_text)),
                         pymupdf_score, docling_score, selected_provider,
                         "higher_source_text_fidelity" if pymupdf_score != docling_score else "fidelity_tie_preserve_native")

            resolved_cells = []
            resolved_regions: dict[str, tuple[float, float, float, float]] = {}
            if self._table_resolver is not None:
                for conflict in _merge_conflicts(ambiguous_conflicts):
                    candidates = [
                        _candidate_table("pymupdf", table_id, pymupdf_regions[table_id], pymupdf_cells, conflict["bbox"])
                        for table_id in sorted(conflict["pymupdf_ids"])
                        if table_id in pymupdf_regions
                    ] + [
                        _candidate_table("docling", table_id, docling_regions[table_id], table_cells, conflict["bbox"])
                        for table_id in sorted(conflict["docling_ids"])
                        if table_id in docling_regions
                    ]
                    try:
                        cells, regions = _resolve_with_ai(source, page_number, conflict["bbox"], candidates,
                                                          source_page.get("blocks", ()), self._table_resolver)
                    except Exception as exc:
                        table_decisions.append({"pymupdf_table_ids": sorted(conflict["pymupdf_ids"]),
                                                "docling_table_ids": sorted(conflict["docling_ids"]),
                                                "selected_provider": "pymupdf",
                                                "reason": f"ai_resolution_failed:{type(exc).__name__}"})
                        _dev_log("dev_preprocessing_table_resolution stage=failed pageNumber=%d expectedCandidateCount=%d actualFailure=%s recoveryAction=preserve_pymupdf_cells outcome=warning",
                                 page_number, len(candidates), type(exc).__name__)
                        continue
                    if cells:
                        resolved_cells.extend(cells)
                        resolved_regions.update(regions)
                        replaced_pymupdf_ids.update(conflict["pymupdf_ids"])
                        table_decisions.append({"pymupdf_table_ids": sorted(conflict["pymupdf_ids"]),
                                                "docling_table_ids": sorted(conflict["docling_ids"]),
                                                "selected_provider": "llm_resolution",
                                                "resolved_table_count": len(regions),
                                                "reason": "visually_checked_and_source_text_preserved"})
                    else:
                        table_decisions.append({"pymupdf_table_ids": sorted(conflict["pymupdf_ids"]),
                                                "docling_table_ids": sorted(conflict["docling_ids"]),
                                                "selected_provider": "pymupdf",
                                                "reason": "ai_resolution_not_more_complete_preserve_native"})
            elif ambiguous_conflicts:
                _dev_log("dev_preprocessing_table_resolution stage=skipped_unconfigured pageNumber=%d expectedConflictCount=%d actualResolver=unavailable route=pymupdf_fallback recoveryAction=preserve_native_table_cells outcome=warning",
                         page_number, len(_merge_conflicts(ambiguous_conflicts)))

            selected_docling_regions = {
                table_id: region for table_id, region in docling_regions.items()
                if table_id in selected_docling_ids
            }
            table_regions = {**selected_docling_regions,
                             **{table_id: (bbox, "") for table_id, bbox in resolved_regions.items()}}
            kept_blocks = [block for block in source_page.get("blocks", ())
                           if block.get("table_id") not in replaced_pymupdf_ids
                           and not _inside_any_table(block.get("bbox"), block.get("text", ""), table_regions)]
            replaced_block_count += len(source_page.get("blocks", ())) - len(kept_blocks)
            kept_blocks = [
                {**block, "table_id": f"pymupdf:{block['table_id']}",
                 "block_id": f"pymupdf:{block['block_id']}"}
                if block.get("table_id") in pymupdf_regions else block
                for block in kept_blocks
            ]
            cells = [{**{key: value for key, value in block.items() if key != "table_bbox"},
                      "table_id": f"docling:{block['table_id']}",
                      "block_id": f"docling:{block['block_id']}"}
                     for block in table_cells if block.get("table_id") in selected_docling_ids]
            cells.extend(resolved_cells)
            page_missing = page_number not in docling_by_page
            final_table_regions = {
                **{f"pymupdf:{table_id}": region[0] for table_id, region in pymupdf_regions.items()
                   if table_id not in replaced_pymupdf_ids},
                **{f"docling:{table_id}": region[0] for table_id, region in selected_docling_regions.items()},
                **resolved_regions,
            }
            merged.append({**source_page, "blocks": [*kept_blocks, *cells],
                           **({"capability_error": "DOCLING_PAGE_MISSING"} if page_missing else {}),
                           "docling_merge_diagnostics": {
                               "status": "page_missing" if page_missing else "tables_merged" if final_table_regions else "no_docling_tables",
                               "table_count": len(final_table_regions),
                               "cell_count": sum(block.get("kind") == "table_cell" for block in kept_blocks) + len(cells),
                               "replaced_pymupdf_block_count": len(source_page.get("blocks", ())) - len(kept_blocks),
                               "table_regions": final_table_regions,
                               "table_decisions": table_decisions,
                           }})
            table_count += len(final_table_regions)
            cell_count += merged[-1]["docling_merge_diagnostics"]["cell_count"]
        _dev_log("dev_preprocessing_pdf_provider stage=completed provider=hybrid pymupdfPageCount=%d doclingPageCount=%d expectedMergeKey=page_and_bbox actualMergedTableCount=%d actualMergedCellCount=%d replacedPymupdfBlockCount=%d elapsedMs=%d route=positioned_text_plus_table_cells outcome=success",
                 len(pymupdf_pages), len(docling_pages), table_count, cell_count,
                 replaced_block_count, round((time.monotonic() - started) * 1000))
        return merged


def _table_regions(cells: list[Mapping[str, Any]]) -> dict[str, tuple[tuple[float, float, float, float], str]]:
    grouped: dict[str, list[tuple[float, float, float, float]]] = {}
    texts: dict[str, list[str]] = {}
    for cell in cells:
        table_id = str(cell["table_id"])
        raw = cell.get("table_bbox") or cell.get("bbox")
        if isinstance(raw, (list, tuple)) and len(raw) == 4:
            grouped.setdefault(table_id, []).append(tuple(float(value) for value in raw))
            texts.setdefault(table_id, []).append(str(cell.get("text", "")))
    return {table_id: ((min(box[0] for box in boxes), min(box[1] for box in boxes),
                        max(box[2] for box in boxes), max(box[3] for box in boxes)),
                       " ".join(texts[table_id]))
            for table_id, boxes in grouped.items()}


def _bbox_overlap_ratio(left: tuple[float, ...], right: tuple[float, ...]) -> float:
    left_area = max(0.0, left[2] - left[0]) * max(0.0, left[3] - left[1])
    right_area = max(0.0, right[2] - right[0]) * max(0.0, right[3] - right[1])
    if not left_area or not right_area:
        return 0.0
    overlap = (max(0.0, min(left[2], right[2]) - max(left[0], right[0]))
               * max(0.0, min(left[3], right[3]) - max(left[1], right[1])))
    return overlap / min(left_area, right_area)


def _bbox_union(left: tuple[float, ...], *others: tuple[float, ...]) -> tuple[float, float, float, float]:
    boxes = (left, *others)
    return (min(box[0] for box in boxes), min(box[1] for box in boxes),
            max(box[2] for box in boxes), max(box[3] for box in boxes))


def _candidate_table(provider: str, table_id: str,
                     region: tuple[tuple[float, ...], str],
                     cells: list[Mapping[str, Any]], crop: tuple[float, ...]) -> dict[str, Any]:
    width, height = crop[2] - crop[0], crop[3] - crop[1]

    def normalized(box: Any) -> list[int]:
        left, top, right, bottom = (float(value) for value in box)
        return [round(max(0, min(1000, (left - crop[0]) * 1000 / width))),
                round(max(0, min(1000, (top - crop[1]) * 1000 / height))),
                round(max(0, min(1000, (right - crop[0]) * 1000 / width))),
                round(max(0, min(1000, (bottom - crop[1]) * 1000 / height)))]

    candidate_cells = []
    for cell in cells:
        if str(cell.get("table_id")) != table_id or not isinstance(cell.get("bbox"), (list, tuple)):
            continue
        candidate_cells.append({"row": int(cell.get("row", 0)), "column": int(cell.get("column", 0)),
                                "text": str(cell.get("text", "")), "bbox": normalized(cell["bbox"])})
    return {"provider": provider, "tableId": table_id, "bbox": normalized(region[0]), "cells": candidate_cells}


def _merge_conflicts(conflicts: list[dict[str, Any]]) -> list[dict[str, Any]]:
    merged: list[dict[str, Any]] = []
    for conflict in conflicts:
        current = {"bbox": conflict["bbox"], "docling_ids": set(conflict["docling_ids"]),
                   "pymupdf_ids": set(conflict["pymupdf_ids"])}
        index = 0
        while index < len(merged):
            prior = merged[index]
            if current["docling_ids"] & prior["docling_ids"] or current["pymupdf_ids"] & prior["pymupdf_ids"]:
                current["bbox"] = _bbox_union(current["bbox"], prior["bbox"])
                current["docling_ids"].update(prior["docling_ids"])
                current["pymupdf_ids"].update(prior["pymupdf_ids"])
                merged.pop(index)
                index = 0
            else:
                index += 1
        merged.append(current)
    return merged


def _resolve_with_ai(source: Path, page_number: int, bbox: tuple[float, ...],
                     candidates: list[dict[str, Any]], source_blocks: Any,
                     resolver: Any) -> tuple[list[dict[str, Any]], dict[str, tuple[float, float, float, float]]]:
    import fitz
    from preprocessing_agent.adapters.llm_table_extractor import page_table_blocks

    with fitz.open(source) as document:
        page = document[page_number - 1]
        clip = fitz.Rect(*bbox) & page.rect
        if clip.is_empty or clip.is_infinite:
            return [], {}
        image = page.get_pixmap(matrix=fitz.Matrix(2, 2), clip=clip, alpha=False).tobytes("png")
        tables = resolver.resolve(image, candidate_tables=candidates)
    if not tables:
        return [], {}
    for table in tables:
        raw_cells = table.get("cells") if isinstance(table, Mapping) else None
        positions = [(cell.get("row"), cell.get("column")) for cell in raw_cells
                     if isinstance(cell, Mapping)] if isinstance(raw_cells, list) else []
        if not positions or len(positions) != len(set(positions)):
            _dev_log("dev_preprocessing_table_resolution stage=response_rejected pageNumber=%d expectedUniqueCellPositions=true actualCellCount=%d route=pymupdf_fallback recoveryAction=preserve_native_table_cells outcome=warning",
                     page_number, len(positions))
            return [], {}
    local_width, local_height = clip.width, clip.height
    cells, _, rejected = page_table_blocks(tables, page_number=page_number,
                                           page_width=local_width, page_height=local_height)
    if rejected or not cells:
        _dev_log("dev_preprocessing_table_resolution stage=response_rejected pageNumber=%d candidateTableCount=%d expectedRejectedCellCount=0 actualRejectedCellCount=%d route=pymupdf_fallback recoveryAction=preserve_native_table_cells outcome=warning",
                 page_number, len(candidates), len(rejected))
        return [], {}

    source_text = _region_source_text(source_blocks, bbox)
    candidate_text_by_provider: dict[str, list[str]] = {}
    for candidate in candidates:
        candidate_text_by_provider.setdefault(candidate["provider"], []).extend(
            str(cell.get("text", "")) for cell in candidate["cells"])
    source_score = max((_text_fidelity_score(source_text, " ".join(texts))
                        for texts in candidate_text_by_provider.values()), default=0.0)
    ai_score = _text_fidelity_score(source_text, " ".join(str(cell.get("text", "")) for cell in cells))
    _dev_log("dev_preprocessing_table_resolution stage=quality_checked pageNumber=%d candidateTableCount=%d outputTableCount=%d sourceTokenCount=%d bestCandidateFidelity=%.4f aiFidelity=%.4f expectedAiFidelityAtLeastBestCandidate=%s actualAccepted=%s route=visual_candidate_adjudication recoveryAction=%s outcome=%s",
             page_number, len(candidates), len({cell["table_id"] for cell in cells}), len(_tokens(source_text)),
             source_score, ai_score, source_score, ai_score >= source_score,
             "use_ai_cells" if ai_score >= source_score else "preserve_native_table_cells",
             "accepted" if ai_score >= source_score else "fallback")
    if not source_text or ai_score < source_score:
        return [], {}

    local_regions = _table_regions(cells)
    resolved_regions = {}
    for table_id, (local_bbox, _) in local_regions.items():
        resolved_regions[f"llm:{table_id}"] = (local_bbox[0] + clip.x0, local_bbox[1] + clip.y0,
                                                local_bbox[2] + clip.x0, local_bbox[3] + clip.y0)
    resolved_cells = []
    for cell in cells:
        local = cell["bbox"]
        resolved_cells.append({**cell, "table_id": f"llm:{cell['table_id']}",
                               "block_id": f"llm:{cell['block_id']}",
                               "bbox": (local[0] + clip.x0, local[1] + clip.y0,
                                        local[2] + clip.x0, local[3] + clip.y0),
                               "table_extraction_method": "llm_vision_resolution"})
    return resolved_cells, resolved_regions


def _region_source_text(blocks: Any, bbox: tuple[float, ...]) -> str:
    parts = []
    for block in blocks:
        spans = block.get("spans", ())
        matched_spans = [str(span.get("text", "")) for span in spans
                         if len(span.get("bbox", ())) == 4
                         and _bbox_overlap_ratio(bbox, tuple(span["bbox"])) >= 0.5]
        if matched_spans:
            parts.extend(matched_spans)
            continue
        raw = block.get("bbox")
        if isinstance(raw, (list, tuple)) and len(raw) == 4 and _bbox_overlap_ratio(bbox, tuple(raw)) >= 0.5:
            parts.append(str(block.get("text", "")))
    return " ".join(parts)


def _text_fidelity_score(source_text: str, candidate_text: str) -> float:
    source_tokens = Counter(_tokens(source_text))
    candidate_tokens = Counter(_tokens(candidate_text))
    source_count = sum(source_tokens.values())
    candidate_count = sum(candidate_tokens.values())
    if not source_count or not candidate_count:
        return 0.0
    matched = sum(min(count, candidate_tokens[token]) for token, count in source_tokens.items())
    return 2 * matched / (source_count + candidate_count)


def _inside_any_table(raw_bbox: Any, source_text: Any,
                      regions: Mapping[str, tuple[tuple[float, float, float, float], str]]) -> bool:
    if not isinstance(raw_bbox, (list, tuple)) or len(raw_bbox) != 4:
        return False
    x0, y0, x1, y1 = (float(value) for value in raw_bbox)
    area = max(0.0, x1 - x0) * max(0.0, y1 - y0)
    if not area:
        return False
    source_tokens = _tokens(str(source_text))
    for region, table_text in regions.values():
        rx0, ry0, rx1, ry1 = region
        overlap = max(0.0, min(x1, rx1) - max(x0, rx0)) * max(0.0, min(y1, ry1) - max(y0, ry0))
        if overlap / area >= 0.5 and _text_coverage(source_tokens, _tokens(table_text)) >= 0.85:
            return True
    return False


def _tokens(value: str) -> list[str]:
    return re.findall(r"[\w]+", value.casefold())


def _text_coverage(source_tokens: list[str], table_tokens: list[str]) -> float:
    if not source_tokens:
        return 0.0
    available = Counter(table_tokens)
    matched = 0
    for token in source_tokens:
        if available[token]:
            matched += 1
            available[token] -= 1
    return matched / len(source_tokens)


def _dev_log(message: str, *args: Any) -> None:
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
        _LOGGER.info(message, *args)
