from preprocessing_agent.validation import ConfidenceVector, LayoutValidationService


def page(**overrides):
    value = {
        "page_number": 1, "geometry": {"width": 100, "height": 100},
        "blocks": [{"block_id": "b1", "bbox": (1, 1, 20, 10), "text": "x"}],
        "layout": {"ordered_block_ids": ["b1"], "ambiguous": False},
        "heading_associations": [], "tables": [],
        "page_classification": "text-native",
    }
    value.update(overrides)
    return value


def test_confidence_axes_are_independent_and_critical_failure_blocks():
    result = LayoutValidationService().validate(page(), {"page_number": 1, "sha256": "a" * 64})
    assert result.valid
    broken = LayoutValidationService().validate(page(layout={"ordered_block_ids": [], "ambiguous": True}), {"page_number": 1, "sha256": "a" * 64})
    assert not broken.valid
    assert broken.confidence.columns == 0
    assert broken.confidence.text == 1


def test_high_risk_requires_secondary_validator():
    result = LayoutValidationService().validate(page(page_classification="mixed"), {"page_number": 1, "sha256": "a" * 64})
    assert not result.valid
    assert any(item.code == "SECONDARY_VALIDATOR_UNAVAILABLE" for item in result.findings)


def test_confidence_vector_rejects_out_of_range_values():
    import pytest
    with pytest.raises(ValueError):
        ConfidenceVector(text=1.1)


def _low_score_profile(region_id, score):
    candidate = {
        "column_count": 2,
        "columns": [
            {"x0": 1, "y0": 1, "x1": 45, "y1": 50},
            {"x0": 55, "y0": 1, "x1": 99, "y1": 50},
        ],
        "score": score,
        "strategy": "gutter",
    }
    return {
        "region_id": region_id,
        "candidates": [candidate],
        "selected": candidate,
        "confidence": score,
        "ambiguous": False,
        "findings": [],
    }


def test_explicitly_confirmed_low_score_columns_pass_without_changing_evidence():
    profile = _low_score_profile("region-1", .791)
    result = LayoutValidationService().validate(
        page(layout={
            "ordered_block_ids": ["b1"], "ambiguous": False,
            "profiles": [profile],
            "confirmed_selections": [{"region_id": "region-1", "candidate_index": 0}],
        }),
        {"page_number": 1, "sha256": "a" * 64},
    )

    assert result.valid
    assert result.confidence.columns == .8
    assert not any(item.code == "LOW_CONFIDENCE_COLUMNS" for item in result.findings)
    assert profile["confidence"] == .791
    assert profile["candidates"][0]["score"] == .791


def test_unconfirmed_low_score_region_still_requires_review():
    confirmed = _low_score_profile("region-1", .791)
    unconfirmed = _low_score_profile("region-2", .769)
    result = LayoutValidationService().validate(
        page(layout={
            "ordered_block_ids": ["b1"], "ambiguous": False,
            "profiles": [confirmed, unconfirmed],
            "confirmed_selections": [{"region_id": "region-1", "candidate_index": 0}],
        }),
        {"page_number": 1, "sha256": "a" * 64},
    )

    assert not result.valid
    assert result.confidence.columns == .769
    assert any(item.code == "LOW_CONFIDENCE_COLUMNS" for item in result.findings)
