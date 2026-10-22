---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/368
---

## 상태

Planned

## 의존성

#366, #367

## 구현 목적

전투가 시작될 때 그룹의 위치와 대열을 캐릭터별 전투 위치로 바꾸고 전투 종료 후 그룹을 복원한다.

## 범위

- 전투 시작 위치와 전투 종료 뒤 그룹 구성 복원은 사용자가 조작하는 파티 캐릭터 전원에 적용한다. GM은 적만 조작한다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 전투 전환 시 그룹 위치·대열·구성원을 기준으로 각 캐릭터의 전투 토큰을 배치한다.
- 좁은 공간 등 자동 배치할 수 없는 상태는 GM 판단 흐름에 넘긴다.
- 전투 종료 시 기존 그룹 구성과 대열을 복원하고 별도 그룹은 자동 합치지 않는다.

## 테스트 계약

- 단위/정책: 그룹에서 전투 배치로의 변환, 복원, 배치 불가 처리.
- `ui ~ entity` E2E: 조우 전환 UI에서 시작해 전투 토큰 저장과 전투 종료 후 복원된 그룹 상태까지 확인한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-005 탐험 이동 흐름 — ![UC-005 탐험 이동 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-005.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-36, BR-44, BR-45, UC-005.
- 수용 기준: 전투 전환 시 그룹 위치·대열·구성원을 기준으로 각 캐릭터의 전투 토큰을 배치한다.
- 반례: 전투 종료 시 원래 그룹 대열을 복원하지 않거나 서로 다른 그룹을 자동 병합한다.
- 검증 사례: 단위/정책: 그룹에서 전투 배치로의 변환, 복원, 배치 불가 처리.
