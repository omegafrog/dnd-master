# AI 동료·적 전투 행동 구현 계획

## Plan Set

- 상위 이슈: [#361 AI 동료·적 전투 행동 안정화](https://github.com/omegafrog/dnd-master/issues/361)
- 실행 라인: `spec/combat-ai-actions`
- Product Spec: `docs/specs/combat-ai-actions/product-spec.md`
- Architecture Spec: `docs/specs/combat-ai-actions/architecture-spec.md`

## Child plans

| Issue | 단계 | 내용 | 의존성 | 상태 |
|---|---:|---|---|---|
| [#359 적 캐릭터 시트 준비와 모험 내 재사용 구현](https://github.com/omegafrog/dnd-master/issues/359) | 1 | 출처 검증, 저장·재사용, 전투 전 준비, 분리된 전투 상태 및 준비 복구 | 없음 | Planned |
| [#360 AI 동료·적 전투 행동 결정과 자동 복구 구현](https://github.com/omegafrog/dnd-master/issues/360) | 2 | 최신 상황·시트 문맥, 실제 AI 행동 결정, 기본 차례 종료 제거, 차례 복구 | #359 완료 후 | Planned |

## 명세와 다이어그램

- Product Spec: [docs/specs/combat-ai-actions/product-spec.md](../../specs/combat-ai-actions/product-spec.md)
- Architecture Spec: [docs/specs/combat-ai-actions/architecture-spec.md](../../specs/combat-ai-actions/architecture-spec.md)
- Product diagrams:
  - UC-001 동료 행동: [유스케이스](../../specs/combat-ai-actions/diagrams/product/UC-001.usecase.svg) · [흐름](../../specs/combat-ai-actions/diagrams/product/UC-001.activity.svg)
  - UC-002 적 시트 준비: [유스케이스](../../specs/combat-ai-actions/diagrams/product/UC-002.usecase.svg) · [흐름](../../specs/combat-ai-actions/diagrams/product/UC-002.activity.svg)
  - UC-003 적 행동: [유스케이스](../../specs/combat-ai-actions/diagrams/product/UC-003.usecase.svg) · [흐름](../../specs/combat-ai-actions/diagrams/product/UC-003.activity.svg)
- Architecture diagram: [Adventure Runtime class diagram](../../specs/combat-ai-actions/diagrams/architecture/adventure-runtime.class.svg)

GitHub Project `omegafrog` / #6의 `Workflow Status`가 공식 진행 상태다. 각 child Issue에 전체 구현 범위·수용 기준·테스트 계약이 있다.
