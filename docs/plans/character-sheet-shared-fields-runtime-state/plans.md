# 캐릭터 시트 추가 항목 및 모험 중 상태 변경 계획

## GitHub 계획 이슈

- 상위 계획: [#354 — 캐릭터 시트 추가 항목과 모험 중 상태 변경 계획](https://github.com/omegafrog/dnd-master/issues/354)
- 상태의 기준은 위 GitHub 이슈와 하위 이슈입니다. 이 파일은 탐색용 색인입니다.

## 실행 순서와 의존성

1. [#350 — Rulebook·Storybook 근거 기반 추가 항목 추천과 정확성 평가](https://github.com/omegafrog/dnd-master/issues/350) — 기존 D&D 5판 항목은 유지하고 현재 모험의 Rulebook과 권한 있는 Storybook 검색 근거로 추가 후보를 추천합니다. 고정 평가 사례로 항목명, 필수 여부, 근거 정확도의 기준선을 측정합니다. 의존성: 없음.
2. [#351 — 추천 항목의 모험 전체 PC 시트 일괄 적용](https://github.com/omegafrog/dnd-master/issues/351) — 이미 작성된 PC를 포함해 현재 모험의 모든 시트에 선택한 항목을 전체 성공 또는 전체 취소로 적용합니다. 의존성: #350.
3. [#352 — PC별 필수 값 입력과 모험 시작 잠금](https://github.com/omegafrog/dnd-master/issues/352) — 각 PC의 필수 값을 검증하고 누락 시 시작을 막으며 모험 시작 시 항목 구성을 고정합니다. 의존성: #351.
4. [#353 — 해결된 게임플레이 결과에 따른 캐릭터 시트 상태 변경](https://github.com/omegafrog/dnd-master/issues/353) — 플레이어 직접 수정을 막고 규칙에 따라 해결·승인된 진행 결과만 반영합니다. 의존성: #352.

GitHub에는 상위/하위 이슈 관계와 차단 관계를 등록했습니다. 모든 이슈의 `Workflow Status`는 `Planned`입니다.

## 명세

- Product Spec: [`docs/specs/character-sheet-shared-fields-runtime-state/product-spec.md`](../../specs/character-sheet-shared-fields-runtime-state/product-spec.md)
- Architecture Spec: [`docs/specs/character-sheet-shared-fields-runtime-state/architecture-spec.md`](../../specs/character-sheet-shared-fields-runtime-state/architecture-spec.md)

## 다이어그램

- [UC-01 추가 항목 추천·선택](../../specs/character-sheet-shared-fields-runtime-state/diagrams/product/UC-01.activity.svg)
- [UC-02 게임플레이 결과 반영](../../specs/character-sheet-shared-fields-runtime-state/diagrams/product/UC-02.activity.svg)
- [캐릭터 시트 구조](../../specs/character-sheet-shared-fields-runtime-state/diagrams/architecture/character-sheet.class.svg)
- [공통 항목 전체 적용 상태](../../specs/character-sheet-shared-fields-runtime-state/diagrams/architecture/shared-field-batch.state.svg)
- [런타임 캐릭터 변경 상태](../../specs/character-sheet-shared-fields-runtime-state/diagrams/architecture/runtime-character-command.state.svg)

## 기준 브랜치

- 현재 세션 브랜치: `plan/gm-context-compaction`
- 계획 시작 시 기준 커밋: `6ee5edb859d0d8152498ccf9080be3889716a579`
- 명세·다이어그램 반영 커밋: `014d179d`
