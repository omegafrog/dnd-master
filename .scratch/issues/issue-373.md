---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/373
---

## 상태

Planned

## 의존성

#363, #364

## 구현 목적

비전투 엔진이 사용 조건과 자원을 결정적으로 처리하고 환경 결과는 GM이 판정하게 한다.

## 범위

- 능력 사용과 목표는 사용자가 선언한다. GM은 비전투 효과를 판정하지만 파티 행동이나 자원 소모를 임의로 시작하지 않는다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 전투 조준·턴 실행 엔진을 적용하지 않고 능력의 저장된 간략 설명과 근거 위치를 GM에게 제공한다.
- 사용 가능 여부와 자원 비용은 비전투 엔진이 결정적으로 검증·기록한다.
- 사용이 확정되면 결과가 없더라도 비용을 한 번 차감한다.
- 대상이 지도 위치나 공간 범위를 요구하면 중앙 지도 UI에서 범위를 먼저 보여준다.

## 테스트 계약

- 단위/정책: 전투/비전투 경계, 자원 확정, GM 판정 전달, 공간 범위 표시.
- `ui ~ entity` E2E: 비전투 지도에서 능력을 사용해 GM 결과와 저장된 자원 상태를 확인한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-004 환경 응용 흐름 — ![UC-004 환경 응용 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-004.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-22, AC-39, UC-004.
- 수용 기준: 전투 조준·턴 실행 엔진을 적용하지 않고 능력의 저장된 간략 설명과 근거 위치를 GM에게 제공한다.
- 반례: GM이 사용 가능 여부나 자원 비용을 대신 정하거나 전투 실행 엔진을 비전투 사용에 적용한다.
- 검증 사례: 단위/정책: 전투/비전투 경계, 자원 확정, GM 판정 전달, 공간 범위 표시.
