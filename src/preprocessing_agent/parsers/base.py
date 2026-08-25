"""Ports shared by source parsers."""

from pathlib import Path
from typing import Protocol

from preprocessing_agent.domain import ParsedDocument


class ParserError(RuntimeError):
    """A source could not be parsed while retaining its source context."""


class DocumentParser(Protocol):
    def parse(self, source: Path) -> ParsedDocument: ...
