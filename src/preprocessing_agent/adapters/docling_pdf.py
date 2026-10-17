"""Docling's model-free PDF text provider for the shared preprocessing path."""
from __future__ import annotations

import logging
import os
import time
from pathlib import Path
from typing import Any, Mapping

_LOGGER = logging.getLogger(__name__)


class DoclingPdfAdapter:
    """Extract page-local content with Docling's standard model-based PDF pipeline."""

    provider_name = "docling"

    def extract(self, source: Path) -> list[Mapping[str, Any]]:
        started = time.monotonic()
        source = Path(source)
        _dev_log("dev_preprocessing_pdf_provider stage=started provider=docling pipeline=standard_with_models expectedInput=pdf actualSuffix=%s route=model_inference outcome=started",
                 source.suffix.lower() or "none")
        try:
            from docling.datamodel.base_models import InputFormat
            from docling.datamodel.pipeline_options import PdfPipelineOptions
            from docling.document_converter import DocumentConverter, PdfFormatOption
        except ImportError as exc:
            _dev_log("dev_preprocessing_pdf_provider stage=failed provider=docling expectedDependency=docling actualFailure=%s route=none recoveryAction=install_optional_dependency outcome=failed",
                     type(exc).__name__)
            raise RuntimeError("DOCLING_UNAVAILABLE") from exc

        try:
            options = PdfPipelineOptions(do_ocr=False, do_table_structure=True)
            converter = DocumentConverter(
                format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=options)},
                show_progress=False,
            )
            result = converter.convert(source)
            pages = docling_document_to_pages(result.document)
            if not pages:
                raise ValueError("DOCLING_RETURNED_NO_PAGES")
            _dev_log("dev_preprocessing_pdf_provider stage=completed provider=docling pipeline=standard_with_models pageCount=%d blockCount=%d textCharacters=%d tableCellBlockCount=%d expectedPageNumbers=contiguous actualPageNumbers=%s elapsedMs=%d route=shared_page_parser outcome=success",
                     len(pages), sum(len(page["blocks"]) for page in pages),
                     sum(len(str(block["text"])) for page in pages for block in page["blocks"]),
                     sum(block.get("kind") == "table_cell" for page in pages for block in page["blocks"]),
                     "contiguous" if [page["page_number"] for page in pages] == list(range(1, len(pages) + 1)) else "non_contiguous",
                     round((time.monotonic() - started) * 1000))
            return pages
        except Exception as exc:
            _dev_log("dev_preprocessing_pdf_provider stage=failed provider=docling expectedOutput=page_mappings actualFailure=%s route=none recoveryAction=quarantine_source_request elapsedMs=%d outcome=failed",
                     type(exc).__name__, round((time.monotonic() - started) * 1000))
            raise RuntimeError("DOCLING_EXTRACTION_FAILED") from exc


def docling_document_to_pages(document: Any) -> list[dict[str, Any]]:
    """Project Docling text and table items into the shared page/block contract."""
    page_sizes: dict[int, tuple[float, float]] = {}
    for raw_number, page in document.pages.items():
        number = int(raw_number)
        page_sizes[number] = (float(page.size.width), float(page.size.height))
    if not page_sizes:
        return []

    blocks_by_page: dict[int, list[dict[str, Any]]] = {number: [] for number in page_sizes}
    images_by_page: dict[int, list[tuple[float, float, float, float]]] = {number: [] for number in page_sizes}
    table_indices: dict[int, int] = {number: 0 for number in page_sizes}
    for item, _ in document.iterate_items():
        provenances = getattr(item, "prov", ()) or ()
        text = str(getattr(item, "text", "") or "").strip()
        label = str(getattr(getattr(item, "label", None), "value", getattr(item, "label", ""))).casefold()
        for provenance in provenances:
            number = int(provenance.page_no)
            if number not in page_sizes:
                continue
            width, height = page_sizes[number]
            bbox = _top_left_bbox(provenance.bbox, height)
            if label == "table":
                table_id = f"p{number}-table-{table_indices[number]}"
                table_indices[number] += 1
                for cell in getattr(getattr(item, "data", None), "table_cells", ()) or ():
                    cell_text = str(getattr(cell, "text", "") or "").strip()
                    cell_bbox = _top_left_bbox(getattr(cell, "bbox", None), height) or bbox
                    if not cell_text or cell_bbox is None:
                        continue
                    row = int(getattr(cell, "start_row_offset_idx", 0))
                    column = int(getattr(cell, "start_col_offset_idx", 0))
                    blocks_by_page[number].append({
                        "block_id": f"{table_id}-r{row}-c{column}",
                        "table_id": table_id,
                        "kind": "table_cell",
                        "row": row,
                        "column": column,
                        "text": cell_text,
                        "bbox": cell_bbox,
                        "table_bbox": bbox,
                        "docling_label": label,
                    })
                continue
            if not text:
                if label in {"picture", "chart"} and bbox:
                    images_by_page[number].append(bbox)
                continue
            index = len(blocks_by_page[number])
            block: dict[str, Any] = {
                "block_id": f"p{number}-b{index}",
                "text": text,
                "bbox": bbox,
                "kind": "text",
                "docling_label": label or "text",
            }
            if bbox:
                block["lines"] = [{"text": text, "bbox": bbox, "line_index": 0, "spans": []}]
            blocks_by_page[number].append(block)

    return [
        {
            "page_number": number,
            "geometry": {"width": width, "height": height},
            "blocks": blocks_by_page[number],
            "image_regions": images_by_page[number],
            "page_classification": ("mixed" if blocks_by_page[number] and images_by_page[number]
                                    else "image-only" if images_by_page[number]
                                    else "text-native"),
            "docling_extraction": True,
            "docling_pipeline": "standard_with_models",
        }
        for number, (width, height) in sorted(page_sizes.items())
    ]


def _top_left_bbox(box: Any, page_height: float) -> tuple[float, float, float, float] | None:
    if box is None:
        return None
    normalized = box.to_top_left_origin(page_height)
    return tuple(float(value) for value in (normalized.l, normalized.t, normalized.r, normalized.b))


def _dev_log(message: str, *args: Any) -> None:
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
        _LOGGER.info(message, *args)
