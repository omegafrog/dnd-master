# Story Runtime 구현 계획

Parent: [#300](https://github.com/omegafrog/dnd-master/issues/300)

관련 명세:

- [Product Spec](../specs/story-runtime/product-spec.md)
- [Architecture Spec](../specs/story-runtime/architecture-spec.md)

공통 다이어그램:

- [Stage Backbone 생성](../specs/story-runtime/diagrams/product/UC-1.usecase.svg)
- [Runtime 진행과 재계획](../specs/story-runtime/diagrams/product/UC-4.activity.svg)
- [Stage 업무 상태](../specs/story-runtime/diagrams/product/stage.business-state.svg)
- [구조와 책임 경계](../specs/story-runtime/diagrams/architecture/story-runtime.class.svg)
- [AI 제안 반영 상태](../specs/story-runtime/diagrams/architecture/runtime-proposal.state.svg)

## 실행 순서

1. [#295 — Stage 산출물과 저장 구조](https://github.com/omegafrog/dnd-master/issues/295) — 의존성 없음
2. [#296 — 모험 시작과 현재 Stage 상세화](https://github.com/omegafrog/dnd-master/issues/296) — #295
3. [#297 — 일반 진행과 Situation·Revelation·Pressure](https://github.com/omegafrog/dnd-master/issues/297) — #295, #296
4. [#298 — Funnel과 Stage 전환·미해결 이탈](https://github.com/omegafrog/dnd-master/issues/298) — #297
5. [#299 — 선택적 미래 재계획과 실행 안정성](https://github.com/omegafrog/dnd-master/issues/299) — #295, #297, #298

각 Child Issue가 해당 기능 묶음의 구현 목적, 범위, 수용 기준, 정책 단위 테스트, `ui ~ entity` E2E 계약의 정본이다. GitHub Parent/Child Issue와 Project 5의 `Workflow Status`가 추적 정본이다.

계획 브랜치:

- `plan/story-runtime`
- 기준 브랜치: `spec/story-runtime`
- 고정 기준 HEAD: `2f4fe4d5e4d1b2a55bd9aa386515b17a1ffaf6f5`

# Issue #278 계획 인덱스

- Parent Issue: [#292](https://github.com/omegafrog/dnd-master/issues/292)
- Plan branch: `plan/278-combat-map`
- Captured base: `fix/278-map-bugs` @ `dfd506498a7f54b5cc6522e3b69acc11ec6289e8`
- Product Spec: [docs/specs/278/product-spec.md](../specs/278/product-spec.md)
- Architecture Spec: [docs/specs/278/architecture-spec.md](../specs/278/architecture-spec.md)

## 실행 순서와 의존성

1. [#289 — Map Preparation + Grid Calibration](278-01-map-preparation.md) — 선행 없음
2. [#290 — Map Activation + Player Spawn](278-02-map-activation.md) — #289 blocking
3. [#291 — Visibility, Player Projection + UI](278-03-visibility-player-ui.md) — #290 blocking
4. [#294 — Runtime Context Spawn, Fog Trigger + Map Movement Repair](278-04-runtime-context-spawn-fog-movement.md) — #290, #291 후속 보완

## 다이어그램

Spec 다이어그램은 렌더링 완료됐다: [Product SVG](../specs/278/diagrams/product/UC-278-combat-map.usecase.svg), [Architecture SVG](../specs/278/diagrams/architecture/combat-map.class.svg).

GitHub Project 5가 상태 원본이며, 모든 Issue는 `Workflow Status=Planned`.
