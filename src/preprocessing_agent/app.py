"""CLI-facing application entrypoints."""
from pathlib import Path
from typing import Any, Mapping
from .pipeline import PipelineResult, PreprocessingPipeline
def preprocess(source: str | Path, config: Mapping[str, Any] | str | Path, output_dir: str | Path) -> PipelineResult:
    return PreprocessingPipeline.from_config(config, output_dir=output_dir).run(source=source)
