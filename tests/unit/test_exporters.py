import json

from preprocessing_agent.domain import Chunk, ContentType, DocumentTree, SectionNode, SourceSpan
from preprocessing_agent.exporters import ArtifactExporter


def test_exporter_writes_four_artifacts_and_excludes_invalid_spans(tmp_path):
    good = Chunk("chk-good", "rules.good", ContentType.RULE, "Good.", "Good.", 1,
                 (SourceSpan(1, 0, 0, 5),), ("rules",))
    bad = Chunk("chk-bad", "rules.bad", ContentType.RULE, "Bad.", "Bad.", 1,
                (SourceSpan(4, 0, 0, 4),), ("rules",))
    tree = DocumentTree("doc", SectionNode("root", "Rules", 0, ContentType.RULE))
    manifest = ArtifactExporter().export(tmp_path, "doc.pdf", "source", (good, bad), (), tree,
                                         page_count=1, invalid_chunk_ids={"chk-bad"})
    assert {p.name for p in tmp_path.iterdir()} == {
        "chunks.jsonl", "issues.jsonl", "document_tree.json", "manifest.json"
    }
    assert len((tmp_path / "chunks.jsonl").read_text().splitlines()) == 1
    assert manifest["statistics"]["chunks"]["exported"] == 1
    json.loads((tmp_path / "manifest.json").read_text())
