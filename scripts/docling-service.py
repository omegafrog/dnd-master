#!/usr/bin/env python3
"""Small engine-neutral Docling HTTP sidecar for rule-knowledge-service."""
import base64
import io
import hashlib
import logging
import os
import re
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling.datamodel.base_models import ConversionStatus, DocumentStream, InputFormat
from docling.datamodel.pipeline_options import EasyOcrOptions, OcrMode, PdfPipelineOptions
try:
    from document_extraction.pymupdf_adapter import PyMuPdfAdapter, PyMuPdfExtractionError
except ModuleNotFoundError:  # direct test/module loading
    import sys
    sys.path.insert(0, os.path.dirname(__file__))
    from document_extraction.pymupdf_adapter import PyMuPdfAdapter, PyMuPdfExtractionError

app = FastAPI()
logger = logging.getLogger(__name__)
USER_ERROR_MESSAGE = "document extraction failed"
# Prefer usable native PDF text. OCR only layout regions without reliable
# programmatic text; document-level partial failures use the page fallback.
pdf_options = PdfPipelineOptions(do_ocr=False, force_backend_text=True)
converter = DocumentConverter(
    allowed_formats=[InputFormat.PDF, InputFormat.DOCX],
    format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=pdf_options)},
)
text_converter = DocumentConverter(allowed_formats=[InputFormat.MD])
_ocr_reader = None

def _ocr_languages():
    configured = os.getenv("DOCLING_OCR_LANGS", "en")
    return [language.strip() for language in configured.split(",") if language.strip()] or ["en"]

class ExtractRequest(BaseModel):
    format: str
    contentBase64: str

@app.get("/health")
def health():
    return {"status": "UP"}

@app.post("/extract")
def extract(request: ExtractRequest):
    if request.format not in {"PDF", "DOCX"}:
        logger.warning("Unsupported extraction format: %s", request.format)
        raise HTTPException(status_code=415, detail=USER_ERROR_MESSAGE)
    try:
        raw = base64.b64decode(request.contentBase64, validate=True)
        suffix = {"PDF": ".pdf", "DOCX": ".docx", "PPTX": ".pptx"}.get(request.format, ".bin")
        conversion = converter.convert(DocumentStream(name=f"upload{suffix}", stream=io.BytesIO(raw)))
        if conversion.status != ConversionStatus.SUCCESS:
            raise RuntimeError(
                f"Docling returned {conversion.status}: {conversion.errors}")
        result = conversion.document
        data = result.export_to_dict()
        return _normalised_response({
            "nodes": _nodes(data),
            "tables": _tables(data),
            "images": _images(data),
            "warnings": [],
            "rawText": result.export_to_markdown(),
            "sourceIdentity": _source_identity(raw),
            "source": data,
        })
    except Exception as exc:
        logger.exception("Docling extraction failed")
        if request.format == "PDF":
            try:
                logger.warning("Using PyMuPDF text -> Docling pipeline fallback")
                return _pymupdf_to_docling(raw)
            except Exception as fallback_exc:
                logger.exception("Rendered-page OCR fallback failed")
                raise HTTPException(status_code=422, detail=USER_ERROR_MESSAGE) from fallback_exc
        raise HTTPException(status_code=422, detail=USER_ERROR_MESSAGE) from exc

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
    """Select explicit PyMuPDF adapter after Docling failure."""
    result = PyMuPdfAdapter(page_recovery=_ocr_pages).extract(raw)
    result["warnings"].insert(0, {
        "code": "NATIVE_PDF_TEXT_FAILED", "severity": "WARNING",
        "message": "Docling native extraction failed; PyMuPDF fallback preserved available evidence."})
    return result


def _classify_fallback_failure(error):
    if isinstance(error, PyMuPdfExtractionError):
        return error.classification
    return "UNPROCESSABLE"


def _source_identity(raw):
    return "sha256:" + hashlib.sha256(raw).hexdigest()


def _normalised_response(response):
    """Return stable shadow contract while retaining old compatibility fields."""
    nodes = response.get("nodes", [])
    elements = []
    pages = {}

    def visit(node, parent_id=None, order=0):
        page = int(node.get("page", 1))
        node_id = str(node["id"])
        pages.setdefault(page, {"number": page})
        span = node.get("sourceSpan") or {"sourceId": node_id, "page": page, "order": order}
        elements.append({
            "id": node_id,
            "type": node.get("type", "UNKNOWN"),
            "text": node.get("text", ""),
            "page": page,
            "order": order,
            "parentId": parent_id,
            "parserLevel": node.get("level"),
            "childIds": [str(child["id"]) for child in node.get("children", [])],
            "sourceSpan": span,
            "style": node.get("style", ""),
            "layout": node.get("layout", ""),
        })
        next_order = order + 1
        for child in node.get("children", []):
            next_order = visit(child, node_id, next_order)
        return next_order

    order = 0
    for node in nodes:
        order = visit(node, None, order)
    result = {key: value for key, value in response.items() if key != "source"}
    result.update({
        "schemaVersion": "normalized-document.v1",
        "extractor": "docling",
        "extractorVersion": os.getenv("DOCLING_VERSION", "unknown"),
        "sourceIdentity": response.get("sourceIdentity", "sha256:unknown"),
        "pages": sorted(pages.values(), key=lambda page: page["number"]),
        "elements": elements,
        "outlines": response.get("outlines", []),
        "parserRelations": response.get("parserRelations", []),
    })
    return result


def _page_nodes(pages):
    roots = []
    for page_index, blocks in enumerate(pages, start=1):
        children = []
        for block_index, block in enumerate(blocks):
            children.append({
                "id": f"raw-page-{page_index}-block-{block_index}",
                "type": "HEADING" if block["heading"] else "PARAGRAPH",
                "page": page_index,
                "text": block["text"],
                "children": [],
            })
        roots.append({
            "id": f"raw-page-{page_index}",
            "type": "ROOT",
            "page": page_index,
            "text": f"Page {page_index}",
            "children": children,
        })
    return roots


def _page_markdown(pages):
    lines = []
    for page_index, blocks in enumerate(pages, start=1):
        lines.extend([f"# Page {page_index}", ""])
        for block in blocks:
            prefix = "## " if block["heading"] else ""
            lines.extend([prefix + block["text"], ""])
    return "\n".join(lines)


def _pymupdf_images(document):
    images = []
    for page_index, page in enumerate(document, start=1):
        for image_index, info in enumerate(page.get_image_info(xrefs=True)):
            xref = info.get("xref")
            mime_type = "application/octet-stream"
            if xref:
                extracted = document.extract_image(xref)
                if extracted.get("ext"):
                    mime_type = f"image/{extracted['ext']}"
            bbox = info.get("bbox", (0.0, 0.0, 0.0, 0.0))
            images.append({
                "id": f"pymupdf-image-{page_index}-{image_index}",
                "page": page_index,
                "bbox": {"left": bbox[0], "top": bbox[1], "right": bbox[2], "bottom": bbox[3]},
                "mimeType": mime_type,
                "caption": "",
            })
    return images


def _canonical_nodes(toc, pages):
    """Project the reconciled tree to the stable evidence-node contract."""
    roots = []
    front = [block for page in pages[:1] for block in page]
    if front:
        roots.append({
            "id": "canonical-front-matter",
            "type": "HEADING",
            "page": 1,
            "text": "Front matter",
            "children": [{
                "id": f"canonical-front-{index}",
                "type": "PARAGRAPH",
                "page": block["page"],
                "text": block["text"],
                "children": [],
            } for index, block in enumerate(front)],
        })

    counter = [0]

    def make_node(entry):
        counter[0] += 1
        node = {
            "id": f"canonical-heading-{counter[0]}",
            "type": "HEADING",
            "page": entry["page"] or 1,
            "text": entry["title"],
            "children": [],
        }
        current = node
        for block in entry["blocks"]:
            counter[0] += 1
            if block["heading"]:
                child = {
                    "id": f"canonical-heading-{counter[0]}",
                    "type": "HEADING",
                    "page": block["page"],
                    "text": block["text"],
                    "children": [],
                }
                node["children"].append(child)
                current = child
            else:
                current["children"].append({
                    "id": f"canonical-paragraph-{counter[0]}",
                    "type": "PARAGRAPH",
                    "page": block["page"],
                    "text": block["text"],
                    "children": [],
                })
        for child_entry in entry["children"]:
            node["children"].append(make_node(child_entry))
        return node

    roots.extend(make_node(entry) for entry in toc)
    return roots


def _extract_printed_toc(document):
    """Return a document tree from PDF outline data or a detected printed TOC.

    No document language, title, page number, or fixed TOC page is assumed.
    """
    outline = document.get_toc(simple=True)
    if outline:
        roots = []
        stack = []
        for level, title, page, *_ in outline:
            entry = _new_toc_entry(title, int(level), int(page) if page else None)
            _add_toc_entry(roots, stack, entry)
        return roots

    entries = []
    stack = []
    toc_pages = _find_toc_pages(document)
    major_seen = False
    for page_index in toc_pages:
        for raw_line in document[page_index].get_text("text").splitlines():
            line = _normalise_toc_line(raw_line)
            parsed = _parse_toc_line(line)
            if not parsed:
                continue
            title, target = parsed
            if _is_major_toc_heading(title):
                major_seen = True
            level = _toc_level(title, stack, major_seen)
            entry = _new_toc_entry(title, level, target)
            _add_toc_entry(entries, stack, entry)

    # The section entries carry the useful printed page numbers.  Infer
    # missing part/chapter starts from their first child for range matching.
    def infer(entry):
        for child in entry["children"]:
            infer(child)
        child_pages = [child["page"] for child in entry["children"] if child["page"] is not None]
        if entry["page"] is None and child_pages:
            entry["page"] = min(child_pages)
    for entry in entries:
        infer(entry)
    return entries


def _find_toc_pages(document):
    """Find pages with navigation-like density without assuming a page index."""
    scored = []
    for page_index, page in enumerate(document[: min(24, len(document))]):
        lines = [_normalise_toc_line(line) for line in page.get_text("text").splitlines()]
        references = sum(1 for line in lines if _parse_toc_line(line))
        structural = sum(1 for line in lines if line and _is_major_toc_heading(line))
        score = references + structural * 2
        if references >= 3 and score >= 6:
            scored.append((page_index, score))
    return [page_index for page_index, _ in scored]


def _normalise_toc_line(line):
    return " ".join(line.split()).strip(" .\t")


def _parse_toc_line(line):
    if not line or len(line) > 180:
        return None
    match = re.search(r"(?:\.{2,}|…+|\s{2,}|\t+)\s*(\d{1,4})\s*$", line)
    if not match:
        # Some generators omit leader dots for chapter/part entries.
        if not re.search(r"(?:\b(?:part|book|chapter|appendix)\b|부|장|권|편|부록)", line, re.IGNORECASE):
            return None
        match = re.search(r"\s+(\d{1,4})\s*$", line)
        if not match:
            return (line.strip(" ."), None) if _is_major_toc_heading(line) else None
    title = line[:match.start()].strip(" .:")
    if not title or title.isdigit():
        return None
    return title, int(match.group(1))


def _is_major_toc_heading(title):
    return bool(re.match(
        r"^\s*(?:part\b|book\b|volume\b|chapter\b|appendix\b|"
        r"(?:제\s*)?\d+\s*(?:부|편|권|장)\b|부록\b)",
        title, re.IGNORECASE))


def _toc_level(title, stack, major_seen):
    if re.match(r"^\s*(?:part\b|book\b|volume\b|(?:제\s*)?\d+\s*(?:부|편|권)\b)", title, re.IGNORECASE):
        return 1
    if re.match(r"^\s*(?:chapter\b|appendix\b|(?:제\s*)?\d+\s*장\b|부록\b)", title, re.IGNORECASE):
        return 2 if major_seen else 1
    if stack and stack[-1]["level"] >= 2:
        return stack[-1]["level"] + 1
    return 2 if major_seen and stack else 1


def _new_toc_entry(title, level, page):
    return {"title": title.strip(), "level": level, "page": page, "children": [], "blocks": []}


def _add_toc_entry(entries, stack, entry):
    while stack and stack[-1]["level"] >= entry["level"]:
        stack.pop()
    if stack:
        stack[-1]["children"].append(entry)
    else:
        entries.append(entry)
    stack.append(entry)


def _extract_body_pages(document):
    pages = []
    for page_index, page in enumerate(document):
        blocks = []
        for block in page.get_text("blocks"):
            if len(block) < 7 or block[6] != 0:
                continue
            text = " ".join(line.strip() for line in block[4].splitlines() if line.strip())
            if text:
                blocks.append({"page": page_index + 1, "text": text, "heading": _looks_like_heading(text)})
        page_text = "\n".join(block["text"] for block in blocks)
        if _bad_text(page_text):
            ocr_texts, ocr_lines = _ocr_pages(document, [page_index])
            blocks = [{
                "page": page_index + 1,
                "text": item["text"],
                "heading": item.get("label") == "section_header",
            } for item in ocr_texts]
            if not blocks:
                blocks = [{
                    "page": page_index + 1,
                    "text": text,
                    "heading": _looks_like_heading(text),
                } for text in ocr_lines if text.strip()]
        pages.append(blocks)
    return pages


def _canonical_markdown(toc, pages):
    """Assign body blocks to printed TOC ranges and emit one canonical tree."""
    flattened = []

    def visit(entry):
        flattened.append(entry)
        for child in entry["children"]:
            visit(child)
    for entry in toc:
        visit(entry)

    # The PDF's printed page numbers match its physical page numbers here.
    # Keep the rule explicit so a later edition can provide an offset.
    for page_blocks in pages:
        for block in page_blocks:
            if block["page"] <= 2:
                continue  # cover and the source TOC are metadata, not content
            candidates = [(index, entry) for index, entry in enumerate(flattened)
                          if entry["page"] is not None and entry["page"] <= block["page"]]
            if candidates:
                # For equal printed pages the last TOC entry owns the body
                # until the next entry begins (e.g. several sections start
                # on page 59).
                _, target = max(candidates, key=lambda pair: (pair[1]["page"], pair[0]))
                if block["heading"] and _same_heading(block["text"], target["title"]):
                    continue
                target["blocks"].append(block)

    lines = ["# Document", ""]
    for page_blocks in pages[:1]:
        if page_blocks:
            lines.extend(["## Front matter", ""])
            lines.extend(block["text"] + "\n" for block in page_blocks)

    def emit(entry):
        lines.extend(["#" * (entry["level"] + 1) + " " + entry["title"], ""])
        for block in entry["blocks"]:
            if block["heading"]:
                lines.extend(["#" * (entry["level"] + 2) + " " + block["text"], ""])
            else:
                lines.extend([block["text"], ""])
        for child in entry["children"]:
            emit(child)

    for entry in toc:
        emit(entry)
    return "\n".join(lines)


def _same_heading(left, right):
    normalize = lambda value: re.sub(r"[^0-9A-Za-z가-힣]", "", value).lower()
    return normalize(left) == normalize(right) or normalize(left).endswith(normalize(right))

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
        _ocr_reader = easyocr.Reader(_ocr_languages(), gpu=use_gpu, verbose=False)

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
    result = []
    pictures = value.get("pictures", []) if isinstance(value, dict) else []
    for index, picture in enumerate(pictures):
        if not isinstance(picture, dict):
            continue
        provenance = picture.get("prov", [])
        source = provenance[0] if isinstance(provenance, list) and provenance else {}
        bbox = source.get("bbox", {}) if isinstance(source, dict) else {}
        image = picture.get("image")
        mime_type = image.get("mimetype") if isinstance(image, dict) else None
        self_ref = str(picture.get("self_ref", ""))
        identifier = self_ref.rsplit("/", 1)[-1] if self_ref else str(index)
        result.append({
            "id": f"picture-{identifier}",
            "page": source.get("page_no", 1),
            "bbox": {
                "left": bbox.get("l", 0.0),
                "top": bbox.get("t", 0.0),
                "right": bbox.get("r", 0.0),
                "bottom": bbox.get("b", 0.0),
            },
            "mimeType": mime_type or "application/octet-stream",
            "caption": "",
        })
    return result

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host=os.getenv("DOCLING_HOST", "127.0.0.1"), port=int(os.getenv("DOCLING_PORT", "8099")))
