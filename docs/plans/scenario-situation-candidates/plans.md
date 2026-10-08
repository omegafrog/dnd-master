# 모험별 필수 상황과 중간 후보 구현 계획 목차

이 문서는 승인된 두 구현 작업의 탐색 목차다. 각 작업의 전체 범위·수용 기준·테스트 계약과 상태는 GitHub 이슈에서 관리한다.

## 이슈와 실행 순서

상위 이슈: [#377 모험별 필수 상황·중간 후보 준비와 플레이 진행](https://github.com/omegafrog/dnd-master/issues/377)

| 순서 | 구현 작업 | 요약 | 선행 작업 |
|---|---|---|---|
| 1 | [#378 모험별 필수 상황과 중간 상황 후보 준비](https://github.com/omegafrog/dnd-master/issues/378) | 원문 필수 상황과 여러 후보를 모험마다 독립 생성·저장하고 전체 준비 후 시작한다. | 없음 |
| 2 | [#379 플레이에 따른 상황 선택·생성과 턴 확정](https://github.com/omegafrog/dnd-master/issues/379) | 플레이에 맞춰 후보를 선택하거나 새 상황을 만들고, 굴림 대기·복구를 거쳐 게임 결과와 함께 확정한다. | #378 |

두 구현 이슈는 #377의 실제 하위 이슈다. #378이 #379를 막는 관계도 GitHub에 등록했다. 프로젝트는 [dnd-master-workflow](https://github.com/users/omegafrog/projects/5)이며, 이후 상태는 프로젝트의 `Workflow Status` 필드에서 확인한다.

## 관련 명세

- [제품 명세](../../specs/scenario-situation-candidates/product-spec.md)
- [설계 명세](../../specs/scenario-situation-candidates/architecture-spec.md)
- [시스템 목표](../../specs/scenario-situation-candidates/system-targets.yaml)
- [승인된 설계 계약](../../specs/scenario-situation-candidates/architecture-contracts.yaml)

추가 룰북이 있는 새 모험이 대상이다. 기존 모험 자료는 그대로 두고 이관·호환성 지원은 제외한다. 주어진 맵만 사용하며 적합한 맵이 없으면 맵 없이 진행한다. 신규 형식의 복원, 권한·버전 검증, 굴림 대기와 실패 복구는 각 구현 이슈의 검증 범위에 포함한다.

## 다이어그램

| 사용 작업 | 내용 | SVG |
|---|---|---|
| #378 | 초기 준비의 사용자 흐름 | [UC-1 사용자 흐름](../../specs/scenario-situation-candidates/diagrams/product/UC-1.usecase.svg) |
| #378 | 모험별 초기 상황 준비 | [UC-1 준비 흐름](../../specs/scenario-situation-candidates/diagrams/product/UC-1.activity.svg) |
| #379 | 후보 선택과 후보 부재 시 생성 | [UC-2 플레이 흐름](../../specs/scenario-situation-candidates/diagrams/product/UC-2.activity.svg) |
| #378·#379 | 모험별 상황과 저장 관계 | [상황과 저장 관계](../../specs/scenario-situation-candidates/diagrams/architecture/situations.class.svg) |
| #379 | 굴림 대기와 턴 확정 상태 전이 | [턴 확정 상태 전이](../../specs/scenario-situation-candidates/diagrams/architecture/turn-commit.state.svg) |

GitHub 이슈에는 현재 세션 브랜치의 원격 명세·SVG 링크를 연결했다.

## 구현 인계

- 세션 작업 경로: `/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range`
- 세션 브랜치: `spec/combat-movement-spell-range`
- 구현 결과를 합칠 브랜치(`execution_line`): `spec/combat-movement-spell-range`
- 계획 기준 명세 커밋: `b45b4c23e4efed4d397ee66a8907a3d9d66ad7bb`
- 승인된 분할: #378 → #379. 상황 선택과 신규 상황 생성은 같은 턴 확정 경계를 사용하므로 #379에서 함께 구현한다.
- 구현 시 각 이슈의 단위·정책·저장소 및 화면부터 저장 상태까지 검증 계약을 따른다. 실행 환경 검증은 [EXE.md](../../../EXE.md)를 따른다.
- 전체 구현·검증과 통합이 끝난 뒤 한 최종 PR로 인계한다. 계획 단계에서는 PR을 만들지 않는다.

## 계획 검증 기록

- 이슈 본문을 필수 제목·명세 경로·다이어그램·테스트 계약·상태 기준으로 검증했다. 실제 이슈 번호가 확보된 뒤 상위 이슈를 구조화된 입력으로 재검증·렌더링하고 갱신했다.
- 상위·하위 관계 양방향, #378 → #379 차단 관계 양방향, 세 이슈의 프로젝트 연결·담당자·계획 상태를 GitHub에서 확인했다.
- 원격 명세 2개와 SVG 5개가 현재 세션 브랜치에 존재하고 비어 있지 않음을 확인했다.
- 사용자는 “ㅇㅇ 해”로 현재 브랜치의 명세 커밋 푸시 및 번호 없는 최초 생성 후 실제 번호로 재검증·갱신하는 예외를 허용했다. 설치 도구는 첫 생성 전에도 실제 이슈 번호를 요구하므로 최초 생성에만 승인된 예외를 적용했다. 번호를 추측하지 않았으며 도구·스킬 코드는 변경하지 않았다.
- 이 기록은 구현·빌드·앱 테스트 완료를 뜻하지 않는다.
