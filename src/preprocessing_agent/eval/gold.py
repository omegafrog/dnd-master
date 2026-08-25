"""Gold context and required-evidence contracts for offline evaluation."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Iterable

from preprocessing_agent.domain import Chunk


@dataclass(frozen=True, slots=True)
class RequiredEvidence:
    group_id: str
    keys: tuple[str, ...]


@dataclass(frozen=True, slots=True)
class GoldCase:
    case_id: str
    question: str
    gold_context_keys: tuple[str, ...]
    required_evidence: tuple[RequiredEvidence, ...] = ()

    @classmethod
    def from_dict(cls, value: dict[str, object]) -> "GoldCase":
        raw_evidence = value.get("required_evidence", ()) or ()
        if isinstance(raw_evidence, dict):
            raw_evidence = tuple({"group_id": key, "keys": value} for key, value in raw_evidence.items())
        evidence = tuple(RequiredEvidence(str(item if isinstance(item, str) else item.get("group_id", item.get("id", index))),
                                          (str(item),) if isinstance(item, str) else tuple(str(key) for key in item.get("keys", ())))
                        for index, item in enumerate(raw_evidence))
        keys = value.get("gold_context_keys", value.get("gold_context", ())) or ()
        return cls(str(value.get("case_id", value.get("id", ""))), str(value.get("question", "")), tuple(str(key) for key in keys), evidence)


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
                failures.append({"type": "MISSING_REQUIRED_EVIDENCE", "case_id": case.case_id, "canonical_key": case.gold_context_keys[0] if case.gold_context_keys else "", "chunk_ids": list(matched), "details": {"group_id": group.group_id, "keys": list(group.keys)}})
        if unmatched:
            failures.append({"type": "MISSING_GOLD_CONTEXT", "case_id": case.case_id, "canonical_key": unmatched[0], "chunk_ids": list(matched), "details": {"unmatched_keys": list(unmatched)}})
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
