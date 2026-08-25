import json
import subprocess
import sys
import hashlib
from pathlib import Path


def test_process_port_returns_one_json_response_and_keeps_logs_off_stdout(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("# Heading\nA native page", encoding="utf-8")
    output = tmp_path / "artifacts"
    request = {"schema_version": "1", "operation": "preprocess", "request_id": "req-1", "source_path": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "policy_version": "p1", "output_dir": str(output)}
    proc = subprocess.run(
        [sys.executable, "-m", "preprocessing_agent.adapters.process_cli"],
        input=json.dumps(request), text=True, capture_output=True,
        env={"PYTHONPATH": "src"},
    )
    assert proc.returncode == 0, proc.stderr
    response = json.loads(proc.stdout)
    assert response["schema_version"] == "1"
    assert response["request_id"] == "req-1"
    assert response["status"] == "READY"
    assert response["page_summary"]["processed"] == 1
    assert response["artifacts"]["manifest_sha256"]
    assert (output / "chunks.jsonl").exists()

    status = subprocess.run(
        [sys.executable, "-m", "preprocessing_agent.adapters.process_cli"],
        input=json.dumps({"schema_version": "1", "operation": "status", "request_id": "req-status", "version_id": response["version_id"], "artifact_root": str(output)}),
        text=True, capture_output=True, env={"PYTHONPATH": "src"},
    )
    assert status.returncode == 0
    assert json.loads(status.stdout)["status"] == "READY"


def test_invalid_request_has_stable_error_and_nonzero_exit() -> None:
    proc = subprocess.run(
        [sys.executable, "-m", "preprocessing_agent.adapters.process_cli"],
        input='{"schema_version":"1","operation":"preprocess"}', text=True, capture_output=True,
        env={"PYTHONPATH": "src"},
    )
    assert proc.returncode != 0
    response = json.loads(proc.stdout)
    assert response["error"]["code"] == "INVALID_REQUEST"


def test_expected_hash_and_idempotency_are_enforced(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("content", encoding="utf-8")
    request = {"schema_version": "1", "operation": "preprocess", "request_id": "same", "source_path": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "policy_version": "p1", "output_dir": str(tmp_path / "out")}
    first = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(request), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert first.returncode == 0
    second = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(request), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert json.loads(first.stdout)["version_id"] == json.loads(second.stdout)["version_id"]
    bad = dict(request, source_sha256="0" * 64, request_id="different")
    rejected = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(bad), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert rejected.returncode != 0
    assert json.loads(rejected.stdout)["error"]["code"] == "SOURCE_HASH_MISMATCH"


def test_needs_review_quarantines_current_root_artifacts(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("content", encoding="utf-8")
    output = tmp_path / "out"
    request = {"schema_version": "1", "operation": "preprocess", "request_id": "ready", "source_path": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "policy_version": "p1", "output_dir": str(output)}
    assert subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(request), text=True, capture_output=True, env={"PYTHONPATH": "src"}).returncode == 0
    # A malformed PDF adapter failure is represented by a non-ready version and cannot expose old root chunks.
    bad = tmp_path / "bad.pdf"
    bad.write_bytes(b"not a pdf")
    blocked = dict(request, request_id="blocked", source_path=str(bad), source_sha256=hashlib.sha256(bad.read_bytes()).hexdigest())
    proc = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(blocked), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert proc.returncode == 3
    assert json.loads(proc.stdout)["error"]["code"] == "NATIVE_EXTRACTION_FAILED"
    assert not (output / "current.json").exists()


def test_schema_and_exit_codes_are_distinct(tmp_path: Path) -> None:
    unsupported = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input='{"schema_version":"9"}', text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert unsupported.returncode == 2
    assert json.loads(unsupported.stdout)["error"]["code"] == "UNSUPPORTED_SCHEMA"
    invalid = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input='{"schema_version":"1"}', text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert invalid.returncode == 2
    assert json.loads(invalid.stdout)["error"]["code"] == "INVALID_REQUEST"
    malformed = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input="{", text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert malformed.returncode == 2
    assert json.loads(malformed.stdout)["error"]["code"] == "INVALID_REQUEST"


def test_status_requires_artifact_root_and_rejects_unsafe_version_id(tmp_path: Path) -> None:
    proc = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps({"schema_version": "1", "operation": "status", "version_id": "../escape"}), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert proc.returncode == 2
    assert json.loads(proc.stdout)["error"]["code"] == "INVALID_REQUEST"


def test_status_requires_request_id_and_exposes_page_read_model(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("content", encoding="utf-8")
    output = tmp_path / "out"
    request = {"schema_version": "1", "operation": "preprocess", "request_id": "r1", "source_path": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "policy_version": "p1", "output_dir": str(output)}
    first = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(request), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    version_id = json.loads(first.stdout)["version_id"]
    status = dict(schema_version="1", operation="status", request_id="status-1", version_id=version_id, artifact_root=str(output))
    result = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(status), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    payload = json.loads(result.stdout)
    assert payload["request_id"] == "status-1"
    assert payload["pages"][0]["attempts"] == 1
    assert "findings" in payload["pages"][0]
    missing = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(dict(status, request_id=None)), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert missing.returncode == 2


def test_corrupt_request_index_is_recovered(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("content", encoding="utf-8")
    output = tmp_path / "out"
    output.mkdir()
    (output / "request-index.json").write_text("{", encoding="utf-8")
    request = {"schema_version": "1", "operation": "preprocess", "request_id": "r1", "source_path": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "policy_version": "p1", "output_dir": str(output)}
    result = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(request), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert result.returncode == 0
    assert (output / "request-index.json").exists()


def test_source_hash_must_be_lowercase_sha256(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("content", encoding="utf-8")
    request = {"schema_version": "1", "operation": "preprocess", "request_id": "r", "source_path": str(source), "source_sha256": "A" * 64, "policy_version": "p", "output_dir": str(tmp_path / "out")}
    proc = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(request), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert proc.returncode == 2
    assert json.loads(proc.stdout)["error"]["code"] == "INVALID_REQUEST"


def test_rejected_version_id_does_not_remove_previous_current(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("content", encoding="utf-8")
    output = tmp_path / "out"
    request = {"schema_version": "1", "operation": "preprocess", "request_id": "r", "source_path": str(source), "source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "policy_version": "p", "output_dir": str(output), "version_id": "v1"}
    assert subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(request), text=True, capture_output=True, env={"PYTHONPATH": "src"}).returncode == 0
    rejected = subprocess.run([sys.executable, "-m", "preprocessing_agent.adapters.process_cli"], input=json.dumps(dict(request, request_id="r2")), text=True, capture_output=True, env={"PYTHONPATH": "src"})
    assert rejected.returncode == 3
    assert (output / "current.json").exists()
