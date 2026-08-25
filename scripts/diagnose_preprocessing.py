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
    parser.add_argument("--min-broken", type=int, default=30)
    args = parser.parse_args(argv)
    try:
        path = write_diagnostic(args.run, args.source_pdf, args.output, min_broken=args.min_broken)
    except (OSError, ValueError) as exc:
        print(f"diagnostic error: {exc}", file=sys.stderr)
        return 2
    print(path)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
