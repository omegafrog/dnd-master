"""RAG-008 extraction lifecycle and the single-column walking skeleton."""
from __future__ import annotations

import hashlib
import json
import uuid
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Any, Mapping, Protocol

from preprocessing_agent.domain import PageStatus, ParsedDocument, ParsedPage, ParsedBlock, SourceSpan
from preprocessing_agent.domain.layout import BoundingBox, PageGeometry
from preprocessing_agent.exporters import ArtifactExporter
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.pipeline.pipeline import PreprocessingPipeline


class ExtractionStatus(str, Enum):
    QUEUED = "QUEUED"
    PROCESSING = "PROCESSING"
    VALIDATING = "VALIDATING"
    READY = "READY"
    NEEDS_REVIEW = "NEEDS_REVIEW"


@dataclass
class PageExtraction:
    page_number: int
    status: PageStatus = PageStatus.PENDING
    findings: list[str] = field(default_factory=list)

    @classmethod
    def validated(cls, page_number: int) -> "PageExtraction":
        return cls(page_number, PageStatus.VALIDATED)

    @classmethod
    def needs_review(cls, page_number: int, finding: str) -> "PageExtraction":
        return cls(page_number, PageStatus.NEEDS_REVIEW, [finding])


@dataclass
class ExtractionVersion:
    version_id: str
    document_id: str
    policy_version: str
    page_count: int
    status: ExtractionStatus = ExtractionStatus.QUEUED
    pages: dict[int, PageExtraction] = field(default_factory=dict)

    @classmethod
    def create(cls, version_id: str, document_id: str, policy_version: str, page_count: int) -> "ExtractionVersion":
        if not version_id or not document_id or not policy_version or page_count < 1:
            raise ValueError("version identity and positive page count are required")
        return cls(version_id, document_id, policy_version, page_count, ExtractionStatus.PROCESSING)

    def record_page(self, page: PageExtraction) -> None:
        if not 1 <= page.page_number <= self.page_count:
            raise ValueError("page number is outside extraction version")
        self.pages[page.page_number] = page
        self.status = ExtractionStatus.VALIDATING
        if page.status == PageStatus.NEEDS_REVIEW:
            self.status = ExtractionStatus.NEEDS_REVIEW

    def publish(self) -> None:
        if len(self.pages) != self.page_count or any(page.status != PageStatus.VALIDATED for page in self.pages.values()):
            self.status = ExtractionStatus.NEEDS_REVIEW
            raise ValueError("cannot publish extraction version with unvalidated pages")
        self.status = ExtractionStatus.READY


class NativePdfPort(Protocol):
    def extract(self, source: Path) -> list[Mapping[str, Any]]: ...


class PyMuPdfNativePdfAdapter:
    def extract(self, source: Path) -> list[Mapping[str, Any]]:
        try:
            import fitz  # type: ignore
        except ImportError as exc:
            raise RuntimeError("PyMuPDF is required for PDF process requests") from exc
        pages: list[Mapping[str, Any]] = []
        with fitz.open(source) as document:
            for number, page in enumerate(document, 1):
                rect = page.rect
                blocks = []
                for index, raw in enumerate(page.get_text("blocks")):
                    text = str(raw[4]).strip()
                    if not text:
                        continue
                    blocks.append({"block_id": f"p{number}-b{index}", "text": text, "bbox": raw[:4]})
                pages.append({"page_number": number, "geometry": {"width": rect.width, "height": rect.height}, "blocks": blocks})
        return pages


class ExtractionApplicationService:
    def __init__(self, native_pdf: NativePdfPort | None = None) -> None:
        self.native_pdf = native_pdf or PyMuPdfNativePdfAdapter()
        self._versions: dict[str, Mapping[str, Any]] = {}

    def preprocess(self, request: Mapping[str, Any]) -> Mapping[str, Any]:
        source = Path(str(request.get("source_path", request.get("source", ""))))
        if not source.is_file():
            raise ValueError("SOURCE_NOT_FOUND")
        output = Path(str(request.get("output_dir", source.parent / "preprocessed")))
        policy = str(request.get("policy_version", "rag-008"))
        raw_pages = self.native_pdf.extract(source) if source.suffix.lower() == ".pdf" else self._text_pages(source)
        document_id = hashlib.sha256(source.read_bytes()).hexdigest()[:16]
        version_id = str(request.get("version_id") or f"ev-{uuid.uuid4().hex[:12]}")
        version = ExtractionVersion.create(version_id, document_id, policy, len(raw_pages) or 1)
        parser_pages: list[Mapping[str, Any]] = []
        page_artifacts: list[dict[str, Any]] = []
        for position, raw in enumerate(raw_pages, 1):
            number = int(raw.get("page_number", position))
            geometry_raw = raw.get("geometry", {})
            try:
                geometry = PageGeometry(float(geometry_raw["width"]), float(geometry_raw["height"]))
                blocks = list(raw.get("blocks", ()))
                for block in blocks:
                    bbox = BoundingBox(*(float(item) for item in block["bbox"]))
                    if not geometry.contains(bbox):
                        raise ValueError("INVALID_GEOMETRY")
                version.record_page(PageExtraction.validated(number))
                parser_pages.append(raw)
                page_artifacts.append({"page_number": number, "status": PageStatus.VALIDATED.value, "geometry": {"width": geometry.width, "height": geometry.height, "unit": geometry.unit, "origin": geometry.origin}})
            except (KeyError, TypeError, ValueError) as exc:
                version.record_page(PageExtraction.needs_review(number, str(exc) or "INVALID_GEOMETRY"))
                page_artifacts.append({"page_number": number, "status": PageStatus.NEEDS_REVIEW.value, "findings": version.pages[number].findings})
        ready = False
        manifest: Mapping[str, Any] = {}
        if all(page.status == PageStatus.VALIDATED for page in version.pages.values()) and version.pages:
            version.publish()
            parser = PdfDocumentParser(extractor=lambda _source: parser_pages)
            result = PreprocessingPipeline.from_config({"name": "extraction-version", "pipeline_version": policy}, parser=parser, output_dir=output).run(source=source)
            manifest = dict(result.manifest)
            ready = True
        else:
            output.mkdir(parents=True, exist_ok=True)
            for name in ("chunks.jsonl", "document_tree.json"):
                path = output / name
                if path.exists(): path.unlink()
            (output / "manifest.json").write_text(json.dumps({"status": version.status.value, "version_id": version.version_id}, sort_keys=True) + "\n", encoding="utf-8")
        version_artifact = {"version_id": version.version_id, "status": version.status.value, "pages": page_artifacts, "manifest": str(output / "manifest.json")}
        (output / "version.json").write_text(json.dumps(version_artifact, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        response = {"schema_version": "1", "request_id": str(request.get("request_id", "")), "version_id": version.version_id, "status": version.status.value, "page_summary": {"count": len(page_artifacts), "ready": sum(item["status"] == "VALIDATED" for item in page_artifacts)}, "artifacts": {"manifest": str(output / "manifest.json"), "version": str(output / "version.json"), "chunks": str(output / "chunks.jsonl") if ready else None}, "manifest": manifest}
        self._versions[version.version_id] = response
        return response

    def get_status(self, version_id: str) -> Mapping[str, Any]:
        if version_id not in self._versions:
            raise ValueError("VERSION_NOT_FOUND")
        return self._versions[version_id]

    @staticmethod
    def _text_pages(source: Path) -> list[Mapping[str, Any]]:
        text = source.read_text(encoding="utf-8")
        blocks = [{"block_id": f"p1-b{i}", "text": line.strip(), "bbox": (0, i * 12, 500, i * 12 + 10)} for i, line in enumerate(text.splitlines()) if line.strip()]
        return [{"page_number": 1, "geometry": {"width": 612, "height": max(792, len(blocks) * 12 + 20)}, "blocks": blocks}]
