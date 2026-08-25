"""Deterministic, source-preserving chunk quality checks."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from preprocessing_agent.domain import Chunk, ParsedDocument, ValidationIssue, ValidationResult
from preprocessing_agent.utils.tokens import count_tokens


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
    for chunk in items:
        path = chunk.chunk_id
        text = chunk.source_text.strip()
        if not text:
            issues.append(ValidationIssue("empty_chunk", "chunk has no source text", path=path))
        if chunk.token_count < policy.min_tokens or (text and count_tokens(text) < policy.min_tokens):
            issues.append(ValidationIssue("too_small_chunk", "chunk is below the minimum token policy", severity="warning", path=path))
        if not text or not text.endswith((".", "!", "?", ":", ";", "。", "！", "？", "：", "；", "|")):
            issues.append(ValidationIssue("broken_sentence", "chunk ends before a sentence boundary", severity="warning", path=path))
        if max(chunk.token_count, count_tokens(text)) > policy.max_tokens:
            issues.append(ValidationIssue("max_token_overflow", "chunk exceeds the maximum token policy", path=path))
        if not chunk.section_path:
            issue_type = "orphan_heading" if len(text.split()) <= 12 else "missing_section_path"
            issues.append(ValidationIssue(issue_type, "chunk has no document section path", path=path))
        identity = (chunk.canonical_key, text)
        if text in seen_text or identity in seen_identity:
            issues.append(ValidationIssue("duplicate", f"duplicate content of {seen_text.get(text, path)}", path=path))
        else:
            seen_text[text] = path
        seen_identity.add(identity)
        if chunk.content_type.value == "table" and ("|" in text or "\t" in text) and "\n" in text:
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
    return ValidationResult(not issues, tuple(issues), checked)
