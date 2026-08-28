# RAG-021: 실패 폐쇄형 GM 후보 수명주기

- 상태: `completed`
- 의존성: RAG-020
- Product Spec: UC-GM-001~002, BR-GM-001~002, BR-GM-004~005, AC-GM-002, AC-GM-004
- Architecture Spec: Sections 3.3~3.8, 4.3~4.9, 6.5~6.7, 7.1~7.4
- GitHub Issue: [#196](https://github.com/omegafrog/dnd-master/issues/196)
- Parent Issue: [#194](https://github.com/omegafrog/dnd-master/issues/194)

## 구현 목적

공급자가 narration, judgment 또는 필요한 citation을 누락했을 때 중립 문장이나 임의 근거를 채워 정상 턴처럼 확정하지 않는다. 최초 후보와 위반 목록을 사용해 같은 Effective Selection과 Evidence Pack으로 한 번만 보정하고, 그래도 실패하면 모험 상태를 보존한 `FAILED_RETRYABLE` 턴으로 종료한다.

## 사용자·엔티티 흐름

```text
플레이어 행동
→ GmTurn STARTED/PROCESSING
→ 최초 후보 생성·전체 검증
→ 실패 시 같은 실행 선택으로 1회 repair
→ 성공: Adventure + RuntimeTurn + GmTurn 원자적 COMMITTED
→ 실패: Adventure 불변 + GmTurn FAILED_RETRYABLE
```

## 구현 범위

- 필수 candidate envelope와 `GmCandidateViolation`
- parser/normalizer의 narration·judgment·citation 의미 기본값 제거
- 최초 후보와 위반을 입력으로 하는 최대 1회 response repair
- 최초/보정 후보에 동일한 전체 검증 적용
- `GmTurn`의 `FAILED_RETRYABLE` 상태, command fingerprint와 terminal idempotency
- Adventure, conversation, stage, RuntimeTurn의 성공 트랜잭션 원자성
- rollback 밖의 안전한 실패 감사 기록
- 같은 command 재시도에서 중복 provider 호출·중복 상태 변경 방지

## 수용 기준

- 빈 객체, 필수 필드 누락, malformed candidate가 의미 기본값으로 성공하지 않는다.
- repair는 최초 호출과 같은 Effective Selection, endpoint version, Evidence Pack을 사용한다.
- provider/candidate/final validation 실패 시 모험 version, conversation, stage가 바뀌지 않는다.
- 성공한 후보만 한 번의 원자적 commit으로 저장된다.
- 동일 command의 terminal 결과는 다시 열리거나 중복 턴을 만들지 않는다.

## 테스트 계약

- 정책 단위 테스트: 필수 필드, 위반 분류, repair 1회 상한, terminal transition, idempotency
- application 테스트: initial success, repair success, repair failure, provider timeout, final-validator failure
- transaction 통합 테스트: 성공 원자 commit과 실패 시 adventure/conversation/version rollback
- UI ↔ Entity E2E: 플레이어 행동 → malformed initial/repair → 재시도 안내 → DB 상태 불변
- 회귀 E2E: 정상 후보는 한 번의 호출로 기존 플레이 흐름을 유지

## 제외 범위

- evidence 최대 8개 선택과 claim-level citation 정책
- tactical preparation retry budget
- 공급자 전환 fallback

## 완료 증거

- 후보/repair/provider failure fixture
- 성공·실패 전후 Adventure/GmTurn/RuntimeTurn DB 대조
- 플레이어 화면의 안전한 retryable failure 증거
