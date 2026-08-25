"""Artifact boundary and intrinsic evaluation orchestration."""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import re
from collections.abc import Mapping
from typing import Any, Callable, Iterable

from preprocessing_agent.domain import Chunk, DocumentTree
from preprocessing_agent.domain.serialization import from_dict
from preprocessing_agent.parsers.base import ParserError
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from .metrics import boundary_metrics, duplicate_metrics, token_statistics


class EvaluationInputError(ValueError):
    """The exported run cannot satisfy the evaluator input contract."""


@dataclass(frozen=True, slots=True)
class ExportedRun:
    run_dir: Path
    chunks: tuple[Chunk, ...]
    tree: DocumentTree
    manifest: dict[str, Any]
    source_text: str | None
    source_pages: tuple[str, ...]
    validation_issues: tuple[dict[str, Any], ...] = ()


@dataclass(frozen=True, slots=True)
class EvalConfig:
    tiny_tokens: int = 100
    oversized_tokens: int = 500
    near_duplicate_jaccard: float = .8
    source_traceability_min: float = .999
    source_mutation_max: float = 0.0


SourceExtractor = Callable[[Path], str | Iterable[str] | Iterable[Mapping[str, Any]]]


def _injected_pdf_extractor(extractor: SourceExtractor, source_path: Path) -> Iterable[Mapping[str, Any]]:
    extracted = extractor(source_path)
    if isinstance(extracted, str):
        extracted = (extracted,)
    values = tuple(extracted)
    if all(isinstance(value, Mapping) for value in values):
        return values
    if all(isinstance(value, str) for value in values):
        return tuple({"page_number": position, "blocks": [{"text": value}]} for position, value in enumerate(values, 1))
    raise EvaluationInputError("injected PDF extractor must return page text or page mappings")


def _read_pdf_pages(source_path: Path, extractor: SourceExtractor | None = None) -> tuple[str, tuple[str, ...]]:
    parser = PdfDocumentParser(
        None if extractor is None else lambda path: _injected_pdf_extractor(extractor, path)
    )
    try:
        parsed = parser.parse(source_path)
    except ParserError as exc:
        raise EvaluationInputError(str(exc)) from exc
    return parsed.source_text, tuple(page.source_text for page in parsed.pages)


def _read_source_text(source_path: Path, extractor: SourceExtractor | None = None) -> str:
    """Read an exported source without applying text decoding to known PDFs."""
    if source_path.suffix.lower() == ".pdf":
        return _read_pdf_pages(source_path, extractor)[0]
    try:
        return source_path.read_text(encoding="utf-8")
    except UnicodeDecodeError as exc:
        raise EvaluationInputError(f"source is neither a PDF nor valid UTF-8 text: {source_path}") from exc


def _read_source_pages(source_path: Path, extractor: SourceExtractor | None = None) -> tuple[str, ...]:
    """Read source text while retaining the page boundary used by source spans."""
    if source_path.suffix.lower() == ".pdf":
        return _read_pdf_pages(source_path, extractor)[1]
    try:
        return (source_path.read_text(encoding="utf-8"),)
    except UnicodeDecodeError as exc:
        raise EvaluationInputError(f"source is neither a PDF nor valid UTF-8 text: {source_path}") from exc


def load_exported_run(run_dir: str | Path, source_extractor: SourceExtractor | None = None) -> ExportedRun:
    root = Path(run_dir)
    required = ("chunks.jsonl", "document_tree.json", "manifest.json")
    missing = [name for name in required if not (root / name).is_file()]
    if missing:
        raise EvaluationInputError("missing exported artifact(s): " + ", ".join(missing))
    try:
        chunks = tuple(from_dict(Chunk, json.loads(line)) for line in (root / "chunks.jsonl").read_text(encoding="utf-8").splitlines() if line.strip())
        tree = from_dict(DocumentTree, json.loads((root / "document_tree.json").read_text(encoding="utf-8")))
        manifest = json.loads((root / "manifest.json").read_text(encoding="utf-8"))
    except (OSError, ValueError, TypeError, KeyError) as exc:
        raise EvaluationInputError(f"invalid exported artifact: {exc}") from exc
    source_text = manifest.get("source_text")
    source_pages: tuple[str, ...] = (source_text,) if isinstance(source_text, str) else ()
    if source_text is None and isinstance(manifest.get("source"), dict):
        source_path = Path(str(manifest["source"].get("path", "")))
        for candidate in (source_path, root / source_path, root.parent / source_path):
            if candidate.is_file():
                if candidate.suffix.lower() == ".pdf":
                    source_text, source_pages = _read_pdf_pages(candidate, source_extractor)
                else:
                    source_pages = _read_source_pages(candidate, source_extractor)
                    source_text = "".join(source_pages)
                break
    issue_path = root / "issues.jsonl"
    validation_issues = tuple(json.loads(line) for line in issue_path.read_text(encoding="utf-8").splitlines() if line.strip()) if issue_path.is_file() else ()
    return ExportedRun(root, tuple(sorted(chunks, key=lambda item: item.chunk_id)), tree, manifest, source_text, source_pages, validation_issues)


def _normalize_layout(text: str) -> str:
    return re.sub(r"\s+", " ", text).strip()


def reconstruct_chunk_source(chunk: Chunk, source_text: str | None, source_pages: tuple[str, ...] = ()) -> tuple[str | None, str]:
    if not source_pages or not chunk.source_spans:
        return None, "SOURCE_TRACE_ERROR"
    candidates: list[str] = []
    for span in chunk.source_spans:
        page_index = span.page_number - 1
        if page_index < 0 or page_index >= len(source_pages):
            return None, "SOURCE_TRACE_ERROR"
        if (span.char_start is None or span.char_end is None or span.char_start < 0 or
                span.char_end < span.char_start or span.char_end > len(source_pages[page_index])):
            return None, "SOURCE_TRACE_ERROR"
        candidates.append(source_pages[page_index][span.char_start:span.char_end])
    reconstructed = " ".join(candidates)
    return reconstructed, "SOURCE_MUTATION" if _normalize_layout(reconstructed) != _normalize_layout(chunk.source_text) else "OK"


def evaluate_intrinsic(run: ExportedRun, config: EvalConfig = EvalConfig()) -> tuple[dict[str, object], list[dict[str, object]]]:
    failures: list[dict[str, object]] = []
    traceable = mutations = 0
    for chunk in run.chunks:
        _, status = reconstruct_chunk_source(chunk, run.source_text, run.source_pages)
        if status == "OK":
            traceable += 1
        else:
            mutations += status == "SOURCE_MUTATION"
            failures.append({"type": status, "canonical_key": chunk.canonical_key, "chunk_ids": [chunk.chunk_id], "details": {"message": status.lower()}})
    for issue in run.validation_issues:
        failures.append({"type": str(issue.get("issue_type", "VALIDATION")),
                         "canonical_key": next((chunk.canonical_key for chunk in run.chunks if chunk.chunk_id == issue.get("path")), ""),
                         "chunk_ids": [str(issue["path"])] if issue.get("path") else [],
                         "details": {"message": issue.get("message", "")}})
    total = len(run.chunks) or 1
    source = {"source_traceability_rate": traceable / total, "source_mutation_rate": mutations / total,
              "traceable_chunks": traceable, "mutated_chunks": mutations}
    stats = token_statistics(run.chunks, config.tiny_tokens, config.oversized_tokens)
    validation = {"issues": len(run.validation_issues), "issue_types": sorted({str(item.get("issue_type", "VALIDATION")) for item in run.validation_issues})}
    intrinsic = {"source": source, "boundary": boundary_metrics(run.chunks), "size": stats,
                 "validation": validation,
                 "duplicate": duplicate_metrics(run.chunks, config.near_duplicate_jaccard), "token_stats": stats}
    return intrinsic, sorted(failures, key=lambda item: (item["type"], item["chunk_ids"]))
