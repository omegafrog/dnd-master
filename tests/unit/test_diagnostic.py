import json

from preprocessing_agent.eval.diagnostic import (
    DiagnosticClassification,
    DiagnosticTrace,
    classify_trace,
)


def test_classification_uses_first_invalid_stage():
    trace = DiagnosticTrace(
        candidate_id="cand-1",
        chunk_id="chunk-1",
        issue_types=("broken_sentence",),
        source_blocks=({"page": 2, "block_id": "p2-b1", "text": "A sentence."},),
        reading_order_blocks=("p2-b1",),
        section_node={"node_id": "sec-1", "path": ["Rules"]},
        chunk_candidate={"canonical_key": "rules", "source_text": "A sentence."},
        final_chunk={"source_text": "A sentence", "source_spans": []},
        evidence={"final_text_matches_source": False, "reading_order_valid": True},
    )
    assert classify_trace(trace) is DiagnosticClassification.CHUNK_BOUNDARY_ERROR


def test_trace_serialization_is_stable_and_json_safe():
    trace = DiagnosticTrace(
        candidate_id="cand-1", chunk_id="chunk-1", issue_types=("MIXED_CONTEXT",),
        source_blocks=(), reading_order_blocks=(), section_node={},
        chunk_candidate={}, final_chunk={}, evidence={},
        classification=DiagnosticClassification.VALIDATOR_FALSE_POSITIVE,
    )
    encoded = trace.to_dict()
    assert encoded["classification"] == "VALIDATOR_FALSE_POSITIVE"
    assert json.loads(json.dumps(encoded, sort_keys=True))["chunk_id"] == "chunk-1"
