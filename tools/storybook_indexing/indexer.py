"""Structure-preserving Storybook PDF ingestion.

Docling owns PDF layout recovery. LlamaIndex owns sentence-sized retrieval
nodes. Output stays framework-neutral JSON so the Java pgvector pipeline can
embed and persist it without a second PDF parse.
"""

from __future__ import annotations

import argparse
import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Sequence


@dataclass(frozen=True)
class MarkdownSection:
    path: list[str]
    content: str


def split_markdown_sections(markdown: str) -> list[MarkdownSection]:
    """Split on Markdown headings; never cross a recovered document section."""
    stack: list[str] = []
    content: list[str] = []
    sections: list[MarkdownSection] = []

    def flush() -> None:
        text = "\n".join(content).strip()
        if text:
            sections.append(MarkdownSection(list(stack), text))

    for line in markdown.splitlines():
        if line.startswith("#"):
            marker, separator, heading = line.partition(" ")
            level = len(marker)
            if separator and 1 <= level <= 6 and marker == "#" * level:
                flush()
                content.clear()
                stack[level - 1 :] = [heading.strip()]
                continue
        content.append(line)
    flush()
    return sections


def llama_sentence_splitter(chunk_size: int, chunk_overlap: int) -> Callable[[str, dict], Sequence[str]]:
    """Build LlamaIndex splitter lazily; CLI --help works without dependencies."""
    try:
        from llama_index.core import Document
        from llama_index.core.node_parser import SentenceSplitter
    except ImportError as error:
        raise RuntimeError(
            "llama-index-core is required. Install tools/storybook_indexing/requirements.txt"
        ) from error

    parser = SentenceSplitter(chunk_size=chunk_size, chunk_overlap=chunk_overlap)

    def split(text: str, metadata: dict) -> Sequence[str]:
        nodes = parser.get_nodes_from_documents([Document(text=text, metadata=metadata)])
        return [node.text.strip() for node in nodes if node.text.strip()]

    return split


def index_markdown(
    markdown: str,
    source: str,
    title: str,
    splitter: Callable[[str, dict], Sequence[str]] | None = None,
    chunk_size: int = 1024,
    chunk_overlap: int = 96,
) -> list[dict]:
    """Create retrieval records with separate source and retrieval text."""
    if not markdown or not markdown.strip():
        raise ValueError("markdown must not be blank")
    if not source.strip() or not title.strip():
        raise ValueError("source and title must not be blank")
    if chunk_size <= 0 or chunk_overlap < 0 or chunk_overlap >= chunk_size:
        raise ValueError("chunk_size must be positive and larger than chunk_overlap")

    split = splitter or llama_sentence_splitter(chunk_size, chunk_overlap)
    chunks: list[dict] = []
    for section in split_markdown_sections(markdown):
        metadata = {"source": source, "title": title, "section_path": section.path}
        for position, text in enumerate(split(section.content, metadata)):
            section_label = " > ".join(section.path) if section.path else "Preamble"
            contextual_content = f"Document: {title}\nSection: {section_label}\n\n{text}"
            digest = hashlib.sha256(
                f"{source}\0{section_label}\0{position}\0{text}".encode("utf-8")
            ).hexdigest()
            chunks.append(
                {
                    "chunk_id": f"storybook-{digest[:24]}",
                    "source": source,
                    "title": title,
                    "section_path": section.path,
                    "content": text,
                    "contextual_content": contextual_content,
                    "element_types": ["paragraph"],
                }
            )
    if not chunks:
        raise ValueError("Docling produced no indexable text")
    return chunks


def convert_pdf_to_markdown(pdf: Path) -> str:
    try:
        from docling.document_converter import DocumentConverter
    except ImportError as error:
        raise RuntimeError(
            "docling is required. Install tools/storybook_indexing/requirements.txt"
        ) from error
    return DocumentConverter().convert(pdf).document.export_to_markdown().strip()


def index_pdf(pdf: Path, chunk_size: int, chunk_overlap: int) -> dict:
    markdown = convert_pdf_to_markdown(pdf)
    return {
        "schema_version": "storybook-index.v1",
        "source": str(pdf),
        "title": pdf.stem,
        "markdown": markdown,
        "chunks": index_markdown(markdown, str(pdf), pdf.stem, chunk_size=chunk_size, chunk_overlap=chunk_overlap),
    }


def main() -> None:
    parser = argparse.ArgumentParser(description="Index a Storybook PDF with Docling and LlamaIndex")
    parser.add_argument("input", type=Path, help="Storybook PDF")
    parser.add_argument("--output", type=Path, required=True, help="Output JSON path")
    parser.add_argument("--chunk-size", type=int, default=1024)
    parser.add_argument("--chunk-overlap", type=int, default=96)
    args = parser.parse_args()

    if not args.input.is_file():
        parser.error(f"input does not exist or is not a file: {args.input}")
    result = index_pdf(args.input, args.chunk_size, args.chunk_overlap)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
