#!/usr/bin/env python3
"""Run the same PDF preprocessing pipeline with PyMuPDF and Docling."""
from __future__ import annotations

import argparse
from collections import Counter
import json
from pathlib import Path
import re
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from preprocessing_agent.adapters.docling_pdf import DoclingPdfAdapter  # noqa: E402
from preprocessing_agent.adapters.pymupdf4llm_pdf import PyMuPdf4LlmPdfAdapter  # noqa: E402
from preprocessing_agent.config import load_config  # noqa: E402
from preprocessing_agent.pipeline.extraction_service import PyMuPdfNativePdfAdapter  # noqa: E402
from preprocessing_agent.pipeline.pipeline import PreprocessingPipeline  # noqa: E402
from preprocessing_agent.parsers.pdf import PdfDocumentParser  # noqa: E402


def _tokens(text: str) -> Counter[str]:
    return Counter(re.findall(r"\w+", text.casefold(), flags=re.UNICODE))


def _table_counts(chunks_path: Path) -> tuple[int, int, int]:
    table_ids: set[str] = set()
    table_chunk_count = table_cell_span_count = 0
    for line in chunks_path.read_text(encoding="utf-8").splitlines():
        chunk = json.loads(line)
        spans = chunk.get("source_spans", ())
        span_ids = {str(span["table_id"]) for span in spans if span.get("table_id")}
        if span_ids:
            table_chunk_count += 1
            table_ids.update(span_ids)
            table_cell_span_count += sum(bool(span.get("table_cell")) for span in spans)
    return len(table_ids), table_chunk_count, table_cell_span_count


def _run(provider: str, source: Path, config_path: Path, output: Path) -> tuple[dict[str, object], str]:
    adapter = {
        "pymupdf": PyMuPdfNativePdfAdapter(analyze_structure=False),
        "pymupdf4llm": PyMuPdf4LlmPdfAdapter(),
        "docling": DoclingPdfAdapter(),
    }[provider]
    started = time.perf_counter()
    pages = adapter.extract(source)
    extraction_seconds = time.perf_counter() - started
    parser = PdfDocumentParser(extractor=lambda _source: pages, analyze_structure=False, provider_name=provider)
    pipeline = PreprocessingPipeline.from_config(config_path, parser=parser, output_dir=output)
    started = time.perf_counter()
    result = pipeline.run(source=source)
    downstream_seconds = time.perf_counter() - started
    text = result.document.source_text
    table_count, table_chunk_count, table_cell_span_count = _table_counts(output / "chunks.jsonl")
    metrics: dict[str, object] = {
        "provider": provider,
        "source_page_count": result.document.metadata.get("source_page_count", len(result.document.pages)),
        "parsed_page_count": len(result.document.pages),
        "extracted_block_count": sum(len(page.get("blocks", ())) for page in pages),
        "extracted_text_characters": sum(len(str(block.get("text", ""))) for page in pages for block in page.get("blocks", ())),
        "normalized_text_characters": len(text),
        "word_count": sum(_tokens(text).values()),
        "chunk_count": len(result.chunks),
        "table_id_count_in_chunk_spans": table_count,
        "table_chunk_count": table_chunk_count,
        "table_cell_source_span_count": table_cell_span_count,
        "validation_valid": result.valid,
        "validation_issue_count": len(result.issues),
        "validation_issue_types": dict(Counter(str(issue.issue_type) for issue in result.issues)),
        "extraction_seconds": round(extraction_seconds, 3),
        "shared_pipeline_seconds": round(downstream_seconds, 3),
        "total_seconds": round(extraction_seconds + downstream_seconds, 3),
        "artifact_dir": str(output),
    }
    return metrics, text


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pdf", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path, help="new directory for both provider artifacts and report")
    parser.add_argument("--config", type=Path, default=Path("configs/profiles/dnd_rulebook.yaml"))
    args = parser.parse_args()
    source = args.pdf.expanduser().resolve(strict=True)
    if source.suffix.casefold() != ".pdf":
        parser.error("--pdf must point to a PDF")
    root = args.output.expanduser().resolve()
    root.mkdir(parents=True, exist_ok=False)
    config = load_config(args.config)
    results: dict[str, dict[str, object]] = {}
    texts: dict[str, str] = {}
    for provider in ("pymupdf", "pymupdf4llm", "docling"):
        provider_dir = root / provider
        provider_dir.mkdir()
        results[provider], texts[provider] = _run(provider, source, args.config, provider_dir)
        print(json.dumps({"stage": "provider_complete", **results[provider]}, sort_keys=True), flush=True)

    pairwise_overlap = {}
    for left_name, right_name in (("pymupdf", "pymupdf4llm"), ("pymupdf4llm", "docling")):
        left, right = _tokens(texts[left_name]), _tokens(texts[right_name])
        shared = sum((left & right).values())
        pairwise_overlap[f"{left_name}_vs_{right_name}"] = {
            "shared_word_count": shared,
            f"{left_name}_word_count": sum(left.values()),
            f"{right_name}_word_count": sum(right.values()),
            f"{left_name}_overlap_rate": round(shared / max(1, sum(left.values())), 6),
            f"{right_name}_overlap_rate": round(shared / max(1, sum(right.values())), 6),
        }
    report = {
        "source": {"path": str(source), "sha256": __import__("hashlib").sha256(source.read_bytes()).hexdigest()},
        "config": str(args.config.resolve()),
        "providers": results,
        "text_comparison": {"metric": "normalized_word_multiset_overlap; differential comparison, not gold accuracy",
                            "pairwise": pairwise_overlap},
        "limitations": ["Docling uses layout and table models with OCR disabled for this text-based PDF; model download time is outside the run after the smoke test warmed its cache.",
                        "Differential text overlap is not a human-verified accuracy score.",
                        "Table counts come from source identifiers in emitted chunks and are not human-verified table recall or precision.",
                        "Blank-form exclusion still follows only the existing page mapping contract; Docling form labels are not yet used to exclude pages."],
    }
    report_path = root / "comparison.json"
    report_path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"stage": "comparison_complete", "report": str(report_path),
                      "text_comparison": report["text_comparison"]}, ensure_ascii=False, sort_keys=True), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
