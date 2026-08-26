"""Configuration loading for the preprocessing agent skeleton."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping

import yaml


@dataclass(frozen=True, slots=True)
class AgentConfig:
    """Validated top-level configuration shared by later pipeline stages."""

    name: str = "preprocessing-agent"
    pipeline_version: str = "0.1.0"
    chunking: Mapping[str, Any] = field(default_factory=dict)
    values: Mapping[str, Any] = field(default_factory=dict)


def load_config(path: str | Path) -> AgentConfig:
    """Load a YAML config file and return the stable skeleton config object.

    Unknown keys are retained in ``values`` so later tasks can add configuration
    without changing this loader's contract.
    """

    config_path = Path(path)
    with config_path.open("r", encoding="utf-8") as config_file:
        raw = yaml.safe_load(config_file) or {}

    if not isinstance(raw, dict):
        raise ValueError(f"Configuration root must be a mapping: {config_path}")

    chunking = raw.get("chunking", {})
    if not isinstance(chunking, dict):
        raise ValueError("Configuration key 'chunking' must be a mapping")

    values = dict(raw)
    values.pop("name", None)
    values.pop("pipeline_version", None)
    values.pop("chunking", None)
    return AgentConfig(
        name=_string_value(raw, "name", "preprocessing-agent"),
        pipeline_version=_string_value(raw, "pipeline_version", "0.1.0"),
        chunking=dict(chunking),
        values=values,
    )


def _string_value(values: Mapping[str, Any], key: str, default: str) -> str:
    value = values.get(key, default)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"Configuration key '{key}' must be a non-empty string")
    return value
