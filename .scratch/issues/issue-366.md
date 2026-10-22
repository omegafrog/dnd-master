---
status: completed
source_issue: https://github.com/omegafrog/dnd-master/issues/366
---

## 상태

In Progress

## 의존성

없음

## 구현 목적

탐험 이동을 캐릭터가 아니라 그룹 단위로 표현하고 분리·합류 상태를 저장한다.

## 범위

- Solo Player가 개인을 포함한 파티의 모든 그룹 구성·대열·분리·합류를 직접 선택하고 확정한다. GM은 파티 그룹을 대신 움직이지 않는다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 개인도 구성원 한 명인 그룹으로 취급하며 탐험 지도에 그룹당 토큰 하나를 둔다.
- 그룹 구성원, 전열·중열·후열 대열, 위치와 가장 느린 구성원 기준 이동 속도를 저장한다.
- 분리는 현재 위치에서 그룹과 대열을 새로 정한 뒤 각각 경로를 지정한다.
- 같은 칸에 있어도 자동 합류하지 않고 명시적 합류 선택만 적용한다.

## 테스트 계약

- 단위/정책: 단일 인원 그룹, 분리·합류, 대열 구성, 느린 구성원 속도.
- `ui ~ entity` E2E: 지도에서 그룹 분리/합류를 조작해 저장된 그룹 구성·토큰·대열 상태까지 확인한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-005 탐험 이동 흐름 — ![UC-005 탐험 이동 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-005.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-31, AC-32, BR-38, BR-39, G-09, UC-005.
- 수용 기준: 개인도 구성원 한 명인 그룹으로 취급하며 탐험 지도에 그룹당 토큰 하나를 둔다.
- 반례: 그룹을 사용자 선택 없이 자동 병합하거나 개인을 그룹 단위가 아닌 별도 규칙으로 처리한다.
- 검증 사례: 단위/정책: 단일 인원 그룹, 분리·합류, 대열 구성, 느린 구성원 속도.
