# RAG Retrieval Evaluation Architecture Spec

## 1. Design Scope

| 항목 | 대상 |
|---|---|
| Product Spec | `docs/specs/product-spec.md` |
| Bounded Contexts | Gold Validation, Retrieval Evaluation, Generation Evaluation |
| Existing Services | Python evaluator, optional Java rule-knowledge search |
| Affected Data | `chunks.jsonl`, gold cases, ranked results, evaluation reports |

## 1.1 Product Spec Mapping

| Product 요구 | Architecture 요소 |
|---|---|
| Gold 검증 | `GoldValidationService`, `gold_validation.json` |
| Retrieval baseline | `RetrieverPort`, Dense/BM25 adapters, ranked-result contract |
| Hybrid/Reranker | `HybridRetriever`, `RerankerPort`, experiment runner |
| 실패 분석 | per-case `RetrievalFailure` taxonomy |
| Generation/Abstention | isolated `GenerationEvaluator` |

## 2. Domain Flow

Gold cases와 chunks를 로드하고, gold 검증이 성공하면 retriever adapter를 실행한다. ranked ID 계약을 검증한 뒤 Recall/MRR/Evidence Recall을 계산하고, Hybrid·Reranker 비교와 failure classification을 수행한다. retrieval 결과가 안정된 경우에만 context와 generation/abstention 평가를 실행한다. Gold 검증 실패 시 `gold_validation.json`만 작성하고 후속 평가를 차단한다.

| Command | Actor | Input | Preconditions | Result |
|---|---|---|---|---|
| ValidateGoldCases | 평가 담당자 | run dir, gold JSONL | artifact 존재 | validation report |
| EvaluateRetriever | 평가 담당자 | cases, retriever, cutoffs | gold valid | summary/details |
| CompareExperiments | 개발자 | experiment reports | same snapshot | comparison report |
| EvaluateGeneration | 평가 담당자 | ranked context, LLM adapter | retrieval 완료 | generation report |

## 3. DDD Architecture

| Bounded Context | Responsibility | Owned Model | Owned Data |
|---|---|---|---|
| Gold Validation | case·chunk·evidence 계약 검증 | `GoldCase`, `GoldValidationResult` | `gold_validation.json` |
| Retrieval Evaluation | 검색 실행, ranked contract, metrics | `RetrieverPort`, `RankedChunk`, `RetrievalMetrics` | summaries/details/failures |
| Generation Evaluation | context·answer·citation·abstention | `GenerationCase`, `GenerationResult` | generation report |

### Aggregates and Value Objects

| Type | Kind | Responsibility |
|---|---|---|
| `GoldCase` | Entity | question, answerability, gold chunks, evidence groups |
| `GoldValidationResult` | Aggregate | all case/ID/evidence invariants |
| `RetrieverExperiment` | Aggregate | immutable dataset/retriever/cutoff run |
| `RankedChunk` | Value Object | evaluator chunk ID, rank, score, metadata |
| `RetrievalMetrics` | Value Object | Recall@K, MRR, Evidence Recall, nDCG |
| `RetrievalFailure` | Entity | case, taxonomy, found/missing evidence |
| `GenerationResult` | Entity | answer, citations, abstention, labels |

### Invariants

- Gold case IDs are unique; all gold chunk IDs exist in the exported run.
- Answerable cases have valid gold/evidence; unanswerable cases have none.
- Ranked results contain known, unique evaluator chunk IDs.
- Cutoffs support 1, 3, 5, 10, and 20.
- Python evaluator IDs and Java UUIDs are compared only through explicit adapter mapping.
- Generation accepts only validated retrieval context.

## 4. Program Design

The entry CLI invokes `GoldValidationService`, then `RetrievalEvaluationService`. The retrieval service depends on `RetrieverPort`; Dense, BM25, Hybrid/RRF, and Reranker are adapters. `ReportWriter` persists gold, experiment, comparison, and failure artifacts. Adapters do not calculate metrics and evaluators do not depend on concrete vector stores.

```python
class RetrieverPort(Protocol):
    def retrieve(self, query: str, limit: int) -> Sequence[RankedChunk]: ...

class RerankerPort(Protocol):
    def rerank(self, query: str, candidates: Sequence[RankedChunk], limit: int) -> Sequence[RankedChunk]: ...
```

## 5. Technical Architecture

| Context | Existing seam | Required extension |
|---|---|---|
| Gold Validation | `src/preprocessing_agent/eval/gold.py`, `gold_mapper.py` | explicit answerable model, duplicate/missing validation, CLI |
| Retrieval Evaluation | `eval/retrieval.py`, `report.py`, `runner.py` | top-20, adapters, per-case reports |
| Artifact loading | `eval/preprocessing.py` | snapshot/hash and ID mapping |
| Java integration | `RuleEvidenceSearchPort`, pgvector repository | optional ACL adapter |
| Generation | not present | isolated post-retrieval context |

### Artifacts

```text
gold_validation.json
retrieval_<experiment>_summary.json
retrieval_<experiment>_details.jsonl
retrieval_comparison.json
retrieval_failures.jsonl
generation_<experiment>.json
```

Every experiment records source run hash, gold snapshot hash, retriever version, cutoffs, and adapter metadata.

### Dependency Rules and Decisions

- Gold Validation depends only on artifact loading and chunk schema.
- Retrieval depends on validated gold and `RetrieverPort`, not concrete vector stores.
- Hybrid/Reranker depend on ranked-result contracts, not report internals.
- Generation depends on retrieval context contracts, never database entities.
- Java live integration uses an ACL mapping UUID/locator to evaluator chunk IDs.
- First implementation uses offline/local adapter seams; live Java integration is later.
- BM25 indexes normalized `embedding_text`; Hybrid starts with Reciprocal Rank Fusion.
- Parent expansion and generation follow retrieval baseline/comparison, not the first slice.
