#!/usr/bin/env python3
"""Validate a gold JSONL snapshot against an exported chunks artifact."""
from __future__ import annotations

import argparse
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from preprocessing_agent.eval.gold import load_gold_cases, validate_gold_cases, write_gold_validation  # noqa: E402
from preprocessing_agent.eval.preprocessing import EvaluationInputError, load_exported_run  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True, type=Path)
    parser.add_argument("--gold", required=True, type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--expected-case-id", action="append", dest="expected_case_ids")
    parser.add_argument("--expected-count", type=int, default=50)
    args = parser.parse_args()
    try:
        run = load_exported_run(args.run)
        cases = load_gold_cases(args.gold)
        result = validate_gold_cases(cases, run.chunks, expected_case_ids=args.expected_case_ids,
                                     expected_count=None if args.expected_case_ids else args.expected_count)
    except (EvaluationInputError, OSError, ValueError) as exc:
        print(f"input error: {exc}", file=sys.stderr)
        return 2
    output = args.output or args.run / "gold_validation.json"
    write_gold_validation(result, output)
    if not result.valid:
        for issue in result.issues:
            print(f"{issue['type']}: {issue.get('case_id', '')} {issue.get('details', {})}", file=sys.stderr)
        return 1
    print(f"validated {result.metrics['case_count']} gold cases: {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
