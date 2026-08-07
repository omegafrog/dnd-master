# Structured GM Authority Plan Tracker

Tracker mode: local Markdown. 외부 Issue는 생성하지 않는다.

| ID | Ticket | Status | Dependencies |
|---|---|---|---|
| 036-1 | [Provider-native GM response envelope](036-1-provider-native-gm-envelope.md) | ready-for-agent | - |
| 036-2 | [Server-owned evidence citation registry](036-2-server-owned-evidence-citations.md) | planned | 036-1 |
| 036-3 | [Private GM state and player projection boundary](036-3-private-state-player-projection.md) | planned | 036-1, 036-2 |
| 036-4 | [Authoritative adjudication and combat orchestration](036-4-authoritative-adjudication-orchestration.md) | planned | 036-1, 036-2, 036-3 |
| 036-5 | [Real-provider UI five-turn and combat acceptance](036-5-real-provider-ui-acceptance.md) | planned | 036-1, 036-2, 036-3, 036-4 |

## Release gates

- Provider response passes provider-native strict JSON Schema or fails closed after one repair.
- Every citation resolves through a server-owned per-turn registry; provider-authored evidence is rejected.
- Private GM state is absent from every browser response, stream, error, log artifact, and public citation.
- Dice, checks, attacks, damage, HP, initiative, combat state, and cursor are authoritative and idempotent.
- A live-provider Playwright journey completes five Storybook-grounded turns and Rulebook-grounded combat through UI only.
