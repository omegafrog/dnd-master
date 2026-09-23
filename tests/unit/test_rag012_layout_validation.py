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
