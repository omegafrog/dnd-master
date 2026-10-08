from preprocessing_agent.validation.layout import LayoutValidationService
from preprocessing_agent.adapters import process_cli
from io import StringIO
from unittest.mock import patch
import json
import hashlib
import pytest

from preprocessing_agent.pipeline.extraction_service import ExtractionApplicationService
from preprocessing_agent.ports.extraction import RenderedPage


class _NativeColumns:
    def extract(self, _source):
        return [{"page_number": 1, "geometry": {"width": 100, "height": 100}, "blocks": [
            {"block_id": "a", "text": "left one", "bbox": (10, 10, 35, 20)},
            {"block_id": "b", "text": "left two", "bbox": (10, 30, 35, 40)},
            {"block_id": "c", "text": "right one", "bbox": (60, 10, 85, 20)},
        ]}]


class _NativeTwoPages(_NativeColumns):
    def extract(self, source):
        page = super().extract(source)[0]
        return [{**page, "page_number": number} for number in (1, 2)]


class _Render:
    def available(self): return True
    def render(self, _source, page_number, region=None): return RenderedPage(page_number, 100, 100, b"png")


def test_confirmed_layout_and_attempt_history_survive_full_version_promotion(tmp_path):
    source = tmp_path / "source.pdf"
    source.write_bytes(b"pdf")
    output = tmp_path / "artifacts"
    service = ExtractionApplicationService(_NativeColumns(), _Render(), None)
    first = service.preprocess({"request_id": "initial", "source_path": str(source),
        "source_sha256": hashlib.sha256(b"pdf").hexdigest(), "policy_version": "p1",
        "output_dir": str(output), "version_id": "candidate-1"})
    assert first["status"] == "NEEDS_REVIEW"
    profile = first["pages"][0]["layout_review"]["profiles"][0]
    candidate = next(index for index, value in enumerate(profile["candidates"])
                     if value["column_count"] == 2)
    assert profile["candidates"][candidate]["score"] < 0.8

    promoted = service.retry_pages("candidate-1", output, [1], request_id="retry-1",
        layout_selections={1: {profile["region_id"]: candidate}}, confirmed_by="admin-1")

    assert promoted["status"] == "READY"
    assert promoted["pages"][0]["status"] == "VALIDATED"
    assert promoted["pages"][0]["attempts"] == 2
    assert promoted["pages"][0]["attempt_history"][-1]["attempt"] == 2
    assert promoted["pages"][0]["attempt_history"][-1]["status"] == "VALIDATED"
    confirmation = promoted["pages"][0]["layout_confirmation"]
    assert confirmation["admin_id"] == "admin-1"
    assert confirmation["candidate_version"] == "candidate-1"
    assert confirmation["selections"] == {profile["region_id"]: candidate}


def test_fresh_review_replaces_stale_recovered_confirmation_before_promotion(tmp_path):
    source = tmp_path / "source.pdf"
    source.write_bytes(b"pdf")
    output = tmp_path / "artifacts"
    service = ExtractionApplicationService(_NativeColumns(), _Render(), None)
    first = service.preprocess({"request_id": "initial", "source_path": str(source),
        "source_sha256": hashlib.sha256(b"pdf").hexdigest(), "policy_version": "p1",
        "output_dir": str(output), "version_id": "candidate-1"})
    profile = first["pages"][0]["layout_review"]["profiles"][0]
    candidate = next(index for index, value in enumerate(profile["candidates"])
                     if value["column_count"] == 2)
    selection = {profile["region_id"]: candidate}

    first_promotion = service.retry_pages("candidate-1", output, [1], request_id="retry-1",
        layout_selections={1: selection}, confirmed_by="admin-1")
    replay = service.retry_pages("candidate-1", output, [1], request_id="retry-1",
        layout_selections={1: selection}, confirmed_by="admin-1")
    assert replay["version_id"] == first_promotion["version_id"]
    assert replay["pages"][0]["attempts"] == 2
    snapshot_path = output / "versions" / "candidate-1" / "retry-state.json"
    snapshot = json.loads(snapshot_path.read_text())
    # Reproduce a validated read model paired with an older recovered payload.
    snapshot["recovered_pages"]["1"]["layout_confirmation"] = None
    snapshot_path.write_text(json.dumps(snapshot))

    promoted = service.retry_pages("candidate-1", output, [1], request_id="retry-2",
        layout_selections={1: selection}, confirmed_by="admin-2")

    assert promoted["status"] == "READY"
    assert promoted["pages"][0]["attempts"] == 3
    assert promoted["pages"][0]["layout_confirmation"] == {
        "admin_id": "admin-2", "candidate_version": "candidate-1", "selections": selection}


def test_unselected_review_state_survives_final_page_promotion(tmp_path):
    source = tmp_path / "source.pdf"
    source.write_bytes(b"pdf")
    output = tmp_path / "artifacts"
    service = ExtractionApplicationService(_NativeTwoPages(), _Render(), None)
    first = service.preprocess({"request_id": "initial", "source_path": str(source),
        "source_sha256": hashlib.sha256(b"pdf").hexdigest(), "policy_version": "p1",
        "output_dir": str(output), "version_id": "candidate-1"})
    profile = first["pages"][0]["layout_review"]["profiles"][0]
    candidate = next(index for index, value in enumerate(profile["candidates"])
                     if value["column_count"] == 2)
    selection = {profile["region_id"]: candidate}
    service.retry_pages("candidate-1", output, [1], request_id="retry-1",
        layout_selections={1: selection}, confirmed_by="admin-1")

    # Match the persisted state produced by the prior promotion path: the
    # page read model has review metadata, while its recovery payload does not.
    snapshot_path = output / "versions" / "candidate-1" / "retry-state.json"
    snapshot = json.loads(snapshot_path.read_text())
    snapshot["recovered_pages"]["1"].pop("layout_confirmation", None)
    snapshot["recovered_pages"]["1"].pop("_retry_attempts", None)
    snapshot["recovered_pages"]["1"].pop("_retry_attempt_history", None)
    snapshot_path.write_text(json.dumps(snapshot))

    before = service.get_status("candidate-1", output)["pages"][0]
    promoted = service.retry_pages("candidate-1", output, [2], request_id="retry-2",
        layout_selections={2: selection}, confirmed_by="admin-2")

    page = promoted["pages"][0]
    assert page["status"] == before["status"] == "VALIDATED"
    assert page["attempts"] == before["attempts"] == 2
    assert page["attempt_history"] == before["attempt_history"]
    assert page["layout_confirmation"] == before["layout_confirmation"]
    assert promoted["pages"][1]["status"] == "VALIDATED"


def test_failed_fresh_review_invalidates_validated_page_and_recovered_result(tmp_path):
    source = tmp_path / "source.pdf"
    source.write_bytes(b"pdf")
    output = tmp_path / "artifacts"
    native = _NativeColumns()
    service = ExtractionApplicationService(native, _Render(), None)
    first = service.preprocess({"request_id": "initial", "source_path": str(source),
        "source_sha256": hashlib.sha256(b"pdf").hexdigest(), "policy_version": "p1",
        "output_dir": str(output), "version_id": "candidate-1"})
    profile = first["pages"][0]["layout_review"]["profiles"][0]
    candidate = next(index for index, value in enumerate(profile["candidates"])
                     if value["column_count"] == 2)
    first_retry = service.retry_pages("candidate-1", output, [1], request_id="retry-1",
        layout_selections={1: {profile["region_id"]: candidate}}, confirmed_by="admin-1")
    assert first_retry["status"] == "READY"

    native.extract = lambda _source: [{"page_number": 1, "geometry": {"width": 100, "height": 100},
        "blocks": [], "capability_error": "SOURCE_RECHECK_FAILED"}]
    failed = service.retry_pages("candidate-1", output, [1], request_id="retry-2",
        layout_selections={1: {profile["region_id"]: candidate}}, confirmed_by="admin-2")

    assert failed["status"] == "NEEDS_REVIEW"
    assert failed["pages"][0]["status"] == "NEEDS_REVIEW"
    assert failed["pages"][0]["attempt_history"][-1]["status"] == "NEEDS_REVIEW"
    assert failed["pages"][0].get("layout_confirmation") is None
    snapshot = json.loads((output / "versions" / "candidate-1" / "retry-state.json").read_text())
    assert "1" not in snapshot["recovered_pages"]


def test_retry_rejects_candidate_index_outside_recomputed_profile(tmp_path):
    source = tmp_path / "source.pdf"
    source.write_bytes(b"pdf")
    output = tmp_path / "artifacts"
    service = ExtractionApplicationService(_NativeColumns(), _Render(), None)
    first = service.preprocess({"request_id": "initial", "source_path": str(source),
        "source_sha256": hashlib.sha256(b"pdf").hexdigest(), "policy_version": "p1",
        "output_dir": str(output), "version_id": "candidate-1"})
    profile = first["pages"][0]["layout_review"]["profiles"][0]

    with pytest.raises(ValueError, match="INVALID_LAYOUT_CANDIDATE"):
        service.retry_pages("candidate-1", output, [1], request_id="retry-invalid-index",
            layout_selections={1: {profile["region_id"]: len(profile["candidates"])}}, confirmed_by="admin-1")
    assert not (output / "versions" / "candidate-1" / "retry-state.json").exists()


def test_pdf_confirmed_column_choice_preserves_other_hard_errors():
    page = {
        "page_number": 1,
        "geometry": {"width": 100, "height": 100},
        "blocks": [{"block_id": "a", "bbox": [5, 5, 40, 20], "text": "hello"}],
        "layout": {"profiles": [{"confidence": 0.769, "selected": {"column_count": 2}}],
                   "ordered_block_ids": ["a"]},
    }
    validator = LayoutValidationService()
    assert not validator.validate(page, {"page_number": 1, "sha256": "abc"}).valid
    accepted = validator.validate(page, {"page_number": 1, "sha256": "abc"}, confirmed_columns=True)
    assert accepted.valid
    assert accepted.confidence.columns == 0.769
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
