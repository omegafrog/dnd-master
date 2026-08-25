# RAG Preprocessing Agent Architecture Spec

## 1. Design Scope

| 항목 | 대상 |
|---|---|
| Product Spec | `docs/specs/product-spec.md` |
| Use Cases | UC-01 전처리, UC-02 semantic fallback, UC-03 validation/repair, UC-04 export, UC-05 evaluation |
| Domain | Document preprocessing for RAG |
| Bounded Contexts | Parsing & Provenance, Semantic Structure, Chunk Quality, Evaluation |
| Existing Services | Java `rule-knowledge-service` downstream consumer |
| External Dependencies | PDF parser, optional Agent provider, filesystem/CLI |
| Affected Data | ParsedDocument, DocumentTree, ChunkCandidate, Chunk, ValidationResult, Manifest |

### Product Spec Mapping

| Product 항목 | Architecture 요소 |
|---|---|
| 원문 보존 | `ParsedDocument`, `SourceSpan`, parser boundary |
| semantic unit chunk | `DocumentTree`, `ChunkPlanner`, `ContentTypePolicy` |
| Agent는 판단만 수행 | typed Agent ports와 deterministic application layer |
| stable/reproducible output | content hash ID, canonical key, manifest |
| 품질 검증/repair | deterministic validator와 repair engine |
| retrieval feedback loop | gold mapper, offline evaluator |

## 2. Domain Flow

```text
Operator
  → RunPreprocessing(source, profile)
  → Parser → ParsedDocumentCreated
  → StructureDetector → DocumentTreeBuilt
  → ContentClassifier → ChunkPlanner
  → ChunkCandidatesPlanned
  → Splitter/Assembler → ChunksAssembled
  → DeterministicValidator
      ├─ valid → ExportChunks
      ├─ repairable → RepairChunks → ValidateAgain
      └─ ambiguous → AgentValidator → ApplyDecisionDeterministically or ManualReview
  → Exported
```

### Commands

| Command | Actor | Target | Input | Preconditions | Result |
|---|---|---|---|---|---|
| `ParseDocument` | Pipeline | Parser | source path | source readable | ParsedDocument |
| `BuildDocumentTree` | Pipeline | Structure | parsed document | blocks ordered | DocumentTree |
| `ClassifyContent` | Pipeline | Classification | section node | text available | typed candidates |
| `PlanChunks` | Pipeline | ChunkPlanner | tree + policy | structure available | ChunkCandidate[] |
| `AssembleChunks` | Pipeline | Assembler | candidates | token counter | Chunk[] |
| `ValidateChunks` | Pipeline | Validator | chunks + tree | chunks assembled | ValidationResult |
| `RepairChunks` | Pipeline | RepairEngine | issues + chunks | operation allowed | repaired chunks |
| `ExportArtifacts` | Operator | Exporter | final result | validation complete | artifacts |
| `EvaluateRetrieval` | Evaluation Operator | Evaluator | gold + retrieved IDs | mapping available | metrics |

### Events and Policies

| Event | Policy | Result |
|---|---|---|
| `ParsedDocumentCreated` | StructureDetectionPolicy | deterministic heading detection |
| `LowConfidenceHeadingDetected` | StructureAgentFallbackPolicy | structured decision or review |
| `ChunkOverflowDetected` | OverflowPolicy | semantic split or parent-child split |
| `ValidationIssueDetected` | RepairPolicy | deterministic operation or manual review |
| `ChunksValidated` | ExportPolicy | JSONL/tree/manifest export |

## 3. DDD Architecture

### 3.1 Bounded Contexts

| Context | Responsibility | Owned Model | Owned Data |
|---|---|---|---|
| Parsing & Provenance | PDF extraction and normalization | ParsedDocument, SourceSpan | parsed artifact |
| Semantic Structure | hierarchy and content type | DocumentTree, SectionNode, ContentType | structure artifact |
| Chunk Quality | planning, assembly, validation, repair | ChunkCandidate, Chunk, ValidationResult | chunk/issue artifacts |
| Evaluation | gold mapping and retrieval metrics | GoldContext, EvaluationResult | evaluation report |

기존 Java `rule-knowledge-service`는 Python pipeline의 downstream consumer로 남긴다. Java persistence와 직접 공유하는 shared kernel은 v0.1에서 만들지 않는다.

### 3.2 Context Map

```text
PDF Source → Parsing & Provenance → Semantic Structure
           → Chunk Quality → JSONL/manifest → RAG consumer
Gold dataset ───────────────────────────────→ Evaluation
Retrieved IDs ──────────────────────────────→ Evaluation
```

| Upstream | Downstream | Relationship | Contract | Translation |
|---|---|---|---|---|
| Parsing & Provenance | Semantic Structure | Published Language | ParsedDocument | none |
| Semantic Structure | Chunk Quality | Customer/Supplier | DocumentTree/policy | none |
| Chunk Quality | Java service | ACL/file contract | JSONL + manifest | Java adapter later |
| Gold dataset | Evaluation | Published Language | gold context keys | mapper |

### 3.3 Aggregates and Domain Services

| Aggregate/Service | Responsibility | Invariants |
|---|---|---|
| ParsedDocument | ordered source representation | source text and spans immutable |
| DocumentTree | hierarchy | parent paths valid and ordered |
| ChunkSet | final chunk collection | stable IDs, valid spans, no silent duplicates |
| StructureDetector | heading decision | low confidence explicit |
| ContentClassifier | content type decision | unknown allowed; text unchanged |
| ChunkPlanner | semantic unit selection | type policy and boundary priority |
| RepairEngine | issue operation execution | allowlisted deterministic operations |

### 3.4 State Transitions

| Current | Command/Event | Next | Owner | Preconditions |
|---|---|---|---|---|
| received | ParseDocument | parsed | parser | readable source |
| parsed | BuildDocumentTree | structured | structure | ordered blocks |
| structured | ClassifyContent | classified | classifier | section text |
| classified | PlanChunks | planned | planner | policy loaded |
| planned | AssembleChunks | assembled | assembler | token counter |
| assembled | ValidateChunks | validated/issues | validator | candidate output |
| issues | RepairChunks | repaired/manual review | repair engine | issue operation |
| validated | ExportArtifacts | exported | exporter | manifest inputs |

## 4. Program Design

### 4.1 Program Structure

```text
scripts/preprocess.py
  → PreprocessingPipeline
      → ParserPort
      → StructureDetector
      → ContentClassifier
      → ChunkPlanner / Splitter / Assembler
      → DeterministicValidator / RepairEngine
      → Exporters

StructureAgentPort / ClassificationAgentPort / ValidationAgentPort
  → optional Agent adapters
  → structured decision only
```

### 4.2 Major Components

| Component | Responsibility | Must Not Do |
|---|---|---|
| `parsers.pdf` | page/block/layout extraction | semantic classification |
| `structure.detector` | deterministic heading features | source rewrite |
| `structure.tree_builder` | hierarchy construction | token splitting |
| `classification.classifier` | rule-based type candidates | text rewrite |
| `chunking.planner` | semantic candidate boundaries | final persistence |
| `chunking.splitter` | token-aware safe split | invent text |
| `chunking.assembler` | IDs, keys, embedding text, spans | sequential unstable IDs |
| `validation.deterministic` | mechanical checks | semantic rewrite |
| `validation.repair` | allowlisted operations | unapproved mutation |
| `agents.*` | structured judgment | replacement source generation |
| `exporters.*` | JSONL/tree/manifest | vector DB writes in v0.1 |
| `eval.*` | gold mapping and metrics | production chunk mutation |

### 4.3 Key Contracts

```python
class DocumentParser(Protocol):
    def parse(self, source: Path) -> ParsedDocument: ...

class StructureAgent(Protocol):
    def decide(self, input: StructureDecisionInput) -> StructureDecision: ...

class ContentClassifier(Protocol):
    def classify(self, section: SectionNode) -> ClassificationDecision: ...

class PreprocessingPipeline(Protocol):
    def run(self, source: Path) -> PipelineResult: ...
```

Agent decisions contain label, confidence, reason, and review_required. They never contain an authoritative replacement `source_text`.

### 4.4 Identity and Provenance

- `chunk_id = chk_<content_hash>`; algorithm and input are versioned in manifest.
- `canonical_key` is derived from normalized semantic path, e.g. `ch09.combat.making_an_attack.opportunity_attacks`.
- `SourceSpan` includes page/block and character or token offsets where available.
- `embedding_text` is immutable source text plus allowed metadata, never an LLM summary.
- Existing Java `locator/chapter/section` mapping is a later adapter concern.

### 4.5 Error Propagation

| Failure | Converted Result | Handling |
|---|---|---|
| PDF parser | parse failure with source context | operator review |
| low-confidence detector | structured fallback or review | bounded retry |
| malformed Agent result | agent issue | discard, retry/review |
| token overflow | split/parent-child issue | deterministic repair |
| invalid span | validation issue | do not export chunk |
| unsupported type | unknown classification | preserve text |

## 5. Technical Architecture

| Context | Package | Modules |
|---|---|---|
| Parsing & Provenance | `src/preprocessing_agent/parsers` | base, pdf, normalize |
| Semantic Structure | `.../structure`, `.../classification` | detector, tree_builder, classifier |
| Chunk Quality | `.../chunking`, `.../validation` | planner, splitter, assembler, repair |
| Agent boundary | `.../agents` | client and typed fallbacks |
| Output | `.../exporters` | jsonl, manifest |
| Evaluation | `.../eval` | gold mapper, metrics, runner |

### Dependency Rules

Allowed: application/pipeline → domain contracts and ports; adapters → ports; exporters → immutable result models; evaluation → exported contracts.

Forbidden: parser → Agent client; Agent adapter → persistence/source mutation; domain model → PDF/HTTP/Java dependency; Python pipeline → PostgreSQL/pgvector direct writes; evaluation → production mutation.

### Existing Repository Integration

`tools/storybook_indexing`과 Java `RulebookIndexingApplicationService`는 reference seam과 compatibility risk로 남기고 이 slice에서 교체하지 않는다. Python output과 Java `rulebook_vector_chunk` 사이의 명시적 adapter는 v0.1 이후 별도 plan으로 다룬다.

## 6. Test Architecture

- policy unit: domain invariant, chunk policy, validator, repair, metric
- contract: serialization과 JSON Schema
- integration: D&D fixture parser → tree → chunk → validation → export
- CLI~entity E2E: CLI → pipeline → filesystem artifacts → parsed output. v0.1에는 browser UI가 없으므로 UI~entity 경계의 CLI equivalent로 정의한다.
- reproducibility: 동일 source/config의 ID, key, manifest 결과 비교

## 7. Deferred Decisions

- Python JSONL과 Java `rulebook_vector_chunk` ingestion 계약
- UUID와 content-hash ID identity mapping
- Storybook indexer 흡수 또는 병렬 유지
- OCR/layout 확대
- embedding/reranker/vector DB 운영 최적화
