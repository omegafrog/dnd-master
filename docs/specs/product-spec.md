# RAG Preprocessing Agent Product Spec

## 1. Problem and Context

PDF와 비정형 규칙 문서를 RAG에 넣기 위해 전체 문서를 LLM이 직접 읽고 자유롭게 분할하면 원문 손실, 불안정한 경계, 재현 불가능한 결과가 발생한다. 현재 저장소에는 Storybook용 indexing utility와 Java rule-knowledge indexing pipeline이 있지만, page/block provenance, semantic content type, 안정적인 canonical key, validation/repair, offline retrieval evaluation을 하나의 제품 흐름으로 제공하지 않는다.

첫 통합 fixture는 구조가 명확하고 반복 가능한 필드를 가진 D&D Basic Rules 2018로 한다.

## 2. Goals and Desired Outcomes

- 문서 원문과 source 위치를 보존한 `ParsedDocument`를 만든다.
- 구조와 content type을 식별해 semantic unit 기반 chunk를 만든다.
- 같은 입력과 설정에서 의미적으로 같은 chunk 결과를 재현한다.
- 품질 문제를 탐지하고 자동 repair 또는 manual review로 분류한다.
- 결과와 실행 정보를 다른 시스템이 소비할 수 있는 산출물로 제공한다.
- gold context와 retrieval 결과를 연결해 chunk policy를 Recall, MRR, Evidence Recall으로 비교한다.
- 불확실한 semantic 판단만 Agent에 맡기고 원문 변경은 허용하지 않는다.

## 3. Users and Actors

- **Preprocessing Operator**: 문서와 profile을 선택하고 결과와 issue를 검토한다.
- **RAG Application**: chunk, manifest, document tree를 소비한다.
- **Evaluation Operator**: gold context와 retrieval 결과로 정책을 비교한다.
- **Deterministic Pipeline**: 추출, 계산, 분할, 조립, 검증, repair, persistence를 수행한다.
- **Semantic Agent**: 구조·분류·품질에 대한 structured decision만 반환한다.

## 4. Ubiquitous Language and Terminology

- **ParsedDocument**: 원문 page/block과 위치·서식 정보를 보존한 문서 표현.
- **DocumentTree**: Part, Chapter, Section, Subsection 계층을 가진 문서 구조.
- **ContentType**: `spell`, `monster_stat_block`, `rule`, `table` 등 chunk policy를 결정하는 분류.
- **ChunkCandidate**: semantic unit으로 결정됐지만 최종 split/ID 전인 후보.
- **Chunk**: source text, source span, token count, canonical key, stable ID를 가진 검색 단위.
- **Atomic Entity**: spell, monster stat block처럼 가능한 한 하나로 유지하는 콘텐츠.
- **Canonical Key**: 문서 내 의미 위치를 나타내는 안정적인 경로 키.
- **Validation Issue**: chunk 결과의 구조·크기·provenance·중복 문제.
- **Manual Review**: deterministic repair로 안전하게 처리할 수 없는 상태.

## 5. Core Use Cases

### UC-01 문서 전처리 실행

Operator가 source PDF와 profile을 지정하면 parser, structure detection, classification, chunk planning, assembly, validation, repair 순서로 실행하고 산출물과 manifest를 만든다.

### UC-02 불명확한 구조 또는 분류 처리

deterministic confidence가 threshold 미만이면 Agent가 구조 또는 content type 결정을 반환한다. Agent는 원문을 수정하지 않으며 pipeline이 결정만 적용한다.

### UC-03 chunk 품질 검증과 repair

빈 chunk, token overflow, orphan heading, duplicate, invalid source span, split table 등을 보고한다. 허용된 operation은 deterministic code가 수행하고 안전하지 않은 문제는 manual review로 남긴다.

### UC-04 결과 소비

RAG Application은 JSONL chunk와 document tree를 읽고 embedding/indexing을 수행할 수 있다. v0.1에서는 Java persistence에 직접 쓰지 않고 파일/CLI 경계로 전달한다.

### UC-05 retrieval 평가

gold context key와 retrieval 결과를 제공하면 gold chunk ID를 매핑하고 Recall@K, MRR, Evidence Recall 및 정책별 비교 결과를 생성한다.

## 6. Business Rules and Invariants

- Agent는 source text를 요약, paraphrase, 수정, 보정, 삭제, 창작하지 않는다.
- parser는 의미 분류나 heading 의미 판단을 하지 않고 원문 구조 정보를 보존한다.
- semantic boundary 우선순위는 entity → subsection → paragraph → sentence → token boundary다.
- atomic entity는 기본적으로 하나의 chunk이며 max token을 넘을 때만 parent-child로 분리한다.
- `chunk_id`는 순번이 아닌 content hash 기반으로 안정적으로 생성한다.
- `canonical_key`는 의미 위치를 나타내며 chunk ID와 독립적이다.
- table은 split하지 않으며, 안전한 repair가 없으면 manual review다.
- 동일 source, profile, pipeline/schema version에서는 의미적으로 동일한 결과를 재현해야 한다.
- deterministic code만 split, merge, reclassify, persistence를 수행한다.
- v0.1은 Python pipeline과 기존 Java indexing/persistence의 책임을 분리한다.

## 7. States and State Transitions

```text
RECEIVED → PARSED → STRUCTURED → CLASSIFIED → PLANNED
→ ASSEMBLED → VALIDATED → REPAIRED → EXPORTED
```

```text
DETECTED → AUTO_REPAIRED
DETECTED → MANUAL_REVIEW
```

## 8. Failures, Exceptions, and Boundary Conditions

- PDF extraction 실패는 source와 page 정보를 포함한 실행 실패로 반환한다.
- 낮은 heading confidence는 Agent fallback 또는 review_required로 표시한다.
- token overflow는 우선순위에 따라 재분할하고 atomic overflow는 parent-child로 처리한다.
- 유효하지 않은 source span은 export하지 않고 issue를 남긴다.
- Agent timeout, malformed response, 금지된 text 변경은 결과에 적용하지 않고 재시도 또는 review로 보낸다.
- duplicate chunk는 임의로 삭제하지 않고 deterministic issue로 보고한다.
- 미지원 문서 유형은 `unknown`으로 분류해 원문을 보존한다.

## 9. Inputs and Outputs

입력: PDF source path, profile/config, 선택적 Agent 설정, 선택적 gold context와 retrieval result.

출력: `chunks.jsonl`, `issues.jsonl`, `document_tree.json`, `manifest.json`, evaluation report.

manifest에는 source SHA-256, pipeline/schema version, profile, token policy, page/chunk/agent/validation 통계를 포함한다.

## 10. Scope and Non-goals

포함: D&D representative fixture, PDF provenance, deterministic structure/classification/chunking/validation/repair, structured Agent fallback, JSONL/manifest export, gold mapping과 retrieval metrics.

비범위: GraphRAG, knowledge graph extraction, multi-agent orchestration framework, 자동 embedding model 선택, vector DB 최적화, reranker tuning, 자동 gold-label 생성, 복잡한 OCR, 모든 PDF layout 지원, v0.1의 Java persistence 직접 연계.

## 11. Priorities and Trade-offs

우선순위는 원문 보존과 provenance, 재현 가능한 deterministic 결과, retrieval 품질, 안전한 review fallback, Agent 호출 비용 순서다. 자유로운 LLM chunking보다 검증 가능성과 재현성을 우선한다.

## 12. Success Conditions and Acceptance Criteria

- 대표 fixture에서 page/block ordering과 원문 text가 보존된다.
- Part/Chapter/Section/Subsection tree가 생성된다.
- spell, monster, class feature, condition, rule, table이 정책에 따라 분류된다.
- token 정책과 atomic parent-child 표현이 적용된다.
- export chunk가 유효한 source span과 stable ID를 가진다.
- validation과 repair 결과가 issues에 기록된다.
- Agent의 text 변경 시도는 적용되지 않는다.
- 동일 입력/config 재실행 결과의 chunk ID와 canonical key가 안정적이다.
- gold context가 chunk ID로 매핑되고 Recall@K, MRR, Evidence Recall이 계산된다.
