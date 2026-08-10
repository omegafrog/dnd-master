#!/usr/bin/env python3
"""Small engine-neutral Docling HTTP sidecar for rule-knowledge-service."""
import base64
import io
import os
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from docling.document_converter import DocumentConverter
from docling.datamodel.base_models import DocumentStream

app = FastAPI()
converter = DocumentConverter()

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
    result = []
    for index, item in enumerate(value.get("texts", []) if isinstance(value, dict) else []):
        text = item.get("text", "") if isinstance(item, dict) else str(item)
        if not text.strip():
            continue
        label = str(item.get("label", "paragraph")).upper() if isinstance(item, dict) else "PARAGRAPH"
        kind = "HEADING" if "SECTION" in label or "TITLE" in label else "PARAGRAPH"
        result.append({"id": f"{prefix}-{index}", "type": kind, "page": page, "text": text, "children": []})
    return result

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
