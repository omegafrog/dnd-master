# Failure Observability And Recovery Plan Index

Parent: [#237](https://github.com/omegafrog/dnd-master/issues/237)

Tracker: GitHub `omegafrog/dnd-master`

| Plan | Issue | Status | Dependencies |
| --- | --- | --- | --- |
| [TT-001](TT-001-compilation-candidate-diagnostics.md) | [#238](https://github.com/omegafrog/dnd-master/issues/238) | `completed` | 없음 |
| [TT-002](TT-002-bounded-candidate-repair.md) | [#239](https://github.com/omegafrog/dnd-master/issues/239) | `completed` | TT-001 |
| [TT-003](TT-003-typed-gm-failure-artifacts.md) | [#240](https://github.com/omegafrog/dnd-master/issues/240) | `ready-for-agent` | 없음 |
| [TT-004](TT-004-meaningful-progress.md) | [#241](https://github.com/omegafrog/dnd-master/issues/241) | `planned` | TT-003 |
| [TT-005](TT-005-async-tactical-readiness.md) | [#242](https://github.com/omegafrog/dnd-master/issues/242) | `ready-for-agent` | 없음 |
| [TT-006](TT-006-phase-based-progress.md) | [#243](https://github.com/omegafrog/dnd-master/issues/243) | `planned` | TT-005 |
| [TT-007](TT-007-runtime-prompt-lineage.md) | [#244](https://github.com/omegafrog/dnd-master/issues/244) | `planned` | TT-003 |

```text
TT-001 → TT-002
TT-003 → TT-004, TT-007
TT-005 → TT-006
```
