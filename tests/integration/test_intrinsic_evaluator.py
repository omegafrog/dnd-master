import json
import subprocess
import sys

from preprocessing_agent.domain import Chunk, ContentType, DocumentTree, SectionNode, SourceSpan
from preprocessing_agent.exporters import ArtifactExporter


def test_cli_writes_two_stable_outputs(tmp_path):
    source = tmp_path / "source.txt"
    source.write_text("A complete sentence.", encoding="utf-8")
    run = tmp_path / "run"
    ArtifactExporter().export(run, source, source.read_text(),
                              (Chunk("c1", "k", ContentType.NARRATIVE, "A complete sentence.", "A complete sentence.", 3, (SourceSpan(1, 0, 0, 20),)),), (),
                              DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE)))
    evaluation = tmp_path / "cases.jsonl"
    evaluation.write_text("", encoding="utf-8")
    result = subprocess.run([sys.executable, "scripts/evaluate_preprocessing.py", "--run", str(run), "--eval", str(evaluation)], capture_output=True, text=True)
    assert result.returncode == 0, result.stderr
    assert (run / "preprocessing_eval.json").is_file()
    assert (run / "preprocessing_eval_failures.jsonl").is_file()
    assert json.loads((run / "preprocessing_eval.json").read_text())["passed"] is True
