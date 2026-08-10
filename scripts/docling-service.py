#!/usr/bin/env python3
"""Small engine-neutral Docling HTTP sidecar for rule-knowledge-service."""
import base64
import io
import os
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from docling.document_converter import DocumentConverter, PdfFormatOption
from docling.datamodel.base_models import DocumentStream, InputFormat
from docling.datamodel.pipeline_options import PdfPipelineOptions

app = FastAPI()
# The rulebook pipeline already has a legacy OCR fallback. Keeping Docling OCR
# off avoids forcing the OCR model onto text PDFs and preserves their native
# text/layout extraction (including this fixture's embedded font encoding).
converter = DocumentConverter(
    allowed_formats=[InputFormat.PDF, InputFormat.DOCX],
    format_options={InputFormat.PDF: PdfFormatOption(pipeline_options=PdfPipelineOptions(do_ocr=False))},
)

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
        raise HTTPException(status_code=422, detail="document extraction failed") from exc

def _nodes(value, page=1, prefix="node"):
    """Map Docling's versioned dict shape to stable nodes; unknown fields ignored."""
    roots = []
    heading_stack = []
    items = value.get("texts", []) if isinstance(value, dict) else []
    for index, item in enumerate(items):
        text = item.get("text", "") if isinstance(item, dict) else str(item)
        if not text.strip():
            continue
        label = str(item.get("label", "paragraph")).upper() if isinstance(item, dict) else "PARAGRAPH"
        is_heading = "SECTION" in label or "TITLE" in label or "HEADING" in label
        node = {
            "id": f"{prefix}-{index}",
            "type": "HEADING" if is_heading else "PARAGRAPH",
            "page": _page(item, page),
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
