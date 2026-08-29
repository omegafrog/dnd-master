# EVAL-004: Eval runner, report, and seed benchmark

Status: `completed`

Parent: #204

Issue: #217

Dependencies: EVAL-001, EVAL-002, EVAL-003

## 구현 목적

Versioned benchmark를 실행해 hard pass/failure rate, quality/category aggregate, pairwise win/tie/loss와 비교 metadata를 JSON report로 남긴다. 30~50개의 handcrafted turn 사례를 이후 GM 변경의 회귀 기준으로 제공한다.

## Scope

- Implement `EvalRunner`, configuration, optional response-generator port, JSON report writer, and plain Java/Gradle runner entry.
- Persist run/case metadata: run ID, timestamp, case ID, dataset version, model, prompt/config, TurnPlan/schema when relevant, scores/failures.
- Add v1 JSONL benchmark (30-50 unique handcrafted cases) and reusable anchored rubric data under `gm-eval-service` resources.
- Report rule/state/information/agency hard rates, quality averages/per-category breakdown, pairwise win/tie/loss.
- Keep benchmark data outside production runtime/app-all resources and do not modify existing deployment gate.

## Acceptance Criteria

- [ ] Runner consumes a pinned dataset/configuration and creates a structured JSON report.
- [ ] Report has per-case results and required aggregate hard/quality/pairwise metrics.
- [ ] Seed benchmark has 30-50 valid cases, with approximately ten coverage instances per target category; overlap permitted.
- [ ] Dataset integrity validates unique IDs, schema version, category coverage, rubric references, and case count.
- [ ] Two runs can be compared through sufficient persisted metadata.

## Test Contract

- Policy unit tests: aggregation denominators; UNEVALUATED exclusion/reporting; win/tie/loss math; metadata completeness; no hidden blended score.
- UI ~ entity e2e: runner entry + fixture dataset + fake generator/judges -> written JSON report -> golden report assertion.
- Dataset contract tests: JSONL/rubric parsing, 30-50 count, category coverage, unique IDs, reference resolution, report schema round-trip.

## Out of Scope

- CI pass/fail policy, production telemetry, automated production-sample import, scene/session evaluation.
