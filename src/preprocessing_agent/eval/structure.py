"""Offline structural accuracy evaluation for annotated PDF pages."""
from __future__ import annotations

import hashlib
import json
import logging
import math
import os
import re
import time
import unicodedata
from collections import Counter
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any

_LOG = logging.getLogger(__name__)
_PAGE_TYPES = {"text-native", "image-only", "mixed", "ambiguous", "form"}
_SECTION_RELATIONS = {"SAME_SECTION", "DIFFERENT_SECTION", "UNRESOLVED"}
_CONTENT_CONTINUITIES = {"NONE", "PARAGRAPH_CONTINUATION", "TABLE_CONTINUATION", "UNRESOLVED"}


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}


class StructureEvaluationError(ValueError):
    """Evaluation inputs do not satisfy the annotation contract."""


def _read_json(path: str | Path) -> dict[str, Any]:
    try:
        value = json.loads(Path(path).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise StructureEvaluationError(f"cannot read JSON input: {Path(path).name}") from exc
    if not isinstance(value, dict):
        raise StructureEvaluationError("version input must be a JSON object")
    return value


def _file_sha256(path: str | Path) -> str:
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def _bbox(value: Any, where: str) -> tuple[float, float, float, float]:
    if isinstance(value, dict):
        value = [value.get("x0"), value.get("y0"), value.get("x1"), value.get("y1")]
    if not isinstance(value, list) or len(value) != 4:
        raise StructureEvaluationError(f"{where} bbox must contain four PDF-point coordinates")
    try:
        box = tuple(float(x) for x in value)
    except (TypeError, ValueError) as exc:
        raise StructureEvaluationError(f"{where} bbox coordinates must be numbers") from exc
    if not all(math.isfinite(x) for x in box) or box[2] < box[0] or box[3] < box[1]:
        raise StructureEvaluationError(f"{where} bbox coordinates are invalid")
    return box  # type: ignore[return-value]


def _iou(a: tuple[float, ...], b: tuple[float, ...]) -> float:
    iw, ih = max(0., min(a[2], b[2]) - max(a[0], b[0])), max(0., min(a[3], b[3]) - max(a[1], b[1]))
    intersection = iw * ih
    area_a = max(0., a[2] - a[0]) * max(0., a[3] - a[1])
    area_b = max(0., b[2] - b[0]) * max(0., b[3] - b[1])
    union = area_a + area_b - intersection
    return intersection / union if union else float(a == b)


def _overlap_of_first(a: tuple[float, ...], b: tuple[float, ...]) -> float:
    intersection = max(0., min(a[2], b[2]) - max(a[0], b[0])) * max(0., min(a[3], b[3]) - max(a[1], b[1]))
    area = max(0., a[2] - a[0]) * max(0., a[3] - a[1])
    return intersection / area if area else 0.


def _norm(text: Any) -> str:
    return " ".join(unicodedata.normalize("NFC", str(text or "")).split())


def _text_key(text: Any) -> str:
    return re.sub(r"[^\w]", "", _norm(text).casefold(), flags=re.UNICODE)


def _prf(tp: int, pred: int, gold: int) -> dict[str, float | None]:
    return {"precision": tp / pred if pred else None, "recall": tp / gold if gold else None}


def _align_blocks(row: dict[str, Any], page: dict[str, Any]) -> tuple[dict[str, list[str]], set[str], set[str], set[str]]:
    """Align differently split text blocks by their page location and combined text."""
    predicted = page.get("blocks", [])
    order = page.get("layout", {}).get("ordered_block_ids", [])
    rank = {block_id: index for index, block_id in enumerate(order)}
    table_boxes = [_bbox(table["bbox"], "annotation table") for table in row.get("tables", [])]
    captions = {_norm(table["caption"]) for table in row.get("tables", []) if table.get("caption")}
    ignored_gold = {
        block["id"] for block in row.get("blocks", [])
        if _norm(block["text"]) in captions or any(
            _overlap_of_first(_bbox(block["bbox"], "annotation block"), table_box) >= .5 for table_box in table_boxes
        )
    }
    ignored_predicted = {
        block.get("block_id", "") for block in predicted
        if any(_overlap_of_first(_bbox(block["bbox"], "predicted block"), table_box) >= .5 for table_box in table_boxes)
    }
    aligned: dict[str, list[str]] = {}
    matched_predicted: set[str] = set()
    for gold in row.get("blocks", []):
        if gold["id"] in ignored_gold:
            continue
        gold_box = _bbox(gold["bbox"], "annotation block")
        candidates = [block for block in predicted if _overlap_of_first(
            _bbox(block["bbox"], "predicted block"), gold_box) >= .5]
        candidates.sort(key=lambda block: (rank.get(block.get("block_id", ""), float("inf")),
                                           block["bbox"][1], block["bbox"][0]))
        gold_text = _text_key(gold["text"])
        predicted_text = _text_key(" ".join(block.get("text", "") for block in candidates))
        similarity = SequenceMatcher(None, gold_text, predicted_text).ratio() if gold_text or predicted_text else 1.
        if similarity >= .8:
            ids = [str(block.get("block_id", "")) for block in candidates if block.get("block_id")]
            aligned[gold["id"]] = ids
            matched_predicted.update(ids)
    return aligned, matched_predicted, ignored_gold, ignored_predicted


def _page_map(version: dict[str, Any]) -> dict[int, dict[str, Any]]:
    if not isinstance(version.get("source_sha256"), str) or not isinstance(version.get("pages"), list):
        raise StructureEvaluationError("version must contain source_sha256 and pages")
    pages = {}
    for page in version["pages"]:
        if not isinstance(page, dict) or not isinstance(page.get("page_number"), int):
            raise StructureEvaluationError("each version page must have an integer page_number")
        if page["page_number"] < 1 or page["page_number"] in pages:
            raise StructureEvaluationError("version page numbers must be positive and unique")
        pages[page["page_number"]] = page
    return pages


def _table_grid(table: dict[str, Any], ignore_empty_rows: bool = True) -> list[list[str]]:
    rows = table.get("rows", [])
    if not rows:
        return []
    if isinstance(rows[0], list):
        grid = [[_norm(cell.get("text", "") if isinstance(cell, dict) else cell) for cell in row]
                for row in rows]
    else:
        cells = [cell for row in rows if isinstance(row, dict) for cell in row.get("cells", [])]
        cells.extend(cell for cell in table.get("cells", []) if isinstance(cell, dict))
        positions: dict[tuple[int, int], str] = {}
        for cell in cells:
            row_index, column_index = cell.get("row_index"), cell.get("column_index")
            if isinstance(row_index, int) and isinstance(column_index, int):
                positions[(row_index, column_index)] = _norm(cell.get("text", ""))
        if not positions:
            return []
        height = max(row for row, _ in positions) + 1
        width = max(column for _, column in positions) + 1
        grid = [[positions.get((row, column), "") for column in range(width)] for row in range(height)]
    return [row for row in grid if any(row)] if ignore_empty_rows else grid


def _heading_parents(tree: Any) -> dict[str, str | None]:
    parents: dict[str, str | None] = {}

    def visit(node: Any, parent: str | None) -> None:
        if not isinstance(node, dict):
            return
        block_ids = node.get("block_ids", [])
        level = node.get("level", 0)
        current = parent
        if type(level) is int and level > 0 and block_ids:
            heading_block_id = str(block_ids[0])
            parents[heading_block_id] = parent
            current = heading_block_id
        for child in node.get("children", []):
            visit(child, current)

    document_tree = tree.get("root") if isinstance(tree, dict) else None
    visit(document_tree, None)
    return parents


def _unresolved_heading_blocks(tree: Any) -> set[str]:
    unresolved: set[str] = set()

    def visit(node: Any) -> None:
        if not isinstance(node, dict):
            return
        if node.get("hierarchy_resolved", True) is False:
            unresolved.update(str(item) for item in node.get("block_ids", []))
        for child in node.get("children", []):
            visit(child)

    visit(tree.get("root") if isinstance(tree, dict) else None)
    return unresolved


def _heading_hierarchy_coverage(tree: Any) -> tuple[int, int]:
    total = resolved = 0

    def visit(node: Any) -> None:
        nonlocal total, resolved
        if not isinstance(node, dict):
            return
        if type(node.get("level")) is int and node["level"] > 0 and node.get("block_ids"):
            total += 1
            resolved += node.get("hierarchy_resolved", True) is not False
        for child in node.get("children", []):
            visit(child)

    visit(tree.get("root") if isinstance(tree, dict) else None)
    return resolved, total


def _block_section_paths(tree: Any) -> dict[str, tuple[str, ...] | None]:
    """Return resolved heading ancestry for each block in the exported document tree."""
    paths: dict[str, tuple[str, ...] | None] = {}

    def visit(node: Any, ancestors: tuple[str, ...], resolved: bool) -> None:
        if not isinstance(node, dict):
            return
        current_resolved = resolved and node.get("hierarchy_resolved", True) is not False
        current = ancestors
        node_id = node.get("node_id")
        if node_id is not None and str(node_id) != "root" and _norm(node.get("title")):
            current = (*ancestors, str(node_id))
        for block_id in node.get("block_ids", []):
            paths[str(block_id)] = current if current_resolved else None
        for child in node.get("children", []):
            visit(child, current, current_resolved)

    visit(tree.get("root") if isinstance(tree, dict) else None, (), True)
    return paths


def _section_node_children(tree: Any) -> dict[str, bool]:
    """Whether a represented section has child headings, making it too broad for same-section proof."""
    children: dict[str, bool] = {}

    def visit(node: Any) -> None:
        if not isinstance(node, dict):
            return
        node_id = node.get("node_id")
        if node_id is not None:
            children[str(node_id)] = any(
                isinstance(child, dict) and child.get("node_id") not in (None, "root")
                and _norm(child.get("title"))
                for child in node.get("children", [])
            )
        for child in node.get("children", []):
            visit(child)

    visit(tree.get("root") if isinstance(tree, dict) else None)
    return children


def _classify_section_relation(source_path: tuple[str, ...] | None,
                               destination_path: tuple[str, ...] | None,
                               section_children: dict[str, bool]) -> str:
    """Use only complete, resolved section leaves; shared or incomplete ancestry abstains."""
    if not source_path or not destination_path:
        return "UNRESOLVED"
    if source_path == destination_path:
        return "UNRESOLVED" if section_children.get(source_path[-1], False) else "SAME_SECTION"
    common = min(len(source_path), len(destination_path))
    if source_path[:common] == destination_path[:common] and common == min(len(source_path), len(destination_path)):
        return "UNRESOLVED"  # one endpoint is only known to share an ancestor with the other
    if section_children.get(source_path[-1], False) or section_children.get(destination_path[-1], False):
        return "UNRESOLVED"
    return "DIFFERENT_SECTION"


def _gold_rows(path: str | Path, source_hash: str) -> list[dict[str, Any]]:
    rows, seen = [], set()
    try:
        lines = Path(path).read_text(encoding="utf-8").splitlines()
        for line_no, line in enumerate(lines, 1):
            if not line.strip():
                continue
            row = json.loads(line)
            if not isinstance(row, dict):
                raise StructureEvaluationError(f"annotation line {line_no} must be an object")
            if row.get("source_sha256") != source_hash:
                raise StructureEvaluationError(f"source SHA mismatch at annotation line {line_no}")
            number = row.get("page_number")
            if not isinstance(number, int) or number < 1 or number in seen:
                raise StructureEvaluationError(f"invalid or duplicate page annotation at line {line_no}")
            seen.add(number)
            if "page_type" in row and row["page_type"] not in _PAGE_TYPES:
                raise StructureEvaluationError(f"invalid page_type at annotation line {line_no}")
            for key in ("blocks", "headings", "reading_order", "prose_regions", "tables", "section_continuations"):
                if key in row and not isinstance(row[key], list):
                    raise StructureEvaluationError(f"{key} must be an array at annotation line {line_no}")
            rows.append(row)
    except (OSError, json.JSONDecodeError) as exc:
        raise StructureEvaluationError("cannot read annotation JSONL") from exc
    return rows


def _validate_annotations(row: dict[str, Any], rows_by_page: dict[int, dict[str, Any]]) -> None:
    ids = set()
    for block in row.get("blocks", []):
        if not isinstance(block, dict) or not isinstance(block.get("id"), str) or block["id"] in ids:
            raise StructureEvaluationError("annotation blocks require unique string ids")
        ids.add(block["id"])
        if not isinstance(block.get("text"), str):
            raise StructureEvaluationError("annotation block text must be a string")
        _bbox(block.get("bbox"), "annotation block")
    for key in ("reading_order",):
        values = row.get(key, [])
        if not isinstance(values, list) or any(x not in ids for x in values) or len(values) != len(set(values)):
            raise StructureEvaluationError(f"{key} must contain unique annotation block ids")
    headings = row.get("headings", [])
    heading_ids = set()
    for h in headings:
        if not isinstance(h, dict):
            raise StructureEvaluationError("each heading annotation must be an object")
        if not isinstance(h.get("id"), str) or h["id"] in heading_ids:
            raise StructureEvaluationError("heading annotations require unique string ids")
        heading_ids.add(h["id"])
        if type(h.get("level")) is not int or h["level"] < 1:
            raise StructureEvaluationError("headings require an integer level")
        if "block_id" in h:
            if h["block_id"] not in ids or "anchor" in h:
                raise StructureEvaluationError("heading block_id must reference one annotation block")
        elif isinstance(h.get("anchor"), dict):
            anchor = h["anchor"]
            if not isinstance(anchor.get("text"), str) or not anchor["text"].strip():
                raise StructureEvaluationError("heading anchor requires text")
            _bbox(anchor.get("bbox"), "heading anchor")
        else:
            raise StructureEvaluationError("headings require block_id or anchor")
        if h.get("parent_id") is not None and h["parent_id"] not in {x.get("id") for x in headings if isinstance(x, dict)}:
            raise StructureEvaluationError("heading parent_id must reference an annotation heading id")
    for link in row.get("section_continuations", []):
        if not isinstance(link, dict):
            raise StructureEvaluationError("section continuation must be an object")
        for endpoint_name in ("source", "destination"):
            endpoint = link.get(endpoint_name)
            if not isinstance(endpoint, dict) or type(endpoint.get("page_number")) is not int or endpoint["page_number"] < 1:
                raise StructureEvaluationError("section continuation endpoints require a positive page_number")
            if "block_id" in endpoint:
                referenced_page = rows_by_page.get(endpoint["page_number"])
                if referenced_page is not None and endpoint["block_id"] not in {
                    block.get("id") for block in referenced_page.get("blocks", [])
                }:
                    raise StructureEvaluationError("section continuation block_id must reference a block on its page")
            else:
                anchor = endpoint.get("anchor")
                if not isinstance(anchor, dict) or not isinstance(anchor.get("text"), str) or not anchor["text"].strip():
                    raise StructureEvaluationError("context-only section continuation endpoints require anchor text")
                _bbox(anchor.get("bbox"), "section continuation anchor")
        relation = link.get("section_relation")
        if relation is not None and (not isinstance(relation, str) or relation not in _SECTION_RELATIONS):
            raise StructureEvaluationError("section continuation section_relation must be SAME_SECTION, DIFFERENT_SECTION, or UNRESOLVED")
        if "relation_types" in link:
            raise StructureEvaluationError("section continuation relation_types is obsolete; use section_relation and content_continuity")
        continuity = link.get("content_continuity")
        if continuity is not None and (not isinstance(continuity, str) or continuity not in _CONTENT_CONTINUITIES):
            raise StructureEvaluationError("section continuation content_continuity has an unsupported value")


def _endpoint_geometry(endpoint: dict[str, Any], rows_by_page: dict[int, dict[str, Any]]) -> tuple[str, tuple[float, ...]]:
    if "block_id" in endpoint:
        page = rows_by_page.get(endpoint["page_number"])
        block = next((item for item in page.get("blocks", []) if item.get("id") == endpoint["block_id"]), None) if page else None
        if block is None:
            raise StructureEvaluationError("section continuation block_id requires a page annotation or an anchor")
        return block["text"], _bbox(block["bbox"], "section continuation block")
    anchor = endpoint["anchor"]
    return anchor["text"], _bbox(anchor["bbox"], "section continuation anchor")


def _match_endpoint(endpoint: dict[str, Any], rows_by_page: dict[int, dict[str, Any]], pages: dict[int, dict[str, Any]]) -> str | None:
    text, box = _endpoint_geometry(endpoint, rows_by_page)
    page = pages.get(endpoint["page_number"])
    if page is None:
        return None
    candidates = []
    for predicted in page.get("blocks", []):
        predicted_text = _norm(predicted.get("text"))
        predicted_box = _bbox(predicted.get("bbox"), "predicted continuation block")
        if "anchor" in endpoint:
            if _norm(text) not in predicted_text:
                continue
            overlap = _overlap_of_first(box, predicted_box)
            if overlap >= .5:
                candidates.append((overlap, str(predicted.get("block_id", ""))))
        elif predicted_text == _norm(text):
            iou = _iou(box, predicted_box)
            if iou >= .5:
                candidates.append((iou, str(predicted.get("block_id", ""))))
    return max(candidates)[1] if candidates else None


def _match_heading_anchor(heading: dict[str, Any], page: dict[str, Any]) -> str | None:
    anchor = heading["anchor"]
    text, box = _norm(anchor["text"]), _bbox(anchor["bbox"], "heading anchor")
    candidates = []
    for predicted in page.get("blocks", []):
        predicted_text = _norm(predicted.get("text"))
        if text not in predicted_text:
            continue
        predicted_box = _bbox(predicted.get("bbox"), "predicted heading block")
        overlap = max(0., min(box[2], predicted_box[2]) - max(box[0], predicted_box[0])) * max(
            0., min(box[3], predicted_box[3]) - max(box[1], predicted_box[1]))
        anchor_area = max(0., box[2] - box[0]) * max(0., box[3] - box[1])
        if anchor_area and overlap / anchor_area >= .5:
            candidates.append((overlap / anchor_area, str(predicted.get("block_id", ""))))
    return max(candidates)[1] if candidates else None
    for region in row.get("prose_regions", []):
        if not isinstance(region, dict) or not isinstance(region.get("columns"), list) or not region["columns"]:
            raise StructureEvaluationError("prose regions require a bbox and at least one column")
        _bbox(region.get("bbox"), "prose region")
        for column in region["columns"]:
            _bbox(column, "expected column")
    for table in row.get("tables", []):
        if not isinstance(table, dict) or not isinstance(table.get("rows"), list):
            raise StructureEvaluationError("tables require bbox and rows")
        if "caption" in table and not isinstance(table["caption"], str):
            raise StructureEvaluationError("table caption must be a string")
        _bbox(table.get("bbox"), "annotation table")
        if any(not isinstance(cells, list) or any(not isinstance(cell, str) for cell in cells)
               for cells in table["rows"]):
            raise StructureEvaluationError("table rows must contain strings, including empty cells")
        row_widths = {len(cells) for cells in table["rows"]}
        if len(row_widths) > 1:
            raise StructureEvaluationError("table rows must be rectangular; use empty strings for empty cells")


def evaluate_structure(version_path: str | Path, annotations_path: str | Path) -> dict[str, Any]:
    """Compare human page annotations with an extraction version; absent categories are null."""
    started = time.monotonic()
    version = _read_json(version_path)
    pages = _page_map(version)
    source_hash = version.get("source_file_sha256", version["source_sha256"])
    annotations = _gold_rows(annotations_path, source_hash)
    rows_by_page = {row["page_number"]: row for row in annotations}
    tree_parents = _heading_parents(version.get("document_tree"))
    unresolved_heading_blocks = _unresolved_heading_blocks(version.get("document_tree"))
    hierarchy_resolved_count, hierarchy_heading_count = _heading_hierarchy_coverage(version.get("document_tree"))
    tree_section_paths = _block_section_paths(version.get("document_tree"))
    section_children = _section_node_children(version.get("document_tree"))
    if _diagnostics_enabled():
        continuation_input_count = sum(len(row.get("section_continuations", [])) for row in annotations)
        expected_relations = Counter(link.get("section_relation", "UNRESOLVED")
                                     for row in annotations for link in row.get("section_continuations", []))
        _LOG.info("dev_structure_eval stage=start versionSha256=%s annotationSha256=%s pageCount=%d pageBoundaryRelationCount=%d expectedSameSection=%d expectedDifferentSection=%d expectedUnresolved=%d outcome=started",
                  _file_sha256(version_path), _file_sha256(annotations_path), len(annotations), continuation_input_count,
                  expected_relations["SAME_SECTION"], expected_relations["DIFFERENT_SECTION"], expected_relations["UNRESOLVED"])
    page_type_correct = page_type_count = 0
    category_pages = Counter()
    predicted_blocks = gold_blocks = matched_blocks = matched_predicted_blocks = order_expected = 0
    heading_pred = heading_gold = heading_match = level_correct = level_resolved_count = parent_correct = heading_parent_count = parent_resolved_count = 0
    order_agree = order_pairs = order_covered = 0
    col_count_ok = col_count_total = 0
    column_ious: list[float] = []
    table_pred = table_gold = table_match = table_shape_matches = 0
    table_shape_diagnostics: list[dict[str, Any]] = []
    cells_correct = cells_total = 0
    continuation_gold = 0
    continuation_relation_gold = Counter()
    relation_confusion: Counter[tuple[str, str]] = Counter()
    relation_unmatched = 0
    continuity_gold = Counter()
    for row in annotations:
        _validate_annotations(row, rows_by_page)
        number = row["page_number"]
        if number not in pages:
            raise StructureEvaluationError(f"annotated page {number} is absent from version")
        page = pages[number]
        for category in ("blocks", "headings", "reading_order", "prose_regions", "tables", "section_continuations"):
            category_pages[category] += category in row
        for link in row.get("section_continuations", []):
            continuation_gold += 1
            source_block_id = _match_endpoint(link["source"], rows_by_page, pages)
            destination_block_id = _match_endpoint(link["destination"], rows_by_page, pages)
            gold_relation = link.get("section_relation", "UNRESOLVED")
            continuation_relation_gold[gold_relation] += 1
            continuity_gold[link.get("content_continuity", "UNRESOLVED")] += 1
            if source_block_id is None or destination_block_id is None:
                relation_unmatched += 1
                continue
            source_path = tree_section_paths.get(source_block_id) if source_block_id is not None else None
            destination_path = tree_section_paths.get(destination_block_id) if destination_block_id is not None else None
            predicted_relation = _classify_section_relation(source_path, destination_path, section_children)
            relation_confusion[(gold_relation, predicted_relation)] += 1
        if "page_type" in row:
            page_type_count += 1
            page_type_correct += row["page_type"] == page.get("page_classification")
        pred = page.get("blocks", [])
        gold = row.get("blocks", [])
        aligned_blocks, matched_predicted, ignored_gold, ignored_predicted = _align_blocks(row, page)
        predicted_blocks += len(pred) - len(ignored_predicted)
        gold_blocks += len(gold) - len(ignored_gold)
        matched_blocks += len(aligned_blocks)
        matched_predicted_blocks += len(matched_predicted)
        matches = {gold_id: predicted_ids[0] for gold_id, predicted_ids in aligned_blocks.items() if predicted_ids}
        gold_headings = row.get("headings", [])
        pred_heads = page.get("heading_associations", [])
        heading_pred += len(pred_heads); heading_gold += len(gold_headings)
        pred_by_block = {h.get("heading_block_id"): h for h in pred_heads}
        heading_matches: dict[str, str] = {}
        for h in gold_headings:
            pid = (matches.get(h["block_id"]) if "block_id" in h else _match_heading_anchor(h, page))
            if pid is not None and pid in pred_by_block:
                heading_matches[h["id"]] = pid
        gold_order = [block_id for block_id in row.get("reading_order", []) if block_id not in ignored_gold]
        pred_order = page.get("layout", {}).get("ordered_block_ids", [])
        pred_rank = {x: i for i, x in enumerate(pred_order)}
        for h in gold_headings:
            pid = heading_matches.get(h["id"])
            ph = pred_by_block.get(pid)
            if ph is None: continue
            heading_match += 1
            if pid not in unresolved_heading_blocks:
                level_resolved_count += 1
                level_correct += h["level"] == ph.get("level")
            if "parent_id" in h:
                heading_parent_count += 1
                if pid in unresolved_heading_blocks:
                    continue
                parent_heading = next((item for item in gold_headings if item.get("id") == h["parent_id"]), None)
                expected_parent = heading_matches.get(parent_heading["id"]) if parent_heading else None
                actual_parent_ids = ph.get("parent_block_ids", ph.get("parent_block_id", tree_parents.get(pid)))
                has_parent_evidence = ("parent_block_ids" in ph or "parent_block_id" in ph or pid in tree_parents)
                if not has_parent_evidence:
                    continue
                parent_resolved_count += 1
                if isinstance(actual_parent_ids, list): actual_parent_ids = actual_parent_ids[0] if len(actual_parent_ids) == 1 else actual_parent_ids
                parent_correct += actual_parent_ids == expected_parent
        order_expected += len(gold_order)
        covered = [(block_id, min((pred_rank[predicted_id] for predicted_id in aligned_blocks.get(block_id, [])
                                    if predicted_id in pred_rank), default=None))
                   for block_id in gold_order]
        covered = [(block_id, rank) for block_id, rank in covered if rank is not None]
        order_covered += len(covered)
        for i, (a, rank_a) in enumerate(covered):
            for b, rank_b in covered[i + 1:]:
                order_pairs += 1
                order_agree += rank_a < rank_b
        for row_region in row.get("prose_regions", []):
            expected = [_bbox(x, "expected column") for x in row_region.get("columns", [])]
            actuals = []
            layout = page.get("layout") or {}
            for profile in layout.get("profiles", []):
                region = next((r for r in layout.get("regions", []) if r.get("region_id") == profile.get("region_id")), {})
                if region.get("bbox") is not None and _iou(_bbox(row_region["bbox"], "prose region"), _bbox(region["bbox"], "predicted region")) >= .5:
                    selected = profile.get("selected")
                    actuals = [_bbox(c, "predicted column") for c in selected.get("columns", [])] if isinstance(selected, dict) else []
                    break
            col_count_total += 1
            col_count_ok += len(actuals) == len(expected)
            if len(actuals) == len(expected):
                expected = sorted(expected, key=lambda box: box[0])
                actuals = sorted(actuals, key=lambda box: box[0])
                column_ious.extend(_iou(x, y) for x, y in zip(expected, actuals))
        predicted_tables = page.get("tables", [])
        gold_tables = row.get("tables", [])
        table_pred += len(predicted_tables); table_gold += len(gold_tables)
        table_candidates = []
        for gold_index, table in enumerate(gold_tables):
            gb = _bbox(table["bbox"], "gold table")
            for predicted_index, predicted in enumerate(predicted_tables):
                if predicted.get("bbox") is None:
                    continue
                iou = _iou(gb, _bbox(predicted["bbox"], "predicted table"))
                if iou >= .5:
                    table_candidates.append((iou, gold_index, predicted_index))
        matched_gold_tables: set[int] = set()
        matched_predicted_tables: set[int] = set()
        for _, gold_index, predicted_index in sorted(table_candidates, reverse=True):
            if gold_index in matched_gold_tables or predicted_index in matched_predicted_tables:
                continue
            matched_gold_tables.add(gold_index)
            matched_predicted_tables.add(predicted_index)
            table_match += 1
            expected_rows = [[_norm(cell) for cell in cells] for cells in gold_tables[gold_index].get("rows", [])]
            expected_rows = [cells for cells in expected_rows if any(cells)]
            actual_rows = _table_grid(predicted_tables[predicted_index])
            raw_actual_rows = _table_grid(predicted_tables[predicted_index], ignore_empty_rows=False)
            nonempty_actual_rows = _table_grid(predicted_tables[predicted_index])
            expected_width = max((len(cells) for cells in expected_rows), default=0)
            actual_width = max((len(cells) for cells in raw_actual_rows), default=0)
            shape_matches = len(expected_rows) == len(raw_actual_rows) and expected_width == actual_width
            table_shape_matches += shape_matches
            table_shape_diagnostics.append({
                "page_number": number,
                "table_index": gold_index,
                "caption": gold_tables[gold_index].get("caption"),
                "expected_rows": len(expected_rows),
                "actual_rows": len(raw_actual_rows),
                "actual_nonempty_rows": len(nonempty_actual_rows),
                "empty_rows": len(raw_actual_rows) - len(nonempty_actual_rows),
                "missing_rows": max(0, len(expected_rows) - len(raw_actual_rows)),
                "extra_rows": max(0, len(raw_actual_rows) - len(expected_rows)),
                "expected_columns": expected_width,
                "actual_columns": actual_width,
                "shape_matches": shape_matches,
            })
            row_count = max(len(expected_rows), len(actual_rows))
            column_count = max((len(cells) for cells in expected_rows + actual_rows), default=0)
            for ri in range(row_count):
                for ci in range(column_count):
                    expected = expected_rows[ri][ci] if ri < len(expected_rows) and ci < len(expected_rows[ri]) else ""
                    actual = actual_rows[ri][ci] if ri < len(actual_rows) and ci < len(actual_rows[ri]) else ""
                    cells_total += 1
                    cells_correct += expected == actual
    report = {
        "source_sha256": source_hash,
        "pipeline_version": version.get("pipeline_version", version.get("policy_version")),
        "annotation_page_count": len(annotations),
        "page_type_accuracy": page_type_correct / page_type_count if page_type_count else None,
        "blocks": ({"precision": matched_predicted_blocks / predicted_blocks if predicted_blocks else None,
                    "recall": matched_blocks / gold_blocks if gold_blocks else None,
                    "matched": matched_blocks, "matched_predicted": matched_predicted_blocks,
                    "predicted": predicted_blocks, "gold": gold_blocks} if category_pages["blocks"] else {"precision": None, "recall": None, "matched": None, "matched_predicted": None, "predicted": None, "gold": None}),
        "headings": ({**_prf(heading_match, heading_pred, heading_gold), "level_accuracy": level_correct / level_resolved_count if level_resolved_count else None, "level_resolution_coverage": level_resolved_count / heading_match if heading_match else None, "level_resolved_count": level_resolved_count, "level_gold_count": heading_match, "parent_accuracy": parent_correct / parent_resolved_count if parent_resolved_count else None, "parent_resolution_coverage": parent_resolved_count / heading_parent_count if heading_parent_count else None, "parent_resolved_count": parent_resolved_count, "parent_gold_count": heading_parent_count, "hierarchy_resolution_coverage": hierarchy_resolved_count / hierarchy_heading_count if hierarchy_heading_count else None, "hierarchy_resolved_count": hierarchy_resolved_count, "hierarchy_heading_count": hierarchy_heading_count} if category_pages["headings"] else {"precision": None, "recall": None, "level_accuracy": None, "level_resolution_coverage": None, "level_resolved_count": None, "level_gold_count": None, "parent_accuracy": None, "parent_resolution_coverage": None, "parent_resolved_count": None, "parent_gold_count": None, "hierarchy_resolution_coverage": None, "hierarchy_resolved_count": None, "hierarchy_heading_count": None}),
        "reading_order_pairwise_agreement": order_agree / order_pairs if order_pairs else None,
        "reading_order_covered_block_count": order_covered,
        "reading_order_block_coverage": order_covered / order_expected if order_expected else None,
        "columns": {"count_accuracy": col_count_ok / col_count_total if col_count_total and category_pages["prose_regions"] else None, "bbox_mean_iou": sum(column_ious) / len(column_ious) if column_ious and category_pages["prose_regions"] else None},
        "tables": ({**_prf(table_match, table_pred, table_gold),
                    "cell_exact_rate": cells_correct / cells_total if cells_total else None,
                    "grid_shape_exact_rate": table_shape_matches / table_match if table_match else None,
                    "matched_table_count": table_match,
                    "shape_diagnostics": table_shape_diagnostics} if category_pages["tables"] else {"precision": None, "recall": None, "cell_exact_rate": None, "grid_shape_exact_rate": None, "matched_table_count": None, "shape_diagnostics": None}),
        "section_continuations": ({"annotated_boundary_count": continuation_gold,
                                    "evaluated_boundary_count": sum(relation_confusion.values()),
                                    "unmatched_endpoint_count": relation_unmatched,
                                    "endpoint_match_rate": sum(relation_confusion.values()) / continuation_gold if continuation_gold else None,
                                    "gold_relation_counts": dict(continuation_relation_gold),
                                    "confusion_counts": {gold: {pred: relation_confusion[(gold, pred)] for pred in sorted(_SECTION_RELATIONS)} for gold in sorted(_SECTION_RELATIONS)},
                                    "unresolved_prediction_count": sum(count for (_, pred), count in relation_confusion.items() if pred == "UNRESOLVED"),
                                    "unresolved_gold_abstention_rate": (relation_confusion[("UNRESOLVED", "UNRESOLVED")] / sum(count for (gold, _), count in relation_confusion.items() if gold == "UNRESOLVED")) if any(gold == "UNRESOLVED" for gold, _ in relation_confusion) else None,
                                    "decision_coverage": (sum(count for (gold, pred), count in relation_confusion.items() if gold != "UNRESOLVED" and pred != "UNRESOLVED") / sum(count for (gold, _), count in relation_confusion.items() if gold != "UNRESOLVED")) if any(gold != "UNRESOLVED" for gold, _ in relation_confusion) else None,
                                    "accuracy": sum(count for (gold, predicted), count in relation_confusion.items() if gold == predicted and gold != "UNRESOLVED") / sum(count for (gold, _), count in relation_confusion.items() if gold != "UNRESOLVED") if any(gold != "UNRESOLVED" for gold, _ in relation_confusion) else None,
                                    "class_metrics": {label: {"precision": relation_confusion[(label, label)] / sum(count for (gold, pred), count in relation_confusion.items() if pred == label and gold != "UNRESOLVED") if sum(count for (gold, pred), count in relation_confusion.items() if pred == label and gold != "UNRESOLVED") else None,
                                                         "recall": relation_confusion[(label, label)] / sum(count for (gold, _), count in relation_confusion.items() if gold == label) if sum(count for (gold, _), count in relation_confusion.items() if gold == label) else None}
                                                      for label in ("SAME_SECTION", "DIFFERENT_SECTION")},
                                    "content_continuity": {"accuracy": None, "status": "not-measured", "gold_relation_counts": dict(continuity_gold)}}
                                   if category_pages["section_continuations"] else {"annotated_boundary_count": None,
                                    "evaluated_boundary_count": None, "unmatched_endpoint_count": None, "endpoint_match_rate": None, "decision_coverage": None,
                                    "accuracy": None, "gold_relation_counts": None, "confusion_counts": None,
                                    "unresolved_prediction_count": None, "unresolved_gold_abstention_rate": None, "class_metrics": None,
                                    "content_continuity": {"accuracy": None, "status": "not-measured", "gold_relation_counts": None}}),
    }
    if _diagnostics_enabled():
        _LOG.info("dev_structure_eval stage=complete sourceSha256=%s pageCount=%d matchedBlockCount=%d headingMatchCount=%d tableMatchCount=%d pageBoundaryRelationCount=%d pageBoundaryRelationEvaluatedCount=%d unmatchedEndpointCount=%d sameSectionCorrect=%d differentSectionCorrect=%d unresolvedGoldAbstained=%d unresolvedRelationPredictionCount=%d elapsedMs=%d outcome=success",
                  source_hash, len(annotations), matched_blocks, heading_match, table_match, continuation_gold,
                  sum(relation_confusion.values()), relation_unmatched,
                  relation_confusion[("SAME_SECTION", "SAME_SECTION")],
                  relation_confusion[("DIFFERENT_SECTION", "DIFFERENT_SECTION")],
                  relation_confusion[("UNRESOLVED", "UNRESOLVED")],
                  sum(count for (_, pred), count in relation_confusion.items() if pred == "UNRESOLVED"),
                  int((time.monotonic() - started) * 1000))
    return report
