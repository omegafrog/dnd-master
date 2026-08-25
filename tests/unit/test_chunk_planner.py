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
