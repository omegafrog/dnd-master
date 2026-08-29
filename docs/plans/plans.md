# GM Eval Suite v1 Plan Index

Parent issue: [#204](https://github.com/omegafrog/dnd-master/issues/204)

| Plan | Issue | Status | Depends on | Vertical outcome |
| --- | --- | --- | --- | --- |
| [EVAL-001](eval-001-eval-model-hard-constraints.md) | [#215](https://github.com/omegafrog/dnd-master/issues/215) | `ready-for-agent` | — | EvalCase contract, dataset loading, deterministic hard absolute evaluation. |
| [EVAL-002](eval-002-rubric-judge-absolute-quality.md) | [#214](https://github.com/omegafrog/dnd-master/issues/214) | `planned` | EVAL-001 | Anchored LLM rubric judging and absolute quality result. |
| [EVAL-003](eval-003-pairwise-evaluation.md) | [#216](https://github.com/omegafrog/dnd-master/issues/216) | `planned` | EVAL-001, EVAL-002 | Same-case A/B pairwise verdict and evidence. |
| [EVAL-004](eval-004-runner-report-seed-benchmark.md) | [#217](https://github.com/omegafrog/dnd-master/issues/217) | `planned` | EVAL-001, EVAL-002, EVAL-003 | Dataset runner, report, and v1 benchmark. |

## Dependency decision

`EVAL-001` is the sole dependency-free implementation slice and is `ready-for-agent`. All other plans remain `planned` until their dependencies complete.
