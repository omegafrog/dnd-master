---
status: in-progress
source_issue: https://github.com/omegafrog/dnd-master/issues/362
---

## Plan Set

- Product/Architecture Spec: [Product Spec](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/product-spec.md), [Architecture Spec](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/architecture-spec.md)
- Child plans: #363, #364, #365, #366, #367, #368, #369, #370, #371, #372, #373, #374, #375, #392

## 목적

모험 중 실제로 배운 능력만 준비하고, 그룹 탐험·전투 이동·지도 기반 범위 확인을 구현한다. 파티의 이동·전투·성장·시트 변경은 사용자가 선택하며, GM은 세계·적·판정과 동료 대화를 맡는다. 지도는 상호작용 화면 중앙에 두고 실행 전에 사거리·효과 범위를 보여준다.

## 실행 순서

1. #363 — 사용자가 선택한 초기·신규 능력만 현재 모험 자료에서 구조화해 저장하고 GM이 MCP로 조회한다.
2. #364 — 캐릭터 시트의 자원 현재량·최대량·회복 조건을 정본으로 관리한다.
3. #365 — 시츄에이션의 지도 필요 속성과 진입 서술로 지도와 비공개 배치를 준비한다.
4. #366 — 그룹 단위 탐험 상태·대열·분리·합류를 저장하고 사용자 명령으로 제어한다.
5. #367 — 정지된 시간 아래 그룹 우선권대로 이동과 조우를 처리하고 공통 환경 시간을 진행한다.
6. #368 — 전투 진입·종료 시 사용자 소유 그룹과 캐릭터 토큰 사이를 전환한다.
7. #369 — 사용자가 정한 전투 경로를 칸별로 검증·적용하고 안개를 갱신한다.
8. #370 — 중앙 지도의 확대·높이 표시와 입력 오버레이 정렬을 구현한다.
9. #371 — 사용자가 고른 주문·기술의 사거리와 대상·효과 범위를 중앙 지도에 미리 보여준다.
10. #372 — 사용자가 확정한 전투 능력을 최신 상태로 검증·실행하고 비용을 반영한다.
11. #373 — 사용자가 선언한 비전투 능력의 사용 조건과 자원은 결정적으로 처리하고 효과는 GM이 판정한다.
12. #374 — 확정된 주문 기본 효과 뒤에 GM이 환경 응용을 판정하게 한다.
13. #375 — 사용자가 자연어로 선언한 이동·능력도 동일한 지도 확인·규칙 실행 경계를 사용한다.
14. #392 — 일상 동료 대화는 GM이 생성하고 중요한 대화 선택은 사용자가 확정한다.

## 명세와 다이어그램

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md
- Product diagrams:
- UC-001 능력 준비와 조회: ![UC-001 능력 준비와 조회](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-001.usecase.svg?raw=true)
- UC-001 능력 준비 흐름: ![UC-001 능력 준비 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-001.activity.svg?raw=true)
- UC-002 지도 이동·행동 미리보기: ![UC-002 지도 이동·행동 미리보기](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.usecase.svg?raw=true)
- UC-002 미리보기와 실행: ![UC-002 미리보기와 실행](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg?raw=true)
- UC-004 비전투 환경 응용: ![UC-004 비전투 환경 응용](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-004.usecase.svg?raw=true)
- UC-004 환경 응용 흐름: ![UC-004 환경 응용 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-004.activity.svg?raw=true)
- UC-005 그룹 탐험과 조우: ![UC-005 그룹 탐험과 조우](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-005.usecase.svg?raw=true)
- UC-005 탐험 이동 흐름: ![UC-005 탐험 이동 흐름](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-005.activity.svg?raw=true)
- UC-006 파티 대화 선택: ![UC-006 파티 대화 선택](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-006.usecase.svg?raw=true)
- UC-006 파티 대화 처리: ![UC-006 파티 대화 처리](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-006.activity.svg?raw=true)
- Architecture diagrams:
- 주문·기술 정의와 실행: ![주문·기술 정의와 실행](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/architecture/spell-action.class.svg?raw=true)
- 주문·기술 실행 상태와 복구: ![주문·기술 실행 상태와 복구](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/architecture/spell-action-execution.state.svg?raw=true)

## 의존성

#376 → #363
#365 → #367
#366 → #367
#366 → #368
#367 → #368
#368 → #369
#369 → #370
#363 → #371
#370 → #371
#364 → #372
#371 → #372
#363 → #373
#364 → #373
#373 → #374
#367 → #375
#369 → #375
#371 → #375
#372 → #375
#373 → #375
#374 → #375
#392은 기존 대화 응답 흐름을 사용하므로 별도 선행 이슈가 없다.

## 검증

- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md
- 각 하위 Issue의 정책 단위 테스트와 ui ~ entity E2E 계약을 실행한다.
- 화면 조작부터 지도·전투·캐릭터 시트·모험 저장 상태까지 확인하며, 파티 행동은 사용자 명령만 실행되는지 검증한다.

구현·검증 완료 전에는 이 Issue를 닫지 않는다.
