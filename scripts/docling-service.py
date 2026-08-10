#!/usr/bin/env python3
"""Small engine-neutral Docling HTTP sidecar for rule-knowledge-service."""
import base64
import io
import logging
import os
import re
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling.datamodel.base_models import DocumentStream, InputFormat
from docling.datamodel.pipeline_options import EasyOcrOptions, OcrMode, PdfPipelineOptions

app = FastAPI()
logger = logging.getLogger(__name__)
# Some rulebook PDFs contain broken native text layers. Full-page OCR is
# deliberately selected here so Docling does not trust those layers when
# reconstructing the structured document.
pdf_options = PdfPipelineOptions(do_ocr=True)
pdf_options.ocr_options = EasyOcrOptions(lang=["en"], mode=OcrMode.FULL_PAGE)
converter = DocumentConverter(
    allowed_formats=[InputFormat.PDF, InputFormat.DOCX],
    format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=pdf_options)},
)
text_converter = DocumentConverter(allowed_formats=[InputFormat.MD])
_ocr_reader = None

class ExtractRequest(BaseModel):
    format: str
    contentBase64: str

@app.get("/health")
def health():
    return {"status": "UP"}

@app.post("/extract")
def extract(request: ExtractRequest):
    if request.format not in {"PDF", "DOCX"}:
        raise HTTPException(status_code=415, detail="unsupported document format")
    try:
        raw = base64.b64decode(request.contentBase64, validate=True)
        suffix = {"PDF": ".pdf", "DOCX": ".docx", "PPTX": ".pptx"}.get(request.format, ".bin")
        result = converter.convert(DocumentStream(name=f"upload{suffix}", stream=io.BytesIO(raw))).document
        data = result.export_to_dict()
        return {
            "nodes": _nodes(data),
            "tables": _tables(data),
            "images": _images(data),
            "warnings": [],
            "rawText": result.export_to_markdown(),
        }
    except Exception as exc:
        logger.exception("Docling extraction failed")
        if request.format == "PDF":
            try:
                logger.warning("Using PyMuPDF text -> Docling pipeline fallback")
                return _pymupdf_to_docling(raw)
            except Exception as fallback_exc:
                logger.exception("Rendered-page OCR fallback failed")
                raise HTTPException(status_code=422, detail="document extraction failed") from fallback_exc
        raise HTTPException(status_code=422, detail="document extraction failed") from exc

def _pymupdf_fallback(raw):
    """Use fast page text blocks and OCR only pages whose text is unusable."""
    import pymupdf

    document = pymupdf.open(stream=raw, filetype="pdf")
    texts = []
    markdown = []
    bad_pages = []
    for page_index, page in enumerate(document):
        blocks = [block for block in page.get_text("blocks") if len(block) >= 7 and block[6] == 0]
        page_text = "\n".join(block[4].strip() for block in blocks if block[4].strip())
        if _bad_text(page_text):
            bad_pages.append(page_index)
            continue
        markdown.append(page_text)
        for block_index, block in enumerate(blocks):
            text = block[4].strip()
            if not text:
                continue
            texts.append({
                "id": f"pymupdf-{page_index + 1}-{block_index}",
                "label": "section_header" if _looks_like_heading(text) else "text",
                "page": page_index + 1,
                "text": text,
                "children": [],
                "prov": [{"page_no": page_index + 1}],
            })
    if bad_pages:
        logger.warning("Using rendered-page OCR fallback for pages %s", [page + 1 for page in bad_pages])
        ocr_texts, ocr_markdown = _ocr_pages(document, bad_pages)
        texts.extend(ocr_texts)
        markdown.extend(ocr_markdown)
    document.close()
    return {
        "nodes": _nodes({"texts": texts}),
        "tables": [],
        "images": [],
        "warnings": [{"code": "NATIVE_PDF_TEXT_FAILED", "severity": "WARNING",
                       "message": "Docling native text parsing failed; PyMuPDF fallback used."}],
        "rawText": "\n".join(markdown),
    }

def _pymupdf_to_docling(raw):
    """Extract with PyMuPDF, then run the extracted Markdown through Docling."""
    import pymupdf

    document = pymupdf.open(stream=raw, filetype="pdf")
    markdown = []
    for page_index, page in enumerate(document):
        markdown.append(f"# Page {page_index + 1}")
        for block in page.get_text("blocks"):
            if len(block) < 7 or block[6] != 0:
                continue
            text = " ".join(line.strip() for line in block[4].splitlines() if line.strip())
            if not text:
                continue
            markdown.append(("## " if _looks_like_heading(text) else "") + text)
        markdown.append("")
    document.close()
    converted = text_converter.convert(DocumentStream(
        name="pymupdf-extracted.md", stream=io.BytesIO("\n".join(markdown).encode("utf-8"))))
    data = converted.document.export_to_dict()
    return {
        "nodes": _nodes(data),
        "tables": _tables(data),
        "images": _images(data),
        "warnings": [{"code": "NATIVE_PDF_TEXT_FAILED", "severity": "WARNING",
                       "message": "Docling PDF backend failed; PyMuPDF text was reprocessed by Docling Markdown pipeline."}],
        "rawText": converted.document.export_to_markdown(),
    }

def _bad_text(text):
    if len(text.strip()) < 30:
        return True
    weird = sum(1 for char in text if ord(char) < 32 and char not in "\n\r\t")
    replacement = text.count("\ufffd")
    return (weird + replacement) / max(1, len(text)) > 0.01

def _ocr_pages(document, page_indexes):
    """Bypass malformed native PDF text streams for selected pages only."""
    import easyocr
    import numpy as np
    import pypdfium2 as pdfium

    global _ocr_reader
    if _ocr_reader is None:
        use_gpu = os.getenv("DOCLING_OCR_GPU", "false").lower() == "true"
        _ocr_reader = easyocr.Reader(["en"], gpu=use_gpu, verbose=False)

    texts = []
    markdown = []
    pdf = pdfium.PdfDocument(io.BytesIO(document.tobytes()))
    for page_index in page_indexes:
        page = pdf[page_index]
        bitmap = page.render(scale=1.5)
        image = bitmap.to_pil()
        detections = _ocr_reader.readtext(np.asarray(image), detail=1, paragraph=False)
        lines = _ocr_lines(detections)
        for line_index, text in enumerate(lines):
            texts.append({
                "id": f"ocr-{page_index + 1}-{line_index}",
                "label": "section_header" if _looks_like_heading(text) else "text",
                "page": page_index + 1,
                "text": text,
                "children": [],
                "prov": [{"page_no": page_index + 1}],
            })
        markdown.extend(lines)
        page.close()
    return texts, markdown

def _ocr_lines(detections):
    ordered = []
    for bbox, text, confidence in detections:
        value = str(text).strip()
        if not value or confidence < 0.25:
            continue
        top = min(point[1] for point in bbox)
        left = min(point[0] for point in bbox)
        height = max(point[1] for point in bbox) - top
        ordered.append((top, left, height, value))
    ordered.sort(key=lambda item: (item[0], item[1]))
    lines = []
    for top, left, height, value in ordered:
        if lines and abs(top - lines[-1][0]) <= max(8, height * 0.6):
            lines[-1][1].append((left, value))
        else:
            lines.append([top, [(left, value)]])
    return [" ".join(value for _, value in sorted(parts)) for _, parts in lines]

def _looks_like_heading(text):
    value = text.strip()
    if not value or len(value) > 90 or value[-1:] in ".,;:!?":
        return False
    if re.match(r"^(?:[-*•]|\d+[.)])\s", value):
        return False
    words = value.split()
    if "\n" not in value and len(words) <= 8 and all(word[:1].isupper() for word in words if word):
        return True
    # Korean headings do not have case. Keep short, punctuation-free Hangul
    # labels (e.g. 능력 판정, 감지, 기술) as headings as well.
    return "\n" not in value and len(value) <= 32 and any("가" <= char <= "힣" for char in value)

def _nodes(value, page=1, prefix="node"):
    """Map Docling's versioned dict shape to stable nodes; unknown fields ignored."""
    roots = []
    heading_stack = []
    last_page = None
    items = value.get("texts", []) if isinstance(value, dict) else []
    for index, item in enumerate(items):
        text = item.get("text", "") if isinstance(item, dict) else str(item)
        if not text.strip():
            continue
        current_page = _page(item, page)
        if last_page is not None and current_page != last_page:
            heading_stack.clear()
        last_page = current_page
        label = str(item.get("label", "paragraph")).upper() if isinstance(item, dict) else "PARAGRAPH"
        is_heading = "SECTION" in label or "TITLE" in label or "HEADING" in label
        node = {
            "id": f"{prefix}-{index}",
            "type": "HEADING" if is_heading else "PARAGRAPH",
            "page": current_page,
            "text": text,
            "children": [],
        }
        if is_heading:
            level = _heading_level(item)
            while heading_stack and heading_stack[-1][0] >= level:
                heading_stack.pop()
            if heading_stack:
                heading_stack[-1][1]["children"].append(node)
            else:
                roots.append(node)
            heading_stack.append((level, node))
        elif heading_stack:
            heading_stack[-1][1]["children"].append(node)
        else:
            roots.append(node)
    return roots

def _heading_level(item):
    if isinstance(item, dict):
        value = item.get("level")
        if isinstance(value, int) and value > 0:
            return value
    return 1

def _page(item, fallback):
    if isinstance(item, dict):
        provenance = item.get("prov", [])
        if isinstance(provenance, list) and provenance:
            page_no = provenance[0].get("page_no") if isinstance(provenance[0], dict) else None
            if isinstance(page_no, int) and page_no > 0:
                return page_no
    return fallback

def _tables(value):
    result = []
    for index, table in enumerate(value.get("tables", []) if isinstance(value, dict) else []):
        rows = table.get("data", {}).get("table_cells", []) if isinstance(table, dict) else []
        result.append({"id": f"table-{index}", "page": 1, "rows": [[str(cell.get("text", "")) for cell in rows]]})
    return result

def _images(value):
    return []

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=os.getenv("DOCLING_HOST", "127.0.0.1"), port=int(os.getenv("DOCLING_PORT", "8099")))
