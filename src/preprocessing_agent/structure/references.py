"""Collect PDF outline and printed contents as non-authoritative heading evidence."""

from __future__ import annotations

from difflib import SequenceMatcher
import json
import logging
import os
import re
import time
import unicodedata
from collections.abc import Mapping, Sequence

from preprocessing_agent.domain import ParsedDocument
from preprocessing_agent.domain import schema_path, validate_json

_LOGGER = logging.getLogger(__name__)
_CONTENTS_LINE = re.compile(r"^\s*(.*?)\s*((?:[.\u2026\ufffd\uf0b7\u2022\t ]){2,})(\d{1,4})\s*$")


def collect_document_structure_references(
    document: ParsedDocument,
    bookmarks: Sequence[Mapping[str, object]] = (),
    printed_contents: Sequence[Mapping[str, object]] = (),
) -> ParsedDocument:
    """Attach possible title-to-reference matches without resolving hierarchy."""
    started = time.monotonic()
    _log("dev_document_references stage=start documentId=%s headingCount=%d bookmarkCount=%d printedContentsEntryCount=%d outcome=started",
         document.document_id, sum(len(page.heading_associations) for page in document.pages),
         len(bookmarks), len(printed_contents))
    contents = [dict(item) for item in printed_contents]
    normalized_bookmarks = _with_parent_bookmark_ids(bookmarks)
    headings = [
        {"page_number": page.page_number, "block_id": str(_value(item, "heading_block_id", "")),
         "title": _heading_text(page, item)}
        for page in document.pages for item in page.heading_associations
    ]
    candidates = []
    for heading in headings:
        title = str(heading["title"])
        references = []
        for reference in (*normalized_bookmarks, *contents):
            similarity = _similarity(title, str(reference["title"]))
            if similarity < 0.80:
                continue
            match = {"reference_id": reference["reference_id"], "source": reference["source"],
                     "title": reference["title"], "title_similarity": round(similarity, 4),
                     "relationship_confirmed": False}
            if reference["source"] == "pdf_bookmark":
                match["target_page"] = reference["target_page"]
                match["page_distance"] = abs(int(reference["target_page"]) - int(heading["page_number"]))
            else:
                match["source_page"] = reference["source_page"]
                match["target_printed_page"] = reference["target_printed_page"]
                match["indentation"] = reference["indentation"]
            references.append(match)
        references.sort(key=lambda item: (-float(item["title_similarity"]),
                                          int(item.get("page_distance", 0))))
        candidates.append({**heading, "references": references})

    value = {"pdf_bookmarks": normalized_bookmarks, "printed_contents": contents,
             "heading_reference_candidates": candidates,
             "hierarchy_changed": False}
    validate_json(value, json.loads(schema_path("document-structure-references.schema.json").read_text(encoding="utf-8")))
    metadata = dict(document.metadata)
    metadata["document_structure_references"] = value
    result = ParsedDocument(document.document_id, document.source_path, document.source_text,
                            document.pages, metadata)
    _log("dev_document_references stage=complete documentId=%s headingCount=%d bookmarkCount=%d printedContentsEntryCount=%d candidateLinkCount=%d unmatchedHeadingCount=%d elapsedMs=%d hierarchyChanged=false outcome=success",
         document.document_id, len(headings), len(normalized_bookmarks), len(contents),
         sum(len(item["references"]) for item in candidates),
         sum(not item["references"] for item in candidates),
         int((time.monotonic() - started) * 1000))
    return result


def extract_printed_contents_entries(pages: Sequence[Mapping[str, object]]) -> list[dict[str, object]]:
    entries = []
    for page in pages:
        page_number = int(_value(page, "page_number", 0))
        block_values = _value(page, "blocks", ())
        page_entries = []
        for block_index, block in enumerate(block_values):
            for line_index, line in enumerate(_value(block, "lines", ())):
                text = str(_value(line, "text", ""))
                match = _CONTENTS_LINE.match(text)
                if match is None:
                    continue
                title = match.group(1).strip(" .\t")
                if len(title) < 2:
                    continue
                bbox = _value(line, "bbox", ())
                indentation = float(bbox[0]) if isinstance(bbox, (tuple, list)) and bbox else 0.0
                page_entries.append({"reference_id": f"contents-p{page_number}-b{block_index}-l{line_index}",
                                     "source": "printed_contents", "source_page": page_number,
                                     "title": title, "target_printed_page": int(match.group(3)),
                                     "source_text": text,
                                     "bbox": [float(value) for value in bbox] if len(bbox) == 4 else None,
                                     "indentation": indentation})
        # Repeated leader-and-page patterns distinguish a contents page from prose.
        if len(page_entries) >= 3:
            entries.extend(page_entries)
    return entries


def _with_parent_bookmark_ids(bookmarks: Sequence[Mapping[str, object]]) -> list[dict[str, object]]:
    result = []
    stack: list[tuple[int, str]] = []
    for index, item in enumerate(bookmarks):
        level = int(item.get("level", 1))
        while stack and stack[-1][0] >= level:
            stack.pop()
        reference_id = str(item.get("reference_id", f"bookmark-{index:05d}"))
        result.append({"reference_id": reference_id, "source": "pdf_bookmark", "level": level,
                       "title": str(item.get("title", "")), "target_page": int(item.get("target_page", 0)),
                       "parent_reference_id": stack[-1][1] if stack else None})
        stack.append((level, reference_id))
    return result


def _heading_text(page: object, association: object) -> str:
    explicit = _value(association, "heading_text")
    if explicit:
        return str(explicit).strip()
    block_id = str(_value(association, "heading_block_id", ""))
    block = next((item for item in _value(page, "blocks", ())
                  if str(_value(item, "block_id", "")) == block_id), None)
    if block is None:
        return ""
    text = str(_value(block, "source_text", ""))
    start = _value(association, "source_char_start")
    end = _value(association, "source_char_end")
    if type(start) is int and type(end) is int and 0 <= start <= end <= len(text):
        return text[start:end].strip()
    return text.strip()


def _similarity(left: str, right: str) -> float:
    def normalize(value: str) -> str:
        normalized = unicodedata.normalize("NFKC", value).casefold()
        return " ".join("".join(character if character.isalnum() else " " for character in normalized).split())
    a, b = normalize(left), normalize(right)
    if not a or not b:
        return 0.0
    if a == b:
        return 1.0
    return SequenceMatcher(None, a, b, autojunk=False).ratio()


def _value(item: object, name: str, default: object = None) -> object:
    return item.get(name, default) if isinstance(item, Mapping) else getattr(item, name, default)


def _diagnostics_enabled() -> bool:
    return os.getenv("ADVENTURE_RUNTIME_DIAGNOSTICS_ENABLED", "").lower() in {"1", "true", "yes", "on"}


def _log(message: str, *args: object) -> None:
    if _diagnostics_enabled():
        _LOGGER.info(message, *args)
