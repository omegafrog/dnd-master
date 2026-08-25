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
        response = ExtractionApplicationService().preprocess(request)
        print(json.dumps(response, ensure_ascii=False, sort_keys=True))
        return 0
    except Exception as exc:
        code = str(exc) if str(exc) in {"UNSUPPORTED_SCHEMA", "SOURCE_NOT_FOUND", "VERSION_NOT_FOUND"} else "PROCESSING_FAILED"
        print(json.dumps({"schema_version": SUPPORTED_SCHEMA, "error": {"code": code, "message": str(exc)}}, ensure_ascii=False, sort_keys=True))
        print(f"preprocessing failed: {code}: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
