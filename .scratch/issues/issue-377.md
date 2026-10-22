---
status: planned
source_issue: https://github.com/omegafrog/dnd-master/issues/377
---

## Plan Set

- Product/Architecture Spec: [시츄에이션 후보 제품·설계 명세](https://github.com/omegafrog/dnd-master/issues/378), [상황 선택·턴 확정 명세](https://github.com/omegafrog/dnd-master/issues/379), [동일 지도 격자 선택 명세](https://github.com/omegafrog/dnd-master/issues/393)
- Child plans: [#378](https://github.com/omegafrog/dnd-master/issues/378), [#379](https://github.com/omegafrog/dnd-master/issues/379), [#393](https://github.com/omegafrog/dnd-master/issues/393)

## 목적

추가 룰북이 있는 새 모험에서 필수 상황과 중간 상황 후보를 모험별로 준비하고, 플레이에 따라 기존 후보를 선택하거나 새 상황을 생성한다. 동일 지도 파일에 저장된 격자가 있으면 재사용하거나 직접 수정할지 사용자에게 먼저 묻는다.

## 실행 순서

1. [#378](https://github.com/omegafrog/dnd-master/issues/378) — 초기 상황과 중간 후보를 생성·저장하고 시작 조건을 검증한다.
2. [#379](https://github.com/omegafrog/dnd-master/issues/379) — 플레이에 따른 선택·생성·턴 확정을 처리한다.
3. [#393](https://github.com/omegafrog/dnd-master/issues/393) — 동일 지도 파일의 저장 격자를 재사용하거나 편집할 사용자 선택을 제공한다.

## 명세와 다이어그램

- Product Spec: [docs/specs/scenario-situation-candidates/product-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/product-spec.md)
- Architecture Spec: [docs/specs/scenario-situation-candidates/architecture-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/architecture-spec.md)
- Product diagrams:
  - UC-1 초기 상황 준비: ![UC-1 초기 상황 준비](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/product/UC-1.usecase.svg?raw=true)
  - UC-1 준비 절차: ![UC-1 준비 절차](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/product/UC-1.activity.svg?raw=true)
  - UC-2 상황 선택·생성: ![UC-2 상황 선택·생성](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/product/UC-2.activity.svg?raw=true)
- Architecture diagrams:
  - 모험별 상황 저장 관계: ![모험별 상황 저장 관계](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/architecture/situations.class.svg?raw=true)
  - 턴 확정 상태 전이: ![턴 확정 상태 전이](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/diagrams/architecture/turn-commit.state.svg?raw=true)

## 의존성

#378 → #379, #378 → #393. #379와 #393은 서로 의존하지 않는다.

## 검증

- Product Spec: [docs/specs/scenario-situation-candidates/product-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/product-spec.md)
- Architecture Spec: [docs/specs/scenario-situation-candidates/architecture-spec.md](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/scenario-situation-candidates/architecture-spec.md)
- 하위 이슈의 정책·저장소·화면부터 저장 상태까지 테스트 계약을 수행한다. 시작 후 변화하는 AI 응답 흐름은 Playwright 정적 E2E로 검증하지 않고 각 Markdown 절차를 읽어 실제 UI에서 직접 확인한다.
- ST-01 응답 시간과 ST-02 저장량은 목표값·기준 측정이 확정되지 않아 수치 목표를 추가하지 않는다.

전체 통합 완료 조건: #378·#379·#393 구현 완료 후 저장 격자 재사용/수정 선택 → 모험 시작 → 후보 선택 또는 신규 상황 → 굴림 대기 후 재개까지 한 번의 실제 UI 흐름으로 검증한다. 이 확인 전에는 이 Issue를 닫지 않는다.
