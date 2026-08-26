"""Deterministic heading features and decisions."""

from __future__ import annotations

import re
from dataclasses import dataclass

from preprocessing_agent.domain import ParsedBlock
from preprocessing_agent.parsers.normalize import normalized_key


@dataclass(frozen=True, slots=True)
class HeadingFeature:
    text: str
    normalized_text: str
    numbered: bool
    number_depth: int | None
    font_size: float | None
    font_weight: str | None
    short_line: bool
    punctuation_free: bool


@dataclass(frozen=True, slots=True)
class HeadingDecision:
    is_heading: bool
    level: int | None
    confidence: float
    feature: HeadingFeature
    reason: str


class HeadingDetector:
    def __init__(self, confidence_threshold: float = 0.65) -> None:
        self.confidence_threshold = confidence_threshold

    def features(self, block: ParsedBlock) -> HeadingFeature:
        text = block.source_text.strip()
        match = re.match(r"^(?P<number>(?:\d+(?:\.\d+)*\.?|[IVX]+\.)\s*)", text, re.I)
        number = match.group("number") if match else ""
        named = re.match(r"^(?P<kind>part|chapter|section|subsection)\b", text, re.I)
        depth = len(number.strip().rstrip(".").split(".")) if number.strip() else None
        if named:
            depth = {"part": 1, "chapter": 2, "section": 3, "subsection": 4}[named.group("kind").casefold()]
        return HeadingFeature(text, normalized_key(text), bool(number.strip() or named), depth, block.font_size, block.font_weight, len(text) <= 100, not text.endswith((".", ":", ";")))

    def detect(self, block: ParsedBlock) -> HeadingDecision:
        feature = self.features(block)
        numeric_prefix = bool(re.match(r"^(?:\d+(?:\.\d+)*\.?|[IVX]+\.)\s+", feature.text, re.I))
        # A bare number or numeric table value is not a section heading.  The
        # feature regex intentionally accepts compact numeric labels for
        # hierarchy, so guard the decision here before short-line scoring can
        # promote values such as "2–3\n-4" or a page number.
        if feature.numbered and not numeric_prefix and not re.match(r"^(?:part|chapter|section|subsection)\b", feature.text, re.I):
            return HeadingDecision(False, None, 0.0, feature, "numeric value without heading prefix")
        if numeric_prefix and not (feature.font_weight and feature.font_weight.casefold() in {"bold", "semibold", "700", "bolditalic"}) and not (feature.font_size is not None and feature.font_size >= 13):
            return HeadingDecision(False, None, 0.0, feature, "numeric text without heading layout evidence")
        score = 0.0
        if feature.numbered:
            score += 0.55
        if feature.font_weight and feature.font_weight.casefold() in {"bold", "semibold", "700", "bolditalic"}:
            score += 0.25
        if feature.short_line:
            score += 0.10
        if feature.punctuation_free:
            score += 0.10
        if feature.font_size is not None and feature.font_size >= 13:
            score += 0.80
        is_heading = score >= self.confidence_threshold
        level = _level(feature) if is_heading else None
        return HeadingDecision(is_heading, level, min(score, 1.0), feature, "deterministic heading features" if is_heading else "below heading threshold")


def _level(feature: HeadingFeature) -> int:
    if feature.number_depth is not None:
        return min(feature.number_depth, 4)
    return 1
