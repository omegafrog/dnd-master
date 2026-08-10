import importlib.util
import unittest
from pathlib import Path


def load_adapter():
    path = Path(__file__).parents[1] / "document_extraction" / "pymupdf_adapter.py"
    spec = importlib.util.spec_from_file_location("pymupdf_adapter", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


class Page:
    def __init__(self, blocks, images=()):
        self.blocks = blocks
        self.images = images

    def get_text(self, kind):
        if kind == "dict":
            return {"blocks": self.blocks}
        return "\n".join(block["text"] for block in self.blocks)

    def get_image_info(self, xrefs=True):
        return list(self.images)


class Document:
    def __init__(self, pages):
        self.pages = pages

    def __iter__(self):
        return iter(self.pages)

    def __len__(self):
        return len(self.pages)

    def get_toc(self, simple=True):
        return [[1, "Chapter One", 1]]

    def close(self):
        pass


class PyMuPdfAdapterTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.module = load_adapter()

    def test_maps_blocks_order_bbox_outline_and_capability_warnings(self):
        document = Document([Page([
            {"text": "Chapter One", "bbox": (10, 20, 100, 40), "type": 0},
            {"text": "Body text", "bbox": (10, 50, 200, 70), "type": 0},
        ], images=[{"xref": 7, "bbox": (1, 2, 3, 4)}])])
        adapter = self.module.PyMuPdfAdapter(lambda raw: document)

        result = adapter.extract(b"pdf")

        self.assertEqual("pymupdf", result["extractor"])
        self.assertEqual(["Chapter One", "Body text"], [element["text"] for element in result["elements"]])
        self.assertTrue(all(element["type"] == "PARAGRAPH" for element in result["elements"]))
        self.assertTrue(all(element["parserLevel"] is None for element in result["elements"]))
        self.assertEqual({"left": 10, "top": 20, "right": 100, "bottom": 40},
                         result["elements"][0]["sourceSpan"]["bbox"])
        self.assertEqual("Chapter One", result["outlines"][0]["title"])
        self.assertEqual("PYMUPDF_TABLES_UNAVAILABLE", result["warnings"][0]["code"])
        self.assertEqual("PYMUPDF_PICTURE_BYTES_UNAVAILABLE", result["warnings"][1]["code"])

    def test_classifies_open_failure_as_fallback_failure(self):
        adapter = self.module.PyMuPdfAdapter(lambda raw: (_ for _ in ()).throw(ValueError("bad pdf")))

        with self.assertRaises(self.module.PyMuPdfExtractionError) as raised:
            adapter.extract(b"pdf")

        self.assertEqual("UNPROCESSABLE", raised.exception.classification)

    def test_maps_real_dict_blocks_and_ocr_heading_callback(self):
        document = Document([Page([{
            "type": 0,
            "bbox": (1, 2, 3, 4),
            "lines": [{"spans": [{"text": "bad\ufffd"}]}],
        }])])
        adapter = self.module.PyMuPdfAdapter(
            lambda raw: document,
            page_recovery=lambda doc, indexes: ([{"text": "OCR heading", "label": "section_header"}], []),
        )

        result = adapter.extract(b"pdf")

        self.assertEqual("OCR heading", result["elements"][0]["text"])
        self.assertEqual("PARAGRAPH", result["elements"][0]["type"])
        self.assertEqual(result["pictures"], result["images"])


if __name__ == "__main__":
    unittest.main()
