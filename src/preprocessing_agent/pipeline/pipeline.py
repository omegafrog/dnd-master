from __future__ import annotations
import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Mapping
from preprocessing_agent.chunking import ChunkAssembler, ChunkPlanner, ChunkPolicy, ChunkSplitter
from preprocessing_agent.classification import DeterministicContentClassifier
from preprocessing_agent.config import AgentConfig, load_config
from preprocessing_agent.domain import Chunk, ContentType, DocumentTree, ParsedDocument, SectionNode, ValidationIssue, ValidationResult
from preprocessing_agent.exporters import ArtifactExporter
from preprocessing_agent.parsers.pdf import PdfDocumentParser
from preprocessing_agent.validation import ValidationPolicy, validate_chunks
from preprocessing_agent.parsers.normalize import normalized_key
from preprocessing_agent.structure import DocumentTreeBuilder
@dataclass(frozen=True, slots=True)
class PipelineResult:
    source: Path
    document: ParsedDocument
    tree: DocumentTree
    chunks: tuple[Chunk, ...]
    validation: ValidationResult
    issues: tuple[ValidationIssue, ...]
    manifest: Mapping[str, Any]
    @property
    def valid(self) -> bool:
        return self.validation.valid
class _TextDocumentParser:
    """Small deterministic fixture adapter; production PDFs use PdfDocumentParser."""
    def parse(self, source: Path) -> ParsedDocument:
        from preprocessing_agent.domain import ParsedBlock, ParsedPage, SourceSpan
        raw = source.read_text(encoding="utf-8")
        blocks = []
        for index, line in enumerate(raw.splitlines()):
            text = line.strip()
            if text:
                blocks.append(ParsedBlock(f"p1-b{index}", text, SourceSpan(1, index, 0, len(text)), font_weight="bold" if text.startswith("#") else None))
        page = ParsedPage(1, tuple(blocks), raw)
        document_id = hashlib.sha256(raw.encode("utf-8")).hexdigest()[:16]
        return ParsedDocument(document_id, str(source), raw, (page,), {"format": source.suffix.lstrip(".") or "text"})
class PreprocessingPipeline:
    def __init__(self, config: AgentConfig, *, parser: Any | None = None, output_dir: str | Path | None = None) -> None:
        self.config = config
        values = dict(config.chunking)
        self.policy = ChunkPolicy(
            target_tokens=int(values.get("target_tokens", 350)), max_tokens=int(values.get("max_tokens", 500)),
            min_tokens=int(values.get("min_tokens", 100)), overlap_tokens=int(values.get("overlap_tokens", 30)),
        )
        self.validation_policy = ValidationPolicy(self.policy.min_tokens, self.policy.max_tokens)
        self.parser = parser or PdfDocumentParser()
        self.output_dir = Path(output_dir or config.values.get("output_dir", "./data/processed"))
    @classmethod
    def from_config(cls, config: AgentConfig | str | Path | Mapping[str, Any], *, parser: Any | None = None,
                    output_dir: str | Path | None = None) -> "PreprocessingPipeline":
        if isinstance(config, (str, Path)):
            config = load_config(config)
        elif isinstance(config, Mapping):
            chunking = config.get("chunking", {})
            if not isinstance(chunking, Mapping):
                raise ValueError("Configuration key 'chunking' must be a mapping")
            config = AgentConfig(str(config.get("name", "preprocessing-agent")), str(config.get("pipeline_version", "0.1.0")), dict(chunking),
                                 {key: value for key, value in config.items() if key not in {"name", "pipeline_version", "chunking"}})
        return cls(config, parser=parser, output_dir=output_dir)
    def run(self, *, source: str | Path) -> PipelineResult:
        source_path = Path(source)
        parser = self.parser
        if isinstance(parser, PdfDocumentParser) and source_path.suffix.lower() not in {".pdf"}:
            parser = _TextDocumentParser()
        document = parser.parse(source_path)
        tree = self._classify_tree(DocumentTreeBuilder().build(document), document)
        candidates = ChunkPlanner().plan(tree, document)
        chunks = ChunkAssembler(ChunkSplitter(self.policy)).assemble(candidates)
        validation = validate_chunks(chunks, document=document, policy=self.validation_policy)
        issues = validation.issues
        manifest = ArtifactExporter().export(
            self.output_dir, source_path, document.source_text, chunks, issues, tree,
            page_count=len(document.pages), profile=self.config.name, policy=self.policy,
            pipeline_version=self.config.pipeline_version,
        )
        return PipelineResult(source_path, document, tree, chunks, validation, issues, manifest)
    def _classify_tree(self, tree: DocumentTree, document: ParsedDocument) -> DocumentTree:
        blocks = {block.block_id: block for page in document.pages for block in page.blocks}
        classifier = DeterministicContentClassifier()
        def visit(node: SectionNode) -> SectionNode:
            text = "\n".join(blocks[item].source_text for item in node.block_ids if item in blocks)
            decision = classifier.classify(node, text)
            children = tuple(visit(child) for child in node.children)
            return SectionNode(node.node_id, node.title, node.level, decision.label, node.source_spans, node.block_ids, children)
        return DocumentTree(tree.document_id, visit(tree.root))
