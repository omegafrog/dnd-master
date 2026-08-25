# RAG-001 Gold Validation

- 상태: `completed`
- 의존성: 없음

## 구현 목적

50개 평가 질문과 현재 `chunks.jsonl`의 관계를 명시적인 gold case 계약으로 고정하고, retrieval 실험 전에 잘못된 근거·answerability·evidence 구성을 차단한다.

## 범위

- gold case 입력 모델과 JSONL schema
- `answerable`, `gold_chunk_ids`, evidence groups, 중복/누락 검증
- `gold_validation.json` 생성 CLI
- 기존 canonical-key 매핑과 evaluator chunk ID 매핑

## 수용 기준

- 50개 case ID가 유일하고 누락이 없다.
- answerable case는 하나 이상의 실제 chunk ID를 가진다.
- unanswerable case는 gold evidence가 없다.
- invalid chunk ID, duplicate evidence, missing case가 모두 보고된다.
- validation 실패 시 retrieval 평가가 실행되지 않는다.

## 테스트 계약

- 정책 단위 테스트: answerability/evidence/중복/누락 규칙
- 계약 테스트: JSONL schema와 `gold_validation.json`
- 통합 테스트: clean run의 `chunks.jsonl` + 50-case fixture → validation report
- CLI entity 통합 테스트: 잘못된 입력에서 비정상 종료와 상세 오류 확인

## 구현 경계

허용: `src/preprocessing_agent/eval/gold.py`, gold schema, gold CLI와 테스트. 금지: retriever 구현, embedding, vector store, generation.
