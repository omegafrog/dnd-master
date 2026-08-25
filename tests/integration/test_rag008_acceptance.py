import hashlib
import json
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
