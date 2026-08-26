import json
from pathlib import Path

from preprocessing_agent.eval.diagnostic import (
    DiagnosticClassification,
    DiagnosticTrace,
    _bbox_order_evidence,
    classify_trace,
    _parse_source_pdf,
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


def test_reading_order_uses_bbox_geometry_not_block_index():
    blocks = (
        {"block_id": "p1-b0", "page": 1, "bbox": [200, 100, 300, 120]},
        {"block_id": "p1-b1", "page": 1, "bbox": [20, 20, 100, 40]},
    )
    evidence = _bbox_order_evidence(blocks)
    assert evidence["parser_block_ids"] == ["p1-b0", "p1-b1"]
    assert evidence["bbox_expected_block_ids"] == ["p1-b1", "p1-b0"]
    assert evidence["reading_order_valid"] is False


def test_reading_order_reports_when_bbox_is_unavailable():
    evidence = _bbox_order_evidence(({"block_id": "p1-b0", "page": 1, "bbox": None},))
    assert evidence["reading_order_comparable"] is False


def test_before_mode_preserves_legacy_extractor_order(monkeypatch, tmp_path):
    raw = [{"page_number": 1, "blocks": [
        {"block_id": "right", "text": "Right", "bbox": [400, 10, 500, 30]},
        {"block_id": "left", "text": "Left", "bbox": [10, 10, 100, 30]},
        {"block_id": "right-2", "text": "Right two", "bbox": [400, 50, 500, 70]},
        {"block_id": "left-2", "text": "Left two", "bbox": [10, 50, 100, 70]},
    ]}]
    monkeypatch.setattr("preprocessing_agent.parsers.pdf._default_extractor", lambda _: raw)
    source = tmp_path / "source.pdf"
    source.write_bytes(b"fixture")
    before = _parse_source_pdf(source, "before")
    after = _parse_source_pdf(source, "after")
    assert [block.block_id for block in before.pages[0].blocks] == ["right", "left", "right-2", "left-2"]
    assert [block.block_id for block in after.pages[0].blocks] == ["left", "left-2", "right", "right-2"]
