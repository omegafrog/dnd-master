# GMQ-004 Tuning Readiness Gate

- Issue: #234
- Parent Issue: #211
- Status: `completed`
- Dependencies: GMQ-003

## 구현 목적

Fine-tuning을 증거 기반 결정으로 제한한다. 선행 개선으로 해결되지 않은 failure category, provenance, split, baseline을 검증해 training 가능한 proposal만 다음 단계로 넘긴다.

## 구현 범위

- `TuningProposal`, failure taxonomy, training provenance/quality gate, role-scoped eligibility.
- stable contract, Eval, baseline, optimized prompt, curated data, holdout 선행 조건.
- secret leak, rule contradiction, agency violation, unresolved hallucination, 권한 불명확 텍스트 제외.
- adventure/session 단위 leakage 검사.
- SFT/preference 후보와 base model + optimized prompt 비교 기준.
- operator proposal 결과 조회.

## Acceptance Criteria

- 선행 조건·반복 failure evidence 없으면 proposal은 rejected다.
- provenance 결함/leakage sample은 training set에 들어가지 않는다.
- Writer proposal이 다른 role tuning을 자동 요구하지 않는다.
- baseline/dataset/prompt/Eval/rejection 사유를 재현한다.

## Test Contract

- Policy unit: prerequisite gate, unsafe sample exclusion, split leakage, role isolation.
- Integration: proposal/provenance persistence, taxonomy query, rejected audit.
- UI ~ entity E2E: proposal → evidence/data 등록 → gate → eligible/rejected projection → runtime config 불변.
