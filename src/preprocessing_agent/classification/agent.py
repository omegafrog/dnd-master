"""Safe classification-agent boundary and low-confidence fallback."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Mapping, Protocol

from preprocessing_agent.domain import ContentType, SectionNode
from .classifier import ClassificationDecision


class ClassificationAgent(Protocol):
    def decide(self, section: SectionNode, source_text: str) -> ClassificationDecision: ...


class ClassificationFallback:
    def __init__(self, agent: ClassificationAgent | None = None, threshold: float = 0.70) -> None:
        self.agent = agent
        self.threshold = threshold

    def decide(self, section: SectionNode, source_text: str, deterministic: ClassificationDecision) -> ClassificationDecision:
        if deterministic.confidence >= self.threshold:
            return deterministic
        if self.agent is None:
            return ClassificationDecision(ContentType.UNKNOWN, deterministic.confidence, "review required below threshold", True, source_text)
        result = self.agent.decide(section, source_text)
        if result.source_text not in (None, source_text):
            return ClassificationDecision(ContentType.UNKNOWN, 0.0, "agent attempted to change source text", True, source_text)
        return ClassificationDecision(result.label, max(0.0, min(1.0, result.confidence)), result.reason, result.review_required, source_text)


def structured_decision(payload: Mapping[str, Any], source_text: str, threshold: float = 0.70) -> ClassificationDecision:
    if payload.get("source_text") not in (None, source_text):
        return ClassificationDecision(ContentType.UNKNOWN, 0.0, "agent attempted to change source text", True, source_text)
    try:
        label = ContentType(payload.get("label", ContentType.UNKNOWN))
    except ValueError:
        label = ContentType.UNKNOWN
    confidence = float(payload.get("confidence", 0.0))
    return ClassificationDecision(label, max(0.0, min(1.0, confidence)), str(payload.get("reason", "")), confidence < threshold, source_text)
