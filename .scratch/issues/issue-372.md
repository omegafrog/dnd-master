---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/372
---

## 상태

Planned

## 의존성

#364, #371

## 구현 목적

지도에서 조준을 확인한 능력을 최신 전투·지도·캐릭터 상태로 재검증한 뒤 실행한다.

## 범위

- 사용자가 행동과 비용·효과 선택을 확정한 요청만 실행한다. GM 판정 대체는 선택을 대신하지 않고 효과 해결만 맡는다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 지도에서 사용자가 확정한 주문·기술의 대상·선택·범위를 서버가 최신 상태로 재검증한다.
- 행동·사거리·대상·자원·집중·지속·발동·높은 등급 선택 등 정의된 규칙을 처리한다.
- 확정된 사용 비용은 효과가 없더라도 한 번만 차감하고 중복 요청은 상태 변경을 반복하지 않는다.
- 엔진이 효과를 처리하지 못하면 GM이 효과 해결 전체를 대신하되 비용 기록은 엔진이 보장한다.

## 테스트 계약

- 단위/정책: 최신 상태 재검증, 엔진 지원/GM 대체, 비용 멱등성, 효과 선택.
- `ui ~ entity` E2E: 지도에서 주문·기술 조준 후 실행해 전투 결과와 캐릭터 시트 자원 상태까지 확인한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-002 미리보기와 실행 — ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-11, AC-15, AC-16, AC-38, AC-43, BR-14, BR-15, BR-17, BR-46, BR-47, EX-04, EX-07, G-04, G-05, ST-002, UC-002.
- 수용 기준: 지도에서 사용자가 확정한 주문·기술의 대상·선택·범위를 서버가 최신 상태로 재검증한다.
- 반례: 오래된 상태 또는 중복 요청에서도 자원을 다시 소모하거나 최신 상태 검증을 생략한다.
- 검증 사례: 단위/정책: 최신 상태 재검증, 엔진 지원/GM 대체, 비용 멱등성, 효과 선택.
