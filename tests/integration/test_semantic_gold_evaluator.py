import json
import subprocess
import sys

from preprocessing_agent.domain import Chunk, ContentType, DocumentTree, SectionNode
from preprocessing_agent.exporters import ArtifactExporter


def test_cli_enriches_intrinsic_report_with_semantic_and_gold(tmp_path):
    source = tmp_path / "source.txt"
    source.write_text("Fireball. Damage.", encoding="utf-8")
    run = tmp_path / "run"
    ArtifactExporter().export(run, source, source.read_text(),
        (Chunk("c1", "spell.fireball", ContentType.SPELL, "Fireball.", "Fireball.", 1, ()),), (),
        DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.SPELL)))
    evaluation = tmp_path / "cases.jsonl"
    evaluation.write_text(json.dumps({"case_id": "q1", "question": "What?", "gold_context_keys": ["spell.fireball"],
                                      "required_evidence": [{"group_id": "identity", "keys": ["spell.fireball"]}]}) + "\n")
    result = subprocess.run([sys.executable, "scripts/evaluate_preprocessing.py", "--run", str(run), "--eval", str(evaluation)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    report = json.loads((run / "preprocessing_eval.json").read_text())
    assert report["semantic"]["mixed_context_rate"] == 0.0
    assert report["gold"]["gold_context_coverage"] == 1.0
    assert report["gold"]["single_chunk_answerability_rate"] == 1.0
    assert report["gold"]["evidence_completeness"] == 1.0

