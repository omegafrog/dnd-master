#!/usr/bin/env python3
"""Compare completed Dense, BM25, and injected Hybrid retrieval artifacts."""

from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from preprocessing_agent.eval.hybrid import compare_retrieval_experiments
from preprocessing_agent.eval.retrieval import DEFAULT_CUTOFFS, RetrievalInputError


def _load(path: Path, name: str) -> dict[str, object]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise RetrievalInputError(f"{name} experiment must be an object")
    value.setdefault("experiment", name)
    return value


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("dense", type=Path)
    parser.add_argument("bm25", type=Path)
    parser.add_argument("hybrid", type=Path)
    parser.add_argument("--output", type=Path, default=Path("retrieval_comparison.json"))
    args = parser.parse_args()
    loaded: dict[str, dict[str, object]] = {}
    errors: dict[str, str] = {}
    for name, path in (("dense", args.dense), ("bm25", args.bm25), ("hybrid", args.hybrid)):
        try:
            loaded[name] = _load(path, name)
        except (OSError, ValueError, json.JSONDecodeError, RetrievalInputError) as exc:
            errors[name] = str(exc)
    if errors:
        output = {"status": "partial", "experiments": loaded, "errors": errors,
                  "cutoffs": list(DEFAULT_CUTOFFS), "winner": None}
    else:
        try:
            output = compare_retrieval_experiments(loaded["dense"], loaded["bm25"], loaded["hybrid"])
            output["status"] = "completed"
        except RetrievalInputError as exc:
            print(f"comparison error: {exc}", file=sys.stderr)
            return 2
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(output, ensure_ascii=False, sort_keys=True, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"comparison": str(args.output), "status": output["status"], "errors": errors}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
