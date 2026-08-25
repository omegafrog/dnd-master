# RAG-005 Hybrid Comparison

- 상태: `completed`
- 의존성: RAG-003, RAG-004

## 구현 목적

Dense와 BM25 결과를 RRF로 결합하고 두 baseline과 동일한 조건으로 비교해 retrieval 실패 원인을 수치와 taxonomy로 연결한다.

## 범위

- Reciprocal Rank Fusion adapter
- Dense/BM25/Hybrid comparison report
- `RETRIEVAL_MISS`, `RANKING_ERROR`, `CHUNK_BOUNDARY`, `QUERY_MISMATCH`, `METADATA_MISMATCH`, `MULTI_EVIDENCE_MISS`, `TABLE_RETRIEVAL_FAILURE`
- `retrieval_failures.jsonl`

## 수용 기준

- 세 실험이 동일 snapshot에서 비교된다.
- Hybrid Recall@5가 baseline 대비 개선 또는 동률인지 확인된다.
- 각 실패 case에 found/missing evidence와 taxonomy가 기록된다.
- retrieval failure와 preprocessing warning이 구분된다.

## 테스트 계약

- RRF rank formula 단위 테스트
- failure taxonomy 결정표 테스트
- 통합 테스트: 세 ranked fixture → comparison/failure artifacts
- CLI entity 통합 테스트: malformed experiment 하나가 전체 비교를 오염시키지 않음

## 구현 경계

허용: RRF, comparison, failure analyzer. 금지: reranker, parent expansion, generation.
