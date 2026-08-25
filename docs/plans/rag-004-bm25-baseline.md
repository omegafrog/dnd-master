# RAG-004 BM25 Baseline

- 상태: `completed`
- 의존성: RAG-002

## 구현 목적

정확한 규칙명·수치·약어 검색에서 Dense가 놓칠 수 있는 근거를 BM25로 측정하고 Dense와 동일한 평가 계약에 연결한다.

## 범위

- normalized `embedding_text` 기반 BM25 adapter
- tokenization/index lifecycle
- BM25 summary/details와 experiment metadata

## 수용 기준

- Dense와 동일한 50-case snapshot, cutoffs, metrics를 사용한다.
- exact keyword, numeric rule, rule-name 질문의 per-case 결과를 확인할 수 있다.
- 반환 ID는 evaluator chunk ID이며 unknown/duplicate 계약을 통과한다.

## 테스트 계약

- tokenization/ranking 단위 테스트
- query keyword와 numeric term fixture 테스트
- 통합 테스트: clean chunks + BM25 → report
- CLI entity 통합 테스트: 빈 index와 missing query 처리

## 구현 경계

허용: BM25 adapter와 baseline runner. 금지: Dense model, Hybrid/Reranker, generation.
