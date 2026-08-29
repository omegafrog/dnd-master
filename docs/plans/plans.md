# GM Turn Lifecycle And Quality Governance Plan Index

| Plan | Parent | Status | Dependencies | Vertical outcome |
|---|---:|---|---|---|
| [220](220-planning-diagnostics-compatibility.md) | #220 | `completed` | #219 completed | diagnostics, legacy replay, duplicate/retry compatibility |
| [GMQ-001](GMQ-001-prompt-registry-baseline.md) | [#231](https://github.com/omegafrog/dnd-master/issues/231) / #210 | `ready-for-agent` | #220 | role registry, baseline, split contract |
| [GMQ-002](GMQ-002-gated-prompt-evaluation.md) | [#232](https://github.com/omegafrog/dnd-master/issues/232) / #210 | `planned` | GMQ-001 | hard-gated candidate Eval/report |
| [GMQ-003](GMQ-003-prompt-approval-rollback.md) | [#233](https://github.com/omegafrog/dnd-master/issues/233) / #210 | `planned` | GMQ-002 | review, activation, lineage, rollback |
| [GMQ-004](GMQ-004-tuning-readiness-gate.md) | [#234](https://github.com/omegafrog/dnd-master/issues/234) / #211 | `planned` | GMQ-003 | data/evidence tuning gate |
| [GMQ-005](GMQ-005-role-scoped-tuning-evaluation.md) | [#235](https://github.com/omegafrog/dnd-master/issues/235) / #211 | `planned` | GMQ-004 | guarded role tuning activation |

## Dependency Decision

`#218`, `#219` completed prerequisites. `#220` only dependency-free executable plan. Prompt optimization starts after diagnostics/compatibility. Fine-tuning starts only through readiness gate.
