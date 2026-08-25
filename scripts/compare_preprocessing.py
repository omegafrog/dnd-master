#!/usr/bin/env python3
"""Compare two existing preprocessing evaluation reports without scoring them."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from preprocessing_agent.eval.report import COMPARISON_PRIORITY, compare_reports, load_report, write_comparison


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("baseline", type=Path, help="run directory or preprocessing_eval.json")
    parser.add_argument("variant", type=Path, help="run directory or preprocessing_eval.json")
    parser.add_argument("--output", type=Path, default=Path("preprocessing_comparison.json"))
    args = parser.parse_args()
    try:
        left, right = load_report(args.baseline), load_report(args.variant)
        comparison = compare_reports(left, right).to_dict()
        output = write_comparison(left, right, args.output)
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"comparison error: {exc}", file=sys.stderr)
        return 2
    print("metric\tbaseline\tvariant\tdelta\tresult")
    for item in comparison["metrics"]:
        print(f"{item['metric']}\t{item['baseline']:.6f}\t{item['variant']:.6f}\t{item['delta']:+.6f}\t{item['result']}")
    print(json.dumps({"comparison": str(output), "priority_order": list(COMPARISON_PRIORITY), "winner": None}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
