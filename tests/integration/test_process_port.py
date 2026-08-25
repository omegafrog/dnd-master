import json
import subprocess
import sys
from pathlib import Path


def test_process_port_returns_one_json_response_and_keeps_logs_off_stdout(tmp_path: Path) -> None:
    source = tmp_path / "input.md"
    source.write_text("# Heading\nA native page", encoding="utf-8")
    output = tmp_path / "artifacts"
    request = {"schema_version": "1", "request_id": "req-1", "source_path": str(source), "output_dir": str(output)}
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
    assert (output / "chunks.jsonl").exists()


def test_invalid_request_has_stable_error_and_nonzero_exit() -> None:
    proc = subprocess.run(
        [sys.executable, "-m", "preprocessing_agent.adapters.process_cli"],
        input='{"schema_version":"unsupported"}', text=True, capture_output=True,
        env={"PYTHONPATH": "src"},
    )
    assert proc.returncode != 0
    response = json.loads(proc.stdout)
    assert response["error"]["code"] == "UNSUPPORTED_SCHEMA"
