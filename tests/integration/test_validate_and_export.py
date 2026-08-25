from preprocessing_agent.domain import Chunk, ContentType, SourceSpan, DocumentTree, SectionNode
from preprocessing_agent.exporters import ArtifactExporter
from preprocessing_agent.validation import validate_chunks


def test_invalid_span_flows_to_issue_and_not_export(tmp_path):
    chunk = Chunk("chk-bad", "rules.bad", ContentType.RULE, "Bad.", "Bad.", 1,
                  (SourceSpan(2, 0, 0, 4),), ("rules",))
    result = validate_chunks((chunk,), page_count=1, block_count=1)
    assert not result.valid
    assert any(i.issue_type == "invalid_source_span" for i in result.issues)
    ArtifactExporter().export(tmp_path, "doc.pdf", "source", (chunk,), result.issues,
                              DocumentTree("doc", SectionNode("root", "Rules", 0, ContentType.RULE)),
                              page_count=1, invalid_chunk_ids={"chk-bad"})
    assert (tmp_path / "chunks.jsonl").read_text() == ""
