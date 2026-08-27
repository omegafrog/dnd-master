# RAG-020: 실제 GM 공급자 실행 정체성

- 상태: `ready-for-agent`
- 의존성: 없음
- Product Spec: UC-PROVIDER-001, BR-PROVIDER-001~002, AC-PROVIDER-001
- Architecture Spec: Sections 3.5, 4.6~4.8, 5.5, 5.9, 11.3
- GitHub Issue: [#195](https://github.com/omegafrog/dnd-master/issues/195)
- Parent Issue: [#194](https://github.com/omegafrog/dnd-master/issues/194)

## 구현 목적

세션이 요청한 공급자·모델과 AI Game Master가 실제 호출한 엔드포인트·모델을 별도 값으로 보존한다. 한 턴의 최초 호출과 후속 보정이 동일한 실행 선택을 사용하게 할 기반을 만들고, 품질 기록이 표시용 요청값을 실제 실행값으로 오인하지 않게 한다.

## 사용자·엔티티 흐름

```text
GM 설정 선택
→ RequestedGmProviderSelection 저장
→ 턴 시작 시 endpoint/version 하나로 해석
→ adapter가 EffectiveGmProviderSelection 그대로 실행
→ internal v2 candidate envelope 반환
→ GmTurn/RuntimeTurn 감사 정보 저장·조회
```

## 구현 범위

- `RequestedGmProviderSelection`과 `EffectiveGmProviderSelection` 타입 및 검증
- 호출 전 단 한 번의 endpoint/model/reasoning 해석과 endpoint configuration version 고정
- `GmCompletionResult`가 실제 실행 선택을 후보와 함께 반환하는 포트 계약
- `/internal/v2/gm/agent-turns` request/response envelope와 구조화된 선택 실패
- requested/effective 필드를 분리하는 additive migration과 repository mapping
- 기존 internal v1 reader와 과거 row의 `LEGACY_UNKNOWN` 호환
- 요청값과 실행값 불일치에 대한 안전한 로그·metric

## 수용 기준

- adapter가 사용한 endpoint id/version, provider, model, reasoning이 Effective Selection과 정확히 일치한다.
- active endpoint가 요청값과 달라도 실제 실행값이 요청값으로 다시 라벨링되지 않는다.
- 턴 도중 endpoint 설정이 바뀌어도 해당 턴의 고정 선택은 변하지 않는다.
- 신규 턴은 requested/effective 값을 모두 저장하고 과거 row는 추정 없이 `LEGACY_UNKNOWN`으로 읽힌다.
- 해석할 endpoint가 없으면 공급자를 호출하지 않고 안정적인 선택 실패를 반환한다.

## 테스트 계약

- 정책 단위 테스트: resolver 우선순위, endpoint version 고정, 요청/실행 불일치, legacy mapping
- adapter 계약 테스트: Codex/Ollama/OpenAI-compatible fixture의 실제 model과 Effective Selection 일치
- internal API 계약 테스트: v2 envelope, v1 격리, 구조화된 409 오류
- UI ↔ Entity E2E: 설정 화면의 선택 → 플레이어 턴 → 실제 adapter invocation → GM turn audit row 일치
- persistence 통합 테스트: 신규 컬럼 round-trip과 기존 `provider_metadata` row 호환

## 제외 범위

- GM 후보 구조 검증과 한 번의 보정
- 근거 검색 상한 또는 인용 관련성 정책
- 공급자 간 자동 fallback

## 완료 증거

- WSL 테스트 명령과 결과
- provider별 invocation fixture
- v1/v2 contract fixture와 migration 검증
- 요청값·실행값·저장값 대조 결과
