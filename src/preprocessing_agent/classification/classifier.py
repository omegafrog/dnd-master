"""Deterministic D&D content candidate classification."""

from __future__ import annotations

import re
from dataclasses import dataclass

from preprocessing_agent.domain import ContentType, SectionNode


@dataclass(frozen=True, slots=True)
class ClassificationDecision:
    label: ContentType
    confidence: float
    reason: str
    review_required: bool = False
    source_text: str | None = None

    def __post_init__(self) -> None:
        # Agent-facing decisions may carry an echo for verification, never a replacement.
        if self.source_text is not None:
            object.__setattr__(self, "source_text", str(self.source_text))


class DeterministicContentClassifier:
    def __init__(self, confidence_threshold: float = 0.70) -> None:
        self.confidence_threshold = confidence_threshold

    def classify(self, section: SectionNode, source_text: str = "") -> ClassificationDecision:
        text = f"{section.title}\n{source_text}".strip()
        rules: tuple[tuple[ContentType, tuple[str, ...], float], ...] = (
            (ContentType.SPELL, (r"\b(?:spell|cantrip)\b", r"\bcasting time\b", r"\brange\b.*\bduration\b"), 0.92),
            (ContentType.MONSTER_STAT_BLOCK, (r"\b(?:armor class|hit points|saving throws)\b", r"\bchallenge\b", r"\bactions\b"), 0.95),
            (ContentType.TABLE, (r"\btable\b", r"\bd\d+\b", r"\|.*\|"), 0.88),
            (ContentType.RULE, (r"\b(?:rule|rules|advantage|disadvantage|proficiency)\b",), 0.78),
        )
        for label, patterns, confidence in rules:
            if sum(bool(re.search(pattern, text, re.I | re.S)) for pattern in patterns) >= (2 if label in {ContentType.SPELL, ContentType.MONSTER_STAT_BLOCK, ContentType.TABLE} else 1):
                return ClassificationDecision(label, confidence, "deterministic content pattern", confidence < self.confidence_threshold, source_text)
        return ClassificationDecision(ContentType.UNKNOWN, 0.0, "no deterministic content pattern", True, source_text)
