#!/usr/bin/env python3
"""Evaluate one exported preprocessing run without invoking the pipeline."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from preprocessing_agent.eval.preprocessing import EvalConfig, EvaluationInputError, load_exported_run  # noqa: E402
from preprocessing_agent.eval.report import write_report  # noqa: E402
from preprocessing_agent.eval.retrieval import OfflineRankedIdRetriever  # noqa: E402
from preprocessing_agent.eval.dense import run_dense_baseline, write_dense_blocked  # noqa: E402
from preprocessing_agent.eval.gold import load_gold_cases  # noqa: E402


class FixtureEmbeddingProvider:
    def __init__(self, values: dict[str, object]):
        self.values = values

    def embed(self, text: str):
        if text not in self.values:
            raise ValueError(f"embedding fixture has no vector for text: {text}")
        return self.values[text]


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
    parser.add_argument("--retrieved", type=Path, help="JSON object mapping query IDs to ranked evaluator chunk IDs")
    parser.add_argument("--dense", action="store_true", help="run the injected Dense baseline")
    parser.add_argument("--embedding-fixture", type=Path, help="JSON object mapping embedding_text/query to vectors")
    args = parser.parse_args()
    try:
        retriever = None
        if args.retrieved:
            retriever = OfflineRankedIdRetriever(json.loads(args.retrieved.read_text(encoding="utf-8")))
        loaded_run = load_exported_run(args.run)
        report, failures = write_report(loaded_run, args.output, _config(args.config), args.eval_jsonl, retriever)
        if args.dense:
            dense_output = args.output or args.run
            provider = None
            if args.embedding_fixture:
                provider = FixtureEmbeddingProvider(json.loads(args.embedding_fixture.read_text(encoding="utf-8")))
            cases = load_gold_cases(args.eval_jsonl) if args.eval_jsonl and args.eval_jsonl.is_file() else ()
            source_hash = str(loaded_run.manifest.get("source_sha256", ""))
            gold_hash = hashlib.sha256(args.eval_jsonl.read_bytes()).hexdigest() if args.eval_jsonl and args.eval_jsonl.is_file() else ""
            try:
                run_dense_baseline(loaded_run.chunks, cases, provider, dense_output,
                                   source_run_hash=source_hash, gold_snapshot_hash=gold_hash)
            except Exception as exc:
                write_dense_blocked(dense_output, exc, source_run_hash=source_hash, gold_snapshot_hash=gold_hash)
                print(f"Dense baseline blocked: {exc}", file=sys.stderr)
                return 2
    except EvaluationInputError as exc:
        print(f"input error: {exc}", file=sys.stderr)
        return 2
    print(json.dumps({"report": str(report), "failures": str(failures)}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
