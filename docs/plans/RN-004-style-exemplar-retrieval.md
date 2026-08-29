# Plan RN-004: GM Style Exemplar Retrieval

- Issue: #229
- Parent Issue: #225
- Status: `planned`
- Dependencies: RN-003
- Source: #209

## 구현 목적

룰·시나리오 사실을 검색하는 Knowledge RAG와 좋은 GM 표현을 참고하는 Exemplar retrieval을 분리한다. Writer 품질은 높이되 exemplar의 사건·규칙·비밀이 현재 세계에 오염되지 않게 한다.

## 구현 범위

- `StyleExemplar`, `ExemplarQuery`, `ExemplarResult`, provenance contract.
- metadata filter → semantic retrieval → rerank → bounded Top-K.
- separate corpus/index adapter와 admission policy.
- verifier ERROR 응답 제외, generic/anonymized exemplar 우선.
- Knowledge와 Exemplar를 구분한 Writer context handoff.
- retrieval IDs/scores/query/model/latency audit.

## 제외 범위

- Knowledge RAG 전처리/인덱스 교체.
- exemplar 자동 승격 학습.
- Writer/Verifier 자체 재설계.

## Acceptance Criteria

- Knowledge와 Exemplar provenance가 분리된다.
- scene purpose/interaction/tone/pacing/length가 검색에 반영된다.
- K configurable, 기본 1~3.
- verifier ERROR 응답은 검색 후보가 될 수 없다.
- exemplar 사건·entity·규칙이 Runtime State에 추가되지 않는다.
- 적절한 exemplar가 없으면 빈 context로 정상 동작.

## Test Contract

- Policy unit: metadata filtering, K bound, admission/pollution rules, provenance.
- Contract/integration: separate index adapter, rerank response, empty/error fallback.
- UI ~ entity E2E: 탐색/긴장 장면에서 style context가 writer에 전달되며 사실 근거·플레이어 응답에는 exemplar 설정이 누출되지 않음.

## 구현 순서

1. exemplar types/port/provenance.
2. catalog admission/index adapter.
3. runtime writer handoff.
4. retrieval audit와 E2E.
