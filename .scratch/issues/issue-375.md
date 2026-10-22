---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/375
---

## 상태

Planned

## 의존성

#367, #369, #371, #372, #373, #374

## 구현 목적

자연어로 선언한 이동·능력도 지도 미리보기와 동일한 판정·확정 경계를 거치게 한다.

## 범위

- 자연어 행동도 사용자가 선언하며, GM은 이를 사용자 승인 없이 파티 행동으로 선택·확정하지 않는다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 자연어 행동은 같은 능력 정의·범위·대상·이동 검증을 사용한다.
- 지도상 위치나 범위가 있는 선언은 중앙 지도 UI에 선택 대상과 범위를 표시하고 사용자 확정 후 실행한다.
- 지도 조작과 자연어 입력은 동일한 엔티티 상태와 자원 결과를 만든다.
- 거부·재시도·GM 대체 결과도 숨겨진 정보를 노출하지 않는다.

## 테스트 계약

- 단위/정책: 자연어에서 행동 선택으로의 변환, 지도 UI 확인 경계, 결과 일관성.
- `ui ~ entity` E2E: 같은 행동을 지도 UI와 자연어로 수행해 저장된 지도·전투·캐릭터 상태가 규칙상 일치하는지 확인한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-002 미리보기와 실행 — ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)
- 제품: UC-005 탐험 이동 흐름 — ![UC-005 탐험 이동 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-005.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-19, AC-20, BR-18, BR-19, BR-21, EX-10, G-07.
- 수용 기준: 자연어 행동은 같은 능력 정의·범위·대상·이동 검증을 사용한다.
- 반례: 자연어 선언이 직접 지도 조작과 다른 규칙 검증·권한·저장 결과를 만든다.
- 검증 사례: 단위/정책: 자연어에서 행동 선택으로의 변환, 지도 UI 확인 경계, 결과 일관성.
