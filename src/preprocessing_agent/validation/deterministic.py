"""Deterministic, source-preserving chunk quality checks."""

from __future__ import annotations

from dataclasses import dataclass
import logging
import os
from typing import Iterable

from preprocessing_agent.domain import Chunk, ContentType, DocumentTree, ParsedDocument, SectionNode, ValidationIssue, ValidationResult
from preprocessing_agent.utils.tokens import count_tokens
import re


_CANONICAL_KEY = re.compile(r"^[a-z0-9]+(?:[._-][a-z0-9]+)*$")
_LOGGER = logging.getLogger(__name__)


@dataclass(frozen=True, slots=True)
class ValidationPolicy:
    min_tokens: int = 100
    max_tokens: int = 500

    def __post_init__(self) -> None:
        if self.min_tokens < 0 or self.max_tokens < self.min_tokens:
            raise ValueError("validation token limits are invalid")


def validate_chunks(
    chunks: Iterable[Chunk],
    *,
    document: ParsedDocument | None = None,
    tree: DocumentTree | None = None,
    page_count: int | None = None,
    block_count: int | None = None,
    policy: ValidationPolicy | None = None,
) -> ValidationResult:
    """Return issues in stable input order; no chunk is mutated."""
    policy = policy or ValidationPolicy()
    items = tuple(chunks)
    pages = {page.page_number: page for page in document.pages} if document else {}
    if page_count is None:
        page_count = len(pages) or None
    if block_count is None and document:
        block_count = sum(len(page.blocks) for page in document.pages)
    issues: list[ValidationIssue] = []
    seen_text: dict[str, str] = {}
    seen_identity: set[tuple[str, str]] = set()
    seen_chunk_ids: dict[str, str] = {}
    seen_canonical_keys: dict[str, str] = {}
    table_parent_counts: dict[str, int] = {}
    if _diagnostics_enabled():
        _LOGGER.info("dev_chunk_validation stage=section_path_check outcome=started chunkCount=%d treeAvailable=%s",
                     len(items), tree is not None)
    unresolved_hierarchy_chunk_ids = {
        chunk.chunk_id for chunk in items
        if not chunk.section_path and tree is not None and _fully_belongs_to_unresolved_heading(chunk, tree.root)
    }
    for chunk in items:
        if chunk.content_type is ContentType.TABLE and chunk.parent_key:
            table_parent_counts[chunk.parent_key] = table_parent_counts.get(chunk.parent_key, 0) + 1
    for chunk in items:
        path = chunk.chunk_id
        if chunk.chunk_id in seen_chunk_ids:
            issues.append(ValidationIssue("duplicate_chunk_id", f"duplicate chunk id of {seen_chunk_ids[chunk.chunk_id]}", path=path))
        else:
            seen_chunk_ids[chunk.chunk_id] = path
        if chunk.canonical_key in seen_canonical_keys:
            issues.append(ValidationIssue("duplicate_canonical_key", f"duplicate canonical key of {seen_canonical_keys[chunk.canonical_key]}", path=path))
        else:
            seen_canonical_keys[chunk.canonical_key] = path
        text = chunk.source_text.strip()
        if not _CANONICAL_KEY.fullmatch(chunk.canonical_key):
            issues.append(ValidationIssue("malformed_canonical_key", "canonical key is not in canonical format", path=path))
        if not text:
            issues.append(ValidationIssue("empty_chunk", "chunk has no source text", path=path))
        elif _is_garbage_candidate(text):
            issues.append(ValidationIssue("garbage_candidate", "chunk looks like extracted noise and needs review", severity="warning", path=path))
        if chunk.token_count < policy.min_tokens or (text and count_tokens(text) < policy.min_tokens):
            issues.append(ValidationIssue("too_small_chunk", "chunk is below the minimum token policy", severity="warning", path=path))
        if chunk.content_type not in {ContentType.TABLE, ContentType.MONSTER_STAT_BLOCK} and (not text or not text.endswith((".", "!", "?", ":", ";", "。", "！", "？", "：", "；", "|"))):
            issues.append(ValidationIssue("broken_sentence", "chunk ends before a sentence boundary", severity="warning", path=path))
        if (max(chunk.token_count, count_tokens(text)) > policy.max_tokens and
                chunk.content_type.value not in {"table", "monster_stat_block"}):
            issues.append(ValidationIssue("max_token_overflow", "chunk exceeds the maximum token policy", path=path))
        if not chunk.section_path:
            if chunk.chunk_id not in unresolved_hierarchy_chunk_ids:
                if chunk.content_type is ContentType.UNKNOWN:
                    issues.append(ValidationIssue("section_path_unresolved", "검색에는 사용할 수 있지만 확정된 절 경로는 없습니다.",
                                                  severity="warning", path=path))
                else:
                    issue_type = "orphan_heading" if len(text.split()) <= 12 else "missing_section_path"
                    issues.append(ValidationIssue(issue_type, "chunk has no document section path", path=path))
        identity = (chunk.canonical_key, text)
        if text in seen_text or identity in seen_identity:
            issues.append(ValidationIssue("duplicate", f"duplicate content of {seen_text.get(text, path)}", path=path))
        else:
            seen_text[text] = path
        seen_identity.add(identity)
        if chunk.content_type is ContentType.TABLE and chunk.parent_key and table_parent_counts.get(chunk.parent_key, 0) > 1:
            issues.append(ValidationIssue("split_table", "table content must remain a single chunk", path=path))
        for span in chunk.source_spans:
            page = pages.get(span.page_number)
            if document is not None:
                invalid = page is None
                if page is not None and span.block_index is not None and span.block_index >= len(page.blocks):
                    invalid = True
                if page is not None and any(
                    offset is not None and offset > len(page.source_text)
                    for offset in (span.char_start, span.char_end)
                ):
                    invalid = True
            else:
                invalid = page_count is not None and span.page_number > page_count
                if block_count is not None and span.block_index is not None and span.block_index >= block_count:
                    invalid = True
            if invalid:
                issues.append(ValidationIssue("invalid_source_span", "source span is outside the parsed document", path=path, source_span=span))
    checked = tuple(chunk.chunk_id for chunk in items)
    if _diagnostics_enabled():
        _LOGGER.info("dev_chunk_validation stage=section_path_check chunkCount=%d unresolvedHierarchyFullyCoveredChunkCount=%d missingPathIssueCount=%d issueCount=%d route=complete_source_range_coverage outcome=%s",
                     len(items), len(unresolved_hierarchy_chunk_ids),
                     sum(issue.issue_type in {"orphan_heading", "missing_section_path"} for issue in issues),
                     len(issues), "success" if not any(issue.severity == "error" for issue in issues) else "needs_review")
    return ValidationResult(not any(issue.severity == "error" for issue in issues), tuple(issues), checked)


def _fully_belongs_to_unresolved_heading(chunk: Chunk, root: SectionNode) -> bool:
    """Return true only when unresolved headings account for every source character."""
    chunk_spans = tuple(chunk.source_spans)
    if not chunk_spans:
        return False

    unresolved_segments: list[SourceSpan] = []

    def visit(node: SectionNode) -> None:
        if not node.hierarchy_resolved:
            unresolved_segments.extend(segment.source_span for segment in node.source_segments or ())
        for child in node.children:
            visit(child)

    visit(root)
    if not unresolved_segments:
        return False
    return all(_span_fully_covered(span, unresolved_segments) for span in chunk_spans)


def _span_fully_covered(span: SourceSpan, candidates: list[SourceSpan]) -> bool:
    if (span.block_index is None or span.char_start is None or span.char_end is None
            or span.char_start >= span.char_end):
        return False
    ranges = sorted(
        (candidate.char_start, candidate.char_end)
        for candidate in candidates
        if candidate.page_number == span.page_number
        and candidate.block_index == span.block_index
        and candidate.char_start is not None
        and candidate.char_end is not None
        and candidate.char_start < candidate.char_end
        and candidate.char_end > span.char_start
        and candidate.char_start < span.char_end
    )
    covered_until = span.char_start
    for start, end in ranges:
        if start > covered_until:
            return False
        covered_until = max(covered_until, end)
        if covered_until >= span.char_end:
            return True
    return False


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}


def _is_garbage_candidate(text: str) -> bool:
    """Flag likely extraction noise without deciding that a chunk is deletable."""
    compact = "".join(text.split())
    if not compact or len(compact) > 80:
        return False
    if any(character.isalpha() for character in compact):
        return False
    return any(character.isdigit() for character in compact) or any(not character.isalnum() for character in compact)


def is_numeric_separator_only_unknown(chunk: Chunk) -> bool:
    """Identify deterministic extraction noise safe to exclude at export time."""
    if chunk.content_type is not ContentType.UNKNOWN:
        return False
    compact = "".join(chunk.source_text.split())
    return bool(compact) and not any(character.isalpha() for character in compact) and _is_garbage_candidate(chunk.source_text)
