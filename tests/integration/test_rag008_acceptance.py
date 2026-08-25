import hashlib
import json
import subprocess
import sys
from pathlib import Path

from preprocessing_agent.pipeline.extraction_service import ExtractionApplicationService


def test_request_response_schemas_load_and_declare_envelopes() -> None:
    root = Path(__file__).parents[2] / "schemas"
    request = json.loads((root / "extraction-request.schema.json").read_text())
    response = json.loads((root / "extraction-response.schema.json").read_text())
    assert request["type"] == "object" and request["additionalProperties"] is False
    assert {"preprocess", "status"} <= {branch["properties"]["operation"]["const"] for branch in request["anyOf"]}
    assert len(response["oneOf"]) == 2
    assert {"success", "error", "artifact"} <= set(response["$defs"])
    assert "error" in response["$defs"]["error"]["required"]


def test_real_jsonschema_validator_accepts_process_envelope_and_rejects_invalid_request() -> None:
    validator = __import__("pytest").importorskip("jsonschema")
    root = Path(__file__).parents[2] / "schemas"
    request_schema = json.loads((root / "extraction-request.schema.json").read_text())
    response_schema = json.loads((root / "extraction-response.schema.json").read_text())
    request = {"schema_version": "1", "operation": "status", "request_id": "r", "version_id": "v1", "artifact_root": "/tmp/artifacts"}
    validator.Draft202012Validator(request_schema).validate(request)
    error = {"schema_version": "1", "operation": "unknown", "request_id": "r", "error": {"code": "INVALID_REQUEST", "message": "bad", "exit_class": "request"}}
    validator.Draft202012Validator(response_schema).validate(error)
    invalid = dict(request, unexpected=True)
    try:
        validator.Draft202012Validator(request_schema).validate(invalid)
    except validator.ValidationError:
        pass
    else:
        raise AssertionError("unknown request fields must be rejected")


def test_invalid_geometry_native_page_is_needs_review_and_does_not_publish_chunks(tmp_path: Path) -> None:
    source = tmp_path / "input.pdf"
    source.write_bytes(b"fixture")
    output = tmp_path / "out"
    digest = hashlib.sha256(source.read_bytes()).hexdigest()

    class Native:
        def extract(self, _source):
            return [{"page_number": 1, "geometry": {"width": 100, "height": 100}, "blocks": [{"block_id": "b1", "text": "x", "bbox": (0, 0, 101, 10)}]}]

    result = ExtractionApplicationService(Native()).preprocess({"request_id": "bad", "source_path": str(source), "source_sha256": digest, "policy_version": "p1", "output_dir": str(output)})
    assert result["status"] == "NEEDS_REVIEW"
    assert "chunks" not in result["artifacts"]
    assert not (output / "current.json").exists()


def test_process_cli_invalid_geometry_stdin_stdout_path(tmp_path: Path) -> None:
    fake = tmp_path / "fake"
    fake.mkdir()
    (fake / "fitz.py").write_text("class R:\n width=100\n height=100\nclass P:\n rect=R()\n def get_text(self, *_): return [(0,0,101,10,'x')]\nclass D:\n def __enter__(self): return self\n def __exit__(self,*a): pass\n def __iter__(self): return iter([P()])\ndef open(_): return D()\n", encoding="utf-8")
    source = tmp_path / "input.pdf"
    source.write_bytes(b"fixture")
    request = {"schema_version": "1", "operation": "preprocess", "request_id": "cli-bad", "source_path": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "policy_version": "p", "output_dir": str(tmp_path / "out")}
    env = {"PYTHONPATH": f"{fake}:src"}
    proc = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(request), text=True, capture_output=True, env=env)
    payload = json.loads(proc.stdout)
    assert proc.returncode == 0 and payload["status"] == "NEEDS_REVIEW"
    assert "chunks" not in payload["artifacts"] and not (tmp_path / "out" / "current.json").exists()


def test_malformed_status_page_and_artifact_are_quarantined(tmp_path: Path) -> None:
    root = tmp_path / "out" / "versions" / "v1"
    root.mkdir(parents=True)
    (root / "response.json").write_text(json.dumps({"schema_version": "1", "version_id": "v1", "status": "READY", "pages": [None], "page_summary": {}, "artifacts": [], "manifest": {}}), encoding="utf-8")
    try:
        ExtractionApplicationService().get_status("v1", tmp_path / "out")
    except ValueError as exc:
        assert str(exc) == "VERSION_ARTIFACT_CORRUPT"
    else:
        raise AssertionError("malformed status must be quarantined")
    assert (root / "response.corrupt.json").exists()


def test_explicit_version_id_is_part_of_idempotency_contract(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("content", encoding="utf-8")
    service = ExtractionApplicationService()
    base = {"request_id": "r", "source_path": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "policy_version": "p", "output_dir": str(tmp_path / "out"), "version_id": "v1"}
    service.preprocess(base)
    try:
        service.preprocess({**base, "version_id": "v2"})
    except ValueError as exc:
        assert str(exc) == "VERSION_ID_CONFLICT"
    else:
        raise AssertionError("version mismatch must be rejected")
