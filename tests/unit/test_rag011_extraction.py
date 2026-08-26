import hashlib
import json

from preprocessing_agent.pipeline.extraction_service import ExtractionApplicationService
from preprocessing_agent.adapters.ocr import TesseractOcrAdapter
from preprocessing_agent.ports.extraction import RenderedPage, normalize_ocr_blocks


class Native:
    def __init__(self, blocks, **extra):
        self.blocks, self.extra = blocks, extra

    def extract(self, _source):
        return [{"page_number": 1, "geometry": {"width": 100, "height": 100}, "blocks": self.blocks, **self.extra}]


class Render:
    def available(self): return True
    def render(self, _source, page_number, region=None): return RenderedPage(page_number, 100, 100, b"png")


class Ocr:
    def available(self): return True
    def recognize(self, _rendered, region=None): return [{"text": "scanned", "bbox": (5, 5, 40, 15), "text_confidence": .91}]


def request(tmp_path, name):
    source = tmp_path / f"{name}.pdf"
    source.write_bytes(b"pdf")
    return {"request_id": name, "source_path": str(source), "source_sha256": hashlib.sha256(b"pdf").hexdigest(), "policy_version": "p1", "output_dir": str(tmp_path / name)}


def test_image_only_page_uses_ocr_and_preserves_provenance(tmp_path):
    result = ExtractionApplicationService(Native([]), Render(), Ocr()).preprocess(request(tmp_path, "image"))
    assert result["status"] == "READY"
    page = result["pages"][0]
    assert page["status"] == "VALIDATED"


def test_image_only_page_is_blocked_when_ocr_capability_missing(tmp_path):
    result = ExtractionApplicationService(Native([]), Render(), None).preprocess(request(tmp_path, "missing"))
    assert result["status"] == "NEEDS_REVIEW"
    assert "OCR_UNAVAILABLE" in result["pages"][0]["findings"]
    assert "chunks" not in result["artifacts"]


def test_mixed_page_keeps_native_and_adds_targeted_ocr(tmp_path):
    result = ExtractionApplicationService(Native([{"block_id": "n", "text": "native", "bbox": (1, 1, 30, 10)}], image_regions=[(40, 1, 90, 30)]), Render(), Ocr()).preprocess(request(tmp_path, "mixed"))
    assert result["status"] == "READY"
    evidence = json.loads((tmp_path / "mixed" / "versions" / result["version_id"] / "version.json").read_text())
    page = evidence["pages"][0]
    assert page["page_classification"] == "mixed"
    assert {block["extraction_method"] for block in page["blocks"]} == {"native", "ocr"}
    assert next(block["bbox"] for block in page["blocks"] if block["extraction_method"] == "ocr") == [45.0, 6.0, 80.0, 16.0]
    assert next(block["text_confidence"] for block in page["blocks"] if block["extraction_method"] == "ocr") == .91


def test_service_wires_optional_tesseract_adapter_by_default():
    assert isinstance(ExtractionApplicationService().ocr, TesseractOcrAdapter)


def test_ocr_normalization_rejects_out_of_bounds_and_keeps_confidence():
    blocks = normalize_ocr_blocks([{"text": "x", "bbox": (0, 0, 10, 10), "confidence": .8}], page_number=2, width=20, height=20)
    assert blocks[0]["extraction_method"] == "ocr"
    assert blocks[0]["text_confidence"] == .8
    regional = normalize_ocr_blocks([{"text": "x", "bbox": (1, 2, 4, 6)}], page_number=2, width=10, height=10, offset=(30, 40, 50, 60))
    assert regional[0]["bbox"] == (31.0, 42.0, 34.0, 46.0)


def test_pymupdf_renderer_uses_local_dimensions_for_region(tmp_path):
    fitz = __import__("pytest").importorskip("fitz")
    document = fitz.open()
    document.new_page(width=200, height=100)
    source = tmp_path / "render.pdf"
    document.save(source)
    document.close()
    from preprocessing_agent.adapters.ocr import PyMuPdfPageRenderAdapter
    rendered = PyMuPdfPageRenderAdapter().render(source, 1, (40, 10, 140, 60))
    assert (rendered.width, rendered.height) == (100.0, 50.0)
    assert rendered.pixel_width and rendered.pixel_height


def test_tesseract_tsv_adapter_projects_crop_coordinates_to_page():
    from preprocessing_agent.adapters.ocr import _parse_tsv
    rendered = RenderedPage(1, 100.0, 50.0, b"", pixel_width=200, pixel_height=100, region_origin=(40.0, 10.0))
    tsv = "level\tpage\tblock\tpar\tline\tword\tleft\ttop\twidth\theight\tconf\ttext\n5\t1\t1\t1\t1\t1\t10\t20\t30\t10\t90\tword"
    block = _parse_tsv(tsv, rendered, 200, 100)[0]
    assert block["bbox"] == (45.0, 20.0, 60.0, 25.0)


def test_parsed_page_rejects_unknown_classification():
    from preprocessing_agent.domain import ParsedPage
    import pytest
    with pytest.raises(ValueError, match="classification"):
        ParsedPage(1, (), "", page_classification="unknown")
