"""Filesystem exporters for the public preprocessing artifacts."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Iterable

from preprocessing_agent.domain import Chunk, DocumentTree, ValidationIssue, to_dict


_CHUNK_FIELDS = (
    "chunk_id", "canonical_key", "content_type", "source_text", "embedding_text",
    "token_count", "source_spans", "section_path", "parent_key",
)


def _chunk_to_dict(chunk: Chunk) -> dict[str, object]:
    """Serialize only the public Chunk contract, excluding planning metadata."""
    return {name: to_dict(getattr(chunk, name)) for name in _CHUNK_FIELDS}


class ArtifactExporter:
    PIPELINE_VERSION = "rag-preprocessing-v0.1"
    SCHEMA_VERSION = "1"

    def export(self, output_dir: str | Path, source: str | Path, source_text: str,
               chunks: Iterable[Chunk], issues: Iterable[ValidationIssue], tree: DocumentTree,
               *, page_count: int = 0, profile: str = "default", policy: object | None = None,
               agent_stats: dict[str, int] | None = None, invalid_chunk_ids: set[str] | None = None,
               pipeline_version: str = PIPELINE_VERSION, schema_version: str = SCHEMA_VERSION) -> dict[str, object]:
        output = Path(output_dir); output.mkdir(parents=True, exist_ok=True)
        all_chunks = tuple(chunks); all_issues = tuple(issues)
        invalid = invalid_chunk_ids or {item.path for item in all_issues if item.issue_type == "invalid_source_span"}
        exported = tuple(item for item in all_chunks if item.chunk_id not in invalid)
        self._jsonl(output / "chunks.jsonl", exported, serializer=_chunk_to_dict)
        self._jsonl(output / "issues.jsonl", all_issues)
        (output / "document_tree.json").write_text(json.dumps(to_dict(tree), ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        source_bytes = source_text.encode("utf-8")
        manifest = {
            "source": {"path": str(source), "sha256": hashlib.sha256(source_bytes).hexdigest()},
            "source_sha256": hashlib.sha256(source_bytes).hexdigest(),
            "pipeline_version": pipeline_version, "schema_version": schema_version,
            "profile": profile, "policy": to_dict(policy) if policy is not None else {},
            "token_policy": to_dict(policy) if policy is not None else {},
            "statistics": {
                "pages": page_count, "chunks": {"input": len(all_chunks), "exported": len(exported), "excluded": len(all_chunks) - len(exported)},
                "validation": {"issues": len(all_issues), "valid": not all_issues},
                "agent": agent_stats or {"calls": 0, "accepted": 0, "rejected": 0},
            },
        }
        manifest["page_statistics"] = {"count": page_count}
        manifest["chunk_statistics"] = manifest["statistics"]["chunks"]
        manifest["agent_statistics"] = manifest["statistics"]["agent"]
        manifest["validation_statistics"] = manifest["statistics"]["validation"]
        (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
        return manifest

    @staticmethod
    def _jsonl(path: Path, values: Iterable[object], *, serializer=to_dict) -> None:
        path.write_text("".join(json.dumps(serializer(value), ensure_ascii=False, sort_keys=True) + "\n" for value in values), encoding="utf-8")
