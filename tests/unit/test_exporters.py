import json

from preprocessing_agent.chunking import ChunkAssembler, ChunkPolicy, ChunkSplitter
from preprocessing_agent.domain import Chunk, ChunkCandidate, ContentType, DocumentTree, SectionNode, SourceSegment, SourceSpan
from preprocessing_agent.exporters import ArtifactExporter
from preprocessing_agent.domain import schema_path, validate_json


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


def test_exporter_serializes_split_chunk_as_one_schema_valid_jsonl_line(tmp_path):
    text = "first block\n\nsecond block with enough words to split"
    spans = (SourceSpan(1, 0, 0, 11), SourceSpan(1, 1, 12, len(text)))
    candidate = ChunkCandidate(
        "candidate", "rules.split", ContentType.RULE, text, spans,
        source_segments=tuple(SourceSegment(part, span) for part, span in zip(text.split("\n\n"), spans)),
    )
    chunks = ChunkAssembler(ChunkSplitter(ChunkPolicy(target_tokens=3, max_tokens=4, min_tokens=1, overlap_tokens=0))).assemble([candidate])
    tree = DocumentTree("doc", SectionNode("root", "Rules", 0, ContentType.RULE))

    ArtifactExporter().export(tmp_path, "doc.pdf", text, chunks, (), tree)
    lines = (tmp_path / "chunks.jsonl").read_text(encoding="utf-8").splitlines()

    assert len(lines) == len(chunks)
    schema = json.loads(schema_path("chunk.schema.json").read_text(encoding="utf-8"))
    for line in lines:
        payload = json.loads(line)
        validate_json(payload, schema)
        assert "source_segments" not in payload
