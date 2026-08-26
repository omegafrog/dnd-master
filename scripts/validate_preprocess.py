#!/usr/bin/env python3
from __future__ import annotations
import argparse
import json
from pathlib import Path
def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("artifact_dir", type=Path)
    args = parser.parse_args()
    required = ("chunks.jsonl", "issues.jsonl", "document_tree.json", "manifest.json")
    missing = [name for name in required if not (args.artifact_dir / name).is_file()]
    if missing:
        print(json.dumps({"valid": False, "missing": missing}))
        return 1
    manifest = json.loads((args.artifact_dir / "manifest.json").read_text(encoding="utf-8"))
    print(json.dumps({"valid": True, "manifest": manifest}, ensure_ascii=False, sort_keys=True))
    return 0
if __name__ == "__main__":
    raise SystemExit(main())
