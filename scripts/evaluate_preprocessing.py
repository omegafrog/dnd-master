#!/usr/bin/env python3
"""Evaluate one exported preprocessing run without invoking the pipeline."""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from preprocessing_agent.eval.preprocessing import EvalConfig, EvaluationInputError, load_exported_run  # noqa: E402
from preprocessing_agent.eval.report import write_report  # noqa: E402


def _config(path: Path | None) -> EvalConfig:
    values: dict[str, object] = {}
    if path and path.is_file():
        try:
            import yaml
            values = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        except ImportError:
            for line in path.read_text(encoding="utf-8").splitlines():
                if ":" in line and not line.lstrip().startswith("#"):
                    key, value = line.split(":", 1)
                    values[key.strip()] = float(value) if "." in value else int(value)
    return EvalConfig(**{key: values[key] for key in EvalConfig.__dataclass_fields__ if key in values})


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--run", required=True, type=Path)
    parser.add_argument("--eval", dest="eval_jsonl", type=Path, help="accepted for the shared evaluator CLI; intrinsic metrics need no cases")
    parser.add_argument("--config", type=Path, default=Path(__file__).resolve().parents[1] / "configs/preprocessing_eval.yaml")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        report, failures = write_report(load_exported_run(args.run), args.output, _config(args.config))
    except EvaluationInputError as exc:
        print(f"input error: {exc}", file=sys.stderr)
        return 2
    print(json.dumps({"report": str(report), "failures": str(failures)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
