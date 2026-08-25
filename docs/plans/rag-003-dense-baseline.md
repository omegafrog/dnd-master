# RAG-003 Dense Baseline

- 상태: `ready-for-agent`
- 의존성: RAG-002

## 구현 목적

현재 388개 clean chunk와 50개 gold case를 기준으로 Dense retrieval의 첫 수치 기준선을 만든다.

## 범위

- injected embedding/index adapter
- `embedding_text` 기반 chunk indexing
- query embedding과 ranked evaluator ID 변환
- Dense summary/details artifact와 실행 metadata

## 수용 기준

- answerable case 전체에 대해 Recall@1/3/5/10/20, MRR, Evidence Recall이 생성된다.
- source run hash와 gold snapshot hash가 결과에 기록된다.
- embedding provider가 없는 환경에서는 명확히 blocked 되고 가짜 성공을 내지 않는다.

## 테스트 계약

- adapter 단위 테스트: deterministic vector fixture
- contract 테스트: embedding/index ID round trip
- 통합 테스트: clean chunks + fixture embedding → Dense report
- CLI entity 통합 테스트: provider failure가 baseline failure로 기록됨

## 구현 경계

허용: Dense adapter와 baseline runner. 금지: BM25/Hybrid/Reranker 변경, preprocessing mutation.
