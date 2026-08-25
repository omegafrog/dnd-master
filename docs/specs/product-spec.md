# RAG Retrieval Evaluation Product Spec

## 1. Problem and Context

현재 D&D 문서는 전처리되어 388개의 검색 대상 chunk와 50개의 평가 질문을 제공한다. 다음 단계는 gold 근거를 검증하고 Dense, BM25, Hybrid, Reranker 검색 품질을 동일한 평가셋으로 수치화하는 것이다. 검색 실패와 생성 실패를 분리하지 않으면 전처리·검색·생성 중 어느 단계가 품질을 저해하는지 판단할 수 없다.

## 2. Goals and Desired Outcomes

- gold case와 실제 chunk의 정합성을 검증한다.
- answerable 질문에 대해 검색 결과의 Recall@K, MRR, Evidence Recall을 산출한다.
- Dense와 BM25의 상호 보완 관계를 측정하고 Hybrid 기준선을 만든다.
- Reranker와 parent-context 확장의 효과를 독립적으로 비교한다.
- retrieval failure를 원인별로 분류하고 전처리 피드백으로 연결한다.
- retrieval이 안정된 후 generation과 unanswerable 판단을 별도 평가한다.

## 3. Users and Actors

- 평가 담당자: 평가셋과 gold 근거를 관리하고 실험 결과를 비교한다.
- RAG 개발자: 검색기·reranker·context builder의 성능을 개선한다.
- 검색 평가기: 동일 입력에 대해 재현 가능한 결과와 지표를 생성한다.
- 생성 평가기: 검색 context와 답변·인용의 품질을 분리 측정한다.

## 4. Ubiquitous Language and Terminology

- chunk: 전처리 결과의 검색 단위.
- gold case: 질문, answerability, 정답 근거 chunk, 멀티근거 요구사항을 포함한 평가 사례.
- answerable: 현재 문서만으로 충분한 근거를 찾을 수 있는 질문.
- evidence: 답변을 뒷받침하는 필수 근거 chunk 또는 근거 그룹.
- retriever: 질문에 대해 순위가 있는 chunk 목록을 반환하는 검색기.
- reranker: 후보 목록을 재정렬하는 평가 대상.
- parent context: 검색된 child chunk가 속한 상위 문맥.

## 5. Core Use Cases

### UC-01 Gold Cases 검증

평가 담당자는 gold case를 입력하고, 모든 gold chunk ID가 현재 artifact에 존재하는지, answerable 규칙과 evidence 요구사항이 일치하는지 확인한다.

### UC-02 Retrieval Baseline 평가

평가 담당자는 동일한 50문항으로 Dense와 BM25를 실행하고 Recall@1/3/5/10/20, MRR, Evidence Recall을 얻는다.

### UC-03 Retrieval 조합 비교

개발자는 Dense only, BM25 only, Dense+BM25 Hybrid, Hybrid+Reranker 실험을 동일 조건으로 비교한다.

### UC-04 실패 분석 및 피드백

개발자는 검색 실패를 `RETRIEVAL_MISS`, `RANKING_ERROR`, `CHUNK_BOUNDARY`, `QUERY_MISMATCH`, `METADATA_MISMATCH`, `MULTI_EVIDENCE_MISS`, `TABLE_RETRIEVAL_FAILURE`로 분류하고, 실제 검색 실패와 연결된 전처리 문제만 수정 대상으로 선택한다.

### UC-05 Generation 평가

검색 결과가 안정된 뒤 생성 결과의 정답성, 충실성, 인용 정확성, context 활용, abstention을 평가한다.

### UC-06 Unanswerable 평가

문서에 충분한 근거가 없는 질문에 대해 시스템이 근거 부족을 판단하는지 평가한다.

## 6. Business Rules and Invariants

- 평가 실행은 고정된 chunk artifact와 gold case snapshot을 사용해야 한다.
- answerable case는 최소 하나의 유효한 gold chunk를 가져야 한다.
- unanswerable case는 gold 근거를 가져서는 안 된다.
- gold chunk ID는 실제 chunk ID와 정확히 일치해야 한다.
- 검색 결과는 존재하는 chunk ID만 포함해야 하며 한 결과 내 ID 중복을 허용하지 않는다.
- 모든 retriever 비교는 동일한 질문·gold·cutoff 집합을 사용한다.
- 멀티근거 평가는 모든 필수 evidence group 충족 여부를 별도로 계산한다.
- retrieval failure와 generation failure는 서로 다른 결과로 기록한다.
- validator 경고만으로 전처리 변경을 강제하지 않으며, retrieval 실패와 연결된 경우에만 feedback 대상으로 승격한다.

## 7. States and State Transitions

`draft gold → validated gold → evaluated baseline → compared experiments → selected retrieval → generation evaluated`.

gold 검증 실패 또는 입력 artifact 불일치 시 retrieval 평가로 진행하지 않는다. 검색 실험은 baseline이 생성된 뒤에만 비교 가능하다.

## 8. Failures, Exceptions, and Boundary Conditions

- 없는 chunk ID, 누락된 gold case, 중복 evidence는 gold validation 실패다.
- answerable인데 gold가 없거나 unanswerable인데 gold가 있으면 case 계약 위반이다.
- retriever가 알 수 없는 chunk ID나 중복 ID를 반환하면 ranking error다.
- evidence 일부만 찾으면 multi-evidence miss다.
- 표·수치·정확한 규칙명 검색 실패는 별도 failure type으로 분류한다.
- embedding model/index가 없으면 Dense 실험만 차단하고 BM25 실험까지 함께 성공했다고 보고하지 않는다.
- 문서 밖 질문은 retrieval 성공으로 간주하지 않고 abstention 평가로 보낸다.

## 9. Inputs and Outputs

입력은 `chunks.jsonl`, gold cases JSONL, 평가 질문, retriever 설정과 선택적 ranked-result fixture다.

출력은 `gold_validation.json`, retriever별 summary/details, `retrieval_comparison.json`, `retrieval_failures.jsonl`, 선택적 generation 평가 결과다.

## 10. Scope and Non-goals

포함: gold 검증, Dense/BM25 baseline, Hybrid/RRF, reranker 비교, failure taxonomy, parent-context 실험, generation/unanswerable 평가의 계약.

제외: 새로운 chunk-size 최적화, PDF 원문 재작성, 특정 LLM·embedding provider 종속, 운영용 vector DB 배포, 자동 모델 선택.

## 11. Priorities and Trade-offs

1. gold 정합성과 평가 재현성
2. Dense/BM25 baseline 수치 확보
3. Hybrid과 reranker 비교
4. retrieval 실패 기반 전처리 feedback
5. generation과 abstention 확장

초기에는 최고 성능보다 실패 원인 분리와 재현 가능한 baseline을 우선한다.

## 12. Success Conditions and Acceptance Criteria

- 50개 평가 질문에 대해 gold validation 결과가 생성된다.
- invalid chunk ID와 missing gold case가 0이다.
- answerable/unanswerable 및 멀티근거 사례가 보고된다.
- Dense와 BM25 각각의 Recall@K, MRR, Evidence Recall 결과가 생성된다.
- Hybrid과 reranker 비교 결과가 동일 평가셋 기준으로 생성된다.
- 모든 실패 사례가 taxonomy 중 하나로 분류된다.
- retrieval 평가가 끝난 뒤에만 generation 평가가 실행된다.
