# Combat UI 구현 계획

Parent: [#286](https://github.com/omegafrog/dnd-master/issues/286)

관련 명세:

- [Product Spec](../specs/combat-ui/product-spec.md)
- [Architecture Spec](../specs/combat-ui/architecture-spec.md)

다이어그램:

- [Product Use Case](../specs/combat-ui/diagrams/product/UC-CUI.usecase.svg)
- [Product Activity](../specs/combat-ui/diagrams/product/UC-CUI.activity.svg)
- [Architecture Class](../specs/combat-ui/diagrams/architecture/adventure-runtime.class.svg)
- [Architecture State](../specs/combat-ui/diagrams/architecture/combat-encounter.state.svg)

## 실행 순서

1. [#279 — 전투 진입·Initiative·Snapshot·Mode 전환](https://github.com/omegafrog/dnd-master/issues/279) — 의존성 없음
2. [#280 — Human action·자원·명시적 Turn 종료](https://github.com/omegafrog/dnd-master/issues/280) — #279
3. [#281 — Map·Mapless 이동](https://github.com/omegafrog/dnd-master/issues/281) — #280
4. [#282 — 자유 행동 선언](https://github.com/omegafrog/dnd-master/issues/282) — #279, #280
5. [#283 — Reaction interrupt·정확한 resume](https://github.com/omegafrog/dnd-master/issues/283) — #280, #281
6. [#284 — AI 자동 진행·Retry·Failure Recovery](https://github.com/omegafrog/dnd-master/issues/284) — #280, #283
7. [#285 — 전투 종료·최종 상태·일반 세션 복귀](https://github.com/omegafrog/dnd-master/issues/285) — #280, #284

각 Sub-issue가 해당 slice의 구현 목적, 범위, 수용 기준, 정책 단위 테스트, `ui ~ entity` E2E 계약의 정본이다. GitHub Parent/Sub-issue 관계와 Project 5의 `Workflow Status`가 추적 정본이다.
