# 상황 생성 개선 구현 계획 목차

## 계획 세트

- 상위 이슈: [#377 모험별 필수 상황·중간 후보 준비와 플레이 진행](https://github.com/omegafrog/dnd-master/issues/377)
- 작업 상태 기준: [GitHub Project 5 — dnd-master-workflow](https://github.com/users/omegafrog/projects/5), `Workflow Status`
- 실행 브랜치: `spec/combat-movement-spell-range`
- 원문 명세: [제품 명세](../../specs/scenario-situation-candidates/product-spec.md), [설계 명세](../../specs/scenario-situation-candidates/architecture-spec.md)

## 하위 계획

| 순서 | 계획 ID / 이슈 | 구현 목적 | 선행 작업 |
|---|---|---|---|
| 1 | `scenario-situation-candidates-378` · [#378 필수 상황과 중간 후보 준비](https://github.com/omegafrog/dnd-master/issues/378) | 이야기 길이 선택과 명시적 결말 표식이 포함된 필수 상황 그래프를 모험별로 준비·저장한다. | 없음 |
| 2 | `scenario-situation-candidates-379` · [#379 그래프 기반 상황 선택·턴 확정](https://github.com/omegafrog/dnd-master/issues/379) | 자연스러운 선택 상황 허용량과 그래프 적격 필수 상황을 적용해 기존 턴 확정 경계에서 진행한다. | #378 |
| 3 | `scenario-situation-candidates-380` · [#393 동일 지도 격자 재사용 또는 수정](https://github.com/omegafrog/dnd-master/issues/393) | 같은 지도 파일의 저장 격자를 쓸지 기존 편집 흐름에서 수정할지 먼저 묻는다. | #378 |

#379의 조회·턴 오케스트레이션 요구사항은 해당 이슈 본문에 합쳤다. GitHub 이슈가 각 계획의 본문 원본이다. 독립된 보조 계획 문서는 두지 않는다.

## 다이어그램

- [UC-1 초기 상황 준비](../../specs/scenario-situation-candidates/diagrams/product/UC-1.usecase.svg)
- [UC-1 준비 절차](../../specs/scenario-situation-candidates/diagrams/product/UC-1.activity.svg)
- [UC-2 상황 선택·생성](../../specs/scenario-situation-candidates/diagrams/product/UC-2.activity.svg)
- [모험별 상황 저장 관계](../../specs/scenario-situation-candidates/diagrams/architecture/situations.class.svg)
- [턴 확정 상태 전이](../../specs/scenario-situation-candidates/diagrams/architecture/turn-commit.state.svg)

#378은 #379와 #393을 막는다. #379와 #393은 서로 의존하지 않는다. 모든 계획은 정책·단위 검증 및 각 이슈의 Markdown 절차에 따른 실제 화면부터 저장 상태까지 검증을 포함한다. 모험 시작 이후 변하는 AI 진행은 Playwright 자동화 대신 사람이 Markdown 지침을 읽으며 실제 UI에서 확인한다. `STARTING` 상태에서는 동일 세션을 10초 간격, 최대 2분 관찰한다.

## 인계

- 세션 작업 경로: `/home/jiwoo/workspace/dnd-master-spec-combat-movement-spell-range`
- 세션 브랜치와 구현 실행선: `spec/combat-movement-spell-range`
- 기존 코드 변경은 구현 계획 승인과 분리되어 있으며, 이 계획 단계에서 코드를 수정하지 않았다.
- 구현·테스트·실제 사용자 흐름 확인은 완료되지 않았다.
