import io
import json

from preprocessing_agent.adapters import process_cli


def test_retry_pages_accepts_layout_selections(monkeypatch, capsys):
    captured = {}

    class FakeExtractionApplicationService:
        def retry_pages(self, version_id, artifact_root, pages, *, request_id, layout_selections=None):
            captured.update(version_id=version_id, artifact_root=artifact_root, pages=pages,
                            request_id=request_id, layout_selections=layout_selections)
            return {"schema_version": "1", "operation": "retry_pages", "request_id": request_id,
                    "version_id": "candidate-retry", "status": "READY"}

    monkeypatch.setattr(process_cli, "ExtractionApplicationService", FakeExtractionApplicationService)
    monkeypatch.setattr(process_cli.sys, "stdin", io.StringIO(json.dumps({
        "schema_version": "1",
        "operation": "retry_pages",
        "request_id": "retry-request",
        "version_id": "candidate-1",
        "artifact_root": "/tmp/artifacts",
        "pages": [2],
        "layout_selections": {"2": {"region-1": 0}},
    })))

    assert process_cli.main() == 0
    assert captured["layout_selections"] == {2: {"region-1": 0}}
    assert json.loads(capsys.readouterr().out)["status"] == "READY"


def test_retry_pages_rejects_invalid_layout_selection_page_key(monkeypatch, capsys):
    monkeypatch.setattr(process_cli.sys, "stdin", io.StringIO(json.dumps({
        "schema_version": "1",
        "operation": "retry_pages",
        "request_id": "retry-request",
        "version_id": "candidate-1",
        "artifact_root": "/tmp/artifacts",
        "pages": [2],
        "layout_selections": {"page-2": {"region-1": 0}},
    })))

    assert process_cli.main() == 2
    assert json.loads(capsys.readouterr().out)["error"]["code"] == "INVALID_REQUEST"
