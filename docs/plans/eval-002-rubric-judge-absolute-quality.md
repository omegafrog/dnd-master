# EVAL-002: Rubric judge and absolute quality evaluation

Status: `planned`

Parent: #204

Issue: #214

Dependencies: EVAL-001

## 구현 목적

Deterministic evaluator로 판정할 수 없는 narrative continuity·품질·간접 정보 노출을 anchored rubric 기반 structured LLM judge로 평가한다. judge 결과는 hard failure를 절대 상쇄하지 않는 독립 quality score다.

## Scope

- Define `RubricJudgePort`, judge request/response DTOs, structured response validator, and AI GM-backed infra adapter.
- Require score, reason, response evidence for every judged dimension.
- Extend absolute evaluation to combine EVAL-001 hard results with quality scores/judge failure state without blending them.
- Add anchored v1 rubrics for clarity, pacing, engagement, atmosphere, dialogue quality, repetition, continuity, semantic/indirect leakage, and agency interpretation.

## Acceptance Criteria

- [ ] Every enabled quality dimension has explicit anchored score definitions.
- [ ] A valid judge output yields structured score/reason/evidence per requested dimension.
- [ ] Missing/invalid dimension, score, reason, or evidence yields judge failure; it cannot create a fabricated score or change hard results.
- [ ] Absolute evaluation reports hard constraints and quality scores independently.

## Test Contract

- Policy unit tests: complete anchor validation; score range; requested dimension exactness; invalid structured reply; hard failure remains failure after high prose score.
- UI ~ entity e2e: fake `RubricJudgePort` -> absolute evaluation entry -> JSON `EvalResult` with separate hard and quality sections.
- Adapter contract tests: AI provider structured output mapping and fail-closed parsing.

## Out of Scope

- A/B pairwise preference, dataset runner/report, prompt optimization or response rewriting.
