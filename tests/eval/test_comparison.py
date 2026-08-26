import json
import subprocess
import sys

from preprocessing_agent.eval.report import FAILURE_TAXONOMY, apply_quality_gate, compare_reports


def report(**overrides):
    value = {"run_id": "a", "passed": True, "gate_failures": [],
             "intrinsic": {"source": {"source_mutation_rate": 0.0}},
             "semantic": {"split_entity_rate": .01, "mixed_context_rate": .02},
             "gold": {"gold_context_coverage": .95, "single_chunk_answerability_rate": .8},
             "retrieval": {"recall_at_5": .7, "mrr": .5},
             "input": {"run_dir": "a", "manifest": {"source_sha256": "sha-a", "pipeline_version": "p"}},
             "baseline": {"id": "baseline-v1"}}
    for key, value_override in overrides.items():
        section, field = key.split(".")
        value[section][field] = value_override
    return value


def test_comparison_is_priority_ordered_and_has_no_aggregate_winner():
    result = compare_reports(report(), report(**{"gold.gold_context_coverage": .99, "semantic.mixed_context_rate": .04})).to_dict()
    assert result["priority_order"] == ["source_mutation_rate", "gold_context_coverage", "single_chunk_answerability", "split_entity_rate", "mixed_context_rate", "recall_at_5", "mrr"]
    assert result["winner"] is None
    assert result["trade_offs"][0]["type"] == "metric_trade_off"
    assert result["metrics"][1]["result"] == "variant"
    assert result["metrics"][4]["result"] == "baseline"


def test_failure_taxonomy_is_closed_and_cli_prints_metric_table(tmp_path):
    left, right = tmp_path / "a.json", tmp_path / "b.json"
    left.write_text(json.dumps(report()), encoding="utf-8")
    right.write_text(json.dumps(report(**{"gold.gold_context_coverage": .91})), encoding="utf-8")
    output = tmp_path / "comparison.json"
    result = subprocess.run([sys.executable, "scripts/compare_preprocessing.py", str(left), str(right), "--output", str(output)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert "metric\tbaseline\tvariant" in result.stdout
    assert json.loads(output.read_text())["winner"] is None
    assert set(FAILURE_TAXONOMY) == {"SOURCE_MUTATION", "SOURCE_TRACE_ERROR", "BROKEN_BOUNDARY", "SPLIT_ENTITY", "MIXED_CONTEXT", "TINY_CHUNK", "OVERSIZED_CHUNK", "DUPLICATION", "GOLD_CONTEXT_MISSING", "GOLD_EVIDENCE_SPLIT", "RETRIEVAL_MISS", "RANKING_ERROR"}


def test_quality_gate_uses_exact_hard_thresholds_without_composite_score():
    value = report()
    value["intrinsic"]["source"]["source_mutation_rate"] = .001
    value["intrinsic"]["source"]["source_traceability_rate"] = .998
    value["gold"]["gold_context_coverage"] = .899
    value["semantic"]["split_entity_rate"] = .051
    passed, failures = apply_quality_gate(value)
    assert passed is False
    assert failures == ("source_mutation_rate", "source_traceability_rate", "gold_context_coverage", "split_entity_rate")
