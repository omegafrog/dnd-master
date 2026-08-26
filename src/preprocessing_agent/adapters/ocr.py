"""Optional PyMuPDF rendering and subprocess Tesseract OCR adapters."""
from __future__ import annotations

import io
import shutil
import subprocess
from pathlib import Path
from typing import Any, Sequence

from preprocessing_agent.ports.extraction import ExtractionCapabilityError, RenderedPage


class PyMuPdfPageRenderAdapter:
    def available(self) -> bool:
        try:
            import fitz  # type: ignore
            return True
        except ImportError:
            return False

    def render(self, source: Path, page_number: int, region: Sequence[float] | None = None) -> RenderedPage:
        try:
            import fitz  # type: ignore
            with fitz.open(source) as document:
                if page_number < 1 or page_number > len(document):
                    raise ExtractionCapabilityError("RENDER_PAGE_NOT_FOUND")
                page = document[page_number - 1]
                pixmap = page.get_pixmap(matrix=fitz.Matrix(2, 2), clip=fitz.Rect(*region) if region else None, alpha=False)
                width = float(region[2] - region[0]) if region else float(page.rect.width)
                height = float(region[3] - region[1]) if region else float(page.rect.height)
                return RenderedPage(page_number, width, height, pixmap.tobytes("png"), pixel_width=pixmap.width, pixel_height=pixmap.height, region_origin=(float(region[0]), float(region[1])) if region else (0.0, 0.0))
        except ExtractionCapabilityError:
            raise
        except ImportError as exc:
            raise ExtractionCapabilityError("OCR_UNAVAILABLE", "Pillow is required for OCR") from exc
        except Exception as exc:
            raise ExtractionCapabilityError("RENDER_FAILED", str(exc)) from exc


class TesseractOcrAdapter:
    returns_page_coordinates = True
    def __init__(self, executable: str = "tesseract", language: str = "eng", timeout: float = 30.0) -> None:
        self.executable, self.language, self.timeout = executable, language, timeout

    def available(self) -> bool:
        if shutil.which(self.executable) is None:
            return False
        try:
            import PIL  # type: ignore
            return True
        except ImportError:
            return False

    def recognize(self, rendered: RenderedPage, region: Sequence[float] | None = None) -> list[dict[str, Any]]:
        if not self.available():
            raise ExtractionCapabilityError("OCR_UNAVAILABLE")
        try:
            from PIL import Image
            image = Image.open(io.BytesIO(rendered.image))
            if region:
                scale_x = image.width / rendered.width
                scale_y = image.height / rendered.height
                image = image.crop(tuple(int(v * (scale_x if i % 2 == 0 else scale_y)) for i, v in enumerate(region)))
            payload = io.BytesIO()
            image.save(payload, format="PNG")
            proc = subprocess.run([self.executable, "stdin", "stdout", "--psm", "3", "-l", self.language, "tsv"], input=payload.getvalue(), capture_output=True, timeout=self.timeout, check=False)
            if proc.returncode != 0:
                raise ExtractionCapabilityError("OCR_FAILED", proc.stderr.decode(errors="replace").strip())
            return _parse_tsv(proc.stdout.decode(errors="replace"), rendered, image.width, image.height)
        except ExtractionCapabilityError:
            raise
        except subprocess.TimeoutExpired as exc:
            raise ExtractionCapabilityError("OCR_TIMEOUT") from exc
        except Exception as exc:
            raise ExtractionCapabilityError("OCR_FAILED", str(exc)) from exc


def _parse_tsv(value: str, rendered: RenderedPage, pixel_width: int, pixel_height: int) -> list[dict[str, Any]]:
    lines = value.splitlines()
    if len(lines) < 2:
        return []
    result = []
    for row in lines[1:]:
        fields = row.split("\t")
        if len(fields) < 12 or not fields[11].strip():
            continue
        try:
            confidence = max(0.0, min(1.0, float(fields[10]) / 100.0))
            ox, oy = rendered.region_origin
            result.append({"text": fields[11], "bbox": (ox + float(fields[6]) * rendered.width / max(1, pixel_width), oy + float(fields[7]) * rendered.height / max(1, pixel_height), ox + float(int(fields[6]) + int(fields[8])) * rendered.width / max(1, pixel_width), oy + float(int(fields[7]) + int(fields[9])) * rendered.height / max(1, pixel_height)), "text_confidence": confidence})
        except (ValueError, IndexError):
            continue
    return result
