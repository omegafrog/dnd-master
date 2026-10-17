from __future__ import annotations
import hashlib
import logging
import os
import re
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping
from preprocessing_agent.chunking import ChunkAssembler, ChunkPlanner, ChunkPolicy, ChunkSplitter
from preprocessing_agent.chunking.planner import table_to_prose
from preprocessing_agent.classification import DeterministicContentClassifier
from preprocessing_agent.config import AgentConfig, load_config
from preprocessing_agent.domain import Chunk, ChunkCandidate, ContentType, DocumentTree, ParsedDocument, SectionNode, SourceSegment, SourceSpan, ValidationIssue, ValidationResult
from preprocessing_agent.exporters import ArtifactExporter, prepare_export_chunks
from preprocessing_agent.validation.deterministic import is_numeric_separator_only_unknown
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.validation import ValidationPolicy, validate_chunks
from preprocessing_agent.postprocessing import linearize_prose, postprocess_chunks
from preprocessing_agent.parsers.normalize import normalized_key
from preprocessing_agent.structure import DocumentTreeBuilder
from preprocessing_agent.structure.tree_builder import intentionally_excluded_source_blocks
from preprocessing_agent.utils.ids import chunk_id
from preprocessing_agent.utils.tokens import count_tokens

_LOGGER = logging.getLogger(__name__)
@dataclass(frozen=True, slots=True)
class PipelineResult:
    source: Path
    document: ParsedDocument
    tree: DocumentTree
    chunks: tuple[Chunk, ...]
    validation: ValidationResult
    issues: tuple[ValidationIssue, ...]
    manifest: Mapping[str, Any]
    @property
    def valid(self) -> bool:
        return self.validation.valid
class _TextDocumentParser:
    """Small deterministic fixture adapter; production PDFs use PdfDocumentParser."""
    def parse(self, source: Path) -> ParsedDocument:
        from preprocessing_agent.domain import ParsedBlock, ParsedPage, SourceSpan
        raw = source.read_text(encoding="utf-8")
        blocks = []
        for index, line in enumerate(raw.splitlines()):
            text = line.strip()
            if text:
                blocks.append(ParsedBlock(f"p1-b{index}", text, SourceSpan(1, index, 0, len(text)), font_weight="bold" if text.startswith("#") else None))
        page = ParsedPage(1, tuple(blocks), raw)
        document_id = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
        return ParsedDocument(document_id, str(source), raw, (page,), {"format": source.suffix.lstrip(".") or "text"})
class PreprocessingPipeline:
    def __init__(self, config: AgentConfig, *, parser: Any | None = None, output_dir: str | Path | None = None) -> None:
        self.config = config
        values = dict(config.chunking)
        self.policy = ChunkPolicy(
            target_tokens=int(values.get("target_tokens", 350)), max_tokens=int(values.get("max_tokens", 500)),
            prose_max_tokens=int(values.get("prose_max_tokens", 300)),
            max_characters=int(values.get("max_characters", 6000)),
            min_tokens=int(values.get("min_tokens", 100)), overlap_tokens=int(values.get("overlap_tokens", 30)),
        )
        self.validation_policy = ValidationPolicy(self.policy.min_tokens, self.policy.max_tokens)
        self.parser = parser or PdfDocumentParser()
        self.output_dir = Path(output_dir or config.values.get("output_dir", "./data/processed"))
    @classmethod
    def from_config(cls, config: AgentConfig | str | Path | Mapping[str, Any], *, parser: Any | None = None,
                    output_dir: str | Path | None = None) -> "PreprocessingPipeline":
        if isinstance(config, (str, Path)):
            config = load_config(config)
        elif isinstance(config, Mapping):
            chunking = config.get("chunking", {})
            if not isinstance(chunking, Mapping):
                raise ValueError("Configuration key 'chunking' must be a mapping")
            config = AgentConfig(str(config.get("name", "preprocessing-agent")), str(config.get("pipeline_version", "0.1.0")), dict(chunking),
                                 {key: value for key, value in config.items() if key not in {"name", "pipeline_version", "chunking"}})
        return cls(config, parser=parser, output_dir=output_dir)
    def run(self, *, source: str | Path) -> PipelineResult:
        source_path = Path(source)
        parser = self.parser
        if isinstance(parser, PdfDocumentParser) and source_path.suffix.lower() not in {".pdf"}:
            parser = _TextDocumentParser()
        document = parser.parse(source_path)
        tree = self._classify_tree(DocumentTreeBuilder().build(document), document)
        candidates = ChunkPlanner().plan(tree, document)
        chunks = ChunkAssembler(ChunkSplitter(self.policy)).assemble(candidates)
        chunks = postprocess_chunks(chunks)
        initial_validation = validate_chunks(chunks, document=document, tree=tree, policy=self.validation_policy)
        invalid_chunk_ids = {
            item.path for item in initial_validation.issues
            if item.issue_type == "invalid_source_span" and item.path is not None
        }
        chunks, _ = prepare_export_chunks(chunks, invalid_chunk_ids=invalid_chunk_ids)
        chunks, source_coverage, recovered_issues = _recover_uncovered_text(document, tree, chunks, self.policy)
        validation = validate_chunks(chunks, document=document, tree=tree, policy=self.validation_policy)
        issues = (*validation.issues, *recovered_issues)
        source_page_count = document.metadata.get("source_page_count", len(document.pages))
        if type(source_page_count) is not int or source_page_count < len(document.pages):
            raise ValueError("invalid source page count")
        excluded_form_pages = document.metadata.get("excluded_form_pages", ())
        prose_started = time.monotonic()
        expected_block_count = sum(len(page.blocks) for page in document.pages)
        expected_table_count = sum(len(page.tables) for page in document.pages)
        if _diagnostics_enabled():
            _LOGGER.info("dev_preprocessing_prose stage=started pageCount=%d expectedBlockCount=%d expectedTableCount=%d route=ordered_blocks_with_structured_tables outcome=started",
                         len(document.pages), expected_block_count, expected_table_count)
        try:
            prose_text, rendered_table_count = _document_prose(document)
        except Exception as exc:
            if _diagnostics_enabled():
                _LOGGER.error("dev_preprocessing_prose stage=failed pageCount=%d expectedBlockCount=%d expectedTableCount=%d failureType=%s elapsedMs=%d recoveryAction=retain_parser_source_and_fail_export outcome=failed",
                              len(document.pages), expected_block_count, expected_table_count,
                              type(exc).__name__, int((time.monotonic() - prose_started) * 1000))
            raise
        if _diagnostics_enabled():
            _LOGGER.info("dev_preprocessing_prose stage=completed pageCount=%d expectedBlockCount=%d actualSourceCharacters=%d expectedTableCount=%d actualRenderedTableCount=%d elapsedMs=%d route=ordered_blocks_with_structured_tables outcome=success",
                         len(document.pages), expected_block_count, len(prose_text), expected_table_count,
                         rendered_table_count,
                         int((time.monotonic() - prose_started) * 1000))
        manifest = ArtifactExporter().export(
            self.output_dir, source_path, document.source_text, chunks, issues, tree,
            page_count=source_page_count, excluded_form_pages=excluded_form_pages,
            profile=self.config.name, policy=self.policy,
            document=document,
            pipeline_version=self.config.pipeline_version, invalid_chunk_ids=invalid_chunk_ids,
            source_coverage=source_coverage, prose_text=prose_text,
        )
        return PipelineResult(source_path, document, tree, chunks, validation, issues, manifest)
    def _classify_tree(self, tree: DocumentTree, document: ParsedDocument) -> DocumentTree:
        blocks = {block.block_id: block for page in document.pages for block in page.blocks}
        classifier = DeterministicContentClassifier()
        def visit(node: SectionNode) -> SectionNode:
            text = ("\n".join(segment.source_text for segment in node.source_segments)
                    if node.source_segments is not None else
                    "\n".join(blocks[item].source_text for item in node.block_ids if item in blocks))
            decision = classifier.classify(node, text)
            children = tuple(visit(child) for child in node.children)
            return SectionNode(node.node_id, node.title, node.level, decision.label, node.source_spans,
                               node.block_ids, children, node.source_segments, node.hierarchy_resolved)
        return DocumentTree(tree.document_id, visit(tree.root))


def _document_prose(document: ParsedDocument) -> tuple[str, int]:
    blocks_in_order: list[str] = []
    rendered_table_count = 0
    for page in document.pages:
        table_by_block: dict[str, tuple[str, object]] = {}
        for table in page.tables:
            table_id = str(getattr(table, "table_id", ""))
            for cell in getattr(table, "cells", ()):
                for block_id in getattr(cell, "source_block_ids", ()):
                    table_by_block[str(block_id)] = (table_id, table)
        emitted_tables: set[str] = set()
        for block in page.blocks:
            table = table_by_block.get(block.block_id)
            if table is None:
                blocks_in_order.append(block.source_text)
            elif table[0] not in emitted_tables:
                blocks_in_order.append(table_to_prose(table[1]))
                emitted_tables.add(table[0])
                rendered_table_count += 1
    return linearize_prose("\n".join(blocks_in_order)), rendered_table_count


def _recover_uncovered_text(document: ParsedDocument, tree: DocumentTree, chunks: tuple[Chunk, ...], policy: ChunkPolicy):
    """Keep meaningful parsed text searchable when planning or export omitted its source range."""
    started = time.monotonic()
    blocks = tuple(block for page in document.pages for block in page.blocks)
    owners: dict[tuple[int, int], list[tuple[int, int, tuple[str, ...], ContentType, int]]] = {}
    intentional_exclusions = intentionally_excluded_source_blocks(document)

    def visit(node: SectionNode, path: tuple[str, ...]) -> None:
        current = path + (_section_key(node.title),) if node.level and node.hierarchy_resolved else path
        for span in node.source_spans:
            _add_owner(owners, span, current, node.content_type, len(current))
        for segment in node.source_segments or ():
            _add_owner(owners, segment.source_span, current, node.content_type, len(current))
        for child in node.children:
            visit(child, current)

    visit(tree.root, ())
    if _diagnostics_enabled():
        _LOGGER.info("dev_source_accounting stage=started pageCount=%d blockCount=%d inputChunkCount=%d route=source_span_coverage outcome=started",
                     len(document.pages), len(blocks), len(chunks))
    recovered: list[Chunk] = []
    splitter = ChunkSplitter(policy)
    recovery_issues: list[ValidationIssue] = []
    covered_characters = unaccounted_characters = intentionally_excluded_character_count = 0
    exclusion_counts: dict[str, int] = {}
    intentionally_excluded_block_ids: set[tuple[int, int]] = set()
    for block in blocks:
        span = block.source_span
        exclusion_reason = intentional_exclusions.get(block.block_id)
        if exclusion_reason:
            if span.block_index is not None:
                intentionally_excluded_block_ids.add((span.page_number, span.block_index))
            intentionally_excluded_character_count += len(block.source_text.strip())
            exclusion_counts[exclusion_reason] = exclusion_counts.get(exclusion_reason, 0) + 1
            continue
        if span.block_index is None:
            unaccounted_characters += len(block.source_text.strip())
            if _diagnostics_enabled():
                _LOGGER.warning("dev_source_accounting stage=range_unrecoverable pageNumber=%d expectedBlockIndex=present actualBlockIndex=missing sourceCharacterCount=%d failureKind=SOURCE_BLOCK_INDEX_MISSING recoveryAction=preserve_in_source_markdown outcome=needs_review",
                                span.page_number, len(block.source_text))
            continue
        block_start = span.char_start or 0
        block_end = span.char_end if span.char_end is not None else block_start + len(block.source_text)
        length = min(len(block.source_text), max(0, block_end - block_start))
        intervals: list[tuple[int, int]] = []
        fully_covered = False
        for chunk in chunks:
            for source_span in chunk.source_spans:
                if source_span.page_number != span.page_number or source_span.block_index != span.block_index:
                    continue
                if source_span.char_start is None or source_span.char_end is None:
                    fully_covered = True
                    break
                intervals.append((max(0, source_span.char_start - block_start),
                                  min(length, source_span.char_end - block_start)))
            if fully_covered:
                break
        if fully_covered:
            covered_characters += len(block.source_text.strip())
            continue
        intervals = _merge_intervals((start, end) for start, end in intervals if start < end)
        gaps = _text_gaps(block.source_text, intervals)
        missing_text = sum(end - start for start, end in gaps)
        covered_characters += max(0, len(block.source_text.strip()) - missing_text)
        if not gaps:
            continue
        owner_candidates = owners.get((span.page_number, span.block_index), ())
        for range_index, (start, end) in enumerate(gaps, start=1):
            text = block.source_text[start:end]
            if not text.strip():
                continue
            recovered_span = _slice_source_span(span, start, end)
            owner = _best_owner(owner_candidates, (block_start + start, block_start + end))
            path, content_type = owner if owner else ((), ContentType.UNKNOWN)
            canonical = f"recovered.p{span.page_number:04d}.b{span.block_index:04d}.r{range_index:03d}"
            candidate = ChunkCandidate(
                "source-recovery-" + canonical, canonical, content_type, text,
                (recovered_span,), path, (SourceSegment(text, recovered_span),),
            )
            for piece in splitter.split(candidate):
                item = Chunk(chunk_id(piece.source_text, piece.canonical_key), piece.canonical_key,
                             content_type, piece.source_text, piece.source_text, count_tokens(piece.source_text),
                             piece.source_spans, path, piece.parent_key)
                if any(existing.chunk_id == item.chunk_id for existing in (*chunks, *recovered)):
                    continue
                recovered.append(item)
                recovery_issues.append(ValidationIssue(
                    "source_text_recovered", "일반 청크 생성에서 빠진 원문 범위를 출처와 함께 검색 청크로 보존했습니다.",
                    severity="info", path=item.chunk_id, source_span=recovered_span,
                ))
            if not any(issue.source_span == recovered_span for issue in recovery_issues) and _diagnostics_enabled():
                _LOGGER.warning("dev_source_accounting stage=recovery_range_failed pageNumber=%d blockIndex=%d expectedCharacters=%d actualExportedChunkCount=0 failureKind=RECOVERY_CHUNK_NOT_CREATED recoveryAction=preserve_source_markdown outcome=needs_review",
                                span.page_number, span.block_index, end - start)
    final_chunks, excluded = prepare_export_chunks((*chunks, *recovered))
    final_ids = {item.chunk_id for item in final_chunks}
    recovery_issues = [issue for issue in recovery_issues if issue.path in final_ids]
    unresolved_ids = {item.chunk_id for item in recovered if item.chunk_id not in final_ids}
    excluded_noise_ids = {item.chunk_id for item in recovered
                          if item.chunk_id in unresolved_ids and is_numeric_separator_only_unknown(item)}
    excluded_noise_characters = sum(len(item.source_text.strip()) for item in recovered if item.chunk_id in excluded_noise_ids)
    intentionally_excluded_block_ids.update(
        (item.source_spans[0].page_number, item.source_spans[0].block_index) for item in recovered
        if item.chunk_id in excluded_noise_ids and item.source_spans and item.source_spans[0].block_index is not None
    )
    excluded_recovery_ids = unresolved_ids - excluded_noise_ids
    unaccounted_characters += sum(len(item.source_text.strip()) for item in recovered if item.chunk_id in excluded_recovery_ids)
    intentionally_excluded_character_count += excluded_noise_characters
    if excluded_noise_ids:
        exclusion_counts["numeric_separator_only_unknown"] = len(excluded_noise_ids)
    recovered_characters = sum(len(item.source_text) for item in recovered if item.chunk_id in final_ids)
    recovered_range_count = len({(issue.source_span.page_number, issue.source_span.block_index,
                                  issue.source_span.char_start, issue.source_span.char_end)
                                 for issue in recovery_issues if issue.source_span is not None})
    if _diagnostics_enabled() and excluded_noise_ids:
        _LOGGER.info("dev_source_accounting stage=known_noise_excluded expectedTextualContent=false actualExcludedChunkCount=%d exclusionReason=numeric_separator_only_unknown recoveryAction=retain_source_markdown outcome=excluded",
                     len(excluded_noise_ids))
    if _diagnostics_enabled() and excluded_recovery_ids:
        _LOGGER.warning("dev_source_accounting stage=recovery_chunk_excluded expectedExportedRecoveryCount=%d actualExportedRecoveryCount=%d excludedChunkCount=%d exclusionReasons=%s recoveryAction=retain_source_markdown_and_report outcome=needs_review",
                        len(recovered) - len(excluded_noise_ids), len(recovered) - len(unresolved_ids),
                        len(excluded_recovery_ids), excluded)
    result = {
        "parsed_block_count": len(blocks), "covered_character_count": covered_characters,
        "recovered_character_count": recovered_characters, "recovered_range_count": recovered_range_count,
        "intentionally_excluded_block_count": len(intentionally_excluded_block_ids),
        "intentionally_excluded_character_count": intentionally_excluded_character_count,
        "intentionally_excluded_by_reason": exclusion_counts,
        "unaccounted_character_count": unaccounted_characters,
        "excluded_recovery_chunk_count": len(unresolved_ids), "excluded_recovery_reasons": excluded,
    }
    if _diagnostics_enabled():
        _LOGGER.info("dev_source_accounting stage=completed parsedBlockCount=%d expectedUnaccountedCharacters=0 actualUnaccountedCharacters=%d recoveredRangeCount=%d recoveredCharacterCount=%d excludedRecoveryChunkCount=%d elapsedMs=%d outcome=%s",
                     len(blocks), unaccounted_characters, recovered_range_count, recovered_characters,
                     len(unresolved_ids), int((time.monotonic() - started) * 1000),
                     "success" if unaccounted_characters == 0 else "needs_review")
    return final_chunks, result, tuple(recovery_issues)


def _add_owner(owners: dict, span: SourceSpan, path: tuple[str, ...], content_type: ContentType, depth: int) -> None:
    if span.block_index is not None and span.char_start is not None and span.char_end is not None:
        owners.setdefault((span.page_number, span.block_index), []).append((span.char_start, span.char_end, path, content_type, depth))


def _best_owner(candidates, source_range: tuple[int, int]):
    matching = [item for item in candidates if item[0] < source_range[1] and item[1] > source_range[0]]
    if not matching:
        return None
    _, _, path, content_type, _ = max(matching, key=lambda value: value[4])
    return path, content_type


def _merge_intervals(intervals):
    merged = []
    for start, end in sorted(intervals):
        if merged and start <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(merged[-1][1], end))
        else:
            merged.append((start, end))
    return merged


def _text_gaps(text: str, covered: list[tuple[int, int]]):
    gaps = []
    cursor = 0
    for start, end in (*covered, (len(text), len(text))):
        left, right = cursor, start
        while left < right and text[left].isspace():
            left += 1
        while right > left and text[right - 1].isspace():
            right -= 1
        if left < right:
            gaps.append((left, right))
        cursor = max(cursor, end)
    return gaps


def _slice_source_span(span: SourceSpan, start: int, end: int) -> SourceSpan:
    base = span.char_start or 0
    return SourceSpan(span.page_number, span.block_index, base + start, base + end,
                      bbox=span.bbox, table_id=span.table_id, table_cell=span.table_cell)


def _section_key(title: str) -> str:
    value = re.sub(r"[^a-z0-9]+", "_", normalized_key(title)).strip("_")
    return value or "section"


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}
