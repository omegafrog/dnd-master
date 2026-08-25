"""Application-facing process-port contract."""
from __future__ import annotations

from typing import Any, Mapping, Protocol


class PreprocessingApplicationPort(Protocol):
    def preprocess(self, request: Mapping[str, Any]) -> Mapping[str, Any]: ...
    def get_status(self, version_id: str) -> Mapping[str, Any]: ...
