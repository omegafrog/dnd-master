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
