"""PyMuPDF4LLM Markdown extraction for page-addressable PDF content."""
from __future__ import annotations

import contextlib
import logging
import os
import sys
import time
from pathlib import Path
from typing import Any, Mapping, Sequence

from preprocessing_agent.layout.page_classification import form_content_state, form_geometry_counts, is_form_like

_LOGGER = logging.getLogger(__name__)


class PyMuPdf4LlmPdfAdapter:
    provider_name = "pymupdf4llm"

    def extract(self, source: Path, *, page_numbers: Sequence[int] | None = None) -> list[Mapping[str, Any]]:
        started = time.monotonic()
        source = Path(source)
        _dev_log("dev_preprocessing_pdf_provider stage=started provider=pymupdf4llm expectedOutput=page_markdown actualSuffix=%s route=markdown_extraction outcome=started",
                 source.suffix.lower() or "none")
        try:
            with contextlib.redirect_stdout(sys.stderr):
                import pymupdf
                import pymupdf4llm
        except ImportError as exc:
            _dev_log("dev_preprocessing_pdf_provider stage=failed provider=pymupdf4llm expectedDependencies=PyMuPDF_and_pymupdf4llm actualFailure=%s recoveryAction=install_project_dependencies outcome=failed",
                     type(exc).__name__)
            raise RuntimeError("PYMUPDF4LLM_UNAVAILABLE") from exc

        try:
            pages_arg = [number - 1 for number in page_numbers] if page_numbers is not None else None
            with contextlib.redirect_stdout(sys.stderr):
                chunks = pymupdf4llm.to_markdown(
                    str(source), pages=pages_arg, page_chunks=True, write_images=False,
                    embed_images=False, ignore_images=True, show_progress=False,
                )
            if not isinstance(chunks, list):
                raise ValueError("PYMUPDF4LLM_RETURNED_NON_PAGE_OUTPUT")
            result: list[Mapping[str, Any]] = []
            with pymupdf.open(source) as document:
                for position, chunk in enumerate(chunks, start=1):
                    if not isinstance(chunk, Mapping) or not isinstance(chunk.get("metadata"), Mapping):
                        raise ValueError("PYMUPDF4LLM_INVALID_PAGE_CHUNK")
                    metadata = chunk["metadata"]
                    number = int(metadata.get("page", position))
                    page = document.load_page(number - 1)
                    text = str(chunk.get("text", "")).strip()
                    rect = page.rect
                    images = [tuple(float(value) for value in item["bbox"])
                              for item in page.get_image_info(xrefs=True)
                              if isinstance(item.get("bbox"), (list, tuple)) and len(item["bbox"]) == 4]
                    text_lines = sum(len(block.get("lines", ())) for block in page.get_text("dict").get("blocks", ())
                                     if block.get("type") == 0)
                    drawings = list(page.get_drawings() or ())
                    form_diagnostics = form_geometry_counts(drawings, text_lines)
                    form_page = is_form_like(**form_diagnostics)
                    widgets = list(page.widgets() or ())
                    populated_widgets = [item for item in widgets if str(getattr(item, "field_value", "") or "").strip()]
                    # shortcut: Markdown has page-level provenance; map word geometry if paragraph-level anchors become necessary.
                    blocks = ([{"block_id": f"p{number}-markdown", "text": text,
                                "bbox": (float(rect.x0), float(rect.y0), float(rect.x1), float(rect.y1)),
                                "kind": "markdown", "extraction_method": "native"}] if text else [])
                    result.append({
                        "page_number": number,
                        "geometry": {"width": float(rect.width), "height": float(rect.height)},
                        "blocks": blocks,
                        "image_regions": images,
                        "page_classification": "form" if form_page else (
                            "mixed" if text and images else "text-native" if text else "image-only" if images else "ambiguous"),
                        "form_diagnostics": form_diagnostics,
                        "form_content_state": form_content_state(
                            populated_field_count=len(populated_widgets), image_region_count=len(images),
                            text_block_count=int(bool(text))),
                        "populated_form_field_count": len(populated_widgets),
                        "markdown_output": True,
                    })
                outline = document.get_toc(simple=True)
                if outline and result:
                    result[0] = {**result[0], "pdf_bookmarks": tuple(
                        {"level": int(level), "title": str(title), "target_page": int(target)}
                        for level, title, target in outline)}
            _dev_log("dev_preprocessing_pdf_provider stage=completed provider=pymupdf4llm pageCount=%d blockCount=%d markdownCharacters=%d imageRegionCount=%d expectedOutput=page_markdown actualOutput=page_markdown elapsedMs=%d route=shared_page_parser outcome=success",
                     len(result), sum(len(item["blocks"]) for item in result),
                     sum(len(str(block["text"])) for item in result for block in item["blocks"]),
                     sum(len(item["image_regions"]) for item in result),
                     round((time.monotonic() - started) * 1000))
            return result
        except Exception as exc:
            _dev_log("dev_preprocessing_pdf_provider stage=failed provider=pymupdf4llm expectedOutput=page_markdown actualFailure=%s recoveryAction=preserve_source_and_select_legacy_provider elapsedMs=%d outcome=failed",
                     type(exc).__name__, round((time.monotonic() - started) * 1000))
            raise RuntimeError("PYMUPDF4LLM_EXTRACTION_FAILED") from exc


def _dev_log(message: str, *args: Any) -> None:
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
        _LOGGER.info(message, *args)
