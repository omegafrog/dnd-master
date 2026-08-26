"""Deterministic layout validation and publication-gate contracts.

The validator is intentionally vendor neutral: renderers and secondary
validators exchange mappings at this boundary, while the gate only consumes
canonical geometry and structure evidence.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Mapping, Protocol, Sequence

from preprocessing_agent.domain.layout import BoundingBox, PageGeometry


@dataclass(frozen=True, slots=True)
class ConfidenceVector:
    text: float = 1.0
    block_type: float = 1.0
    columns: float = 1.0
    order: float = 1.0
    heading: float = 1.0
    table: float = 1.0

    def __post_init__(self) -> None:
        for value in (self.text, self.block_type, self.columns, self.order, self.heading, self.table):
            if not 0 <= value <= 1:
                raise ValueError("confidence axes must be between 0 and 1")

    def as_dict(self) -> dict[str, float]:
        return {"text": self.text, "block_type": self.block_type, "columns": self.columns,
                "order": self.order, "heading": self.heading, "table": self.table}


@dataclass(frozen=True, slots=True)
class LayoutValidationPolicy:
    thresholds: Mapping[str, float] = field(default_factory=lambda: {
        "text": .8, "block_type": .8, "columns": .8, "order": .8, "heading": .8, "table": .8,
    })
    critical_axes: frozenset[str] = frozenset({"text", "block_type", "columns", "order", "heading", "table"})
    high_risk_classes: frozenset[str] = frozenset({"mixed", "image-only", "ambiguous"})

    def __post_init__(self) -> None:
        allowed = set(ConfidenceVector().as_dict())
        if set(self.thresholds) - allowed or not self.critical_axes <= allowed:
            raise ValueError("unknown confidence axis")
        if any(not 0 <= float(value) <= 1 for value in self.thresholds.values()):
            raise ValueError("confidence thresholds must be between 0 and 1")


@dataclass(frozen=True, slots=True)
class ValidationFinding:
    code: str
    message: str
    severity: str = "error"
    action: str = "review"
    bbox: tuple[float, float, float, float] | None = None


@dataclass(frozen=True, slots=True)
class LayoutValidationResult:
    valid: bool
    high_risk: bool
    confidence: ConfidenceVector
    findings: tuple[ValidationFinding, ...] = ()
    render_evidence: Mapping[str, Any] = field(default_factory=dict)
    secondary_validated: bool = False

    def as_dict(self) -> dict[str, Any]:
        return {"valid": self.valid, "high_risk": self.high_risk,
                "confidence": self.confidence.as_dict(),
                "findings": [finding.__dict__ if hasattr(finding, "__dict__") else {
                    "code": finding.code, "message": finding.message, "severity": finding.severity,
                    "action": finding.action, "bbox": finding.bbox} for finding in self.findings],
                "render_evidence": dict(self.render_evidence),
                "secondary_validated": self.secondary_validated}


class SecondaryLayoutValidatorPort(Protocol):
    def validate(self, page: Mapping[str, Any], render_evidence: Mapping[str, Any]) -> Mapping[str, Any] | bool: ...


class LayoutValidationService:
    """Validate independent axes; an average can never override one failure."""

    def __init__(self, policy: LayoutValidationPolicy | None = None,
                 secondary: SecondaryLayoutValidatorPort | None = None) -> None:
        self.policy = policy or LayoutValidationPolicy()
        self.secondary = secondary

    def validate(self, page: Mapping[str, Any], render_evidence: Mapping[str, Any] | None = None) -> LayoutValidationResult:
        evidence = dict(render_evidence or page.get("render_evidence", {}))
        findings: list[ValidationFinding] = []
        blocks = tuple(page.get("blocks", ()))
        geometry = page.get("geometry", {})
        width, height = float(geometry.get("width", 0)), float(geometry.get("height", 0))
        axes = {key: 1.0 for key in ConfidenceVector().as_dict()}
        if not evidence or evidence.get("page_number") not in (None, page.get("page_number")):
            axes["text"] = 0.0; findings.append(ValidationFinding("RENDER_EVIDENCE_MISSING", "render evidence is required"))
        elif evidence.get("image") in (b"", "") and not evidence.get("sha256"):
            axes["text"] = 0.0; findings.append(ValidationFinding("RENDER_EVIDENCE_EMPTY", "render evidence has no image or digest"))
        boxes: list[tuple[str, tuple[float, float, float, float]]] = []
        for block in blocks:
            raw = block.get("bbox") if isinstance(block, Mapping) else None
            if raw is None or len(raw) != 4:
                axes["text"] = 0.0; findings.append(ValidationFinding("BLOCK_BBOX_MISSING", "block geometry is missing")); continue
            box = tuple(float(value) for value in raw)
            boxes.append((str(block.get("block_id", "")), box))
            if width <= 0 or height <= 0 or not (0 <= box[0] <= box[2] <= width and 0 <= box[1] <= box[3] <= height):
                axes["text"] = 0.0; findings.append(ValidationFinding("BLOCK_OUT_OF_BOUNDS", "block lies outside page geometry", bbox=box))
        layout = page.get("layout", {})
        profiles = layout.get("profiles", ()) if isinstance(layout, Mapping) else ()
        if layout.get("ambiguous", False) if isinstance(layout, Mapping) else False:
            axes["columns"] = 0.0; findings.append(ValidationFinding("AMBIGUOUS_COLUMNS", "column hypothesis is ambiguous"))
        if any(isinstance(profile, Mapping) and profile.get("ambiguous") for profile in profiles):
            axes["columns"] = 0.0; findings.append(ValidationFinding("AMBIGUOUS_COLUMNS", "column profile is ambiguous"))
        ordered = layout.get("ordered_block_ids", ()) if isinstance(layout, Mapping) else ()
        ids = [block_id for block_id, _ in boxes]
        if ordered and (set(ordered) != set(ids) or len(ordered) != len(ids)):
            axes["order"] = 0.0; findings.append(ValidationFinding("ORDER_COVERAGE", "reading order does not cover blocks exactly once"))
        headings = page.get("heading_associations", ())
        if any((item.get("ambiguous") or item.get("findings")) for item in headings if isinstance(item, Mapping)):
            axes["heading"] = 0.0; findings.append(ValidationFinding("HEADING_ASSOCIATION_AMBIGUOUS", "heading association requires review"))
        tables = page.get("tables", ())
        if any(item.get("findings") or item.get("uncertain_cell_ids") for item in tables if isinstance(item, Mapping)):
            axes["table"] = 0.0; findings.append(ValidationFinding("TABLE_STRUCTURE_UNCERTAIN", "table structure requires review"))
        if any(str(block.get("kind", "text")).lower() not in {"text", "heading", "table_cell", "table", "cell"} for block in blocks if isinstance(block, Mapping)):
            axes["block_type"] = 0.0; findings.append(ValidationFinding("UNKNOWN_BLOCK_TYPE", "block type is not recognized"))
        page_class = str(page.get("page_classification", "text-native"))
        high_risk = page_class in self.policy.high_risk_classes or any(str(block.get("extraction_method", "native")) != "native" for block in blocks if isinstance(block, Mapping))
        secondary_validated = False
        if high_risk:
            if self.secondary is None:
                findings.append(ValidationFinding("SECONDARY_VALIDATOR_UNAVAILABLE", "high-risk page has no secondary validation"))
            else:
                result = self.secondary.validate(page, evidence)
                secondary_validated = bool(result if isinstance(result, bool) else result.get("valid", False))
                if not secondary_validated:
                    findings.append(ValidationFinding("SECONDARY_VALIDATION_FAILED", "secondary layout validation failed"))
        confidence = ConfidenceVector(**axes)
        findings.extend(ValidationFinding(f"LOW_CONFIDENCE_{axis.upper()}", f"{axis} confidence is below policy")
                        for axis in self.policy.critical_axes if confidence.as_dict()[axis] < self.policy.thresholds.get(axis, .8))
        valid = not findings and all(confidence.as_dict()[axis] >= self.policy.thresholds.get(axis, .8) for axis in self.policy.critical_axes)
        return LayoutValidationResult(valid, high_risk, confidence, tuple(findings), evidence, secondary_validated)
