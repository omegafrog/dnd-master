from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.pipeline import PreprocessingPipeline
def test_representative_dnd_blocks_preserve_hierarchy_type_boundaries_and_spans(tmp_path):
    source = tmp_path / "dnd-fixture.pdf"
    source.write_bytes(b"fixture")
    pages = lambda _: [{"page_number": 1, "blocks": [
        {"block_id": "title", "text": "Chapter 9 Combat", "font_weight": "bold"},
        {"block_id": "rule", "text": "Making an Attack", "font_weight": "bold"},
        {"block_id": "body", "text": "Advantage and disadvantage affect attack rolls. A creature can gain advantage from circumstances."},
        {"block_id": "spell", "text": "3. Fire Bolt Spell Casting Time: 1 action. Range: 120 feet. Duration: instantaneous.", "font_weight": "bold"},
    ]}]
    result = PreprocessingPipeline.from_config(
        {"chunking": {"min_tokens": 1, "target_tokens": 20, "max_tokens": 40}},
        parser=PdfDocumentParser(extractor=pages), output_dir=tmp_path / "out",
    ).run(source=source)
    keys = {chunk.canonical_key: chunk for chunk in result.chunks}
    assert any("chapter_9_combat" in key for key in keys)
    spell = next(chunk for key, chunk in keys.items() if "fire_bolt" in key)
    assert spell.content_type.value == "spell"
    assert spell.source_spans[0].page_number == 1
    assert spell.source_spans[0].block_index == 3
    assert spell.chunk_id.startswith("chk_")
