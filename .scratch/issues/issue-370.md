---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/370
---

## 상태

Planned

## 의존성

#369

## 구현 목적

중앙 지도 화면의 확대·축소와 지면 높이 표현을 포인터·범위 오버레이에 맞춘다.

## 범위

- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 지도 화면은 모험 UI의 중앙 작업 영역에 두고 주변 패널은 지도를 가리지 않도록 배치한다.
- 확대·축소 후 포인터, 격자, 토큰과 오버레이가 같은 지도 좌표를 가리킨다.
- 비행 높이는 지면으로부터의 수치로 표현해 거리·장애물·효과 포함 여부에 사용한다.
- 숨겨진 벽과 지도 상태는 공개 범위에 따라 보인다.

## 테스트 계약

- 단위/정책: 확대 배율별 좌표 변환, 화면 배치 상태, 높이 기반 거리·공개 경계.
- `ui ~ entity` E2E: 지도 확대·축소와 창 크기 변경에서 중앙 배치와 입력·오버레이 정렬을 확인하고 저장된 높이/위치 상태를 대조한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-002 미리보기와 실행 — ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-17, AC-18, BR-07, BR-20, EX-09, EX-11, G-06, UC-003.
- 수용 기준: 지도 화면은 모험 UI의 중앙 작업 영역에 두고 주변 패널은 지도를 가리지 않도록 배치한다.
- 반례: 확대·축소한 뒤 포인터·토큰 오버레이가 지도와 다른 위치를 가리키거나 숨겨진 벽을 드러낸다.
- 검증 사례: 단위/정책: 확대 배율별 좌표 변환, 화면 배치 상태, 높이 기반 거리·공개 경계.
