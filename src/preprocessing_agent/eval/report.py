"""Stable JSON and JSONL output for one intrinsic evaluation run."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Iterable

from .preprocessing import EvalConfig, ExportedRun, evaluate_intrinsic


def _run_id(run: ExportedRun) -> str:
    value = str(run.manifest.get("source_sha256") or run.manifest.get("source", {}).get("sha256") or run.run_dir)
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:16]


def evaluate_run(run: ExportedRun, config: EvalConfig = EvalConfig()) -> tuple[dict[str, object], list[dict[str, object]]]:
    intrinsic, failures = evaluate_intrinsic(run, config)
    source = intrinsic["source"]
    gates: list[str] = []
    if source["source_mutation_rate"] > config.source_mutation_max:
        gates.append("source_mutation_rate")
    if source["source_traceability_rate"] < config.source_traceability_min:
        gates.append("source_traceability_rate")
    report = {"run_id": _run_id(run), "passed": not gates, "gate_failures": gates,
              "intrinsic": intrinsic, "counts": {"chunks": len(run.chunks), "failures": len(failures)},
              "input": {"run_dir": str(run.run_dir), "artifacts": ["chunks.jsonl", "document_tree.json", "manifest.json"]},
              "config": {"tiny_tokens": config.tiny_tokens, "oversized_tokens": config.oversized_tokens,
                         "near_duplicate_jaccard": config.near_duplicate_jaccard,
                         "source_traceability_min": config.source_traceability_min, "source_mutation_max": config.source_mutation_max}}
    return report, failures


def write_report(run: ExportedRun, output_dir: str | Path | None = None, config: EvalConfig = EvalConfig()) -> tuple[Path, Path]:
    destination = Path(output_dir) if output_dir is not None else run.run_dir
    destination.mkdir(parents=True, exist_ok=True)
    report, failures = evaluate_run(run, config)
    report_path = destination / "preprocessing_eval.json"
    failure_path = destination / "preprocessing_eval_failures.jsonl"
    report_path.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    failure_path.write_text("".join(json.dumps(item, ensure_ascii=False, sort_keys=True) + "\n" for item in failures), encoding="utf-8")
    return report_path, failure_path
