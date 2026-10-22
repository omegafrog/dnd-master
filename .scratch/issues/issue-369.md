---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/369
---

## 상태

Planned

## 의존성

#368

## 구현 목적

전투 지도에서 경로를 정하고 확인한 뒤 한 칸씩 이동하며 실제 도달 상태를 반영한다.

## 범위

- 이동 캐릭터 토큰·경로를 선택하고 확인하는 주체는 사용자이며, GM은 이를 대신 선택하거나 실행하지 않는다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 전투 지도는 상호작용 화면의 중앙에 배치하고, 선택한 캐릭터 토큰을 클릭·드래그해 경로를 정한다.
- 확인 전에는 토큰 위치·안개·이동량을 바꾸지 않는다.
- 확인 후 한 칸씩 이동하며 벽·문·지형·점유·이동량을 검증하고 매 칸 안개를 갱신한다.
- 중간 사건이나 장애물에서 멈추면 실제 도달 위치와 사용한 이동량만 저장한다.

## 테스트 계약

- 단위/정책: 경로 유효성, 확인 전 읽기 전용, 칸별 안개·이동량, 중단 위치.
- `ui ~ entity` E2E: 중앙 지도에서 경로를 지정·확인해 지도 토큰·안개·이동 결과 저장까지 확인한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-002 미리보기와 실행 — ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-05, AC-06, AC-07, AC-08, BR-05, BR-06, BR-08, BR-16, EX-08, G-03, UC-002.
- 수용 기준: 전투 지도는 상호작용 화면의 중앙에 배치하고, 선택한 캐릭터 토큰을 클릭·드래그해 경로를 정한다.
- 반례: 경로 확인 전에 위치·안개를 바꾸거나 중단 지점 이후 이동량을 소비한다.
- 검증 사례: 단위/정책: 경로 유효성, 확인 전 읽기 전용, 칸별 안개·이동량, 중단 위치.
