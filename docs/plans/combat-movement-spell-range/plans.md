# 전투 이동·주문 사거리 구현 계획 목록

## 구현 목적

현재 모험에서 실제로 배운 주문·기술만 준비하고, 그룹 탐험과 전투 이동을 구현한다. 지도는 상호작용 화면 중앙에 두며, 주문·기술 실행 전에 사거리·효과 범위를 지도에 표시하고 확정 시 최신 상태와 자원을 검증한다.

## 상위 이슈와 실행 경로

- 상위 이슈: [전투 이동·주문 사거리: 그룹 탐험·지도 조준·능력 처리](https://github.com/omegafrog/dnd-master/issues/362)
- 선행 이슈: [#376 공용 룰북 전처리 페이지 검토·재시도](https://github.com/omegafrog/dnd-master/issues/376) — 완료 상태이며 능력 습득 시 RAG 출처 위치 확인에 사용한다.
- 프로젝트 5번 `Workflow Status`가 계획 진행 상태의 정본이다.
- 작업 디렉터리: `/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range`
- 세션 브랜치와 구현 통합 경로: `spec/combat-movement-spell-range`
- 계획 기준 커밋: `7d0297217875663807284924e7e4845659ab50f3`

## 명세와 다이어그램

- [제품 명세](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/product-spec.md)
- [아키텍처 명세](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/architecture-spec.md)
- 제품 흐름:
  - [UC-001 능력 준비](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-001.activity.svg)
  - [UC-002 이동·조준 미리보기](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg)
  - [UC-004 비전투 환경 응용](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-004.activity.svg)
  - [UC-005 그룹 탐험·조우](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/product/UC-005.activity.svg)
- 구조 흐름:
  - [능력 정의와 실행 책임](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/architecture/spell-action.class.svg)
  - [능력 실행 상태와 복구](https://github.com/omegafrog/dnd-master/blob/spec/combat-movement-spell-range/docs/specs/combat-movement-spell-range/diagrams/architecture/spell-action-execution.state.svg)

## 실행 순서와 의존성

| 순서 | 계획 | 구현 내용 | 선행 이슈 |
|---:|---|---|---|
| 1 | [#363 배운 주문·기술 상세정보 준비와 GM 조회](https://github.com/omegafrog/dnd-master/issues/363) | 배우고 사용할 수 있는 능력만 현재 모험의 자료에서 구조화해 저장하고 MCP로 조회한다. | [#376](https://github.com/omegafrog/dnd-master/issues/376) |
| 2 | [#364 캐릭터 시트 자원 상태와 결정적 소모·회복](https://github.com/omegafrog/dnd-master/issues/364) | 캐릭터 시트의 능력 식별자와 자원 현재량·최대량·회복 조건을 정본으로 관리한다. | 없음 |
| 3 | [#365 시츄에이션 속성에 따른 지도 준비와 비공개 배치](https://github.com/omegafrog/dnd-master/issues/365) | 시츄에이션의 지도 필요 속성을 기준으로 시작 전 지도와 숨겨진 초기 배치를 준비한다. | 없음 |
| 4 | [#366 그룹 토큰·대열·분리·합류](https://github.com/omegafrog/dnd-master/issues/366) | 탐험 이동을 캐릭터가 아니라 그룹 단위로 표현하고 분리·합류 상태를 저장한다. | 없음 |
| 5 | [#367 우선권 순차 이동·발견·조우·환경 순환](https://github.com/omegafrog/dnd-master/issues/367) | 시간을 멈춘 상태에서 그룹 우선권대로 이동시키고 사건과 공통 환경 시간을 순서대로 처리한다. | [#365](https://github.com/omegafrog/dnd-master/issues/365), [#366](https://github.com/omegafrog/dnd-master/issues/366) |
| 6 | [#368 탐험 그룹과 전투 캐릭터 토큰 전환](https://github.com/omegafrog/dnd-master/issues/368) | 전투가 시작될 때 그룹의 위치와 대열을 캐릭터별 전투 위치로 바꾸고 전투 종료 후 그룹을 복원한다. | [#366](https://github.com/omegafrog/dnd-master/issues/366), [#367](https://github.com/omegafrog/dnd-master/issues/367) |
| 7 | [#369 전투 경로 이동과 칸별 안개 갱신](https://github.com/omegafrog/dnd-master/issues/369) | 전투 지도에서 경로를 정하고 확인한 뒤 한 칸씩 이동하며 실제 도달 상태를 반영한다. | [#368](https://github.com/omegafrog/dnd-master/issues/368) |
| 8 | [#370 높이·확대·중앙 지도 화면 정렬](https://github.com/omegafrog/dnd-master/issues/370) | 중앙 지도 화면의 확대·축소와 지면 높이 표현을 포인터·범위 오버레이에 맞춘다. | [#369](https://github.com/omegafrog/dnd-master/issues/369) |
| 9 | [#371 지도에서 주문·기술 조준과 범위 미리보기](https://github.com/omegafrog/dnd-master/issues/371) | 주문·기술을 발사하거나 실행하기 전에 중앙 지도에서 사거리와 대상·효과 범위를 확인하게 한다. | [#363](https://github.com/omegafrog/dnd-master/issues/363), [#370](https://github.com/omegafrog/dnd-master/issues/370) |
| 10 | [#372 전투 주문·기술 실행과 비용 확정](https://github.com/omegafrog/dnd-master/issues/372) | 지도에서 조준을 확인한 능력을 최신 전투·지도·캐릭터 상태로 재검증한 뒤 실행한다. | [#364](https://github.com/omegafrog/dnd-master/issues/364), [#371](https://github.com/omegafrog/dnd-master/issues/371) |
| 11 | [적 보유 능력 정의 준비와 전투 실행 연결](enemy-ability-preparation.md) · [로컬 티켓](../../../.scratch/issues/issue-enemy-ability-preparation.md) | 적 시트의 실제 보유 능력만 준비하고, 플레이어와 같은 정의·규칙으로 조회·실행한다. | [#363](https://github.com/omegafrog/dnd-master/issues/363), [#372](https://github.com/omegafrog/dnd-master/issues/372) |
| 12 | [#373 비전투 주문·기술 사용과 GM 판정](https://github.com/omegafrog/dnd-master/issues/373) | 비전투 엔진이 사용 조건과 자원을 결정적으로 처리하고 환경 결과는 GM이 판정하게 한다. | [#363](https://github.com/omegafrog/dnd-master/issues/363), [#364](https://github.com/omegafrog/dnd-master/issues/364) |
| 13 | [#374 주문 기본 효과 뒤 환경 응용](https://github.com/omegafrog/dnd-master/issues/374) | 주문 기본 효과 이후 허용된 환경 작업으로 응용하고 실패해도 기본 효과와 비용을 반복하지 않는다. | [#373](https://github.com/omegafrog/dnd-master/issues/373) |
| 14 | [#375 자연어 행동과 지도 실행의 동일한 규칙 적용](https://github.com/omegafrog/dnd-master/issues/375) | 자연어로 선언한 이동·능력도 지도 미리보기와 동일한 판정·확정 경계를 거치게 한다. | [#367](https://github.com/omegafrog/dnd-master/issues/367), [#369](https://github.com/omegafrog/dnd-master/issues/369), [#371](https://github.com/omegafrog/dnd-master/issues/371), [#372](https://github.com/omegafrog/dnd-master/issues/372), [#373](https://github.com/omegafrog/dnd-master/issues/373), [#374](https://github.com/omegafrog/dnd-master/issues/374) |
| 15 | [#392 중요한 동료 대화에서 사용자 선택과 파티 상태 보호](https://github.com/omegafrog/dnd-master/issues/392) | 일상 대화는 상태를 바꾸지 않고, 중요한 선택은 기존 대화 흐름에서 사용자에게 확인하며 근거가 있는 성격 충돌만 거절한다. | 없음 |

## 통합·검증 조건

- 지도 시작 위치 진입 흐름의 세부 실행 계획: [시츄에이션 진입 시 지도와 시작 위치 결정](map-entry-flow.md)
- E2E 실행 절차와 개별 테스트: [에이전트 실행 검증 안내](../../testing/combat-movement-spell-range/README.md), [ADR-024](../../adr/ADR-024-agent-driven-runtime-e2e.md). Playwright를 사용하지 않고, 실행 에이전트가 테스트별 Markdown을 읽어 실제 진행과 저장 상태를 확인한다.

- 모든 하위 이슈에는 정책 단위 검증과 화면 조작부터 정본 상태까지 확인하는 `ui ~ entity` E2E 계약이 있다.
- 실제 지도 UI에서 이동 경로, 주문·기술 사거리·효과 범위, 중앙 배치와 자원 상태를 확인한다.
- 각 이슈의 구현·검증 범위는 해당 이슈 본문과 Product/Architecture Spec을 따른다.
- 세션 브랜치에서 계획을 이어가며 별도 계획 브랜치를 만들거나 원격에 푸시하지 않는다. 최종 PR은 모든 구현을 통합한 뒤 만든다.
