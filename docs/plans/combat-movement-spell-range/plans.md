# 전투 이동·주문 사거리 구현 계획 목록

## 구현 목적

D&D 5판(2014) 기본 룰북 전체 주문과 이동·행동을 모험별로 검토한 정의에 따라 실행하고, 지도 미리보기·확대·안개 및 기존 GM 환경 응용 연결을 보강한다.

## 상위 계획과 실행 경로

- 상위 이슈: [#362](https://github.com/omegafrog/dnd-master/issues/362)
- 티켓 저장소: `omegafrog/dnd-master`
- 프로젝트: [dnd-master-workflow · 5번](https://github.com/users/omegafrog/projects/5)
- 작업 디렉터리: `/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range`
- 세션 브랜치: `spec/combat-movement-spell-range`
- 구현 결과를 통합할 브랜치(`execution_line`): `spec/combat-movement-spell-range`
- 계획 기준 커밋: `b55be7b5755f656bde82dc46cd1bef5a8309d973`
- 티켓의 진행 상태는 GitHub 프로젝트의 `Workflow Status`가 정본이다. 이 목록은 개별 이슈로 이동하기 위한 안내이며 구현 계약·상태를 복제하지 않는다.

## 명세와 다이어그램

- [제품 명세](../../specs/combat-movement-spell-range/product-spec.md)
- [아키텍처 명세](../../specs/combat-movement-spell-range/architecture-spec.md)
- [시스템 목표](../../specs/combat-movement-spell-range/system-targets.yaml)

- [UC-001.usecase.svg](../../specs/combat-movement-spell-range/diagrams/product/UC-001.usecase.svg)
- [UC-001.activity.svg](../../specs/combat-movement-spell-range/diagrams/product/UC-001.activity.svg)
- [UC-002.usecase.svg](../../specs/combat-movement-spell-range/diagrams/product/UC-002.usecase.svg)
- [UC-002.activity.svg](../../specs/combat-movement-spell-range/diagrams/product/UC-002.activity.svg)
- [UC-004.usecase.svg](../../specs/combat-movement-spell-range/diagrams/product/UC-004.usecase.svg)
- [UC-004.activity.svg](../../specs/combat-movement-spell-range/diagrams/product/UC-004.activity.svg)
- [spell-action.class.svg](../../specs/combat-movement-spell-range/diagrams/architecture/spell-action.class.svg)
- [spell-action-execution.state.svg](../../specs/combat-movement-spell-range/diagrams/architecture/spell-action-execution.state.svg)

## 실행 순서와 의존성

| 순서 | 계획 | 구현할 내용 | 먼저 완료할 티켓 |
|---|---|---|---|
| 1 | [#363](https://github.com/omegafrog/dnd-master/issues/363) · `combat-01` | 기본 룰북 주문 전수 목록과 출처·실행 기제 확인 | 없음 |
| 2 | [#364](https://github.com/omegafrog/dnd-master/issues/364) · `combat-02` | 현재 모험의 정의 검토·수정·승인과 시작 잠금 | [#363](https://github.com/omegafrog/dnd-master/issues/363) |
| 3 | [#365](https://github.com/omegafrog/dnd-master/issues/365) · `combat-03` | 전투 턴 이동량과 실제 경로 이동 | 없음 |
| 4 | [#366](https://github.com/omegafrog/dnd-master/issues/366) · `combat-04` | 비행 높이·지도 확대·숨겨진 벽 공개 경계 | [#365](https://github.com/omegafrog/dnd-master/issues/365) |
| 5 | [#367](https://github.com/omegafrog/dnd-master/issues/367) · `combat-05` | 주문·일반 행동의 공개 미리보기와 선택 입력 | [#364](https://github.com/omegafrog/dnd-master/issues/364), [#366](https://github.com/omegafrog/dnd-master/issues/366) |
| 6 | [#368](https://github.com/omegafrog/dnd-master/issues/368) · `combat-06` | 단일 선택 주문의 권위 실행과 기본 효과 | [#367](https://github.com/omegafrog/dnd-master/issues/367) |
| 7 | [#369](https://github.com/omegafrog/dnd-master/issues/369) · `combat-07` | 복수 대상·투사체 배분과 공간 효과 | [#368](https://github.com/omegafrog/dnd-master/issues/368) |
| 8 | [#370](https://github.com/omegafrog/dnd-master/issues/370) · `combat-08` | 시전 시점·지속·집중·발동과 높은 등급 슬롯 | [#368](https://github.com/omegafrog/dnd-master/issues/368) |
| 9 | [#371](https://github.com/omegafrog/dnd-master/issues/371) · `combat-09` | 소환·조종과 이후 턴의 지속 주문 효과 | [#370](https://github.com/omegafrog/dnd-master/issues/370) |
| 10 | [#372](https://github.com/omegafrog/dnd-master/issues/372) · `combat-10` | 비전투 변화·생성·정보 확인 주문의 실제 효과 | [#369](https://github.com/omegafrog/dnd-master/issues/369), [#370](https://github.com/omegafrog/dnd-master/issues/370), [#371](https://github.com/omegafrog/dnd-master/issues/371) |
| 11 | [#373](https://github.com/omegafrog/dnd-master/issues/373) · `combat-11` | 주문 기본 효과 뒤 환경 응용과 GM 작업 보정 | [#368](https://github.com/omegafrog/dnd-master/issues/368) |
| 12 | [#374](https://github.com/omegafrog/dnd-master/issues/374) · `combat-12` | 자연어 선언과 지도 조작의 동일 규칙 적용 | [#365](https://github.com/omegafrog/dnd-master/issues/365), [#372](https://github.com/omegafrog/dnd-master/issues/372), [#373](https://github.com/omegafrog/dnd-master/issues/373) |
| 13 | [#375](https://github.com/omegafrog/dnd-master/issues/375) · `combat-13` | 기본 룰북 전체 주문의 실제 실행과 완료 판정 | [#363](https://github.com/omegafrog/dnd-master/issues/363), [#364](https://github.com/omegafrog/dnd-master/issues/364), [#367](https://github.com/omegafrog/dnd-master/issues/367), [#368](https://github.com/omegafrog/dnd-master/issues/368), [#369](https://github.com/omegafrog/dnd-master/issues/369), [#370](https://github.com/omegafrog/dnd-master/issues/370), [#371](https://github.com/omegafrog/dnd-master/issues/371), [#372](https://github.com/omegafrog/dnd-master/issues/372), [#374](https://github.com/omegafrog/dnd-master/issues/374) |

## 공유 파일과 통합 순서

- 계획 1·2는 ScenarioPackage/준비 서비스·저장소·준비 화면을 공유하므로 순차 적용한다.
- 계획 3·4는 Combat Map 이동·공개 투영과 CombatMapView를 공유하므로 순차 적용한다.
- 계획 5~10은 CombatActionApplicationService/주문 전략 등록부/CombatScreen을 공유한다. 논리 의존이 없는 7·8 및 9·11도 동일 파일 변경 시 단일 작업 슬롯 또는 순차 통합으로 충돌을 피한다.
- 계획 11·12는 GM 계획 어댑터를 공유하므로 순차 적용한다.
- 계획 13은 구현 티켓의 실제 실행 증거가 준비될 때까지 완료할 수 없다.

## 구현에 인계할 조건

- 각 이슈의 구현 범위·수용 기준·상태 전이와 단위/E2E 테스트 계약을 따른다. 이 단계에서는 애플리케이션을 구현하거나 테스트하지 않았다.
- 주문별 수치·기제 조합은 모험별 승인 정의 자료로 관리하고, 새로운 실행 기제만 서버 전략·객체 조합으로 확장한다.
- 준비 중 의미·조건은 사용자가 검토하며, 구조·출처·지원 여부의 자동 확인을 완전한 의미 검증으로 바꾸지 않는다.
- 모든 기본 주문의 실제 실행을 확인하기 전에는 전체 지원 완료로 표시하지 않는다. 추가 자료의 미지원 표시로 기본 주문 지원을 대체하지 않는다.
- 기존 컨텍스트·서비스·명령 복구 경계를 유지한다. 새 MCP 서버·애드온 설치 기능은 범위 밖이다.
- 구현 작업 브랜치는 위 실행 라인에 통합한다. 최종 PR은 전체 계획의 구현과 검증이 끝난 뒤 만든다.

## 티켓 생성 검증

- 확인 시각: `2026-10-07T13:48:27.801Z`.
- 상위 1개와 실제 하위 이슈 13개의 공식 연결 및 각 하위의 상위 참조를 확인했다.
- 승인한 의존성은 GitHub의 실제 차단 관계로 저장·대조했다.
- 상위·하위 모두 프로젝트 5번에 연결되고 생성 시 상태는 `Planned`이다.
- 담당자와 실제 이슈 번호를 사용한 최종 본문·계약 및 명세/SVG 링크를 확인했다.
- 스킬 수정 후 이슈 번호 없는 생성용 자료를 먼저 검증하고, 생성 후 실제 번호가 있는 완성 계약으로 다시 검증했다.
