"""Deterministic semantic-integrity candidates and an optional judge port."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, Iterable, Protocol, Sequence

from preprocessing_agent.domain import Chunk, ContentType


@dataclass(frozen=True, slots=True)
class EntityFixture:
    entity_id: str
    canonical_key: str
    content_type: ContentType
    atomic: bool = True
    parent_key: str | None = None
    expected_chunk_count: int = 1


@dataclass(frozen=True, slots=True)
class SemanticCandidate:
    candidate_id: str
    issue: str
    canonical_key: str
    chunk_ids: tuple[str, ...]
    details: dict[str, object]


@dataclass(frozen=True, slots=True)
class SemanticDecision:
    valid: bool
    issue: str | None = None
    action: str | None = None

    def __post_init__(self) -> None:
        if self.valid and (self.issue is not None or self.action is not None):
            raise ValueError("valid semantic decisions cannot contain issue or action")
        if not self.valid and (not self.issue or not self.action):
            raise ValueError("invalid semantic decisions require issue and action")

    def to_dict(self) -> dict[str, object]:
        return {"valid": self.valid, "issue": self.issue, "action": self.action}


class SemanticJudgePort(Protocol):
    def judge(self, candidate: SemanticCandidate) -> SemanticDecision: ...


_ENTITY_TYPES = {ContentType.SPELL, ContentType.MONSTER_STAT_BLOCK, ContentType.CLASS_FEATURE,
                 ContentType.RACE_TRAIT, ContentType.CONDITION, ContentType.MAGIC_ITEM, ContentType.TABLE}


class SemanticCandidateDetector:
    def __init__(self, *, large_span_tokens: int = 500) -> None:
        self.large_span_tokens = large_span_tokens

    def detect(self, chunks: Iterable[Chunk], fixtures: Iterable[EntityFixture] = ()) -> list[SemanticCandidate]:
        values = tuple(sorted(chunks, key=lambda item: item.chunk_id))
        fixture_values = tuple(sorted(fixtures, key=lambda item: (item.canonical_key, item.entity_id)))
        candidates: list[SemanticCandidate] = []
        for fixture in fixture_values:
            related = tuple(chunk for chunk in values if self._related(chunk, fixture))
            if fixture.atomic and len(related) > fixture.expected_chunk_count:
                candidates.append(self._candidate("SPLIT_ENTITY", fixture.canonical_key, related,
                                                  {"entity_id": fixture.entity_id, "expected_chunk_count": fixture.expected_chunk_count}))
            if not fixture.atomic and fixture.parent_key:
                invalid_children = tuple(chunk for chunk in related if chunk.parent_key != fixture.parent_key)
                if invalid_children:
                    candidates.append(self._candidate("PARENT_CHILD_INTEGRITY", fixture.canonical_key, invalid_children,
                                                      {"entity_id": fixture.entity_id, "parent_key": fixture.parent_key}))
        for chunk in values:
            reasons: list[str] = []
            headings = {part for part in chunk.section_path if part}
            entity_ids = {part.split(":", 1)[1] for part in headings if part.startswith("entity:")}
            if len(headings) > 1 or len(entity_ids) > 1:
                reasons.append("multiple_headings_or_entity_ids")
            expected_types = {fixture.content_type.value for fixture in fixture_values if self._related(chunk, fixture)}
            if expected_types and chunk.content_type.value not in expected_types:
                reasons.append("content_type_mismatch")
            if chunk.content_type not in _ENTITY_TYPES and any(part.startswith("entity:") for part in headings):
                reasons.append("content_type_mismatch")
            if chunk.token_count > self.large_span_tokens:
                reasons.append("large_span")
            if reasons:
                candidates.append(self._candidate("MIXED_CONTEXT", chunk.canonical_key, (chunk,), {"reasons": reasons}))
        order = {"SPLIT_ENTITY": 0, "PARENT_CHILD_INTEGRITY": 1, "MIXED_CONTEXT": 2}
        return sorted(candidates, key=lambda item: (order.get(item.issue, 99), item.canonical_key, item.chunk_ids))

    @staticmethod
    def _related(chunk: Chunk, fixture: EntityFixture) -> bool:
        return (chunk.canonical_key == fixture.canonical_key or chunk.canonical_key.startswith(fixture.canonical_key + ".")
                or chunk.parent_key == fixture.canonical_key or fixture.entity_id in chunk.canonical_key)

    @staticmethod
    def _candidate(issue: str, key: str, chunks: Sequence[Chunk], details: dict[str, object]) -> SemanticCandidate:
        ids = tuple(sorted(chunk.chunk_id for chunk in chunks))
        return SemanticCandidate(f"{issue}:{key}:{','.join(ids)}", issue, key, ids, details)


def decide_candidates(candidates: Iterable[SemanticCandidate], judge: SemanticJudgePort | Callable[[SemanticCandidate], SemanticDecision] | None = None) -> list[tuple[SemanticCandidate, SemanticDecision]]:
    if judge is None:
        return []
    function = judge.judge if hasattr(judge, "judge") else judge
    return [(candidate, function(candidate)) for candidate in candidates]


def evaluate_semantic(chunks: Iterable[Chunk], fixtures: Iterable[EntityFixture] = (), *, judge: SemanticJudgePort | Callable[[SemanticCandidate], SemanticDecision] | None = None, large_span_tokens: int = 500) -> tuple[dict[str, object], list[dict[str, object]]]:
    chunks = tuple(chunks)
    fixtures = tuple(fixtures)
    candidates = SemanticCandidateDetector(large_span_tokens=large_span_tokens).detect(chunks, fixtures)
    decisions = dict((candidate.candidate_id, decision) for candidate, decision in decide_candidates(candidates, judge))
    failures: list[dict[str, object]] = []
    for candidate in candidates:
        decision = decisions.get(candidate.candidate_id)
        if decision is not None and decision.valid:
            continue
        failure = {"type": candidate.issue, "canonical_key": candidate.canonical_key, "chunk_ids": list(candidate.chunk_ids), "details": candidate.details}
        if decision is not None:
            failure["details"] = {**candidate.details, "action": decision.action, "judge_issue": decision.issue}
        failures.append(failure)
    split = sum(item.issue == "SPLIT_ENTITY" for item in candidates)
    mixed = sum(item.issue == "MIXED_CONTEXT" for item in candidates)
    entity_count = max(1, len(fixtures))
    chunk_count = max(1, len(chunks))
    return {"split_entity_rate": split / entity_count, "mixed_context_rate": mixed / chunk_count,
            "candidate_count": len(candidates), "split_entity_candidates": split, "mixed_context_candidates": mixed}, sorted(failures, key=lambda item: (item["type"], item["canonical_key"], item["chunk_ids"]))
