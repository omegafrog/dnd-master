# 전투 이동·주문 사거리 구현 계획 목록

## 구현 목적

모험에 적용되는 능력과 자원을 근거에 따라 처리하고, 지도 진입·그룹 탐험·전투 이동·조준·능력 실행을 기존 상태 소유 경계 안에서 구현한다. 전투 이동 중 GM 사건은 전용 고정 계약으로 판정·서술하고 실제 도달 위치와 이동량을 보존한 채 같은 플레이어 차례의 다음 입력을 기다린다.

## 상위 이슈와 명세

- 상위 이슈: [#362 전투 이동·주문 사거리: 그룹 탐험·지도 조준·능력 처리](https://github.com/omegafrog/dnd-master/issues/362)
- Product Spec: docs/specs/combat-movement-spell-range/product-spec.md
- Architecture Spec: docs/specs/combat-movement-spell-range/architecture-spec.md
- 계획 기준 브랜치: spec/combat-movement-spell-range (기준 커밋 65db32352eec348e9b23d7c1691607539b856c56)

## 명세와 다이어그램

- [Product Spec](../../specs/combat-movement-spell-range/product-spec.md)
- [Architecture Spec](../../specs/combat-movement-spell-range/architecture-spec.md)
- 제품 흐름: [UC-001](../../specs/combat-movement-spell-range/diagrams/product/UC-001.activity.svg), [UC-002](../../specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg), [UC-004](../../specs/combat-movement-spell-range/diagrams/product/UC-004.activity.svg), [UC-005](../../specs/combat-movement-spell-range/diagrams/product/UC-005.activity.svg), [UC-006](../../specs/combat-movement-spell-range/diagrams/product/UC-006.activity.svg)
- 아키텍처: [주문·행동 정의](../../specs/combat-movement-spell-range/diagrams/architecture/spell-action.class.svg), [주문·행동 실행 상태](../../specs/combat-movement-spell-range/diagrams/architecture/spell-action-execution.state.svg), [이동 중 사건 중단·재개 상태](../../specs/combat-movement-spell-range/diagrams/architecture/combat-movement-interruption.state.svg)

## 실행 순서

| 순서 | 계획 | 구현 범위 | 선행 이슈 |
|---:|---|---|---|
| 1 | plan-363 — [#363 [전투 이동·주문 01/15] 배운 주문·기술 상세정보 준비와 GM 조회](https://github.com/omegafrog/dnd-master/issues/363) | 모험별 보유 능력 정의 준비와 근거 조회 | #376 |
| 2 | plan-364 — [#364 [전투 이동·주문 02/15] 캐릭터 시트 자원 상태와 결정적 소모·회복](https://github.com/omegafrog/dnd-master/issues/364) | 캐릭터별 자원 상태와 중복 없는 소모·회복 | 없음 |
| 3 | plan-365 — [#365 [전투 이동·주문 03/15] 시츄에이션 속성에 따른 지도 준비와 비공개 배치](https://github.com/omegafrog/dnd-master/issues/365) | 지도 필요 상황의 시작 위치·지도·초기 배치 준비 | 없음 |
| 4 | plan-366 — [#366 [전투 이동·주문 04/15] 그룹 토큰·대열·분리·합류](https://github.com/omegafrog/dnd-master/issues/366) | 그룹 탐험 상태와 대열·분리·합류 | 없음 |
| 5 | plan-367 — [#367 [전투 이동·주문 05/15] 우선권 순차 이동·발견·조우·환경 순환](https://github.com/omegafrog/dnd-master/issues/367) | 탐험 그룹의 차례 이동과 사건·환경 처리 | #365, #366 |
| 6 | plan-368 — [#368 [전투 이동·주문 06/15] 탐험 그룹과 전투 캐릭터 토큰 전환](https://github.com/omegafrog/dnd-master/issues/368) | 탐험 그룹과 전투 캐릭터 위치 전환 | #367 |
| 7 | plan-369 — [#369 [전투 이동·주문 07/15] 전투 이동 중 사건 판정·서술과 차례 유지](https://github.com/omegafrog/dnd-master/issues/369) | 전투 이동이 사건에서 멈춘 뒤 같은 플레이어 차례로 돌아오는 전체 흐름 | #368 |
| — | movement-policy-resolution — [로컬 계획 초안; tracker issue 미할당](movement-policy-resolution-draft.md) | AC-06/BR-06 전투 이동 비용·반응 규칙의 현행 코드 조사와 룰북 근거 기반 완성 | #368 |
| 8 | plan-370 — [#370 [전투 이동·주문 08/15] 높이·확대·중앙 지도 화면 정렬](https://github.com/omegafrog/dnd-master/issues/370) | 중앙 지도에서 높이·확대·입력 오버레이 정렬 | #369 |
| 9 | plan-371 — [#371 [전투 이동·주문 09/15] 지도에서 주문·기술 조준과 범위 미리보기](https://github.com/omegafrog/dnd-master/issues/371) | 전투 능력 조준과 사거리·효과 미리보기 | #363, #370 |
| 10 | plan-372 — [#372 [전투 이동·주문 10/15] 전투 주문·기술 실행과 비용 확정](https://github.com/omegafrog/dnd-master/issues/372) | 최신 상태를 검증한 전투 능력 실행과 비용 반영 | #364, #371 |
| 11 | enemy-ability-preparation — [#394 [전투 이동·주문 11/15] 적 보유 능력 정의 준비와 전투 실행 연결](https://github.com/omegafrog/dnd-master/issues/394) | 적 시트의 보유 능력만 준비해 플레이어와 같은 규칙으로 실행 | #363, #372 |
| 12 | plan-373 — [#373 [전투 이동·주문 12/15] 비전투 주문·기술 사용과 GM 판정](https://github.com/omegafrog/dnd-master/issues/373) | 비전투 능력의 조건·자원은 시스템이 처리하고 효과는 GM 판정 | #363, #364 |
| 13 | plan-374 — [#374 [전투 이동·주문 13/15] 주문 기본 효과 뒤 환경 응용](https://github.com/omegafrog/dnd-master/issues/374) | 주문 기본 효과 이후 허용된 환경 작업 응용 | #373 |
| 14 | plan-375 — [#375 [전투 이동·주문 14/15] 자연어 행동과 지도 실행의 동일한 규칙 적용](https://github.com/omegafrog/dnd-master/issues/375) | 자연어 선언을 같은 이동·능력 실행 경계로 연결 | #367, #369, #371, #372, #373, #374 |
| 15 | plan-392 — [#392 [전투 이동·주문 15/15] 동료 대화와 중요한 선택 확인](https://github.com/omegafrog/dnd-master/issues/392) | 중요한 동료 대화 선택은 사용자 입력으로 확정하고 일상 대화는 무상태로 유지 | 없음 |

## 통합·검증 조건

- 각 계획의 로컬 초안이 검토 정본이다. 전체 계획을 확정한 뒤 한 번에 GitHub에 발행하며, 색인의 Issue 링크는 추적용 연결이다. 이 문서는 탐색·의존성·요구사항 연결용 색인이며 전체 요구사항 원장은 traceability.json에 둔다.
- #363은 외부 선행 이슈 #376을 완료한 뒤 시작한다. #394는 #363과 #372를 선행으로 둔다.
- #369의 이동 사건 처리는 UC-002 흐름과 전투 이동 중단·같은 차례 재개 상태도를 따른다. 사건의 GM 판정 시작 전 `GM_INTERVENTION_PENDING`을 같은 이동 작업에 저장하고, 기존 플레이어 굴림이 필요하면 굴림 제출 뒤 최종 구조화 판정이 저장될 때까지 같은 대기를 유지한다. GM 응답 실패·불일치도 대기를 해제하지 않으며, 대기 중 다른 행동과 턴 종료를 막는다.
- 원본 PDF 네 파일의 역할 연결, Potent Brew 22열×23행 격자 저장, 상황 진입, 자연어 이동 승인, 실제 에이전트 사건 처리, 사건 뒤 같은 차례 유지와 지도 입력 재개를 포함한 단일 E2E는 모든 선행 구현 뒤 #375에서 실행한다. 프로필 adventure-runtime-server, 시간 제한 없음(사용자가 중단할 때까지), 전체 시도 2회(최초 1회와 진단 후 재시도 1회)다. 나머지 경계는 기존 실행 프로필의 단위·정책·통합·계약 검사로 확인한다.
- 계획과 상위 이슈는 세션 브랜치에 유지하며 원격 푸시는 하지 않는다.
