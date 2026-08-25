#!/usr/bin/env python3
"""Trace baseline validation candidates back to the source PDF."""
from __future__ import annotations

import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from preprocessing_agent.eval.diagnostic import write_diagnostic


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source-pdf", required=True)
    parser.add_argument("--run", required=True)
    parser.add_argument("--output", required=True)
    failures = parser.add_mutually_exclusive_group()
    failures.add_argument("--evaluator-failures", type=Path,
                          help="preprocessing_eval_failures.jsonl containing semantic failures")
    failures.add_argument("--eval-dir", type=Path,
                          help="directory containing preprocessing_eval_failures.jsonl")
    parser.add_argument("--min-broken", type=int, default=30)
    parser.add_argument("--expected-mixed", type=int, default=22)
    parser.add_argument("--parser-mode", choices=("before", "after"), default="after",
                        help="before reproduces pre-44a0c38c block order; after uses current parser")
    args = parser.parse_args(argv)
    evaluator_failures = args.evaluator_failures
    if args.eval_dir is not None:
        evaluator_failures = args.eval_dir / "preprocessing_eval_failures.jsonl"
    try:
        path = write_diagnostic(args.run, args.source_pdf, args.output,
                                evaluator_failures_path=evaluator_failures,
                                min_broken=args.min_broken, expected_mixed=args.expected_mixed,
                                parser_mode=args.parser_mode)
    except (OSError, ValueError) as exc:
        print(f"diagnostic error: {exc}", file=sys.stderr)
        return 2
    print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
