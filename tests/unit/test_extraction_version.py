from pathlib import Path

import pytest

from preprocessing_agent.domain.layout import BoundingBox, PageGeometry
from preprocessing_agent.pipeline.extraction_service import ExtractionVersion, PageExtraction


def test_geometry_normalizes_top_left_and_bounds_boxes() -> None:
    geometry = PageGeometry(width=612, height=792)
    assert geometry.contains(BoundingBox(0, 0, 612, 792))
    assert not geometry.contains(BoundingBox(-1, 0, 10, 10))
    with pytest.raises(ValueError):
        BoundingBox(5, 2, 1, 4)


def test_version_is_ready_only_when_every_page_is_validated() -> None:
    version = ExtractionVersion.create("v1", "doc", "policy-1", 2)
    version.record_page(PageExtraction.validated(1))
    assert version.status.value == "VALIDATING"
    version.record_page(PageExtraction.validated(2))
    version.publish()
    assert version.status.value == "READY"


def test_review_page_blocks_publication() -> None:
    version = ExtractionVersion.create("v1", "doc", "policy-1", 1)
    version.record_page(PageExtraction.needs_review(1, "INVALID_GEOMETRY"))
    with pytest.raises(ValueError, match="cannot publish"):
        version.publish()
