"""Structured validation-agent boundary; it cannot return replacement text."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Protocol


VALIDATION_AGENT_PROMPT = """Inspect the supplied chunk metadata only. Return JSON with exactly valid, issue_type, and recommended_action. Never rewrite, summarize, or provide source text. recommended_action must be one of merge_previous, merge_next, split, reclassify, rebuild_section, manual_review."""


@dataclass(frozen=True, slots=True)
class ValidationAgentDecision:
    valid: bool
    issue_type: str | None = None
    recommended_action: str | None = None

    def __post_init__(self) -> None:
        allowed = {"merge_previous", "merge_next", "split", "reclassify", "rebuild_section", "manual_review", None}
        if self.recommended_action not in allowed:
            raise ValueError("validation agent action is not allowlisted")


class ValidationAgentPort(Protocol):
    def decide(self, chunk_metadata: dict[str, object]) -> ValidationAgentDecision: ...
