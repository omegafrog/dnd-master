# RAG-006 Reranker and Context

- 상태: `completed`
- 의존성: RAG-005

## 구현 목적

Hybrid top-30 후보를 재정렬하고, 필요할 때 child chunk의 parent context를 generation 입력으로 확장하되 retrieval recall과 context 확장을 분리 측정한다.

## 범위

- `RerankerPort`와 top-30→top-5 계약
- MRR/nDCG@5와 reranked Recall 지표
- parent-child context builder
- evaluator ID와 Java UUID/locator ACL seam 문서화

## 수용 기준

- reranker 전후 first gold rank가 비교된다.
- parent expansion은 retrieval gold ID를 변경하지 않는다.
- context에는 source citation과 parent/child 관계가 남는다.
- live Java adapter가 없으면 offline adapter로 명확히 평가된다.

## 테스트 계약

- reranker ordering 단위 테스트
- parent expansion provenance 테스트
- 통합 테스트: Hybrid fixture → reranked/context report
- CLI entity 통합 테스트: missing parent는 retrieval failure로 분류

## 구현 경계

허용: reranker/context adapter. 금지: LLM generation, preprocessing rewrite.
