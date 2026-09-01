# Preprocessing Quality Evaluator Product Spec

## 1. Problem and Context

현재 전처리 파이프라인은 PDF를 구조화 문서와 chunk로 변환하고 검색 인덱스의 입력을 만든다. 그러나 생성된 chunk가 원문을 보존하는지, 의미 단위를 올바르게 유지하는지, gold 근거가 검색 가능한지, 전처리 설정 변경이 실제 품질을 개선했는지를 반복 가능하게 측정할 수 없다.

평가 결과가 없으면 다음 문제를 구분할 수 없다.

- 원문이 손실·변형된 전처리 문제
- 의미 단위가 잘린 chunk 경계 문제
- gold 근거가 분산된 coverage 문제
- 검색 순위 또는 retriever 자체의 문제

따라서 전처리 run을 입력으로 받아 intrinsic quality와 retrieval quality를 분리 측정하고, 실패한 chunk와 질문을 재현 가능한 형태로 남기는 평가 기능이 필요하다.

## 2. Goals and Desired Outcomes

- 전처리 run 하나를 평가해 원문 보존, 출처 추적, 경계, 크기, 중복, 의미 무결성 지표를 산출한다. (GOAL-01)
- gold context와 required evidence가 chunk에 보존되었는지 측정한다. (GOAL-02)
- 동일한 평가 질문을 실제 retriever에 실행해 Recall@K, MRR, Evidence Recall을 산출한다. (GOAL-03)
- intrinsic 결과와 retrieval 결과를 섞지 않고 별도 그룹으로 보고한다. (GOAL-04)
- 평가 실패의 원인이 된 chunk·canonical key·질문·검색 결과를 상세 failure record로 남긴다. (GOAL-05)
- 두 전처리 run을 우선순위가 있는 지표와 hard gate 기준으로 비교한다. (GOAL-06)

## 3. Users and Actors

- **전처리 개발자**: 전처리 설정 또는 chunk 정책을 변경하고 run 품질을 확인한다.
- **검색/RAG 개발자**: chunk 품질과 retriever 품질을 분리해 회귀 원인을 찾는다.
- **평가 실행자**: run과 평가셋을 지정해 반복 가능한 평가를 실행한다.
- **전처리 파이프라인**: 평가 대상인 chunks, source span, manifest를 제공한다.
- **Gold 평가셋**: 질문, canonical gold context, required evidence를 제공한다.
- **Retriever**: 평가 질문에 대해 순위가 있는 chunk 식별자를 반환한다.

## 4. Ubiquitous Language and Terminology

- **Preprocessing Run**: 하나의 입력 문서와 전처리 설정으로 생성된 결과 묶음.
- **Intrinsic Quality**: 검색을 실행하지 않고 chunk 자체와 원문·구조의 관계를 평가한 품질.
- **Retrieval Quality**: 평가 질문을 retriever에 넣어 gold chunk가 검색되는 순위 품질.
- **Canonical Key**: 문서의 의미 단위를 안정적으로 식별하는 계층형 키.
- **Gold Context**: 질문에 답하기 위해 반드시 보존되어야 하는 canonical context.
- **Required Evidence**: gold 질문의 정답에 필요한 개별 근거 항목.
- **Single-Chunk Answerability**: 필요한 gold context와 evidence가 하나의 chunk에 함께 있는 정도.
- **Source Traceability**: chunk text를 원문 source span으로 추적·재구성할 수 있는 정도.
- **Failure Record**: 평균 지표가 아니라 구체적인 실패 대상을 설명하는 평가 결과.
- **Hard Gate**: 평균 점수와 무관하게 run을 실패시키는 최소 품질 규칙.

## 5. Core Use Cases

### UC-01 Intrinsic 품질 평가

1. 실행자가 preprocessing run을 지정한다.
2. 평가기는 chunks와 source metadata를 읽는다.
3. 원문 보존·추적, 경계, chunk 크기, 중복, 의미 단위 지표를 계산한다.
4. 치명적 오류와 개별 실패를 기록한다.
5. intrinsic 결과와 pass/fail을 저장한다.

### UC-02 Gold context 및 evidence 평가

1. 실행자가 평가셋을 함께 지정한다.
2. 평가기는 gold canonical key를 run의 chunk에 매핑한다.
3. gold context coverage, single-chunk answerability, evidence completeness를 계산한다.
4. 매핑되지 않거나 여러 chunk로 부적절하게 분리된 질문을 failure record로 남긴다.

### UC-03 Retrieval 품질 평가

1. 평가기가 평가셋의 질문을 순서대로 retriever에 전달한다.
2. retriever의 상위 K chunk 식별자를 받는다.
3. Recall@1/3/5/10, MRR, Evidence Recall@5를 계산한다.
4. retrieval miss와 ranking error를 질문별로 저장한다.

### UC-04 전처리 run 비교

1. 실행자가 두 run을 지정한다.
2. 평가기는 동일한 기준과 평가셋으로 두 결과를 비교한다.
3. hard gate 위반 여부를 먼저 표시한다.
4. source integrity, gold coverage, answerability, semantic integrity, Recall@5, MRR 우선순위로 차이를 보여준다.
5. 어느 run이 절대적으로 우월하다고 단정하지 않고 trade-off를 표시한다.

## 6. Business Rules and Invariants

- (BR-01) intrinsic quality와 retrieval quality는 별도 score group으로 저장한다.
- (BR-02) source mutation rate가 0보다 크면 run은 실패한다.
- (BR-03) source traceability rate가 0.999 미만이면 run은 실패한다.
- (BR-04) gold context coverage가 0.90 미만이면 run은 실패한다.
- (BR-05) split entity rate가 0.05보다 크면 run은 실패한다.
- (BR-06) `spell`, `monster_stat_block`, `class_feature`, `race_trait`, `condition`, `magic_item`, `table`은 의미 단위 분리 평가 대상이다.
- (BR-07) atomic entity가 작다는 이유만으로 tiny chunk 실패로 판정하지 않는다.
- (BR-08) table·stat block 등 비 prose content type에는 일반 문장 경계 규칙을 그대로 적용하지 않는다.
- (BR-09) 모든 평가 지표는 동일 run·동일 평가셋·동일 retriever 조건에서 비교 가능해야 한다.
- (BR-10) 평가기는 전처리 산출물이나 운영 검색 인덱스를 변경하지 않는 읽기 전용 작업이다.

## 7. States and State Transitions

평가 실행 상태:

```text
REQUESTED → LOADING → INTRINSIC_EVALUATED → GOLD_EVALUATED
          → RETRIEVAL_EVALUATED → REPORTED
```

오류가 발생하면:

```text
LOADING | INTRINSIC_EVALUATED | GOLD_EVALUATED | RETRIEVAL_EVALUATED
→ FAILED
```

`passed`는 실행 상태와 별개인 결과 판정이다. 모든 평가가 끝나도 hard gate를 위반하면 `REPORTED`이면서 `passed=false`다.

## 8. Failures, Exceptions, and Boundary Conditions

- run 필수 산출물이나 manifest가 없으면 평가를 시작하지 않고 입력 오류로 종료한다.
- source span으로 chunk를 재구성할 수 없으면 `SOURCE_TRACE_ERROR`를 기록한다.
- source text가 재구성 결과와 다르면 `SOURCE_MUTATION`을 기록하고 hard fail한다.
- canonical key를 찾을 수 없으면 `GOLD_CONTEXT_MISSING`을 기록한다.
- required evidence가 여러 chunk로 분리되면 `GOLD_EVIDENCE_SPLIT`을 기록한다.
- retriever가 결과를 반환하지 않거나 식별자가 해석되지 않으면 해당 질문을 `RETRIEVAL_MISS`로 기록한다.
- 평가셋에 required evidence가 없는 질문은 coverage에는 포함하되 evidence completeness 분모에서는 명시적으로 제외한다.
- semantic judge를 사용할 수 없으면 heuristic 후보와 미평가 상태를 구분해 보고하며, 전체 평가를 조용히 통과시키지 않는다.
- 비교 대상 run의 schema/pipeline 버전이 호환되지 않으면 비교를 거부하거나 호환성 경고를 표시한다.

## 9. Inputs and Outputs

입력:

- preprocessing run 디렉터리
- 평가셋(JSONL)
- gold canonical key와 required evidence
- retriever 또는 사전 계산된 검색 결과
- 평가 정책과 hard gate 기준

출력:

```text
<run>/preprocessing_eval.json
<run>/preprocessing_eval_failures.jsonl
```

결과에는 run 식별자, pass/fail, intrinsic 지표, gold 지표, retrieval 지표, hard gate 결과, 정책 버전이 포함된다.

## 10. Scope and Non-goals

포함:

- intrinsic evaluator
- semantic integrity 후보·판정
- gold context/evidence 평가
- retrieval metric 평가
- failure taxonomy와 상세 report
- 두 run 비교와 baseline 기록
- 초기 hard gate와 핵심 지표 기준

제외:

- 자동 chunk configuration optimizer
- 모든 chunk에 대한 LLM judge 실행
- learned evaluator
- graph 기반 coherence 평가
- 자동 gold dataset 생성
- embedding model 또는 reranker benchmark
- 운영 인덱스나 전처리 결과의 자동 수정

## 11. Priorities and Trade-offs

1. source integrity와 traceability
2. gold context coverage
3. single-chunk answerability와 semantic integrity
4. Recall@5
5. MRR

평균 token 수는 최적화 목표가 아니며 진단 정보로만 사용한다. 평가기는 단일 종합점수보다 지표 그룹과 failure record의 해석 가능성을 우선한다.

## 12. Success Conditions and Acceptance Criteria

- (AC-01) 지정한 run과 평가셋으로 평가 명령을 실행하면 JSON report와 JSONL failure report가 생성된다.
- (AC-02) source mutation, traceability, boundary, size, duplicate 지표가 계산된다.
- (AC-03) gold context coverage, single-chunk answerability, evidence completeness가 계산된다.
- (AC-04) Recall@1/3/5/10, MRR, Evidence Recall@5가 계산된다.
- (AC-05) intrinsic과 retrieval 결과가 report에서 분리된다.
- (AC-06) hard gate 위반 run은 지표가 높더라도 `passed=false`다.
- (AC-07) 실패 report만으로 대상 chunk·canonical key·질문·검색 결과·실패 유형을 재현할 수 있다.
- (AC-08) 동일 평가셋으로 두 run을 비교하면 우선순위 지표와 trade-off가 표시된다.
- (AC-09) 평가 실행은 입력 run, 운영 인덱스, 전처리 산출물을 변경하지 않는다.
- (AC-10) 현재 체크아웃에 전처리 agent 산출물이 없을 경우, 기준 ref와 artifact contract가 명확히 지정되어 평가 결과를 재현할 수 있다.
