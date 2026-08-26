"""Bounded, page-scoped retry policy and diagnostic contracts."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping


@dataclass(frozen=True, slots=True)
class PageAttempt:
    page_number: int
    attempt_number: int
    strategy: str
    regions: tuple[tuple[float, float, float, float], ...] = ()
    status: str = "FAILED"
    findings: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return {"page_number": self.page_number, "attempt": self.attempt_number,
                "strategy": self.strategy, "regions": [list(r) for r in self.regions],
                "status": self.status, "findings": list(self.findings)}


@dataclass(frozen=True, slots=True)
class RetryDirective:
    page_number: int
    strategy: str
    regions: tuple[tuple[float, float, float, float], ...] = ()
    reason: str = ""


class PageRetryPolicy:
    """Maps findings to safe strategies and permits at most two retries."""
    MAX_ADDITIONAL_ATTEMPTS = 2
    _MAPPING = {
        "AMBIGUOUS_COLUMNS": "alternate-column-hypothesis",
        "LOW_CONFIDENCE_COLUMNS": "alternate-column-hypothesis",
        "IRREGULAR_TABLE": "table-region-resplit",
        "TABLE_STRUCTURE_UNCERTAIN": "table-region-resplit",
        "OCR": "ocr-region",
        "RENDER": "render-recheck",
    }

    def directive(self, page: Mapping[str, Any], *, regions: Any = ()) -> RetryDirective:
        number = int(page.get("page_number", 0))
        findings = tuple(str(item) for item in page.get("findings", ()))
        strategy = "diagnostic-recheck"
        for finding in findings:
            for key, value in self._MAPPING.items():
                if key in finding:
                    strategy = value
                    break
            if strategy != "diagnostic-recheck":
                break
        normalized = []
        for region in regions or ():
            if isinstance(region, (list, tuple)) and len(region) == 4:
                normalized.append(tuple(float(value) for value in region))
        return RetryDirective(number, strategy, tuple(normalized), ",".join(findings))

    def can_retry(self, attempts: int) -> bool:
        return isinstance(attempts, int) and attempts >= 1 and attempts - 1 < self.MAX_ADDITIONAL_ATTEMPTS

    def request(self, page: Mapping[str, Any], *, regions: Any = ()) -> PageAttempt:
        attempts = int(page.get("attempts", 1))
        if not self.can_retry(attempts):
            raise ValueError("RETRY_BUDGET_EXHAUSTED")
        directive = self.directive(page, regions=regions)
        return PageAttempt(directive.page_number, attempts + 1, directive.strategy,
                           directive.regions, "REQUESTED", tuple(str(x) for x in page.get("findings", ())))
