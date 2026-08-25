import pytest

from preprocessing_agent.domain import Chunk, ContentType, SourceSpan
from preprocessing_agent.eval.gold import GoldCase
from preprocessing_agent.eval.retrieval import RankedChunk
from preprocessing_agent.eval.reranker import (
    OFFLINE_RERANKER_ADAPTER,
    ContextExpansionPolicy,
    InjectedReranker,
    build_generation_handoff,
    evaluate_reranked_retrieval,
    expand_parent_context,
    run_reranker_evaluation,
)


def _candidate(chunk_id, rank, score=0.0):
    return RankedChunk(chunk_id, rank, score, {"retriever": "hybrid_rrf"})


def test_injected_reranker_consumes_top_30_and_emits_top_5_deterministically():
    seen = []

    def score(query, candidate):
        seen.append(candidate.chunk_id)
        return 1.0

    reranker = InjectedReranker(score)
    candidates = tuple(_candidate(f"c{index}", index) for index in range(1, 35))

    result = reranker.rerank("q", candidates)

    assert seen == [f"c{index}" for index in range(1, 31)]
    assert [item.chunk_id for item in result] == ["c1", "c10", "c11", "c12", "c13"]
    assert [item.rank for item in result] == [1, 2, 3, 4, 5]
    assert all(item.metadata["candidate_rank"] <= 30 for item in result)
    assert reranker.adapter_metadata["mode"] == "offline"


def test_reranked_metrics_compare_first_gold_rank_and_ndcg_at_5():
    baseline = {"q": tuple(_candidate("gold" if index == 5 else f"c{index}", index) for index in range(1, 6))}
    reranked = {"q": (_candidate("other", 1), _candidate("gold", 2), _candidate("other-2", 3))}

    report = evaluate_reranked_retrieval({"q": ("gold",)}, baseline, reranked)

    assert report.details[0]["baseline_first_gold_rank"] == 5
    assert report.details[0]["reranked_first_gold_rank"] == 2
    assert report.reranked_recall_at_5 == 1.0
    assert report.mrr == pytest.approx(0.5)
    assert report.ndcg_at_5 == pytest.approx(1 / __import__("math").log2(3))


def test_parent_context_is_bounded_and_does_not_change_retrieval_gold_ids():
    chunks = (
        Chunk("parent", "section.parent", ContentType.RULE, "Parent rule", "Parent rule", 2, (), ("Chapter",), None),
        Chunk("child", "section.parent.child", ContentType.RULE, "Child rule", "Child rule", 2, (), ("Chapter", "Child"), "section.parent"),
        Chunk("grandchild", "section.parent.child.grand", ContentType.RULE, "Grandchild", "Grandchild", 1, (), ("Chapter", "Child", "Grand"), "section.parent.child"),
    )
    ranked = (_candidate("child", 1),)

    expanded = expand_parent_context(ranked, chunks, ContextExpansionPolicy(max_parent_depth=1, max_items=2))

    assert [item.chunk_id for item in expanded.items] == ["child", "parent"]
    assert expanded.retrieval_gold_ids == ("child",)
    assert expanded.items[1].metadata["relation"] == "parent"
    assert expanded.items[1].metadata["child_chunk_id"] == "child"


def test_generation_handoff_contains_citations_and_acl_mapping_metadata():
    chunk = Chunk("child", "spell.fireball", ContentType.SPELL, "Fireball text", "Fireball text", 2,
                  (SourceSpan(3, 4, 10, 20),), ("Spells", "Fireball"), None)
    context = expand_parent_context((_candidate("child", 1),), (chunk,), ContextExpansionPolicy())

    handoff = build_generation_handoff("q", context, evaluator_to_java={"child": "java-uuid"})

    assert handoff.adapter_metadata["live_java_adapter"] is False
    assert handoff.citations[0]["chunk_id"] == "child"
    assert handoff.citations[0]["java_uuid"] == "java-uuid"
    assert handoff.citations[0]["locator"] == {"page_number": 3, "block_index": 4, "char_start": 10, "char_end": 20}
    assert handoff.context[0]["source_citation"] == "spell.fireball"
    assert OFFLINE_RERANKER_ADAPTER["mode"] == "offline"


def test_hybrid_fixture_to_reranked_context_report(tmp_path):
    chunks = (Chunk("gold", "rule.gold", ContentType.RULE, "Gold", "Gold", 1, (), ("Rules",), None),)
    cases = (GoldCase("q1", "question", gold_chunk_ids=("gold",)),)

    class Hybrid:
        def retrieve(self, query, limit=20):
            return tuple(_candidate("gold", 1) for _ in range(1))

    summary, handoff_path = run_reranker_evaluation(chunks, cases, Hybrid(), InjectedReranker(lambda q, c: 1.0), str(tmp_path))

    assert summary["candidate_limit"] == 30
    assert summary["reranked_recall_at_5"] == 1.0
    assert (tmp_path / "retrieval_reranked_details.jsonl").is_file()
    assert handoff_path.name == "generation_reranked_handoff.jsonl"
