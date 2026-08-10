"""PyMuPDF anti-corruption adapter for the normalized extraction contract."""
import hashlib
import os


class PyMuPdfExtractionError(RuntimeError):
    def __init__(self, message, classification="UNPROCESSABLE"):
        super().__init__(message)
        self.classification = classification


def classify_failure(error):
    if isinstance(error, (TimeoutError, MemoryError)):
        return "TIMEOUT"
    if isinstance(error, ImportError):
        return "UNAVAILABLE"
    return "UNPROCESSABLE"


class PyMuPdfAdapter:
    """Map only evidence PyMuPDF exposes; never invent tables or semantics."""

    def __init__(self, document_opener=None, page_recovery=None):
        self._document_opener = document_opener or self._open
        self._page_recovery = page_recovery

    def extract(self, raw):
        try:
            document = self._document_opener(raw)
            try:
                return self._map_document(document, raw)
            finally:
                close = getattr(document, "close", None)
                if close:
                    close()
        except PyMuPdfExtractionError:
            raise
        except Exception as error:
            raise PyMuPdfExtractionError(
                "PyMuPDF extraction failed", classify_failure(error)) from error

    @staticmethod
    def _open(raw):
        import pymupdf
        return pymupdf.open(stream=raw, filetype="pdf")

    def _map_document(self, document, raw):
        elements = []
        pages = []
        raw_pages = []
        source_key = hashlib.sha256(raw).hexdigest()[:16]
        order = 0
        for page_number, page in enumerate(document, start=1):
            pages.append({"number": page_number,
                          "width": getattr(page.rect, "width", None) if hasattr(page, "rect") else None,
                          "height": getattr(page.rect, "height", None) if hasattr(page, "rect") else None})
            blocks = self._blocks(page, page_number)
            page_text = "\n".join(block["text"] for block in blocks)
            if self._unusable(page_text) and self._page_recovery:
                blocks = self._page_recovery(document, [page_number - 1])[0]
                blocks = [{"text": item["text"], "bbox": item.get("bbox"),
                           "type": 0, "heading": item.get("heading", item.get("label") == "section_header")}
                          for item in blocks]
            page_text = []
            for block_number, block in enumerate(blocks):
                text = block["text"].strip()
                if not text:
                    continue
                element_id = f"pymupdf-{source_key}-page-{page_number}-block-{block_number}"
                bbox = self._bbox(block.get("bbox"))
                elements.append({
                    "id": element_id,
                    "type": "PARAGRAPH",
                    "text": text,
                    "page": page_number,
                    "order": order,
                    "parentId": None,
                    "parserLevel": None,
                    "childIds": [],
                    "sourceSpan": {"sourceId": element_id, "page": page_number,
                                   "order": order, "bbox": bbox},
                    "style": "",
                    "layout": "bbox" if bbox else "",
                })
                order += 1
                page_text.append(text)
            raw_pages.append("\n".join(page_text))

        tables, table_warning = self._tables(document)
        warnings = [table_warning] if table_warning else []
        pictures = self._pictures(document)
        if pictures:
            warnings.append({"code": "PYMUPDF_PICTURE_BYTES_UNAVAILABLE", "severity": "INFO",
                             "message": "Picture locations preserved; picture bytes are not part of normalized contract."})
        return {
            "schemaVersion": "normalized-document.v1",
            "extractor": "pymupdf",
            "extractorVersion": self._version(),
            "sourceIdentity": "sha256:" + hashlib.sha256(raw).hexdigest(),
            "pages": pages,
            "elements": elements,
            "tables": tables,
            "pictures": pictures,
            "images": pictures,
            "outlines": self._outlines(document),
            "parserRelations": [],
            "warnings": warnings,
            "rawText": "\n\n".join(raw_pages).strip(),
        }

    @staticmethod
    def _blocks(page, page_number):
        data = page.get_text("dict")
        result = []
        for block in data.get("blocks", []) if isinstance(data, dict) else []:
            if block.get("type", 0) != 0:
                continue
            if "text" in block:
                text = str(block["text"])
            else:
                lines = []
                for line in block.get("lines", []):
                    lines.append(" ".join(span.get("text", "") for span in line.get("spans", [])))
                text = "\n".join(line for line in lines if line.strip())
            if text.strip():
                result.append({"text": text, "bbox": block.get("bbox"), "type": 0})
        return result

    @staticmethod
    def _unusable(text):
        if not text.strip():
            return True
        bad = sum(1 for char in text if ord(char) < 32 and char not in "\n\r\t")
        return (bad + text.count("\ufffd")) / max(1, len(text)) > 0.01

    @staticmethod
    def _tables(document):
        tables = []
        supported = False
        try:
            for page_number, page in enumerate(document, start=1):
                finder = getattr(page, "find_tables", None)
                if finder is None:
                    continue
                supported = True
                found = finder()
                for index, table in enumerate(getattr(found, "tables", found or [])):
                    rows = table.extract() if hasattr(table, "extract") else []
                    tables.append({"id": f"pymupdf-table-{page_number}-{index}",
                                   "page": page_number,
                                   "rows": [[str(cell or "") for cell in row] for row in rows]})
        except Exception:
            return [], {"code": "PYMUPDF_TABLE_EXTRACTION_FAILED", "severity": "WARNING",
                        "message": "PyMuPDF table extraction failed; table evidence unavailable."}
        if not supported:
            return [], {"code": "PYMUPDF_TABLES_UNAVAILABLE", "severity": "INFO",
                        "message": "PyMuPDF build does not expose table extraction."}
        return tables, None

    @staticmethod
    def _bbox(value):
        if not value or len(value) < 4:
            return None
        return {"left": value[0], "top": value[1], "right": value[2], "bottom": value[3]}

    @classmethod
    def _pictures(cls, document):
        pictures = []
        for page_number, page in enumerate(document, start=1):
            for index, info in enumerate(page.get_image_info(xrefs=True) or []):
                pictures.append({"id": f"pymupdf-picture-{page_number}-{index}",
                                 "page": page_number, "bbox": cls._bbox(info.get("bbox")),
                                 "mimeType": "application/octet-stream", "caption": ""})
        return pictures

    @staticmethod
    def _outlines(document):
        return [{"id": f"pymupdf-outline-{index}", "title": str(item[1]),
                 "level": int(item[0]), "locator": str(item[2])}
                for index, item in enumerate(document.get_toc(simple=True) or [])
                if len(item) >= 3 and str(item[1]).strip()]

    @staticmethod
    def _version():
        try:
            import pymupdf
            return getattr(pymupdf, "__version__", "unknown")
        except ImportError:
            return os.getenv("PYMUPDF_VERSION", "unknown")
