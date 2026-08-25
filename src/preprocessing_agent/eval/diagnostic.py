"""Reproducible root-cause traces for exported preprocessing runs."""

from __future__ import annotations

from dataclasses import dataclass, field, replace
from enum import Enum
import json
from pathlib import Path
from typing import Any, Iterable, Mapping

from preprocessing_agent.chunking import ChunkPlanner
from preprocessing_agent.domain import Chunk, DocumentTree, ParsedDocument, SectionNode, to_dict
from preprocessing_agent.eval.preprocessing import ExportedRun, load_exported_run
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.parsers import pdf as pdf_parser
from preprocessing_agent.structure import DocumentTreeBuilder


class DiagnosticClassification(str, Enum):
    PARSER_READING_ORDER = "PARSER_READING_ORDER"
    SECTION_TREE_ERROR = "SECTION_TREE_ERROR"
    CHUNK_BOUNDARY_ERROR = "CHUNK_BOUNDARY_ERROR"
    TABLE_BOUNDARY_ERROR = "TABLE_BOUNDARY_ERROR"
    VALIDATOR_FALSE_POSITIVE = "VALIDATOR_FALSE_POSITIVE"


@dataclass(frozen=True, slots=True)
class DiagnosticTrace:
    candidate_id: str
    chunk_id: str
    issue_types: tuple[str, ...]
    source_blocks: tuple[Mapping[str, Any], ...]
    reading_order_blocks: tuple[str, ...]
    section_node: Mapping[str, Any]
    chunk_candidate: Mapping[str, Any]
    final_chunk: Mapping[str, Any]
    evidence: Mapping[str, Any]
    classification: DiagnosticClassification | None = None
    details: Mapping[str, Any] = field(default_factory=dict)

    def to_dict(self) -> dict[str, Any]:
        return {
            "candidate_id": self.candidate_id, "chunk_id": self.chunk_id,
            "issue_types": list(self.issue_types), "source_blocks": [dict(item) for item in self.source_blocks],
            "reading_order_blocks": list(self.reading_order_blocks), "section_node": dict(self.section_node),
            "chunk_candidate": dict(self.chunk_candidate), "final_chunk": dict(self.final_chunk),
            "evidence": dict(self.evidence),
            "classification": self.classification.value if self.classification else None,
            "details": dict(self.details),
        }


def classify_trace(trace: DiagnosticTrace) -> DiagnosticClassification:
    evidence = trace.evidence
    if not evidence.get("reading_order_valid", True):
        return DiagnosticClassification.PARSER_READING_ORDER
    if not evidence.get("section_membership_valid", True):
        return DiagnosticClassification.SECTION_TREE_ERROR
    if trace.final_chunk.get("content_type") == "table" and not evidence.get("table_boundary_valid", True):
        return DiagnosticClassification.TABLE_BOUNDARY_ERROR
    if not evidence.get("final_text_matches_source", True):
        return DiagnosticClassification.CHUNK_BOUNDARY_ERROR
    return DiagnosticClassification.VALIDATOR_FALSE_POSITIVE


def _node_index(root: SectionNode, path: tuple[str, ...] = ()) -> dict[str, SectionNode]:
    result = {root.node_id: root}
    for child in root.children:
        result.update(_node_index(child, path + (child.title,)))
    return result


def _find_node(root: SectionNode, section_path: tuple[str, ...]) -> SectionNode | None:
    wanted = {item.casefold().replace(" ", "_") for item in section_path}
    for node in _node_index(root).values():
        titles = {item.casefold().replace(" ", "_") for item in section_path if item}
        if node.title.casefold().replace(" ", "_") in wanted:
            return node
    return root if not section_path else None


def _source_blocks(document: ParsedDocument, chunk: Chunk) -> tuple[Mapping[str, Any], ...]:
    by_span = {(page.page_number, block.source_span.block_index): block for page in document.pages for block in page.blocks}
    result = []
    for span in chunk.source_spans:
        block = by_span.get((span.page_number, span.block_index))
        if block:
            result.append({"page": span.page_number, "block_index": span.block_index,
                           "block_id": block.block_id, "text": block.source_text,
                           "bbox": list(block.bbox) if block.bbox else None,
                           "span": to_dict(span)})
    return tuple(result)


def _ordered_text(document: ParsedDocument, chunk: Chunk) -> str:
    blocks = _source_blocks(document, chunk)
    return "\n\n".join(str(item["text"]) for item in blocks)


def _same_text(left: str, right: str) -> bool:
    return " ".join(left.split()) == " ".join(right.split())


def _legacy_document(source_pdf: str | Path) -> ParsedDocument:
    """Parse with the pre-44a0c38c extractor order, retaining bbox evidence."""
    raw_pages = tuple(pdf_parser._default_extractor(Path(source_pdf)))
    raw_by_id = {
        str(block.get("block_id", f"p{page.get('page_number', position)}-b{index}")): block
        for position, page in enumerate(raw_pages, 1)
        for index, block in enumerate(page.get("blocks", ()))
    }
    # Removing bbox from the adapter input disables the post-44a0c38c ordering
    # seam while preserving the old extractor sequence and source spans.
    legacy_pages = tuple({**page, "blocks": [
        {key: value for key, value in block.items() if key != "bbox"}
        for block in page.get("blocks", ())
    ]} for page in raw_pages)
    parsed = PdfDocumentParser(lambda _: legacy_pages).parse(Path(source_pdf))
    pages = []
    for page in parsed.pages:
        blocks = tuple(type(block)(
            block.block_id, block.source_text, block.source_span,
            tuple(float(item) for item in raw_by_id[block.block_id]["bbox"])
            if raw_by_id.get(block.block_id, {}).get("bbox") is not None else None,
            block.font_size, block.font_weight,
        ) for block in page.blocks)
        pages.append(type(page)(page.page_number, blocks, page.source_text))
    return type(parsed)(parsed.document_id, parsed.source_path, parsed.source_text, tuple(pages), parsed.metadata)


def _parse_source_pdf(source_pdf: str | Path, parser_mode: str) -> ParsedDocument:
    if parser_mode == "before":
        return _legacy_document(source_pdf)
    if parser_mode == "after":
        return PdfDocumentParser().parse(Path(source_pdf))
    raise ValueError("parser_mode must be before or after")


def _bbox_order_evidence(blocks: tuple[Mapping[str, Any], ...]) -> dict[str, Any]:
    """Compare parser order with the geometric top-to-bottom, left-to-right order."""
    actual = [str(item["block_id"]) for item in blocks]
    comparable = all(item.get("bbox") is not None for item in blocks)
    if not comparable:
        return {"reading_order_valid": True, "reading_order_comparable": False,
                "parser_block_ids": actual, "bbox_expected_block_ids": []}
    expected = [str(item["block_id"]) for item in sorted(
        blocks, key=lambda item: (int(item["page"]), float(item["bbox"][1]), float(item["bbox"][0]), str(item["block_id"]))) ]
    return {"reading_order_valid": actual == expected, "reading_order_comparable": True,
            "parser_block_ids": actual, "bbox_expected_block_ids": expected}


def trace_run(run: ExportedRun, source_pdf: str | Path, *, evaluator_failures_path: str | Path | None = None,
              parser_mode: str = "after") -> tuple[DiagnosticTrace, ...]:
    document = _parse_source_pdf(source_pdf, parser_mode)
    tree = DocumentTreeBuilder().build(document)
    candidates = ChunkPlanner().plan(tree, document)
    candidate_by_key = {candidate.canonical_key: candidate for candidate in candidates}
    issues_by_chunk: dict[str, list[str]] = {}
    issue_paths = [run.run_dir / "issues.jsonl"]
    if evaluator_failures_path is not None:
        issue_paths.append(Path(evaluator_failures_path))
    else:
        issue_paths.append(run.run_dir / "preprocessing_eval_failures.jsonl")
    for issue_path in issue_paths:
        if not issue_path.is_file():
            continue
        for line in issue_path.read_text(encoding="utf-8").splitlines():
            if line.strip():
                item = json.loads(line)
                issue_type = str(item.get("issue_type", item.get("type", "unknown")))
                chunk_ids = item.get("chunk_ids", ())
                if item.get("path"):
                    chunk_ids = (*chunk_ids, item["path"])
                for chunk_id in chunk_ids:
                    issues_by_chunk.setdefault(str(chunk_id), []).append(issue_type)
    traces = []
    for chunk in run.chunks:
        issue_types = tuple(issues_by_chunk.get(chunk.chunk_id, ()))
        if not issue_types:
            continue
        blocks = _source_blocks(document, chunk)
        block_ids = tuple(str(item["block_id"]) for item in blocks)
        candidate = candidate_by_key.get(chunk.canonical_key)
        node = _find_node(tree.root, chunk.section_path)
        node_blocks = set(node.block_ids) if node else set()
        source_text = _ordered_text(document, chunk)
        reading_order = _bbox_order_evidence(blocks)
        evidence = {
            **reading_order,
            "section_membership_valid": bool(node and set(block_ids).issubset(node_blocks)),
            "table_boundary_valid": not (chunk.content_type.value == "table" and len({item["page"] for item in blocks}) > 1),
            "final_text_matches_source": _same_text(source_text, chunk.source_text),
            "source_block_count": len(blocks),
        }
        trace = DiagnosticTrace(
            candidate_id=candidate.candidate_id if candidate else f"missing:{chunk.canonical_key}",
            chunk_id=chunk.chunk_id, issue_types=issue_types, source_blocks=blocks,
            reading_order_blocks=block_ids,
            section_node=to_dict(node) if node else {"node_id": None, "section_path": list(chunk.section_path)},
            chunk_candidate=to_dict(candidate) if candidate else {"canonical_key": chunk.canonical_key},
            final_chunk=to_dict(chunk), evidence=evidence,
        )
        classification = classify_trace(trace)
        traces.append(replace(trace, classification=classification))
    return tuple(traces)


def write_diagnostic(run_dir: str | Path, source_pdf: str | Path, output: str | Path, *,
                     evaluator_failures_path: str | Path | None = None, min_broken: int = 30,
                     expected_mixed: int | None = 22, parser_mode: str = "after") -> Path:
    run = load_exported_run(run_dir)
    traces = list(trace_run(run, source_pdf, evaluator_failures_path=evaluator_failures_path,
                            parser_mode=parser_mode))
    broken = [item for item in traces if "broken_sentence" in item.issue_types]
    mixed = [item for item in traces if "MIXED_CONTEXT" in item.issue_types]
    if len(broken) < min_broken:
        raise ValueError(f"expected at least {min_broken} broken_sentence traces, found {len(broken)}")
    if expected_mixed is not None and len(mixed) != expected_mixed:
        raise ValueError(f"expected {expected_mixed} MIXED_CONTEXT traces, found {len(mixed)}")
    destination = Path(output)
    destination.parent.mkdir(parents=True, exist_ok=True)
    payload = {"source_pdf": str(source_pdf), "run_dir": str(run_dir), "parser_mode": parser_mode, "counts": {
        "traces": len(traces), "mixed_context": len(mixed), "broken_sentence": len(broken)},
        "classifications": {label.value: sum(item.classification is label for item in traces) for label in DiagnosticClassification},
        "traces": [item.to_dict() for item in traces]}
    destination.write_text(json.dumps(payload, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return destination
