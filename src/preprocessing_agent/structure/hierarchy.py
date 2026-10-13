"""Resolve heading hierarchy from document-wide evidence, abstaining when ambiguous."""
from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import replace
import logging
import os
import time

from preprocessing_agent.domain import ParsedDocument

_LOGGER = logging.getLogger(__name__)


class DocumentHierarchyResolver:
    """Profile document-wide styles and resolve only hierarchy supported by explicit numbering."""

    def resolve(self, document: ParsedDocument) -> ParsedDocument:
        started = time.monotonic()
        associations = [(page_index, item) for page_index, page in enumerate(document.pages)
                        for item in page.heading_associations]
        if _diagnostics_enabled():
            _LOGGER.info("dev_document_hierarchy stage=start documentId=%s pageCount=%d headingCount=%d candidateCount=%d outcome=started",
                         document.document_id, len(document.pages), len(associations),
                         sum(len(page.heading_candidates) for page in document.pages))

        candidates = {_candidate_key(candidate): candidate
                      for page in document.pages for candidate in page.heading_candidates
                      if _value(candidate, "accepted", False)}
        style_counts: Counter[tuple[float, bool]] = Counter()
        style_levels: dict[tuple[float, bool], set[int]] = defaultdict(set)
        occurrence_styles: dict[tuple[int, int], tuple[float, bool]] = {}
        for page_index, association in associations:
            candidate = candidates.get(_association_key(association))
            if candidate is None or _value(candidate, "font_size") is None:
                continue
            size = round(float(_value(candidate, "font_size")) * 2) / 2
            weight = str(_value(candidate, "font_weight", "") or "").casefold()
            style = (size, any(token in weight for token in ("bold", "semibold", "700")))
            occurrence_styles[(page_index, id(association))] = style
            style_counts[style] += 1
            if "numbered" in tuple(_value(association, "evidence", ())):
                style_levels[style].add(max(1, int(_value(association, "level", 1))))

        conflicting_styles = {style for style, levels in style_levels.items() if len(levels) > 1}
        resolved_count = unresolved_count = 0
        unresolved_ids: list[str] = []
        prior_resolved: list[tuple[int, str]] = []
        pages = []
        for page_index, page in enumerate(document.pages):
            updated = []
            for association in page.heading_associations:
                style = occurrence_styles.get((page_index, id(association)))
                numbered = "numbered" in tuple(_value(association, "evidence", ()))
                level = max(1, int(_value(association, "level", 1))) if numbered else None
                if numbered:
                    evidence = ("explicit_heading_number", "document_style_frequency_checked")
                else:
                    evidence = ()

                parent_id = None
                if level is not None and level > 1:
                    parent_id = next((block_id for prior_level, block_id in reversed(prior_resolved)
                                      if prior_level == level - 1), None)
                    if parent_id is None:
                        level = None
                        evidence = ("explicit_heading_number", "parent_heading_not_found")

                if level is None:
                    if not evidence:
                        evidence = ("document_style_profile_checked",
                                    "style_does_not_uniquely_resolve_level")
                        if style in conflicting_styles:
                            evidence += ("document_style_reused_across_explicit_levels",)
                    updated.append(replace(association, hierarchy_resolved=False,
                                           hierarchy_evidence=evidence, parent_heading_block_id=None,
                                           ambiguous=association.ambiguous, findings=association.findings))
                    prior_resolved.clear()
                    unresolved_count += 1
                    unresolved_ids.append(f"{page.page_number}:{association.heading_block_id}")
                    continue

                updated.append(replace(association, level=level, hierarchy_resolved=True,
                                       hierarchy_evidence=evidence, parent_heading_block_id=parent_id,
                                       ambiguous=False,
                                       findings=tuple(item for item in association.findings
                                                      if item != "HIERARCHY_EVIDENCE_INSUFFICIENT")))
                prior_resolved.append((level, association.heading_block_id))
                resolved_count += 1
            pages.append(replace(page, heading_associations=tuple(updated)))

        if _diagnostics_enabled():
            _LOGGER.info(
                "dev_document_hierarchy stage=complete documentId=%s pageCount=%d headingCount=%d "
                "styleCount=%d repeatedStyleCount=%d conflictingStyleCount=%d resolvedHeadingCount=%d unresolvedHeadingCount=%d "
                "conflictingStyleKeys=%s unresolvedHeadingIds=%s recoveryAction=%s elapsedMs=%d outcome=success",
                document.document_id, len(document.pages), len(associations), len(style_counts),
                sum(count >= 2 for count in style_counts.values()), len(conflicting_styles),
                resolved_count, unresolved_count,
                ",".join(f"{size:g}:{int(bold)}" for size, bold in conflicting_styles),
                ",".join(unresolved_ids[:30]),
                "preserve_unresolved_for_review" if unresolved_count else "none",
                int((time.monotonic() - started) * 1000),
            )
        return replace(document, pages=tuple(pages))


def _candidate_key(candidate: object) -> tuple[str, int, int]:
    return (str(_value(candidate, "block_id", "")),
            int(_value(candidate, "source_char_start", 0) or 0),
            int(_value(candidate, "source_char_end", 0) or 0))


def _association_key(association: object) -> tuple[str, int, int]:
    return (str(_value(association, "heading_block_id", "")),
            int(_value(association, "source_char_start", 0) or 0),
            int(_value(association, "source_char_end", 0) or 0))


def _value(value: object, name: str, default: object = None) -> object:
    return value.get(name, default) if isinstance(value, dict) else getattr(value, name, default)


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}
