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

## 다이어그램

해당 없음 — ticket-scoped SVG 없음. PlantUML renderer 누락으로 `.puml`만 존재.

GitHub Project 5가 상태 원본이며, 모든 Issue는 `Workflow Status=Planned`.
