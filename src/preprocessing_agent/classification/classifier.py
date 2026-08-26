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
        structural_title = bool(re.match(r"\s*(?:part|chapter|section|appendix|book)\b", section.title, re.I))
        if _has_table_layout(source_text):
            return ClassificationDecision(ContentType.TABLE, 0.88, "deterministic table layout", False, source_text)
        rules: tuple[tuple[ContentType, tuple[str, ...], float], ...] = (
            (ContentType.SPELL, (r"\b(?:spell|cantrip)\b", r"\bcasting time\b", r"\brange\b", r"\bduration\b"), 0.92),
            (ContentType.MONSTER_STAT_BLOCK, (r"\barmor class\b", r"\bhit points\b", r"\b(?:challenge|saving throws)\b", r"\bactions\b"), 0.95),
            (ContentType.RULE, (r"\b(?:rule|rules|advantage|disadvantage|proficiency)\b",), 0.78),
        )
        for label, patterns, confidence in rules:
            matches = [bool(re.search(pattern, text, re.I | re.S)) for pattern in patterns]
            if label is ContentType.SPELL:
                entry_signal = bool(re.search(r"\b(?:\d+(?:st|nd|rd|th)-level|evocation|abjuration|conjuration|divination|enchantment|illusion|necromancy|transmutation)\b", text, re.I))
                entry_signal = entry_signal or bool(re.match(r"\s*\d+[.)]\s+", section.title))
                matches_required = not structural_title and entry_signal and matches[0] and matches[1] and (matches[2] or matches[3])
            else:
                required = 4 if label is ContentType.MONSTER_STAT_BLOCK else (2 if label is ContentType.TABLE else 1)
                matches_required = sum(matches) >= required
                if label is ContentType.MONSTER_STAT_BLOCK:
                    matches_required = not structural_title and matches_required
            if matches_required:
                return ClassificationDecision(label, confidence, "deterministic content pattern", confidence < self.confidence_threshold, source_text)
        return ClassificationDecision(ContentType.UNKNOWN, 0.0, "no deterministic content pattern", True, source_text)


def _has_table_layout(text: str) -> bool:
    if "|" in text:
        return True
    rows = re.findall(r"(?m)^\s*\d+\s+\S+", text)
    if len(rows) >= 2 and re.search(r"(?m)^\s*d(?:4|6|8|10|12|20|100)\b", text, re.I):
        return True
    return False
