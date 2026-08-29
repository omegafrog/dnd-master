# EVAL-001: Eval model and deterministic hard constraints

Status: `ready-for-agent`

Parent: #204

Issue: #215

Dependencies: none

## 구현 목적

GM 응답 평가의 정본인 versioned `EvalCase`와 typed hard expectation을 만들고, 규칙·정보·상태·player agency에서 기계적으로 확인 가능한 위반을 prose 품질과 분리해 절대 평가한다. 이후 judge와 runner가 재사용할 안정된 기반을 제공한다.

## Scope

- Add general Java Gradle module `gm-eval-service`; register it in `src/settings.gradle.kts` and architecture module expectation test.
- Add immutable `EvalCase`, `EvalContext`, categories, typed `HardExpectation`, `QualityRubric`, `HardConstraintResult`, `EvalResult` domain model.
- Implement JSONL dataset/rubric loading with schema version `1` validation.
- Implement deterministic strategies for structured forbidden/required fact, explicit resolved-rule contradiction, illegal state mutation, and structurally detectable agency violation.
- Represent unsupported deterministic expectation as `UNEVALUATED`, never `PASS`.
- Provide absolute evaluation of a supplied response; no provider call, production endpoint, startup gate, or LLM judge.

## Acceptance Criteria

- [ ] `EvalCase` represents player input, world/scene state, player knowledge, story stage, optional TurnPlan/resolved context, hard expectations, and rubrics.
- [ ] Hard results are separately typed `PASS`/`FAIL`/`UNEVALUATED` with reason/evidence contracts.
- [ ] Deterministic evaluators cover the initial mechanically expressible rule, information, state, and agency constraints.
- [ ] Invalid dataset/case/rubric version fails before evaluation.
- [ ] Absolute supplied-response evaluation returns hard results without a provider dependency.

## Test Contract

- Policy unit tests: expectation validation; direct leakage/omission; contradiction; state mutation; allowed forced effect vs invented voluntary action; unsupported -> `UNEVALUATED`; hard/quality separation.
- UI ~ entity e2e: JSONL fixture -> module entry/application service -> `EvalCase` -> absolute evaluator -> serialized `EvalResult` fixture.
- Contract tests: Jackson round-trip, malformed version/payload rejection, category and unique ID validation.

## Out of Scope

- LLM rubric judge, pairwise comparison, runner/report, full 30-50 case benchmark.
- Existing AI GM quality endpoint and adventure deployment gate.
