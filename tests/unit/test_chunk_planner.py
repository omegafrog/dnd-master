from preprocessing_agent.chunking import ChunkPlanner
from preprocessing_agent.domain import ContentType, ParsedBlock, ParsedDocument, ParsedPage, SectionNode, SourceSpan, DocumentTree


def test_planner_creates_semantic_candidates_before_token_splitting():
    span = SourceSpan(1, block_index=0)
    block = ParsedBlock("b1", "A rule paragraph.", span)
    document = ParsedDocument("doc", "rules.pdf", block.source_text, (ParsedPage(1, (block,), block.source_text),))
    section = SectionNode("s1", "Combat", 1, ContentType.RULE, block_ids=("b1",))
    candidates = ChunkPlanner().plan(DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), document)
    assert len(candidates) == 1
    assert candidates[0].canonical_key == "combat"
    assert candidates[0].source_text == block.source_text
    assert candidates[0].source_segments[0].source_text == block.source_text
    assert candidates[0].source_segments[0].source_span == span


def test_planner_removes_duplicated_page_prefixes_from_canonical_keys():
    span = SourceSpan(75, block_index=0)
    block = ParsedBlock("b1", "Combat rules.", span)
    document = ParsedDocument("doc", "rules.pdf", block.source_text, (ParsedPage(75, (block,), block.source_text),))
    section = SectionNode("s1", "7575 Chapter 9: Combat", 1, ContentType.RULE, block_ids=("b1",))

    candidates = ChunkPlanner().plan(
        DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), document
    )

    assert candidates[0].canonical_key == "chapter_9_combat"


def test_planner_bounds_long_canonical_keys_with_stable_semantic_suffix():
    span = SourceSpan(1, block_index=0)
    block = ParsedBlock("b1", "A rule paragraph.", span)
    title = "Chapter " + ("very long semantic heading " * 30)
    document = ParsedDocument("doc", "rules.pdf", block.source_text, (ParsedPage(1, (block,), block.source_text),))
    section = SectionNode("s1", title, 1, ContentType.RULE, block_ids=("b1",))

    candidates = ChunkPlanner().plan(
        DocumentTree("doc", SectionNode("root", "doc", 0, ContentType.NARRATIVE, children=(section,))), document
    )

    key = candidates[0].canonical_key
    assert len(key) <= 160
    assert key.startswith("chapter_very_long_semantic_heading")
    assert key.endswith("_" + __import__("hashlib").sha256(("chapter_" + "very_long_semantic_heading_" * 30).strip("_").encode()).hexdigest()[:12])
