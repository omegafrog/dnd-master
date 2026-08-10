import importlib.util
import unittest
from os import environ
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from docling.datamodel.base_models import ConversionStatus


def load_service():
    path = Path(__file__).parents[1] / "docling-service.py"
    spec = importlib.util.spec_from_file_location("docling_service", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class DoclingServiceTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.service = load_service()

    def test_primary_pdf_pipeline_uses_native_text_only(self):
        self.assertFalse(self.service.pdf_options.do_ocr)
        self.assertTrue(self.service.pdf_options.force_backend_text)

    def test_ocr_languages_are_configurable(self):
        with patch.dict(environ, {"DOCLING_OCR_LANGS": "ko,en,ja"}):
            self.assertEqual(["ko", "en", "ja"], self.service._ocr_languages())

    def test_maps_picture_provenance_to_engine_neutral_image(self):
        pictures = [{
            "self_ref": "#/pictures/3",
            "prov": [{
                "page_no": 7,
                "bbox": {"l": 10.5, "t": 200.0, "r": 110.5, "b": 20.0},
            }],
            "image": {"mimetype": "image/png"},
            "captions": [],
        }]

        self.assertEqual([{
            "id": "picture-3",
            "page": 7,
            "bbox": {"left": 10.5, "top": 200.0, "right": 110.5, "bottom": 20.0},
            "mimeType": "image/png",
            "caption": "",
        }], self.service._images({"pictures": pictures}))

    def test_maps_picture_without_generated_bitmap(self):
        pictures = [{
            "self_ref": "#/pictures/0",
            "prov": [{"page_no": 2, "bbox": {"l": 1, "t": 2, "r": 3, "b": 4}}],
            "image": None,
        }]

        image = self.service._images({"pictures": pictures})[0]

        self.assertEqual("application/octet-stream", image["mimeType"])

    def test_maps_table_provenance_page_instead_of_hardcoding_first_page(self):
        tables = self.service._tables({"tables": [{
            "prov": [{"page_no": 42}],
            "data": {"table_cells": [{"text": "cell"}]},
        }]})

        self.assertEqual(42, tables[0]["page"])

    def test_partial_docling_result_uses_safe_pdf_fallback(self):
        document = SimpleNamespace(
            export_to_dict=lambda: {"texts": [], "tables": [], "pictures": []},
            export_to_markdown=lambda: "partial",
        )
        partial = SimpleNamespace(
            status=ConversionStatus.PARTIAL_SUCCESS,
            errors=["page failed"],
            document=document,
        )
        fallback = {
            "nodes": [{"id": "raw", "type": "PARAGRAPH", "page": 1,
                       "text": "fallback", "children": []}],
            "tables": [], "images": [], "warnings": [], "rawText": "fallback",
        }

        with patch.object(self.service.converter, "convert", return_value=partial), \
                patch.object(self.service, "_pymupdf_to_docling", return_value=fallback):
            result = self.service.extract(self.service.ExtractRequest(
                format="PDF", contentBase64="AA=="))

        self.assertIs(fallback, result)

    def test_fallback_uses_ocr_only_for_pages_with_unusable_native_text(self):
        class Page:
            def __init__(self, text):
                self.text = text

            def get_text(self, kind):
                self.assert_kind = kind
                return [(0, 0, 1, 1, self.text, 0, 0)] if self.text else []

        document = [
            Page("This native text is long enough to remain authoritative."),
            Page(""),
        ]
        ocr_result = ([], ["Recovered text from page two"])

        with patch.object(self.service, "_ocr_pages", return_value=ocr_result) as ocr:
            pages = self.service._extract_body_pages(document)

        ocr.assert_called_once_with(document, [1])
        self.assertEqual(1, pages[0][0]["page"])
        self.assertEqual("Recovered text from page two", pages[1][0]["text"])

    def test_raw_fallback_preserves_every_page_without_toc_guessing(self):
        pages = [[{"page": 1, "text": "cover", "heading": False}],
                 [{"page": 2, "text": "body", "heading": False}]]

        nodes = self.service._page_nodes(pages)

        self.assertEqual([1, 2], [node["page"] for node in nodes])
        self.assertEqual("body", nodes[1]["children"][0]["text"])


if __name__ == "__main__":
    unittest.main()
