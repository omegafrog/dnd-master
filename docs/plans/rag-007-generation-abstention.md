# RAG-007 Generation Abstention

- 상태: `ready-for-agent`
- 의존성: RAG-006

## 구현 목적

검색이 안정된 뒤 답변 생성, citation, 문서 밖 질문의 abstention을 retrieval 결과와 분리해 최종 RAG 평가로 확장한다.

## 범위

- context builder와 generation adapter
- answer, citations, answerability/abstention output
- correctness, faithfulness, citation correctness, context utilization
- unanswerable false-answer/abstention accuracy

## 수용 기준

- generation은 validated retrieval context에서만 실행된다.
- 답변별 citation이 chunk ID로 검증된다.
- unanswerable case에서 근거 없는 답변과 올바른 abstention이 구분된다.
- retrieval failure와 generation failure가 별도 artifact로 기록된다.

## 테스트 계약

- citation/abstention policy 단위 테스트
- generation output schema 계약 테스트
- 통합 테스트: ranked context fixture → generation report
- CLI entity 통합 테스트: retrieval failure 시 generation 차단

## 구현 경계

허용: generation/abstention evaluator와 adapter contract. 금지: 새로운 retriever algorithm, preprocessing policy.
