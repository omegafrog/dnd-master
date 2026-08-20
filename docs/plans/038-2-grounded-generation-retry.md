# 038-2 근거 기반 전술 장면 생성과 3회 재생성

- Issue: [#175](https://github.com/omegafrog/dnd-master/issues/175)
- Parent: [#179](https://github.com/omegafrog/dnd-master/issues/179)
- 상태: `blocked`
- 의존성: `038-1`

## 구현 목적

스토리북·지도 근거가 우선인 타입화된 전술 장면 후보를 생성하고, 검증 실패에는 위반 피드백을 담아 최대 세 번만 재생성한다. 세 번째 실패는 모험 시작을 차단하는 명시적 상태가 된다.

## 범위

- `AdventureStoryPlanGenerationPort`에 전술 장면 후보·인용·AI 보완 근거를 포함한 typed contract를 추가한다.
- 애플리케이션 서비스가 후보 검증, 인용 조정, 위반 피드백, 3회 한도, `READY`/`BLOCKED` 상태와 이력을 조율한다.
- 원문과 충돌하거나 새로운 핵심 사실을 만든 추론을 거부한다.

## 비범위

- 런타임 Combat Map materialization과 공개 API projection은 다음 계획에서 처리한다.

## 완료 기준

- 유효 후보는 불변 revision으로 저장되고, 시작 전 재생성은 새 배치를 만든다.
- 위반 후보에는 제한된 재생성만 실행하며 세 번째 실패 뒤 시작이 차단된다.
- 근거 없는 핵심 보스·보상·결말 사실은 발행되지 않는다.

## 테스트 계약

- 정책 단위: 근거 우선, 위반 피드백, 정확히 세 번의 호출, 차단 상태와 revision 이력을 검증한다.
- 어댑터 통합: AI 응답의 typed parsing과 citation reconciliation을 검증한다.
- UI → 엔티티 E2E: 게임 준비 화면의 재생성 결과가 `READY` 또는 `BLOCKED`를 정확히 보여 주고 차단 시 시작 제어를 비활성화한다.
