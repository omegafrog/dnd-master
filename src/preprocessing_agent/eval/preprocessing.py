"""Artifact boundary and intrinsic evaluation orchestration."""

from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
from typing import Any, Callable

from preprocessing_agent.domain import Chunk, DocumentTree
from preprocessing_agent.domain.serialization import from_dict
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


@dataclass(frozen=True, slots=True)
class EvalConfig:
    tiny_tokens: int = 100
    oversized_tokens: int = 500
    near_duplicate_jaccard: float = .8
    source_traceability_min: float = .999
    source_mutation_max: float = 0.0


SourceExtractor = Callable[[Path], str]


def _read_source_text(source_path: Path, extractor: SourceExtractor | None = None) -> str:
    """Read an exported source without applying text decoding to known PDFs."""
    if source_path.suffix.lower() == ".pdf":
        if extractor is not None:
            return extractor(source_path)
        try:
            import pymupdf
        except ImportError as exc:
            raise EvaluationInputError("PDF source requires PyMuPDF or an injected source extractor") from exc
        with pymupdf.open(source_path) as document:
            return "".join(page.get_text("text") for page in document)
    try:
        return source_path.read_text(encoding="utf-8")
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
    if source_text is None and isinstance(manifest.get("source"), dict):
        source_path = Path(str(manifest["source"].get("path", "")))
        for candidate in (source_path, root / source_path, root.parent / source_path):
            if candidate.is_file():
                source_text = _read_source_text(candidate, source_extractor)
                break
    return ExportedRun(root, tuple(sorted(chunks, key=lambda item: item.chunk_id)), tree, manifest, source_text)


def reconstruct_chunk_source(chunk: Chunk, source_text: str | None) -> tuple[str | None, str]:
    if not source_text or not chunk.source_spans:
        return None, "SOURCE_TRACE_ERROR"
    candidates = [source_text[span.char_start:span.char_end] for span in chunk.source_spans
                  if span.char_start is not None and span.char_end is not None]
    if candidates:
        reconstructed = " ".join(candidates)
        return reconstructed, "SOURCE_MUTATION" if reconstructed != chunk.source_text else "OK"
    normalized_chunk = " ".join(chunk.source_text.split())
    if normalized_chunk and normalized_chunk in " ".join(source_text.split()):
        return chunk.source_text, "OK"
    return None, "SOURCE_TRACE_ERROR"


def evaluate_intrinsic(run: ExportedRun, config: EvalConfig = EvalConfig()) -> tuple[dict[str, object], list[dict[str, object]]]:
    failures: list[dict[str, object]] = []
    traceable = mutations = 0
    for chunk in run.chunks:
        _, status = reconstruct_chunk_source(chunk, run.source_text)
        if status == "OK":
            traceable += 1
        else:
            mutations += status == "SOURCE_MUTATION"
            failures.append({"type": status, "canonical_key": chunk.canonical_key, "chunk_ids": [chunk.chunk_id], "details": {"message": status.lower()}})
    total = len(run.chunks) or 1
    source = {"source_traceability_rate": traceable / total, "source_mutation_rate": mutations / total,
              "traceable_chunks": traceable, "mutated_chunks": mutations}
    stats = token_statistics(run.chunks, config.tiny_tokens, config.oversized_tokens)
    intrinsic = {"source": source, "boundary": boundary_metrics(run.chunks), "size": stats,
                 "duplicate": duplicate_metrics(run.chunks, config.near_duplicate_jaccard), "token_stats": stats}
    return intrinsic, sorted(failures, key=lambda item: (item["type"], item["chunk_ids"]))
