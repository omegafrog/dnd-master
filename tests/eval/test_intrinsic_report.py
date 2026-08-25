import json

from preprocessing_agent.domain import Chunk, ContentType, DocumentTree, SectionNode, SourceSpan, ValidationIssue
from preprocessing_agent.exporters import ArtifactExporter
from preprocessing_agent.eval import EvalConfig, load_exported_run, write_report


def test_report_records_trace_failure_and_mutation_gate(tmp_path):
    source = tmp_path / "source.txt"
    source.write_text("Original text.\nChanged text.", encoding="utf-8")
    chunks = (Chunk("1", "one", ContentType.RULE, "Original text.", "Original text.", 2, (SourceSpan(1, 0, 0, 14),)),
              Chunk("2", "two", ContentType.RULE, "Changed!", "Changed!", 1, (SourceSpan(1, 0, 15, 28),)),
              Chunk("3", "three", ContentType.RULE, "missing", "missing", 1, ()),)
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.RULE))
    ArtifactExporter().export(tmp_path / "run", source, source.read_text(), chunks, (), tree)
    report_path, failures_path = write_report(load_exported_run(tmp_path / "run"), config=EvalConfig(source_traceability_min=1.0))
    report = json.loads(report_path.read_text())
    failures = [json.loads(line) for line in failures_path.read_text().splitlines()]
    assert report["passed"] is False
    assert report["intrinsic"]["source"]["source_mutation_rate"] == 1 / 3
    assert {failure["type"] for failure in failures} == {"SOURCE_MUTATION", "SOURCE_TRACE_ERROR"}


def test_report_exports_validation_issues_from_run(tmp_path):
    source = tmp_path / "source.txt"
    source.write_text("4433——", encoding="utf-8")
    chunk = Chunk("1", "bad key", ContentType.RULE, "4433——", "4433——", 1, (SourceSpan(1, 0, 0, 6),))
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.RULE))
    ArtifactExporter().export(tmp_path / "run", source, source.read_text(), (chunk,),
                              (ValidationIssue("garbage_candidate", "noise", path="1"),), tree)

    report_path, failures_path = write_report(load_exported_run(tmp_path / "run"))
    report = json.loads(report_path.read_text())
    failures = [json.loads(line) for line in failures_path.read_text().splitlines()]

    assert report["intrinsic"]["validation"]["issue_types"] == ["garbage_candidate"]
    assert any(item["type"] == "garbage_candidate" for item in failures)
