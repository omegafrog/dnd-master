---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/371
---

## 상태

Planned

## 의존성

#363, #370

## 구현 목적

주문·기술을 발사하거나 실행하기 전에 중앙 지도에서 사거리와 대상·효과 범위를 확인하게 한다.

## 범위

- 사용자가 배운 능력과 대상·지점을 선택한다. 미리보기는 상태를 바꾸지 않고 GM이 능력을 임의 선택하지 않는다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 지도 중앙 작업 영역에서 배운 주문·기술을 선택하고 대상 또는 지도 위치를 지정한다.
- 시전자·사용자 위치 기준 사거리와 유효 경로를 표시하고 경로 칸은 붉게 보여준다.
- 정의에 공간 범위가 있으면 원뿔·선·구 등 실제 효과 모양과 영향을 받을 수 있는 칸·대상을 표시한다.
- 선택 전과 미리보기 중에는 위치·자원·전투 상태를 바꾸지 않고 숨겨진 정보를 노출하지 않는다.
- 자연어로 선언해도 같은 지도 미리보기로 이어져 범위와 대상이 실행 전에 보인다.

## 테스트 계약

- 단위/정책: 사거리·경로·모양·대상 산출과 공개 정보 필터.
- `ui ~ entity` E2E: 주문·기술 선택 UI에서 시작해 중앙 지도 범위 표시를 확인하고 미리보기 전후 DB 상태가 같음을 검증한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-002 미리보기와 실행 — ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-09, AC-10, AC-12, AC-13, AC-14, AC-28, AC-29, AC-30, BR-09, BR-10, BR-11, BR-12, BR-13, BR-30, BR-31, BR-32, EX-05, EX-06, G-04, G-05, G-08, ST-004, UC-002.
- 수용 기준: 지도 중앙 작업 영역에서 배운 주문·기술을 선택하고 대상 또는 지도 위치를 지정한다.
- 반례: 미리보기 중 자원을 소모하거나 정의 자료와 다른 효과 범위·대상을 표시한다.
- 검증 사례: 단위/정책: 사거리·경로·모양·대상 산출과 공개 정보 필터.
