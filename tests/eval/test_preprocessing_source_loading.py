import json

from preprocessing_agent.domain import ContentType, DocumentTree, SectionNode
from preprocessing_agent.eval.preprocessing import load_exported_run
from preprocessing_agent.domain.serialization import to_dict


def test_load_exported_run_uses_injected_extractor_for_pdf_source(tmp_path):
    source = tmp_path / "source.pdf"
    source.write_bytes(b"not a UTF-8 PDF fixture")
    run = tmp_path / "run"
    run.mkdir()
    (run / "chunks.jsonl").write_text("", encoding="utf-8")
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.RULE))
    (run / "document_tree.json").write_text(json.dumps(to_dict(tree)), encoding="utf-8")
    (run / "manifest.json").write_text(json.dumps({"source": {"path": str(source)}}), encoding="utf-8")

    calls = []
    loaded = load_exported_run(run, source_extractor=lambda path: calls.append(path) or "Extracted PDF text")

    assert loaded.source_text == "Extracted PDF text"
    assert calls == [source]
