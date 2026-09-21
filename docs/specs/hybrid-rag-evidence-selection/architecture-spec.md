# Architecture Spec

# 1. Design Scope
## 1.1 Target
| 항목 | 대상 |
|---|---|
| Product Spec | `docs/specs/hybrid-rag-evidence-selection/product-spec.md` |
| Use Cases | UC-HR-001, UC-HR-002, UC-HR-003 |
| Domain | 세션 문서 범위 복합 근거 검색·선택 |
| Bounded Contexts | Document Knowledge, Scenario Preparation, Adventure Runtime, AI Game Master |
| Existing Services | rule-knowledge-service, adventure-service, ai-game-master-service |
| External Dependencies | 기존 모델 제공자·내부 인증 |
| Affected Data | published chunk 길이·term frequency, 벡터/BM25 색인 |
## 1.2 Product Spec Mapping
| Product Spec | Architecture |
|---|---|
| UC-HR-001 | `EvidenceAcquisitionApplicationService` |
| BR-HR-001~004 | BM25/RRF 검색·색인 원자성 |
| BR-HR-005~007 | 재정렬·Judge·고정 선택 ID |
| UC-HR-002 | adventure-service 재시도 소유권 |
| BR-HR-009~016 | 정책·보안·평가 계약 |

# 2. Domain Flow
## 2.1 Event Storming Flow
`요청 → 정책이 문서 유형 결정 → Dense/BM25 병렬 → RRF → 재정렬 → Judge → 충분 결과 또는 missing 추가 검색(최대2회) → 정책별 부족 결과`.
## 2.2 Commands
| Command | Actor | Target | Input | Preconditions | Result |
|---|---|---|---|---|---|
| 근거 확보 | AI Game Master/기존 흐름 | adventure-service | 세션·질의·맥락 | 인증·scope | 정책 결과 |
| 후보 검색 | adventure-service | Document Knowledge | search request | 허가 문서 | ≤60 후보 |
| 재정렬/Judge | adventure-service | AI Game Master | 후보·정책 | ID 검증 가능 | 순위/판정 |
## 2.3 Domain Events
해당 없음 — 요청-지역 처리이며 새 장기 이벤트가 없다.
## 2.4 Policies
| Policy | Trigger | Decision | Command | Owner |
|---|---|---|---|---|
| `EvidenceSufficiencyPolicy` | 후보 | 네 작업별 충분성·부족 결과 | 추가 검색/최종화 | Scenario Preparation/Adventure Runtime 소비 경계의 공통 capability |
## 2.5 Read Models
| Read Model | Consumer | Source | Fields | Owner |
|---|---|---|---|---|
| 검색 후보 DTO | orchestration/AI | 검색 | ID·문서·위치·출처·rank/score | Document Knowledge |
## 2.6 External Interactions
| System | Trigger | Input | Output | Failure |
|---|---|---|---|---|
| 기존 모델 제공자 | rerank/Judge | 후보·맥락 | JSON DTO | 1회 재시도 후 전체 오류 |
## 2.7 Hotspots
| Hotspot | Options | Decision |
|---|---|---|
| 검색기 실패 | 부분/전체 | 전체 오류 |
| 최소성 | subset 재호출/단일 Judge | 단일 Judge |
| 경계 | 신규/기존 | 기존 capability |

# 3. DDD Architecture
## 3.1 Bounded Contexts
| Context | Responsibility | Language | Model | Data |
|---|---|---|---|---|
| Document Knowledge | Dense/BM25·RRF·provenance | 후보·청크·위치 | 검색 후보 | published chunk/index |
| Scenario Preparation | 시나리오 준비 소비·정책 제공 | 준비·원문 위치 | 기존 흐름 | 기존 데이터 |
| Adventure Runtime | 행동 판정·시작 장면·규칙 안내 소비·정책 제공 | 판정·근거 | 기존 흐름 | 기존 데이터 |
| AI Game Master | stateless 재정렬/Judge | 관련도·충분성 | 모델 DTO | 없음 |
## 3.1.1 Boundary Decisions
| Capability | Owner | Candidate | Chosen | Why Not Weaker? | Why Not Stronger? |
|---|---|---|---|---|---|
| 복합 검색 | Document Knowledge | context/service/package | package capability | 기존 데이터 소유와 일치 | 배포·계약 비용 |
| 조정 | adventure-service application layer; business outcome owned by calling context policy | context/package | shared application package capability | 소비 흐름 공통 | 독립 상태 없음 |
| AI 판단 | AI Game Master | service/context | stateless service | provider seam 필요 | 저장·lifecycle 없음 |
## 3.2 Context Map
`Scenario Preparation/Adventure Runtime → shared acquisition capability → /internal/v1/evidence-candidates/search → Document Knowledge`; capability → rerank/Judge → AI Game Master (Published Language, DTO 검증).
## 3.3 Aggregates / 3.4 Entities
해당 없음 — 새 aggregate/entity/domain event/message/repository를 adventure에 만들지 않고 요청 결과는 불변 DTO다.
## 3.4.1 Class Diagram
[Document Knowledge](diagrams/architecture/document-knowledge.class.svg), [근거 선택](diagrams/architecture/evidence-selection.class.svg)
## 3.5 Value Objects
| Type | Values | Validation |
|---|---|---|
| `EvidenceCandidate` | stable ID, 문서/추출/type, locator/provenance, excerpt, ranks | authorized scope·유일 ID |
| `SufficiencyDecision` | sufficient, selected IDs, reasons, missing | subset·≤30·이유 완전성 |
## 3.6 Domain Services
`EvidenceAcquisitionApplicationService`: 검색·재시도·병합·재판단·최종화. `EvidenceSufficiencyPolicy`: 네 정책의 맥락·instruction·부족 mapping.
## 3.7 Business Rule Ownership
| Rule | Owner/Point |
|---|---|
| Dense/BM25 ≤30, RRF k=60 | Document Knowledge 검색 |
| pool 60/120/180, pinned IDs | adventure orchestration |
| Judge subset·유일·이유·missing 검증 | adventure validator |
| 양쪽 색인 후 INDEXED | publication transaction |
## 3.8 Aggregate State Transitions / 3.8.1 State Diagram
해당 없음 — 장기 aggregate 상태가 없고 요청 흐름은 Product 액티비티에 표현된다. 색인 INDEXING→INDEXED/FAILED는 기존 처리 상태다.
## 3.9 Repository Boundaries
Document Knowledge repository가 벡터·길이·term frequency를 publication 단일 transaction으로 소유한다.

# 4. Program Design
## 4.1 Program Structure
`Controller → EvidenceAcquisitionApplicationService → {UnifiedCandidateSearchPort, EvidenceRerankerPort, EvidenceSufficiencyJudgePort}`; adapters만 DB/provider에 의존한다.
## 4.2 Major Components and Responsibilities
| Component | Responsibility | Must Not Do |
|---|---|---|
| `EvidenceAcquisitionApplicationService` | orchestration·재시도·최종화 | prompt 생성/저장 |
| Document Knowledge search | 검색·RRF·출처 | 정책 선택 |
| AI reranker/Judge | stateless 제안 | 저장·최종확정 |
## 4.3 Application Flow
`정책/scope 검증 → search → merge/RRF → rerank → Judge → 출력 검증 → 반환 또는 missing 재검색 최대2회`.
## 4.4 Component Call Contracts
| Order | Caller | Callee | Operation | Input | Output | Failure |
|---:|---|---|---|---|---|---|
|1|orchestration|search port|`search`|unified request|≤60 후보|retry/전체 오류|
|2|orchestration|reranker|`rerank`|≤60/120/180 후보|ID≤30|retry/전체 오류|
|3|orchestration|judge|`judge`|후보≤30·pinned|decision|retry/전체 오류|
## 4.5 Major Types
`EvidenceSearchRequest`, `EvidenceCandidate`, `RerankResult`, `SufficiencyDecision`는 불변 DTO; 네 `EvidenceSufficiencyPolicy` 구현은 무상태 strategy다.
## 4.6 Type Design
`EvidenceSearchRequest`: ownerId/sessionId UUID, scenarioPackageId UUID, stageKey String, actionIntent/SearchIntent, scope(documentId UUID, extractionVersion long, DocumentType), activeLocators, query, denseLimit/bm25Limit(각 1..30). DB/provider 의존 금지.
## 4.7 Interfaces and Function Signatures
```java
interface UnifiedCandidateSearchPort { EvidenceSearchResult search(EvidenceSearchRequest request); }
interface EvidenceRerankerPort { RerankResult rerank(EvidenceRerankRequest request); }
interface EvidenceSufficiencyJudgePort { SufficiencyDecision judge(EvidenceSufficiencyRequest request); }
interface EvidenceSufficiencyPolicy {
  PolicyId policyId();
  Set<DocumentType> selectDocumentTypes(PolicyInput input);
  EvidenceTaskContext buildTaskContext(PolicyInput input);
  SufficiencyInstruction buildInstruction(EvidenceTaskContext context);
  FinalInsufficiency mapFinalInsufficiency(SufficiencyDecision d, PolicyInput input);
}
```
`EvidenceSearchRequest(ownerId: UUID, sessionId: UUID, scenarioPackageId: UUID, stageKey: String, actionIntent: SearchIntent, scope: List<AuthorizedDocumentScope>, activeLocators: List<Locator>, query: String, denseLimit: int, bm25Limit: int)`이며 scope는 `(documentId: UUID, extractionVersion: long, documentType: DocumentType)`이고 limits는 각 1..30, authorized published extraction만 허용한다. `EvidenceRerankRequest`는 query/task context/candidates≤180, `EvidenceSufficiencyRequest`는 policyId/task context/candidates≤30/pinned IDs를 가진다. Judge는 `sufficient`, `selectedEvidenceIds`, ID별 `selectionReasons`, `missing`만 반환한다. `necessity`/`useThroughRank` 금지; true는 ID≥1, false는 nonblank missing.
## 4.8 Error Propagation
transient(timeout/connection/429/5xx/DB/malformed)는 orchestration이 1회 재시도하고 소진 시 전체 오류. auth/authorization/bad request/scope 위반은 즉시 오류.
## 4.9 State Transition Implementation
publication transaction commit 시 INDEXED, 실패 시 rollback 및 기존 FAILED 처리; 새 event 없음.
## 4.10 Dependency Rules
adventure는 ports만 사용하고 DB 직접 접근 금지. AI는 orchestration 저장소·최종 확정에 의존 금지. production은 Python eval 호출 금지.

# 5. Technical Architecture
## 5.1 Boundary Mapping
| Context | Capability | Code | Deployment |
|---|---|---|---|
| Document Knowledge | hybrid search | package | existing service |
| Scenario Preparation / Adventure Runtime | acquisition capability | shared application package | existing adventure deployment |
| AI Game Master | rerank/Judge | existing package | existing service |
## 5.2 Boundary Promotion Decisions
새 Bounded Context/Gradle module/deployment service 없음 — package seam이 소유권·격리를 충족한다.
## 5.3 System Interaction Flow
`adventure → unified search → PostgreSQL scoped stats → candidates → AI rerank/Judge → adventure validation`.
## 5.4 Synchronous Communication
| Caller | Provider | Protocol | Operation | Timeout |
|---|---|---|---|---|
| adventure | rule-knowledge | internal HTTP | `POST /internal/v1/evidence-candidates/search` | 기존 설정 |
| adventure | AI Game Master | internal sync | rerank/Judge | 기존 설정 |
## 5.5 API Contracts
### `POST /internal/v1/evidence-candidates/search` (rule-knowledge-service)
Request: `ownerId/sessionId/scenarioPackageId: UUID`, `stageKey: String`, `actionIntent: SearchIntent`, `scope: [{documentId: UUID, extractionVersion: long, documentType: RULEBOOK|STORYBOOK}]`, `activeLocators: Locator[]`, `query: String`, `denseLimit/bm25Limit: int` (각 1..30). Response: unique candidates≤60 with stable ID, document/extraction/type, locator/provenance/citation, excerpt, denseRank, bm25Rank, rrfScore. 기존 internal token/auth와 scope 검증 유지.

Errors: 400 `EVIDENCE_SEARCH_INVALID_REQUEST`, 401 `EVIDENCE_SEARCH_UNAUTHENTICATED`, 403 `EVIDENCE_SEARCH_SCOPE_FORBIDDEN`, 503 `EVIDENCE_SEARCH_UNAVAILABLE` (model error는 이 endpoint에 없음).

### `POST /internal/v1/gm/evidence-rerank` (AI Game Master)
Request: task context, query, candidates≤180. Response: ordered candidate IDs≤30. Errors: 400 `EVIDENCE_RERANK_INVALID_REQUEST`, 401 `EVIDENCE_GM_UNAUTHENTICATED`, 422 `EVIDENCE_MODEL_OUTPUT_INVALID`, 502 `EVIDENCE_PROVIDER_UNAVAILABLE`.

### `POST /internal/v1/gm/evidence-sufficiency` (AI Game Master)
Request: policyId, taskContext, candidates≤30, pinned IDs. Response: `sufficient`, selected IDs, per-ID reasons, missing. Errors: 400 `EVIDENCE_JUDGE_INVALID_REQUEST`, 401 `EVIDENCE_GM_UNAUTHENTICATED`, 422 `EVIDENCE_MODEL_OUTPUT_INVALID`, 502 `EVIDENCE_PROVIDER_UNAVAILABLE`. 두 AI endpoint 모두 server-side fixed policy templates를 사용하며 caller가 arbitrary prompt policy를 주입할 수 없다.

| Condition | Status / stable code |
|---|---|
| validation | 400 `EVIDENCE_SEARCH_INVALID_REQUEST` |
| authentication | 401 `EVIDENCE_SEARCH_UNAUTHENTICATED` |
| authorization/scope | 403 `EVIDENCE_SEARCH_SCOPE_FORBIDDEN` |
| invalid model output after retry | 422 `EVIDENCE_MODEL_OUTPUT_INVALID` |
| exhausted provider/search failure | 502/503 `EVIDENCE_PROVIDER_UNAVAILABLE` / `EVIDENCE_SEARCH_UNAVAILABLE` |
## 5.6 Asynchronous Communication / 5.7 Message Contracts
해당 없음 — 모든 흐름은 동기 요청이고 새 message가 없다.
## 5.8 Data Ownership
Document Knowledge가 published chunk vector, document length, term frequency를 PostgreSQL에서 소유·조회한다. 통계 N/avgdl/df는 선택된 authorized scope에서만 계산한다.
## 5.9 Schema Changes
다음 Flyway migration(`V19_6`가 여전히 next면 해당 번호)에 published chunk `document_length` column과 `chunk_term_frequency(chunk_id FK, term, term_frequency)` table, `(chunk_id,term)` unique 및 `term`/`chunk_id` indexes를 추가한다. authorized document/extraction scope에서만 N/avgdl/df를 계산한다. vector+terms+status는 같은 transaction이며 개발은 기존 data reset/reindex big-bang, compatibility flag/two-phase rollout 없음.
## 5.10 Consistency Model
publication은 vector+BM25 모두 commit될 때만 INDEXED인 strong transaction; 요청 선택은 request-local이다.
## 5.11 Infrastructure Dependencies
PostgreSQL(repository), 기존 model provider(AI adapter), existing auth/token만 사용. search server/PG extension 없음.
## 5.12 External Dependency Isolation
모델 provider는 `EvidenceRerankerPort`/`EvidenceSufficiencyJudgePort` adapter로 격리하고 Adventure가 모든 ID/형식을 검증한다.
## 5.13 File and Module Structure
### Existing Structure
`RuleKnowledgeController`, `RuleEvidenceSearchApplicationService`, 두 Pgvector repository, publication/indexing services, `RuntimeEvidenceSelector`, `EvidencePack`, `CrossContextHttpRuntimeEvidenceSearchGateway`, `RuleGuidanceApplicationService`, `GroundedRuleAnswerService`, AI controller/config; Python `bm25.py`, `dense.py`, `hybrid.py`, `reranker.py`, `retrieval.py` 및 eval/tests/CI.
### Target Structure
`src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/application/search/EvidenceCandidate.java`, `EvidenceSearchRequest.java`, `EvidenceSearchResult.java`, `HybridEvidenceSearchService.java`, `DenseEvidenceCandidateSearchPort.java`, `Bm25EvidenceCandidateSearchPort.java`, `RrfFusionPolicy.java`; `src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/infrastructure/persistence/PostgreSQLBm25EvidenceCandidateSearchAdapter.java`; `src/adventure-service/src/main/java/com/dndmaster/adventure/application/evidence/EvidenceAcquisitionApplicationService.java`, `EvidenceSufficiencyPolicy.java`, `EvidenceCandidatePool.java`, `SufficiencyDecision.java`, `PlayerActionEvidenceSufficiencyPolicy.java`, `ScenarioPreparationEvidenceSufficiencyPolicy.java`, `OpeningSceneEvidenceSufficiencyPolicy.java`, `RuleGuidanceEvidenceSufficiencyPolicy.java`, `UnifiedEvidenceCandidateSearchPort.java`, `EvidenceRerankerPort.java`, `EvidenceSufficiencyJudgePort.java`; `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/integration/CrossContextHttpEvidenceCandidateSearchGateway.java`; `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/application/evidence/EvidenceRerankerService.java`, `EvidenceSufficiencyJudgeService.java`, `EvidenceRerankRequest.java`, `EvidenceRerankResponse.java`, `EvidenceSufficiencyRequest.java`, `EvidenceSufficiencyResponse.java`.
### File Change Map
| Path | Action | Responsibility |
|---|---|---|
| `src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/api/RuleKnowledgeController.java` | Modify | unified endpoint |
| `src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/application/search/RuleEvidenceSearchApplicationService.java` | Replace/modify | Dense+BM25+RRF |
| `src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/infrastructure/persistence/PgvectorRuleEvidenceSearchRepository.java` | Modify | scoped vector search |
| `src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/infrastructure/persistence/PgvectorStorySourceSearchRepository.java` | Replace/modify | unified story search |
| `src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/application/publication/RagExtractionPublicationService.java` | Modify | atomic publication |
| `src/rule-knowledge-service/src/main/java/com/dndmaster/ruleknowledge/application/indexing/RulebookIndexingApplicationService.java` | Modify | dual index before INDEXED |
| `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeEvidenceSelector.java` | Modify | orchestration caller |
| `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/EvidencePack.java` | Modify | result DTO |
| `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/integration/CrossContextHttpRuntimeEvidenceSearchGateway.java` | Replace | unified endpoint |
| `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/integration/CrossContextHttpScenarioSourceExcerptGateway.java` | Replace | unified endpoint |
| `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/integration/CrossContextHttpOpeningSourceContextSearchGateway.java` | Replace | unified endpoint |
| `src/adventure-service/src/main/java/com/dndmaster/adventure/application/guidance/RuleGuidanceApplicationService.java` | Modify | policy result |
| `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/application/rule/GroundedRuleAnswerService.java` | Modify | selected evidence |
| `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/api/AiGameMasterController.java` | Modify | rerank/Judge endpoints |
| `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/api/AiGameMasterApiConfiguration.java` | Modify | internal auth/config |
| `src/rule-knowledge-service/src/main/resources/db/migration/V19_6__add_bm25_chunk_statistics.sql` | Add | length/terms/FK/indexes; current highest migration 근거로 V19_6 고정, 구현 시 충돌하면 next available로 조정하고 ticket blocker 처리 |
| `src/preprocessing_agent/eval/bm25.py` | Modify | offline BM25 baseline |
| `src/preprocessing_agent/eval/dense.py` | Modify | retrieval baseline |
| `src/preprocessing_agent/eval/hybrid.py` | Modify | RRF comparison |
| `src/preprocessing_agent/eval/reranker.py` | Modify | rerank evaluation |
| `src/preprocessing_agent/eval/retrieval.py` | Modify | retrieval metrics |
| `scripts/evaluate_preprocessing.py` | Modify | evaluation runner |
| `tests/eval/test_bm25_baseline.py` | Modify | BM25 fixtures |
| `tests/eval/test_dense_baseline.py` | Modify | dense baseline |
| `tests/eval/test_hybrid_comparison.py` | Modify | hybrid metrics |
| `tests/eval/test_reranker_context.py` | Modify | reranker contract |
| `tests/eval/test_sufficiency_selection.py` | Add | sufficiency fixtures/metrics |
| `.github/workflows/dnd-master-ci.yml` | Modify | fixed eval CI |

# 6. Runtime Design
## 6.1 Runtime Flow
요청-지역 순차 flow이며 Dense/BM25만 병렬; rerank/Judge는 순차, persisted state 없음.
## 6.2 Concurrent Access / 6.3 Concurrency Control
publication DB transaction이 partial visibility를 막고 검색은 committed index만 읽는다. Dense/BM25는 request 내부 병렬이다.
## 6.4 Ordering
RRF는 contribution `1/(60+rank)`, candidate ID dedupe, score desc 후 stable ID tie; pinned IDs를 먼저 유지하고 reranker 순서로 남은 자리를 채운다.
## 6.5 Transaction Boundaries
publication만 새 consistency boundary이며 vector+BM25/status를 all-or-none commit한다. evidence request는 저장하지 않는다.
## 6.6 Idempotency
request-local read/call이며 persisted side effect가 없어 중복 실행 결과를 저장하지 않는다.
## 6.7 Partial Failure
retriever 하나라도 실패하면 둘 다 재실행하고 partial fallback 금지; publication 실패는 rollback/FAILED.

# 7. Error Handling and Recovery
## 7.1 Failure and Recovery Flow
`분류 → retryable이면 maxAttempts=2 → 소진 전체 오류; non-retryable 즉시 오류`.
## 7.2 Error Classification
| Error | Retry | Result |
|---|---|---|
| timeout/connection/429/5xx/DB/malformed | Yes | 전체 오류 after retry |
| auth/authorization/bad request/scope | No | 즉시 오류 |
## 7.3 Retry Policy
검색은 Dense/BM25 함께 재실행; rerank/Judge는 독립 재시도. adapter/provider는 nested retry 금지.
## 7.4 Compensation
해당 없음 — 요청 저장이 없고 publication rollback이 보상이다.
## 7.5 Recovery
transient는 같은 stage 1회 재시도, incomplete index는 reset/reindex.
## 7.6 Rollback
DB transaction rollback; compatibility rollout 없음.

# 8. Security
## 8.1 Authentication and Authorization
기존 internal token/auth, owner/session/document scope validation을 모든 gateway/controller에서 유지한다.
## 8.2 Input Validation
scope/type/ID 권한, limit≤30, candidate subset·uniqueness·max30·reason completeness, true/false 조건을 검증한다.
## 8.3 Sensitive Data
query/chunk/excerpt/selectionReasons 원문은 저장·로그하지 않는다. Judge `missing`과 추가 검색 질의는 표준 민감정보 redaction 후 추적한다.
## 8.4 Secrets
새 secret 없음; 기존 internal service token 설정·rotation을 사용한다.

# 9. Observability
## 9.1 Logs
request ID, session/target IDs, candidate counts/dedupe, rerank in/out, timing, retries/error category, final IDs, sufficient, redacted `missing`, redacted additional-search query, additional count, reasons presence를 기록한다. 사용자 질의·청크·selectionReasons 원문은 기록하지 않는다.
## 9.2 Metrics
stage/total latency, counts, retries/errors; Recall@5는 Dense보다 strict 개선, MRR/evidence recall/NDCG@5는 1%p 초과 하락 금지; sufficiency accuracy/redundancy/avg pack/missing rate는 최초 report-only.
## 9.3 Tracing
search/rerank/Judge span에 식별자·수량·시간·오류 category만 attributes로 둔다.
## 9.4 Alerts
index FAILED와 기술 오류/재시도 증가를 기존 운영 경보로 관찰한다.

# 10. Change Boundaries
## 10.1 Allowed Changes
위 File Change Map의 Java seams, next migration, Python offline eval/fixtures/CI만 변경한다.
## 10.2 Forbidden Changes
새 service/module/context, Python production, external search server, `ts_rank_cd`를 BM25로 간주하거나 사용하는 것, type weighting, fixed top5, exhaustive subset calls, staged rollout 금지.
## 10.3 Conditional Changes
구체 timeout과 새 metric 차단값은 구현 후 측정하여 사용자 후속 계획에서 확정(비차단).

# 11. Verification Requirements
## 11.1 Domain Verification
공유 deterministic fixtures로 Unicode tokenization/casefold equivalent, k1=1.5/b=.75 TF/IDF, RRF/ranks/dedupe/ties를 Python/Java 양쪽 검증한다.
## 11.2 Program Verification
네 정책, 0/short 후보, pool 60/120/180, pinned IDs, retry ownership, malformed AI output contract/unit/integration/E2E 테스트.
## 11.3 Technical Contract Verification
unified API request/response/auth/scope, Flyway publication atomicity, no-sensitive-log contract tests.
## 11.4 Runtime Verification
Dense/BM25 parallel deterministic output; additional search≤2; no partial fallback.
## 11.5 Recovery Verification
timeout/429/5xx/DB/model malformed injection 후 1 retry 및 전체 오류, publication fault 후 rollback/FAILED.
## 11.6 Agent Verifier Criteria
* [ ] capability/context/code/deployment 경계 일치
* [ ] 포트·API·오류·재시도·보안 계약 일치
* [ ] 색인 원자성·평가 지표·네 정책 검증
* 실행 명령/테스트 결과: 구현 후 CI·Java·E2E에서 기록
* 미검증: 후속 확정 전 timeout·report-only 차단값

# 12. Alternatives and Trade-offs
| Decision | Rejected Option | Reason | Result |
|---|---|---|---|
| 검색 | external search server | 운영 경계·비용 | Reject |
| BM25 | PostgreSQL `ts_rank_cd` | 계약 불일치 | Reject |
| 실행 | Python production | 런타임 결합 | Reject |
| 선택 | fixed top5/exhaustive subset | 최소성·비용 위반 | Reject |
| 유형/배포 | weighting/quota/staged rollout | 합의 범위 위반 | Reject |
| 구현 | Java formula + scoped PostgreSQL stats | 일관성·소유권 | Adopt |

# 13. Risks and Open Questions
## 13.1 Risks
| Risk | Impact | Probability | Mitigation |
|---|---|---|---|
| 지연 증가 | Medium | Medium | stage/total 측정 후 threshold 후속 결정 |
| scope 통계 누출 | High | Low | authorized scope 쿼리·통합 테스트 |
| partial index | High | Low | 단일 transaction·FAILED·reindex |
| malformed model | Medium | Medium | fixed policy prompt·검증·재시도 |
## 13.2 Open Questions
| Question | Blocking | Resolution |
|---|---|---|
| 새 지표 차단값 | No | 구현 후 fixed eval/E2E 뒤 사용자 후속 확정 |
| 구체 timeout | No | 기존 설정 유지, 실측 후 후속 확정 |

## Architecture 다이어그램 계약
클래스 SVG 링크는 3.4.1의 `document-knowledge.class.svg`, `evidence-selection.class.svg`이며 원본은 같은 basename `.puml`이다. 상태 다이어그램은 해당 없음 — persisted aggregate state가 없고 Product 요청 흐름과 중복된다.
