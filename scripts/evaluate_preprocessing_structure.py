#!/usr/bin/env python3
"""Score human-authored PDF page structure annotations offline."""
from __future__ import annotations

import argparse
import json
import logging
import os
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from preprocessing_agent.eval.structure import StructureEvaluationError, evaluate_structure  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", required=True, type=Path, help="extraction version.json or document_structure.json")
    parser.add_argument("--annotations", required=True, type=Path, help="human annotation JSONL")
    parser.add_argument("--output", type=Path, help="write report JSON here; stdout by default")
    args = parser.parse_args()
    if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
        logging.basicConfig(level=logging.INFO, format="%(message)s")
    try:
        report = evaluate_structure(args.version, args.annotations)
    except StructureEvaluationError as exc:
        if os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}:
            logging.getLogger(__name__).error("dev_structure_eval stage=input_validation versionBytes=%d annotationBytes=%d failureType=%s recoveryAction=correct_inputs outcome=failed",
                                              args.version.stat().st_size if args.version.exists() else 0,
                                              args.annotations.stat().st_size if args.annotations.exists() else 0,
                                              type(exc).__name__)
        print(f"input error: {exc}", file=sys.stderr)
        return 2
    serialized = json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n"
    if args.output:
        args.output.write_text(serialized, encoding="utf-8")
    else:
        print(serialized, end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
