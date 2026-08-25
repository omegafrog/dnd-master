#!/usr/bin/env python3
"""Run preprocessing at the external CLI/artifact boundary."""
from __future__ import annotations
import argparse
import json
import sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))
from preprocessing_agent.pipeline import PreprocessingPipeline  # noqa: E402
def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("--profile", default="default")
    parser.add_argument("--config", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args(argv)
    config = args.config if args.config else {"name": args.profile}
    result = PreprocessingPipeline.from_config(config, output_dir=args.output).run(source=args.source)
    print(json.dumps({"output": str(args.output), "chunks": len(result.chunks), "valid": result.valid, "manifest": result.manifest}, ensure_ascii=False, sort_keys=True))
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
