import json

from preprocessing_agent.domain import Chunk, ContentType, DocumentTree, SectionNode, SourceSpan
from preprocessing_agent.eval.preprocessing import load_exported_run, reconstruct_chunk_source
from preprocessing_agent.domain.serialization import to_dict
from preprocessing_agent.parsers.pdf import PdfDocumentParser


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


def test_reconstruct_chunk_source_uses_page_relative_offsets(tmp_path):
    source = tmp_path / "source.pdf"
    source.write_bytes(b"not a UTF-8 PDF fixture")
    run = tmp_path / "run"
    run.mkdir()
    (run / "chunks.jsonl").write_text("", encoding="utf-8")
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.RULE))
    (run / "document_tree.json").write_text(json.dumps(to_dict(tree)), encoding="utf-8")
    (run / "manifest.json").write_text(json.dumps({"source": {"path": str(source)}}), encoding="utf-8")

    loaded = load_exported_run(run, source_extractor=lambda _: ("First page text", "Second page text"))
    chunk = Chunk("c2", "page-two", ContentType.RULE, "Second page", "Second page", 2,
                  (SourceSpan(2, 0, 0, 11),))

    reconstructed, status = reconstruct_chunk_source(chunk, loaded.source_text, loaded.source_pages)

    assert reconstructed == "Second page"
    assert status == "OK"


def test_evaluator_uses_pdf_parser_page_text_normalization(tmp_path):
    source = tmp_path / "source.pdf"
    source.write_bytes(b"not a UTF-8 PDF fixture")
    run = tmp_path / "run"
    run.mkdir()
    (run / "chunks.jsonl").write_text("", encoding="utf-8")
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.RULE))
    (run / "document_tree.json").write_text(json.dumps(to_dict(tree)), encoding="utf-8")
    (run / "manifest.json").write_text(json.dumps({"source": {"path": str(source)}}), encoding="utf-8")

    raw_pages = (
        {"page_number": 1, "blocks": [{"text": "First block"}, {"text": "Second block"}]},
        {"page_number": 2, "blocks": [{"text": "Third block"}]},
    )
    loaded = load_exported_run(run, source_extractor=lambda _: raw_pages)
    parsed = PdfDocumentParser(lambda _: raw_pages).parse(source)

    assert loaded.source_pages == tuple(page.source_text for page in parsed.pages)
    assert loaded.source_text == parsed.source_text


def test_reconstruct_chunk_source_reports_true_character_mutation(tmp_path):
    source = tmp_path / "source.txt"
    source.write_text("Original text", encoding="utf-8")
    run = tmp_path / "run"
    run.mkdir()
    (run / "chunks.jsonl").write_text("", encoding="utf-8")
    tree = DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.RULE))
    (run / "document_tree.json").write_text(json.dumps(to_dict(tree)), encoding="utf-8")
    (run / "manifest.json").write_text(json.dumps({"source": {"path": str(source)}}), encoding="utf-8")

    loaded = load_exported_run(run)
    chunk = Chunk("mutated", "mutated", ContentType.RULE, "Origina1 text", "Origina1 text", 2,
                  (SourceSpan(1, 0, 0, 13),))

    reconstructed, status = reconstruct_chunk_source(chunk, loaded.source_text, loaded.source_pages)

    assert reconstructed == "Original text"
    assert status == "SOURCE_MUTATION"
