"""One-request/one-response JSON process port."""
from __future__ import annotations

import json
import sys
from typing import Any

from preprocessing_agent.pipeline.extraction_service import ExtractionApplicationService


SUPPORTED_SCHEMA = "1"


def main() -> int:
    try:
        request = json.loads(sys.stdin.read())
        if not isinstance(request, dict):
            raise ValueError("INVALID_REQUEST")
        if request.get("schema_version") != SUPPORTED_SCHEMA:
            raise ValueError("UNSUPPORTED_SCHEMA")
        operation = request.get("operation")
        if operation == "preprocess":
            required = ("request_id", "source_path", "policy_version", "source_sha256")
            if any(not isinstance(request.get(key), str) or not request[key] for key in required):
                raise ValueError("INVALID_REQUEST")
            response = ExtractionApplicationService().preprocess(request)
        elif operation == "status":
            if not isinstance(request.get("version_id"), str) or not isinstance(request.get("artifact_root"), str):
                raise ValueError("INVALID_REQUEST")
            response = ExtractionApplicationService().get_status(request["version_id"], request["artifact_root"])
        else:
            raise ValueError("INVALID_REQUEST")
        print(json.dumps(response, ensure_ascii=False, sort_keys=True))
        return 0
    except KeyboardInterrupt:
        print(json.dumps({"schema_version": SUPPORTED_SCHEMA, "error": {"code": "INTERRUPTED", "message": "request interrupted"}}, sort_keys=True))
        return 4
    except Exception as exc:
        code = str(exc) if str(exc) in {"SOURCE_NOT_FOUND", "VERSION_NOT_FOUND", "SOURCE_HASH_MISMATCH", "VERSION_ID_CONFLICT", "INVALID_REQUEST", "UNSUPPORTED_SCHEMA", "NATIVE_EXTRACTION_FAILED"} else "PROCESSING_FAILED"
        print(json.dumps({"schema_version": SUPPORTED_SCHEMA, "error": {"code": code, "message": str(exc)}}, ensure_ascii=False, sort_keys=True))
        print(f"preprocessing failed: {code}: {exc}", file=sys.stderr)
        return 2 if code in {"INVALID_REQUEST", "UNSUPPORTED_SCHEMA"} else 3


if __name__ == "__main__":
    raise SystemExit(main())
