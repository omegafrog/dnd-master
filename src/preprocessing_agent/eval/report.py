"""Stable JSON and JSONL output for one intrinsic evaluation run."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from typing import Any, Iterable

from .preprocessing import EvalConfig, ExportedRun, evaluate_intrinsic
from .gold import GoldCase, evaluate_gold, load_gold_cases
from .semantic import EntityFixture, evaluate_semantic
from .retrieval import RetrieverPort, evaluate_ranked_retrieval


def _run_id(run: ExportedRun) -> str:
    value = str(run.manifest.get("source_sha256") or run.manifest.get("source", {}).get("sha256") or run.run_dir)
    return hashlib.sha256(value.encode("utf-8")).hexdigest()[:16]


def _load_semantic_fixtures(path: str | Path | None) -> tuple[EntityFixture, ...]:
    if path is None or not Path(path).is_file():
        return ()
    import json
    from preprocessing_agent.domain import ContentType
    result = []
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        if not line.strip():
            continue
        value = json.loads(line)
        if value.get("type") in {"entity_fixture", "semantic_fixture"} or "entity_id" in value:
            result.append(EntityFixture(str(value["entity_id"]), str(value["canonical_key"]), ContentType(value["content_type"]), bool(value.get("atomic", True)), value.get("parent_key"), int(value.get("expected_chunk_count", 1))))
    return tuple(result)


def evaluate_run(run: ExportedRun, config: EvalConfig = EvalConfig(), eval_path: str | Path | None = None,
                retriever: RetrieverPort | None = None) -> tuple[dict[str, object], list[dict[str, object]]]:
    intrinsic, failures = evaluate_intrinsic(run, config)
    cases = load_gold_cases(eval_path) if eval_path is not None and Path(eval_path).is_file() else ()
    semantic, semantic_failures = evaluate_semantic(run.chunks, _load_semantic_fixtures(eval_path))
    gold = evaluate_gold(cases, run.chunks)
    gold_report = {**gold.metrics, "unmatched_keys": list(gold.unmatched_keys),
                   "resolutions": [{"case_id": item.case_id, "chunk_ids": list(item.chunk_ids),
                                    "unmatched_keys": list(item.unmatched_keys),
                                    "evidence_complete": list(item.evidence_complete)} for item in gold.resolutions]}
    failures = sorted([*failures, *semantic_failures, *gold.failures], key=lambda item: (item.get("type", ""), item.get("case_id", ""), item.get("canonical_key", ""), item.get("chunk_ids", [])))
    retrieval_report: dict[str, object] = {"queries": 0, "recall_at": {"1": 0.0, "3": 0.0, "5": 0.0, "10": 0.0},
                       "recall_at_1": 0.0, "recall_at_3": 0.0, "recall_at_5": 0.0, "recall_at_10": 0.0,
                       "mrr": 0.0, "evidence_recall_at_5": 0.0, "evidence_recall": 0.0,
                       "single_evidence_queries": 0, "multi_evidence_queries": 0, "failures": []}
    if retriever is not None and cases:
        gold_ids = {resolution.case_id: resolution.chunk_ids for resolution in gold.resolutions}
        required_ids = {case.case_id: tuple(key for group in case.required_evidence for key in group.keys)
                        for case in cases if case.required_evidence}
        key_to_chunk = {chunk.canonical_key: chunk.chunk_id for chunk in run.chunks}
        required_chunks = {case_id: tuple(key_to_chunk[key] for key in keys if key in key_to_chunk)
                           for case_id, keys in required_ids.items()}
        try:
            retrieval = evaluate_ranked_retrieval(gold_ids, retriever, required_evidence=required_chunks,
                                                   known_chunk_ids=(chunk.chunk_id for chunk in run.chunks))
        except ValueError as exc:
            failure = {"type": "RETRIEVAL_INPUT_FAILURE", "case_id": "", "chunk_ids": [], "details": {"message": str(exc)}}
            failures.append(failure)
            retrieval_report["failures"] = [failure]
        else:
            retrieval_report = retrieval.to_dict()
    source = intrinsic["source"]
    gates: list[str] = []
    if source["source_mutation_rate"] > config.source_mutation_max:
        gates.append("source_mutation_rate")
    if source["source_traceability_rate"] < config.source_traceability_min:
        gates.append("source_traceability_rate")
    if cases and gold.metrics["gold_context_coverage"] < .90:
        gates.append("gold_context_coverage")
    if semantic["split_entity_rate"] > .05:
        gates.append("split_entity_rate")
    report = {"run_id": _run_id(run), "passed": not gates, "gate_failures": gates,
              "intrinsic": intrinsic, "semantic": semantic, "gold": gold_report, "retrieval": retrieval_report,
              "counts": {"chunks": len(run.chunks), "failures": len(failures), "gold_cases": len(cases)},
              "input": {"run_dir": str(run.run_dir), "artifacts": ["chunks.jsonl", "document_tree.json", "manifest.json"]},
              "config": {"tiny_tokens": config.tiny_tokens, "oversized_tokens": config.oversized_tokens,
                         "near_duplicate_jaccard": config.near_duplicate_jaccard,
                         "source_traceability_min": config.source_traceability_min, "source_mutation_max": config.source_mutation_max}}
    return report, failures


def write_report(run: ExportedRun, output_dir: str | Path | None = None, config: EvalConfig = EvalConfig(), eval_path: str | Path | None = None,
                 retriever: RetrieverPort | None = None) -> tuple[Path, Path]:
    destination = Path(output_dir) if output_dir is not None else run.run_dir
    destination.mkdir(parents=True, exist_ok=True)
    report, failures = evaluate_run(run, config, eval_path, retriever)
    report_path = destination / "preprocessing_eval.json"
    failure_path = destination / "preprocessing_eval_failures.jsonl"
    report_path.write_text(json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    failure_path.write_text("".join(json.dumps(item, ensure_ascii=False, sort_keys=True) + "\n" for item in failures), encoding="utf-8")
    return report_path, failure_path
