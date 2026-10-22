---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/374
---

## 상태

Planned

## 의존성

#373

## 구현 목적

주문 기본 효과 이후 허용된 환경 작업으로 응용하고 실패해도 기본 효과와 비용을 반복하지 않는다.

## 범위

- 환경 응용은 사용자가 선택한 주문의 기본 효과와 비용을 확정한 뒤 처리하며, GM은 환경 결과만 판정한다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 주문 기본 효과·비용이 확정된 뒤에만 등록된 환경 작업 목록과 입력 제약을 GM에 제공한다.
- 목록에 있는 작업만 호출하고 판정은 기존 주사위 제출 흐름을 사용한다.
- 작업 오류는 GM에게 보정 가능한 구조로 반환하며 기본 효과 재실행과 추가 비용 차감을 금지한다.
- 제안·결과가 숨겨진 정보를 드러내지 않는다.

## 테스트 계약

- 단위/정책: 작업 허용 목록, 입력 제약, 실패 재시도, 기본 효과/비용 중복 방지.
- `ui ~ entity` E2E: 비전투 주문과 환경 응용을 실행해 기본 결과·자원·환경 변경 저장을 확인한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-004 환경 응용 흐름 — ![UC-004 환경 응용 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-004.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-23, AC-24, AC-25, AC-26, BR-23, BR-24, BR-25, BR-26, BR-27, BR-28, EX-12, EX-13, EX-14, UC-004.
- 수용 기준: 주문 기본 효과·비용이 확정된 뒤에만 등록된 환경 작업 목록과 입력 제약을 GM에 제공한다.
- 반례: 환경 작업 실패 뒤 주문 기본 효과를 다시 실행하거나 숨겨진 정보를 응답에 노출한다.
- 검증 사례: 단위/정책: 작업 허용 목록, 입력 제약, 실패 재시도, 기본 효과/비용 중복 방지.
