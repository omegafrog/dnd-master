import json
import logging
from io import StringIO

from preprocessing_agent.adapters import process_cli


def test_cli_emits_dev_diagnostics_to_stderr_only_when_enabled(monkeypatch, capsys):
    class Service:
        def get_status(self, version_id, artifact_root):
            logging.getLogger("preprocessing_agent.test").info("dev_cli_probe outcome=completed")
            return {"version_id": version_id}

    request = {"schema_version": "1", "operation": "status", "request_id": "r1",
               "version_id": "v1", "artifact_root": "/tmp"}
    monkeypatch.setattr(process_cli, "ExtractionApplicationService", Service)
    root = logging.getLogger()
    previous_level = root.level
    configured_handlers = [handler for handler in root.handlers
                           if getattr(handler, "_preprocessing_dev_diagnostics", False)]
    try:
        monkeypatch.setenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "true")
        monkeypatch.setattr(process_cli.sys, "stdin", StringIO(json.dumps(request)))
        assert process_cli.main() == 0
        output = capsys.readouterr()
        assert "dev_cli_probe outcome=completed" in output.err
        assert "dev_cli_probe" not in output.out
        assert json.loads(output.out)["request_id"] == "r1"
    finally:
        for handler in root.handlers[:]:
            if getattr(handler, "_preprocessing_dev_diagnostics", False) and handler not in configured_handlers:
                root.removeHandler(handler)
                handler.close()
        root.setLevel(previous_level)

    monkeypatch.delenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED")
    monkeypatch.setattr(process_cli.sys, "stdin", StringIO(json.dumps(request)))
    assert process_cli.main() == 0
    output = capsys.readouterr()
    assert output.err == ""
    assert json.loads(output.out)["request_id"] == "r1"
