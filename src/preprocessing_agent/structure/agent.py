"""Safe structure-agent boundary: decisions can be applied, source cannot."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping, Protocol


@dataclass(frozen=True, slots=True)
class StructureDecisionInput:
    source_text: str
    current_level: int | None
    confidence: float


@dataclass(frozen=True, slots=True)
class StructureDecision:
    label: str | None
    level: int | None
    confidence: float
    reason: str
    review_required: bool


class StructureAgent(Protocol):
    def decide(self, input: StructureDecisionInput) -> StructureDecision: ...


class StructureFallback:
    def __init__(self, agent: StructureAgent | None = None, threshold: float = 0.65) -> None:
        self.agent = agent
        self.threshold = threshold

    def decide(self, input: StructureDecisionInput) -> StructureDecision:
        if input.confidence >= self.threshold:
            return StructureDecision(None, input.current_level, input.confidence, "deterministic decision accepted", False)
        if self.agent is None:
            return StructureDecision(None, input.current_level, input.confidence, "review required below threshold", True)
        result = self.agent.decide(input)
        return _safe_structure_result(result, input)


def _safe_structure_result(result: StructureDecision, input: StructureDecisionInput) -> StructureDecision:
    level = result.level if result.level is not None and 1 <= result.level <= 4 else input.current_level
    return StructureDecision(result.label, level, max(0.0, min(1.0, result.confidence)), result.reason, result.review_required)


def structured_decision(payload: Mapping[str, Any], source_text: str, threshold: float = 0.65) -> StructureDecision:
    """Parse an untrusted agent payload without accepting a source replacement."""
    if payload.get("source_text") not in (None, source_text):
        return StructureDecision(None, None, 0.0, "agent attempted to change source text", True)
    decision = StructureDecision(payload.get("label"), payload.get("level"), float(payload.get("confidence", 0.0)), str(payload.get("reason", "")), float(payload.get("confidence", 0.0)) < threshold)
    return _safe_structure_result(decision, StructureDecisionInput(source_text, None, decision.confidence))
