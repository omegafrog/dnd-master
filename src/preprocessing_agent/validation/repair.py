"""Allowlisted deterministic repairs. Agents may recommend, never mutate."""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum
from typing import Iterable

from preprocessing_agent.domain import Chunk, ContentType, ValidationIssue
from preprocessing_agent.utils.ids import chunk_id
from preprocessing_agent.utils.tokens import count_tokens


class RepairOperation(str, Enum):
    MERGE_PREVIOUS = "merge_previous"
    MERGE_NEXT = "merge_next"
    SPLIT = "split"
    RECLASSIFY = "reclassify"
    REBUILD_SECTION = "rebuild_section"
    MANUAL_REVIEW = "manual_review"


@dataclass(frozen=True, slots=True)
class RepairResult:
    chunks: tuple[Chunk, ...]
    issues: tuple[ValidationIssue, ...] = ()
    manual_review: bool = False


@dataclass(frozen=True, slots=True)
class RepairInstruction:
    issue: ValidationIssue
    operation: RepairOperation | str
    value: object | None = None


class RepairEngine:
    ALLOWED = frozenset(RepairOperation)

    def apply(self, chunks: Iterable[Chunk], repairs: Iterable[RepairInstruction | tuple[ValidationIssue, RepairOperation | str] | tuple[ValidationIssue, RepairOperation | str, object]]) -> RepairResult:
        current = list(chunks)
        issues: list[ValidationIssue] = []
        review = False
        for repair in repairs:
            if isinstance(repair, RepairInstruction):
                issue, operation, value = repair.issue, repair.operation, repair.value
            else:
                issue, operation = repair[:2]
                value = repair[2] if len(repair) > 2 else None
            operation = RepairOperation(operation)
            if operation not in self.ALLOWED or operation is RepairOperation.MANUAL_REVIEW:
                review = True
                issues.append(issue)
                continue
            index = next((i for i, item in enumerate(current) if item.chunk_id == issue.path or item.canonical_key == issue.path), None)
            if index is None:
                review = True
                issues.append(issue)
                continue
            if operation in (RepairOperation.MERGE_PREVIOUS, RepairOperation.MERGE_NEXT):
                other = index - 1 if operation is RepairOperation.MERGE_PREVIOUS else index + 1
                if not 0 <= other < len(current):
                    review = True; issues.append(issue); continue
                left, right = sorted((index, other))
                current[left] = self._merge(current[left], current[right])
                del current[right]
            elif operation is RepairOperation.SPLIT:
                if issue.issue_type == "split_table":
                    review = True
                    issues.append(issue)
                    continue
                current[index:index + 1] = self._split(current[index])
            elif operation is RepairOperation.RECLASSIFY:
                if not isinstance(value, ContentType):
                    review = True; issues.append(issue); continue
                current[index] = self._replace(current[index], content_type=value)
            elif operation is RepairOperation.REBUILD_SECTION:
                if not isinstance(value, tuple) or not all(isinstance(item, str) for item in value):
                    review = True; issues.append(issue); continue
                current[index] = self._replace(current[index], section_path=value)
        return RepairResult(tuple(current), tuple(issues), review)

    @staticmethod
    def _merge(left: Chunk, right: Chunk) -> Chunk:
        text = f"{left.source_text.rstrip()} {right.source_text.lstrip()}"
        return Chunk(chunk_id(text), left.canonical_key, left.content_type, text, text,
                     count_tokens(text), left.source_spans + right.source_spans,
                     left.section_path or right.section_path, left.parent_key)

    @staticmethod
    def _split(chunk: Chunk) -> tuple[Chunk, ...]:
        words = chunk.source_text.split()
        midpoint = max(1, len(words) // 2)
        pieces = (" ".join(words[:midpoint]), " ".join(words[midpoint:]))
        return tuple(Chunk(chunk_id(text), f"{chunk.canonical_key}.part-{i:03d}",
                           chunk.content_type, text, text, count_tokens(text), chunk.source_spans,
                           chunk.section_path, chunk.canonical_key) for i, text in enumerate(pieces, 1) if text)

    @staticmethod
    def _replace(chunk: Chunk, **changes: object) -> Chunk:
        values = {field: getattr(chunk, field) for field in chunk.__dataclass_fields__}
        values.update(changes)
        return Chunk(**values)
