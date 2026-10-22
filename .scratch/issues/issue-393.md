---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/393
---

## 상태

Planned

## 의존성

[#378](https://github.com/omegafrog/dnd-master/issues/378)

## 구현 목적

준비 중 연결할 지도 파일과 동일한 원본 이미지의 저장 격자가 이미 있으면, 사용자가 그 격자를 재사용할지 기존 편집기에서 직접 수정할지 선택하게 한다.

## 범위

- 기존 지도 동일성 기준과 저장 격자 조회를 사용해 같은 모험 안의 원본 이미지 일치·저장 격자 유무를 판단한다.
- 두 조건이 맞을 때만 사용자에게 “저장된 격자 재사용” 또는 “직접 수정”을 묻는다. 선택 전에 지도 준비를 완료하지 않는다.
- 재사용은 원본 정렬을 바꾸지 않고 기존 정렬 적용 경로로 대상 지도에 반영한다. 직접 수정은 기존 격자 편집기를 연다.
- 새 화면·저장소·지도 생성 기능은 만들지 않는다. 일치하는 파일이나 저장 정렬이 없으면 기존 준비 흐름을 그대로 따른다.

## 수용 기준

- Product Spec UC-1·R-06·R-10을 충족한다.
- 같은 원본 이미지와 저장 격자가 있을 때만 재사용/수정 선택이 나타나며, 사용자의 선택 전에는 완료되지 않는다.
- 재사용 결과는 원본 저장값을 보존하고 대상 지도에 동일 정렬을 적용한다. 직접 수정은 기존 편집 흐름에서 저장한 정렬을 사용한다.
- 다른 이미지, 다른 모험, 또는 저장 격자 없음에서는 질문하지 않고 기존 준비 경로로 간다.

## 테스트 계약

- 단위/정책: `CombatMapViewServiceTest`, `MapPreparationPolicyTest` 및 `CombatMapFlow.test.tsx`를 확장해 이미지 동일성, 저장 정렬 없음, 재사용·편집 분기와 원본 보존을 확인한다.
- `ui ~ entity` E2E: `docs/testing/combat-movement-spell-range/preparation-grid-handoff.md`를 실제 준비 UI에서 실행해 재사용·직접 수정 선택 각각의 저장 결과와 원본 격자 보존을 확인한다. 상황 준비 시 기존 지도 격자를 쓰고 새로 추출하지 않는다.
- 성능: ST-01 측정 기준이 없고 이 동작은 이미지 처리량을 늘리지 않으므로 별도 성능 검증은 해당 없음이다.

- Plan ID: `scenario-situation-candidates-380`. 금지 사례: 다른 이미지나 모험의 격자를 재사용하거나, 확인 없이 저장 격자를 덮어쓰거나, 동일 파일 확인만으로 사용자 선택을 생략하지 않는다. 검증 사례: 같은 이미지+저장 격자에서 선택 질문, 재사용 시 원본 보존, 직접 수정 진입, 다른 이미지·저장값 없음에서 기존 흐름.

추적 검증 기준:
- 수용: 같은 이미지와 저장 격자가 있을 때 재사용 또는 직접 수정을 먼저 묻는다.
- 수용: 재사용은 기존 정렬을 보존하고 직접 수정은 기존 편집 흐름을 사용한다.
- 금지: 다른 이미지·모험의 격자를 재사용하거나 사용자 선택 전에 정렬을 덮어쓴다.
- 확인: 같은 이미지·저장 격자에서 재사용 경로의 대상 지도 적용과 직접 수정 경로의 저장 상태까지 각각 UI에서 확인한다.
- 확인: 원본 격자 보존, 다른 이미지, 저장 격자 없음의 분기를 검증한다.
추적 원문 ID: UC-1, R-06, R-10.

## 관련 명세

- Product Spec: [docs/specs/scenario-situation-candidates/product-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/product-spec.md)
- Architecture Spec: [docs/specs/scenario-situation-candidates/architecture-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/architecture-spec.md)

## 다이어그램

- Product: [UC-1 준비 절차](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/product/UC-1.activity.svg)
