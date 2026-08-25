"""One-request/one-response JSON process port."""
from __future__ import annotations

import json
import sys
import re
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
            if set(request) - {"schema_version", "operation", "request_id", "source_path", "source_sha256", "policy_version", "output_dir", "artifact_root", "version_id"}:
                raise ValueError("INVALID_REQUEST")
            required = ("request_id", "source_path", "policy_version", "source_sha256", "output_dir")
            if any(not isinstance(request.get(key), str) or not request[key] for key in required) or not re.fullmatch(r"[0-9a-f]{64}", request["source_sha256"]):
                raise ValueError("INVALID_REQUEST")
            response = ExtractionApplicationService().preprocess(request)
        elif operation == "status":
            if set(request) - {"schema_version", "operation", "request_id", "version_id", "artifact_root"}:
                raise ValueError("INVALID_REQUEST")
            if not isinstance(request.get("request_id"), str) or not request["request_id"] or not isinstance(request.get("version_id"), str) or not re.fullmatch(r"[A-Za-z0-9._-]+", request["version_id"]) or not isinstance(request.get("artifact_root"), str) or not request["artifact_root"]:
                raise ValueError("INVALID_REQUEST")
            response = ExtractionApplicationService().get_status(request["version_id"], request["artifact_root"])
            response = {**response, "request_id": request["request_id"]}
        else:
            raise ValueError("INVALID_REQUEST")
        print(json.dumps(response, ensure_ascii=False, sort_keys=True))
        return 0
    except json.JSONDecodeError as exc:
        print(json.dumps({"schema_version": SUPPORTED_SCHEMA, "operation": "unknown", "request_id": "", "error": {"code": "INVALID_REQUEST", "message": str(exc), "exit_class": "request"}}, sort_keys=True))
        return 2
    except KeyboardInterrupt:
        print(json.dumps({"schema_version": SUPPORTED_SCHEMA, "operation": "unknown", "request_id": "", "error": {"code": "INTERRUPTED", "message": "request interrupted", "exit_class": "interruption"}}, sort_keys=True))
        return 4
    except Exception as exc:
        code = str(exc) if str(exc) in {"SOURCE_NOT_FOUND", "VERSION_NOT_FOUND", "SOURCE_HASH_MISMATCH", "VERSION_ID_CONFLICT", "INVALID_REQUEST", "UNSUPPORTED_SCHEMA", "NATIVE_EXTRACTION_FAILED", "VERSION_ARTIFACT_CORRUPT"} else "PROCESSING_FAILED"
        print(json.dumps({"schema_version": SUPPORTED_SCHEMA, "operation": request.get("operation", "unknown") if isinstance(request, dict) else "unknown", "request_id": request.get("request_id", "") if isinstance(request, dict) else "", "error": {"code": code, "message": str(exc), "exit_class": "request" if code in {"INVALID_REQUEST", "UNSUPPORTED_SCHEMA"} else "processing"}}, ensure_ascii=False, sort_keys=True))
        print(f"preprocessing failed: {code}: {exc}", file=sys.stderr)
        return 2 if code in {"INVALID_REQUEST", "UNSUPPORTED_SCHEMA"} else 3


if __name__ == "__main__":
    raise SystemExit(main())
