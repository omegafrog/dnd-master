"""Gold context and required-evidence contracts for offline evaluation."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence

from preprocessing_agent.domain import Chunk


@dataclass(frozen=True, slots=True)
class RequiredEvidence:
    group_id: str
    keys: tuple[str, ...]

    @property
    def chunk_ids(self) -> tuple[str, ...]:
        """Explicit-schema alias; legacy fixtures continue to use ``keys``."""

        return self.keys


EvidenceGroup = RequiredEvidence


@dataclass(frozen=True, slots=True)
class GoldCase:
    case_id: str
    question: str
    gold_context_keys: tuple[str, ...] = ()
    required_evidence: tuple[RequiredEvidence, ...] = ()
    answerable: bool | None = None
    gold_chunk_ids: tuple[str, ...] = ()
    evidence_groups: tuple[RequiredEvidence, ...] = ()

    @classmethod
    def from_dict(cls, value: dict[str, object]) -> "GoldCase":
        answerable = value.get("answerable")
        if answerable is not None and not isinstance(answerable, bool):
            raise ValueError("answerable must be a boolean")
        raw_evidence = value.get("evidence_groups", value.get("required_evidence", ())) or ()
        if isinstance(raw_evidence, dict):
            raw_evidence = tuple({"group_id": key, "keys": value} for key, value in raw_evidence.items())
        evidence = tuple(RequiredEvidence(str(item if isinstance(item, str) else item.get("group_id", item.get("id", index))),
                                          (str(item),) if isinstance(item, str) else tuple(str(key) for key in item.get("chunk_ids", item.get("keys", ()))))
                        for index, item in enumerate(raw_evidence))
        keys = value.get("gold_context_keys", value.get("gold_context", ())) or ()
        gold_ids = value.get("gold_chunk_ids", ()) or ()
        explicit_groups = tuple(evidence)
        return cls(str(value.get("case_id", value.get("id", ""))), str(value.get("question", "")),
                   tuple(str(key) for key in keys), explicit_groups, answerable,
                   tuple(str(chunk_id) for chunk_id in gold_ids), explicit_groups)


@dataclass(frozen=True, slots=True)
class GoldResolution:
    case_id: str
    chunk_ids: tuple[str, ...]
    unmatched_keys: tuple[str, ...]
    evidence_complete: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class GoldEvaluation:
    metrics: dict[str, float | int]
    resolutions: tuple[GoldResolution, ...]
    unmatched_keys: tuple[str, ...]
    failures: tuple[dict[str, object], ...]


class GoldValidationError(ValueError):
    """Raised by callers that require a validated gold snapshot."""


@dataclass(frozen=True, slots=True)
class GoldValidationResult:
    valid: bool
    issues: tuple[dict[str, object], ...]
    cases: tuple[dict[str, object], ...]
    metrics: dict[str, int]

    def to_dict(self) -> dict[str, object]:
        return {"valid": self.valid, "issues": list(self.issues), "cases": list(self.cases), "metrics": self.metrics}

    def raise_if_invalid(self) -> None:
        if not self.valid:
            raise GoldValidationError("gold validation failed")


def _issue(issue_type: str, case_id: str = "", **details: object) -> dict[str, object]:
    return {"type": issue_type, "case_id": case_id, "details": details}


def validate_gold_cases(
    cases: Iterable[GoldCase],
    chunks: Iterable[Chunk] | Iterable[str],
    *,
    expected_case_ids: Sequence[str] | None = None,
    expected_count: int | None = None,
) -> GoldValidationResult:
    """Validate an explicit gold snapshot against exported evaluator chunk IDs.

    The legacy canonical-key fields are accepted and checked, but explicit
    ``gold_chunk_ids`` take precedence when present.
    """

    cases = tuple(cases)
    chunk_values = tuple(chunks)
    known_ids = {value.chunk_id if isinstance(value, Chunk) else str(value) for value in chunk_values}
    issues: list[dict[str, object]] = []
    seen: set[str] = set()
    normalized: list[dict[str, object]] = []
    for case in cases:
        case_id = case.case_id
        if not case_id:
            issues.append(_issue("MISSING_CASE_ID"))
        if case_id in seen:
            issues.append(_issue("DUPLICATE_CASE_ID", case_id))
        seen.add(case_id)
        ids = tuple(case.gold_chunk_ids)
        if not ids and case.answerable is None:
            ids = tuple(case.gold_context_keys)
        duplicate_ids = sorted({value for value in ids if ids.count(value) > 1})
        for chunk_id in duplicate_ids:
            issues.append(_issue("DUPLICATE_GOLD_CHUNK_ID", case_id, chunk_id=chunk_id))
        unknown = sorted(set(ids) - known_ids) if case.gold_chunk_ids else ()
        for chunk_id in unknown:
            issues.append(_issue("UNKNOWN_GOLD_CHUNK_ID", case_id, chunk_id=chunk_id))
        answerable = case.answerable if case.answerable is not None else bool(ids)
        groups = case.evidence_groups or case.required_evidence
        has_evidence = bool(ids or any(group.chunk_ids for group in groups))
        if answerable and not ids:
            issues.append(_issue("ANSWERABLE_WITHOUT_GOLD", case_id))
        if not answerable and has_evidence:
            issues.append(_issue("UNANSWERABLE_WITH_GOLD", case_id, chunk_ids=list(ids)))
        group_values = []
        seen_group_ids: set[str] = set()
        for group in groups:
            group_ids = tuple(group.chunk_ids)
            if group.group_id in seen_group_ids:
                issues.append(_issue("DUPLICATE_EVIDENCE_GROUP_ID", case_id, group_id=group.group_id))
            seen_group_ids.add(group.group_id)
            if len(set(group_ids)) != len(group_ids):
                issues.append(_issue("DUPLICATE_EVIDENCE_CHUNK_ID", case_id, group_id=group.group_id))
            unknown_group = sorted(set(group_ids) - known_ids) if case.gold_chunk_ids else ()
            for chunk_id in unknown_group:
                issues.append(_issue("UNKNOWN_EVIDENCE_CHUNK_ID", case_id, group_id=group.group_id, chunk_id=chunk_id))
            if case.gold_chunk_ids and not set(group_ids).issubset(set(case.gold_chunk_ids)):
                issues.append(_issue("EVIDENCE_NOT_IN_GOLD", case_id, group_id=group.group_id))
            if not ids and group_ids:
                issues.append(_issue("EVIDENCE_WITHOUT_GOLD", case_id, group_id=group.group_id))
            group_values.append({"group_id": group.group_id, "chunk_ids": list(group_ids)})
        normalized.append({"case_id": case_id, "question": case.question, "answerable": answerable,
                           "gold_chunk_ids": list(ids), "evidence_groups": group_values})
    if expected_case_ids is not None:
        expected = set(str(value) for value in expected_case_ids)
        for case_id in sorted(expected - seen):
            issues.append(_issue("MISSING_CASE_ID", case_id))
        for case_id in sorted(seen - expected):
            issues.append(_issue("UNEXPECTED_CASE_ID", case_id))
    if expected_count is not None and len(cases) != expected_count:
        issues.append(_issue("CASE_COUNT_MISMATCH", details_expected=expected_count, actual=len(cases)))
    metrics = {"case_count": len(cases), "answerable_cases": sum(bool(case["answerable"]) for case in normalized),
               "unanswerable_cases": sum(not bool(case["answerable"]) for case in normalized), "issue_count": len(issues)}
    return GoldValidationResult(not issues, tuple(issues), tuple(normalized), metrics)


def write_gold_validation(result: GoldValidationResult, output: str | Path) -> Path:
    import json
    destination = Path(output)
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(result.to_dict(), ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return destination


def evaluate_gold(cases: Iterable[GoldCase], chunks: Iterable[Chunk]) -> GoldEvaluation:
    cases = tuple(sorted(cases, key=lambda case: case.case_id))
    by_key = {chunk.canonical_key: chunk.chunk_id for chunk in chunks}
    resolutions: list[GoldResolution] = []
    failures: list[dict[str, object]] = []
    all_keys: list[str] = []
    resolved_keys = 0
    answerable = 0
    complete_groups = 0
    total_groups = 0
    for case in cases:
        all_keys.extend(case.gold_context_keys)
        matched = tuple(sorted({by_key[key] for key in case.gold_context_keys if key in by_key}))
        unmatched = tuple(key for key in case.gold_context_keys if key not in by_key)
        resolved_keys += len(case.gold_context_keys) - len(unmatched)
        if case.gold_context_keys and not unmatched and len(matched) == 1:
            answerable += 1
        complete: list[str] = []
        for group in case.required_evidence:
            total_groups += 1
            if group.keys and all(key in by_key for key in group.keys):
                complete_groups += 1
                complete.append(group.group_id)
            else:
                failures.append({"type": "GOLD_EVIDENCE_SPLIT", "case_id": case.case_id, "canonical_key": case.gold_context_keys[0] if case.gold_context_keys else "", "chunk_ids": list(matched), "details": {"group_id": group.group_id, "keys": list(group.keys)}})
        if unmatched:
            failures.append({"type": "GOLD_CONTEXT_MISSING", "case_id": case.case_id, "canonical_key": unmatched[0], "chunk_ids": list(matched), "details": {"unmatched_keys": list(unmatched)}})
        resolutions.append(GoldResolution(case.case_id, matched, unmatched, tuple(complete)))
    total_key_count = len(all_keys) or 1
    case_count = len(cases) or 1
    return GoldEvaluation({"gold_context_coverage": resolved_keys / total_key_count,
                           "single_chunk_answerability_rate": answerable / case_count,
                           "evidence_completeness": complete_groups / (total_groups or 1),
                           "cases": len(cases), "gold_context_keys": len(all_keys), "unmatched_keys": len(set(all_keys) - set(by_key))},
                          tuple(resolutions), tuple(sorted(set(key for resolution in resolutions for key in resolution.unmatched_keys))),
                          tuple(sorted(failures, key=lambda item: (item.get("type", ""), item.get("case_id", ""), item.get("canonical_key", "")))))


def load_gold_cases(path: str | object) -> tuple[GoldCase, ...]:
    from pathlib import Path
    import json
    values = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if line.strip():
            value = json.loads(line)
            if value.get("type") not in {"entity_fixture", "semantic_fixture"} and "entity_id" not in value:
                values.append(GoldCase.from_dict(value))
    return tuple(values)
