# RAG-002 Retrieval Contract

- 상태: `completed`
- 의존성: RAG-001

## 구현 목적

Dense/BM25가 같은 입력과 출력 계약을 사용하도록 ranked result를 고정하고 Recall@20까지 재현 가능한 평가 harness를 만든다.

## 범위

- `RetrieverPort`와 `RankedChunk` 계약
- top-1/3/5/10/20 cutoff
- Recall@K, MRR, Evidence Recall@K, optional nDCG
- known ID·중복 ID·순위·query 누락 검증
- `retrieval_<experiment>_summary.json`와 details JSONL writer

## 수용 기준

- 모든 metric이 동일 gold snapshot과 cutoff로 계산된다.
- top-20 ranked result가 허용된다.
- 알 수 없는/중복 chunk ID는 ranking error로 분류된다.
- per-case first gold rank와 evidence completeness가 저장된다.

## 테스트 계약

- metric 단위 테스트: cutoff, MRR, multi-evidence
- contract 테스트: invalid ranked IDs와 duplicate IDs
- 통합 테스트: offline ranked fixture → summary/details
- CLI entity 통합 테스트: gold validation 실패 시 retrieval 차단

## 구현 경계

허용: `eval/retrieval.py`, report/CLI contract, offline fixture adapter. 금지: Dense/BM25 모델 구현, Java live adapter, generation.
