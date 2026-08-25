import json

from preprocessing_agent.domain import Chunk, ContentType, DocumentTree, SectionNode
from preprocessing_agent.eval import OfflineRankedIdRetriever, load_exported_run, write_report
from preprocessing_agent.exporters import ArtifactExporter


def test_gold_resolution_and_offline_retriever_are_separate_report_groups(tmp_path):
    source = tmp_path / "source.txt"
    source.write_text("Fireball. Damage.", encoding="utf-8")
    run = tmp_path / "run"
    ArtifactExporter().export(
        run, source, source.read_text(),
        (Chunk("c1", "spell.fireball", ContentType.SPELL, "Fireball.", "Fireball.", 1, ()),
         Chunk("c2", "spell.fireball.damage", ContentType.SPELL, "Damage.", "Damage.", 1, ())),
        (), DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.SPELL)),
    )
    cases = tmp_path / "cases.jsonl"
    cases.write_text(json.dumps({"case_id": "q1", "question": "What?", "gold_context_keys": ["spell.fireball"]}) + "\n", encoding="utf-8")

    report_path, _ = write_report(load_exported_run(run), eval_path=cases,
                                  retriever=OfflineRankedIdRetriever({"q1": ["c2", "c1"]}))
    report = json.loads(report_path.read_text(encoding="utf-8"))
    assert report["intrinsic"]
    assert report["gold"]["gold_context_coverage"] == 1.0
    assert report["retrieval"]["recall_at_1"] == 0.0
    assert report["retrieval"]["recall_at_3"] == 1.0
    assert report["retrieval"]["mrr"] == .5


def test_cli_to_entity_writes_retrieval_score_group(tmp_path):
    source = tmp_path / "source.txt"
    source.write_text("Fireball.", encoding="utf-8")
    run = tmp_path / "run"
    ArtifactExporter().export(
        run, source, source.read_text(),
        (Chunk("c1", "spell.fireball", ContentType.SPELL, "Fireball.", "Fireball.", 1, ()),),
        (), DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.SPELL)),
    )
    cases = tmp_path / "cases.jsonl"
    cases.write_text(json.dumps({"case_id": "q1", "question": "What?", "gold_context_keys": ["spell.fireball"]}) + "\n", encoding="utf-8")
    ranked = tmp_path / "ranked.json"
    ranked.write_text(json.dumps({"q1": ["c1"]}), encoding="utf-8")

    import subprocess
    import sys
    result = subprocess.run([sys.executable, "scripts/evaluate_preprocessing.py", "--run", str(run), "--eval", str(cases), "--retrieved", str(ranked)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    report = json.loads((run / "preprocessing_eval.json").read_text(encoding="utf-8"))
    assert report["retrieval"]["recall_at_1"] == 1.0
