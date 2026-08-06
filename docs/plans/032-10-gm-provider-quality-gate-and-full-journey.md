# 032-10 — GM Provider Quality Gate and Full Journey

- Status: `completed`
- Issue: [#124](https://github.com/omegafrog/dnd-master/issues/124)
- Parent: [#114](https://github.com/omegafrog/dnd-master/issues/114)
- Dependencies: [032-1](032-1-typed-gm-turn-lifecycle.md) through [032-9](032-9-gm-context-compaction-and-resume.md)
- Spec: Product PR-008, AC-014; Architecture §§5.6, 9.2, 11.3

## Outcome

One canonical GM/tool contract runs on local model or configured GPT-5.6 Luna medium. Objective quality gates decide deployment. Full journey proves all entities and UI projections remain coherent.

## Vertical Scope

- Add provider-neutral conformance suite and OpenAI GPT adapter/config.
- Record provider/model/reasoning on every turn/checkpoint without changing domain contract.
- Add local golden scenarios covering rules, plans, secrets, tools, state, map, fog, compaction.
- Enforce hard gates: zero secret leaks, forbidden tools, invented state/rolls.
- Enforce structured success >=99%, rule/evidence accuracy >=95%, plan/fact consistency >=95%, human score >=4/5.
- Support operational provider switch; no automatic per-turn mixing.
- Verify running session continues next turn using same locked data and state.
- Add complete solo-adventure journey suite and operational dashboards/alerts.

## Policy Unit Tests

- provider selection/config and no-mixing policy.
- same canonical tool/output schema across adapters.
- quality report pass/fail calculation at exact thresholds.
- provider switch changes only provider metadata, never session bindings/state.

## Integration and Contract Tests

- local and GPT adapter conformance fixtures.
- timeout/rate/malformed error parity.
- metrics for quality, secret violations, pending Saga, context utilization.
- config-only switch on a persisted session.

## UI ~ Entity E2E

Bundle upload/compile → plan/map binding → character session → text GM turn → official roll/state → confirmed map movement → fog/reveal/last-seen → adaptive plan/time → forced compaction → reconnect → provider switch → ending confirmation.

## Implementation Scope

- AI provider adapters/config/conformance tests
- app-all configuration and secrets wiring
- golden evaluation harness and reports
- system and Playwright full-journey tests
- observability dashboards/alerts/docs

## Out of Scope

Automatic provider mixing, multiplayer, hex grids, generated maps.

## Completion

- Local model passes gate or deployment config selects GPT-5.6 Luna medium.
- Full journey and all prior regression suites pass.
- Status becomes `completed`; remove `ready-for-agent` from all completed issues; parent #114 may close.

## Validation

- Backend `./gradlew check` passes, including all-in-one Flyway recovery.
- Web unit tests: 26 files / 76 tests pass.
- Player journey Playwright suite: 3/3 pass.
- Web typecheck and lint pass.
