"""Stable JSON/JSONL output and deterministic comparison for evaluation runs."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path
from dataclasses import dataclass
from typing import Any, Iterable, Mapping

from .preprocessing import EvalConfig, ExportedRun, evaluate_intrinsic
from .gold import GoldCase, evaluate_gold, load_gold_cases
from .semantic import EntityFixture, evaluate_semantic
from .retrieval import RetrieverPort, evaluate_ranked_retrieval

FAILURE_TAXONOMY = (
    "SOURCE_MUTATION", "SOURCE_TRACE_ERROR", "BROKEN_BOUNDARY", "SPLIT_ENTITY",
    "MIXED_CONTEXT", "TINY_CHUNK", "OVERSIZED_CHUNK", "DUPLICATION",
    "GOLD_CONTEXT_MISSING", "GOLD_EVIDENCE_SPLIT", "RETRIEVAL_MISS", "RANKING_ERROR",
)

COMPARISON_PRIORITY = (
    ("source_mutation_rate", "intrinsic.source.source_mutation_rate", "lower"),
    ("gold_context_coverage", "gold.gold_context_coverage", "higher"),
    ("single_chunk_answerability", "gold.single_chunk_answerability_rate", "higher"),
    ("split_entity_rate", "semantic.split_entity_rate", "lower"),
    ("mixed_context_rate", "semantic.mixed_context_rate", "lower"),
    ("recall_at_5", "retrieval.recall_at_5", "higher"),
    ("mrr", "retrieval.mrr", "higher"),
)


def apply_quality_gate(report: Mapping[str, object], *, source_mutation_max: float = 0.0,
                       source_traceability_min: float = .999, gold_coverage_min: float = .90,
                       split_entity_max: float = .05) -> tuple[bool, tuple[str, ...]]:
    """Apply only the v1 hard gates; score groups remain independent."""

    failures: list[str] = []
    source = report.get("intrinsic", {}).get("source", {}) if isinstance(report.get("intrinsic", {}), Mapping) else {}
    gold = report.get("gold", {}) if isinstance(report.get("gold", {}), Mapping) else {}
    semantic = report.get("semantic", {}) if isinstance(report.get("semantic", {}), Mapping) else {}
    if _number(source.get("source_mutation_rate")) > source_mutation_max:
        failures.append("source_mutation_rate")
    if _number(source.get("source_traceability_rate")) < source_traceability_min:
        failures.append("source_traceability_rate")
    if _number(gold.get("gold_context_coverage")) < gold_coverage_min:
        failures.append("gold_context_coverage")
    if _number(semantic.get("split_entity_rate")) > split_entity_max:
        failures.append("split_entity_rate")
    return not failures, tuple(failures)


def _number(value: object) -> float:
    try:
        return float(value)
    except (TypeError, ValueError):
        return 0.0


@dataclass(frozen=True, slots=True)
class ComparisonReport:
    """A metric-by-metric comparison; deliberately has no aggregate winner."""

    baseline: Mapping[str, object]
    variant: Mapping[str, object]
    metrics: tuple[Mapping[str, object], ...]
    priority_order: tuple[str, ...]

    def to_dict(self) -> dict[str, object]:
        results = {str(item["result"]) for item in self.metrics}
        trade_offs = []
        if "baseline" in results and "variant" in results:
            trade_offs.append({"type": "metric_trade_off", "baseline_metrics": [item["metric"] for item in self.metrics if item["result"] == "baseline"],
                               "variant_metrics": [item["metric"] for item in self.metrics if item["result"] == "variant"]})
        return {"baseline": dict(self.baseline), "variant": dict(self.variant),
                "priority_order": list(self.priority_order), "metrics": [dict(item) for item in self.metrics],
                "winner": None, "trade_offs": trade_offs}


def _at_path(value: Mapping[str, object], path: str) -> float:
    current: object = value
    for part in path.split("."):
        if not isinstance(current, Mapping):
            return 0.0
        current = current.get(part, 0.0)
    try:
        return float(current)
    except (TypeError, ValueError):
        return 0.0


def _run_metadata(report: Mapping[str, object]) -> dict[str, object]:
    input_data = report.get("input", {})
    manifest = input_data.get("manifest", {}) if isinstance(input_data, Mapping) else {}
    return {"run_id": report.get("run_id", ""), "run_dir": input_data.get("run_dir", "") if isinstance(input_data, Mapping) else "",
            "source_sha256": manifest.get("source_sha256", "") if isinstance(manifest, Mapping) else "",
            "pipeline_version": manifest.get("pipeline_version", "") if isinstance(manifest, Mapping) else "",
            "baseline_id": report.get("baseline", {}).get("id", "baseline-v1") if isinstance(report.get("baseline", {}), Mapping) else "baseline-v1"}


def compare_reports(baseline: Mapping[str, object], variant: Mapping[str, object]) -> ComparisonReport:
    metrics: list[Mapping[str, object]] = []
    for name, path, direction in COMPARISON_PRIORITY:
        left, right = _at_path(baseline, path), _at_path(variant, path)
        better = (right > left) if direction == "higher" else (right < left)
        worse = (right < left) if direction == "higher" else (right > left)
        result = "variant" if better else "baseline" if worse else "tie"
        metrics.append({"metric": name, "path": path, "direction": direction,
                        "baseline": left, "variant": right, "delta": right - left, "result": result})
    return ComparisonReport(_run_metadata(baseline), _run_metadata(variant), tuple(metrics), tuple(item[0] for item in COMPARISON_PRIORITY))


def load_report(path: str | Path) -> dict[str, object]:
    root = Path(path)
    report_path = root if root.is_file() else root / "preprocessing_eval.json"
    return json.loads(report_path.read_text(encoding="utf-8"))


def write_comparison(baseline: Mapping[str, object], variant: Mapping[str, object], output: str | Path) -> Path:
    destination = Path(output)
    if destination.suffix.lower() != ".json":
        destination.mkdir(parents=True, exist_ok=True)
        destination = destination / "preprocessing_comparison.json"
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(compare_reports(baseline, variant).to_dict(), ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    return destination


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
            message = str(exc)
            failure = {"type": "RANKING_ERROR" if any(token in message for token in ("duplicate", "invalid", "unknown chunk")) else "RETRIEVAL_MISS",
                       "case_id": "", "canonical_key": "", "chunk_ids": [], "details": {"message": message}}
            failures.append(failure)
            retrieval_report["failures"] = [failure]
        else:
            retrieval_report = retrieval.to_dict()
    source = intrinsic["source"]
    preliminary = {"intrinsic": intrinsic, "semantic": semantic, "gold": gold_report}
    passed, gate_failures = apply_quality_gate(preliminary, source_mutation_max=config.source_mutation_max,
                                               source_traceability_min=config.source_traceability_min)
    if not cases:
        gate_failures = tuple(item for item in gate_failures if item != "gold_context_coverage")
        passed = not gate_failures
    report = {"run_id": _run_id(run), "passed": passed, "gate_failures": list(gate_failures),
              "failure_taxonomy": list(FAILURE_TAXONOMY),
              "intrinsic": intrinsic, "semantic": semantic, "gold": gold_report, "retrieval": retrieval_report,
              "counts": {"chunks": len(run.chunks), "failures": len(failures), "gold_cases": len(cases)},
              "baseline": {"id": str(run.manifest.get("baseline_id", "baseline-v1")), "version": "v1"},
              "input": {"run_dir": str(run.run_dir), "manifest": run.manifest,
                        "artifacts": ["chunks.jsonl", "document_tree.json", "manifest.json"]},
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
