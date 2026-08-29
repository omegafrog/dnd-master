# GMQ-002 Gated Prompt Evaluation

- Issue: #232
- Parent Issue: #210
- Status: `planned`
- Dependencies: GMQ-001

## 구현 목적

고정 Eval Dataset에서 role별 Prompt Candidate를 평가한다. hard constraint 회귀를 soft quality로 상쇄하지 못하게 하고 재현 가능한 run report를 남긴다.

## 구현 범위

- `PromptOptimizationRun`, candidate, metric vector, baseline delta 영속화.
- rule violation, secret leak, agency violation, schema failure hard gate.
- 유효 후보 soft metric 비교와 deterministic tie-break.
- candidate/seed/model/prompt/dataset/Eval/representative output report.
- train은 search, dev는 selection에만 쓰는 runner policy.

## Acceptance Criteria

- 같은 artifact/dataset/model/seed run은 비교 가능한 report를 낸다.
- hard regression 후보는 quality score와 무관하게 `REJECTED`다.
- Writer candidate가 다른 role artifact를 변경하지 않는다.
- candidate, baseline delta, 거부 사유를 조회한다.

## Test Contract

- Policy unit: hard gate 우선, tie-break, role isolation, split 사용 제한.
- Integration: run/candidate/metric persistence, repeatable report, invalid dataset rejection.
- UI ~ entity E2E: operator eval entrypoint → candidate run → rejection/report 조회 → production configuration 불변.
