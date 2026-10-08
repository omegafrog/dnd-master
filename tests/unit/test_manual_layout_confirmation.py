from preprocessing_agent.validation.layout import LayoutValidationService
from preprocessing_agent.adapters import process_cli
from io import StringIO
from unittest.mock import patch
import json


def test_pdf_confirmed_column_choice_preserves_other_hard_errors():
    page = {
        "page_number": 1,
        "geometry": {"width": 100, "height": 100},
        "blocks": [{"block_id": "a", "bbox": [5, 5, 40, 20], "text": "hello"}],
        "layout": {"profiles": [{"confidence": 0.79, "selected": {"column_count": 2}}],
                   "ordered_block_ids": ["a"]},
    }
    validator = LayoutValidationService()
    assert not validator.validate(page, {"page_number": 1, "sha256": "abc"}).valid
    accepted = validator.validate(page, {"page_number": 1, "sha256": "abc"}, confirmed_columns=True)
    assert accepted.valid
    assert accepted.confidence.columns == 0.79
    broken = {**page, "blocks": [{"block_id": "a", "bbox": [5, 5, 120, 20], "text": "hello"}]}
    assert not validator.validate(broken, {"page_number": 1, "sha256": "abc"}, confirmed_columns=True).valid


def test_retry_cli_forwards_page_number_and_admin_confirmation():
    class Service:
        def retry_pages(self, version, root, pages, **kwargs):
            assert version == "ev-1" and pages == [23]
            assert kwargs["layout_selections"] == {23: {"region-1": 0}}
            assert kwargs["confirmed_by"] == "admin-1"
            return {"status": "NEEDS_REVIEW"}

    request = {"schema_version": "1", "operation": "retry_pages", "request_id": "r1",
               "version_id": "ev-1", "artifact_root": "/tmp/artifacts", "pages": [23],
               "layout_selections": {"23": {"region-1": 0}}, "confirmed_by": "admin-1"}
    with patch.object(process_cli, "ExtractionApplicationService", return_value=Service()), \
            patch.object(process_cli.sys, "stdin", StringIO(json.dumps(request))), \
            patch.object(process_cli.sys, "stdout", StringIO()) as output:
        assert process_cli.main() == 0
        assert json.loads(output.getvalue())["status"] == "NEEDS_REVIEW"
