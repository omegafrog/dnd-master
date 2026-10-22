---
status: in-progress
source_issue: https://github.com/omegafrog/dnd-master/issues/367
---

## 상태

Planned

## 의존성

#365, #366

## 구현 목적

시간을 멈춘 상태에서 그룹 우선권대로 이동시키고 사건과 공통 환경 시간을 순서대로 처리한다.

## 범위

- 그룹 이동과 경로는 사용자가 제출·확정하고, GM은 세계·적 행동과 조우·환경 판정만 맡는다.
- 해당 제품·구조 명세의 이 수용 기준에 직접 필요한 기존 화면, API, 정책, 저장소 경계만 수정한다.

## 수용 기준

- 이동 시작 전 세계 시간을 멈추고 이동 그룹의 우선권을 정한다. 우선권이 같은 그룹의 순서는 무작위로 결정한다.
- 각 그룹은 토큰을 클릭·드래그해 경로를 정하고 확인한 뒤 한 칸씩 이동한다.
- 함정·발견·접촉·기습 등 중단 사건이 발생하면 해당 칸에서 전체 그룹 이동을 멈추고 해결한다.
- 위협 발견은 캐릭터별로 판정하며 발견 사실은 발견자에게만 알려진다. 공유 행동 뒤에만 그룹이 알고, 적에게 들릴지는 GM이 판정한다.
- 모든 그룹 차례 뒤 환경을 6초 진행하고 적·순찰, 함정·환경, 지속시간·제한 시간을 처리한다. 저장된 순찰 경로가 없으면 GM이 각 환경 단계에서 주변 적의 이동과 환경 변화를 판단한다. 중단 뒤 남은 환경 단계를 이어간다.

## 테스트 계약

- 단위/정책: 우선권 동점·무작위, 사건별 중단/재개, 캐릭터별 인지, 6초 환경 처리 순서.
- `ui ~ entity` E2E: 그룹 경로 제출에서 시작해 저장된 순서·사건·환경 단계 결과까지 확인한다.

## 관련 명세

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md

## 다이어그램

- 제품: UC-005 탐험 이동 흐름 — ![UC-005 탐험 이동 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-005.activity.svg?raw=true)

## 추적 정보

- 요구사항 식별자: AC-34, AC-35, AC-41, BR-41, BR-42, BR-43, BR-49, G-09, G-10, UC-005.
- 수용 기준: 이동 시작 전 세계 시간을 멈추고 이동 그룹의 우선권을 정한다. 우선권이 같은 그룹의 순서는 무작위로 결정한다.
- 반례: 사건이 발생한 뒤에도 그룹 이동 또는 6초 환경 처리를 멈추지 않고 계속 진행한다.
- 검증 사례: 단위/정책: 우선권 동점·무작위, 사건별 중단/재개, 캐릭터별 인지, 6초 환경 처리 순서.
