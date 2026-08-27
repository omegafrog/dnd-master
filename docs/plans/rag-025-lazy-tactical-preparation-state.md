# RAG-025: 현재 단계 지연 전술 준비 상태

- 상태: `planned`
- 의존성: RAG-023, RAG-024
- Product Spec: UC-COMBAT-002, BR-COMBAT-005~007, AC-COMBAT-005~006
- Architecture Spec: Sections 3.3~3.8, 4.2~4.9, 5.10, 6.1~6.7, 11.1, 11.4~11.5
- GitHub Issue: [#200](https://github.com/omegafrog/dnd-master/issues/200)
- Parent Issue: [#194](https://github.com/omegafrog/dnd-master/issues/194)

## 구현 목적

계획의 전술 준비 필요 의도와 실제 준비 job·scene snapshot을 하나의 상태로 혼동하지 않는다. 미래 단계는 `REQUIRED_PENDING`으로 남기고, 모험 시작 또는 단계 전환으로 현재 단계가 된 경우에만 전술 장면을 준비하며 Combat Skeleton과 일치하는 READY 장면만 맵으로 활성화한다.

## 사용자·엔티티 흐름

```text
published plan의 tactical requirement
→ 현재 단계 여부와 job/scene snapshot 합성
→ NOT_REQUIRED 또는 REQUIRED_PENDING
→ 현재 진입 시 PREPARING job
→ Combat Skeleton 일치 검증
→ READY 후 map activation / 실패 시 FAILED_RETRYABLE
```

## 구현 범위

- plan intent, preparation job, scene snapshot을 합성하는 read model
- NOT_REQUIRED, REQUIRED_PENDING, PREPARING, READY, FAILED_RETRYABLE 상태
- 모험 시작·단계 전환 시 현재 REQUIRED 단계의 create-or-get preparation
- 미래 단계 job/좌표 선생성 금지
- tactical participants/outcomes와 Combat Skeleton 일치 정책
- READY 전 tactical map activation guard
- preparation 실패와 명시적 retry, 기존 job concurrency/idempotency 보존
- player-safe 상태 투영과 내부 진단 분리

## 수용 기준

- 미래 전투 단계는 scene 부재를 NOT_REQUIRED로 표시하지 않고 REQUIRED_PENDING으로 읽힌다.
- 현재 단계가 되기 전에는 tactical job과 구체 좌표를 만들지 않는다.
- 현재 REQUIRED 단계는 READY 전까지 tactical map을 활성화할 수 없다.
- tactical scene의 적·보스 정체성과 수량 범위가 Combat Skeleton과 일치한다.
- 생성 실패는 텍스트 진행으로 우회하지 않고 FAILED_RETRYABLE과 명시적 retry를 제공한다.

## 테스트 계약

- 정책 단위 테스트: composed state matrix와 Combat Skeleton consistency
- application 테스트: start/advance create-or-get, duplicate claim, failure/retry
- persistence 통합 테스트: durable job과 scene snapshot의 재시작 복구
- UI ↔ Entity E2E: Potent Brew 시작/단계 진입 → REQUIRED_PENDING/PREPARING → READY → 맵 활성화
- 차단 E2E: invalid scene 또는 generator failure → 맵 미활성·FAILED_RETRYABLE

## 제외 범위

- 미래 전술 장면 세부 배치 선생성
- GM 후보 repair budget 변경
- Combat Map 자체의 이동/전투 규칙 재설계

## 완료 증거

- 상태 조합 fixture와 API 응답
- 준비 job/scene/map activation DB 대조
- 브라우저 단계 전환과 실패·재시도 증거
