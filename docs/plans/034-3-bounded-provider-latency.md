# 034-3 Bounded provider latency

- Status: `completed`
- Tracker: local Markdown
- Dependencies: none
- Product rules: PR-008, FR-002, BR-026

## Outcome

One end-to-end deadline governs context assembly, retrieval, provider generation, repair, and response validation. Timeout behavior is measurable, reproducible, and fails without partial publication.

## Implementation scope

- Define separate retrieval and total-turn budgets plus remaining-time propagation.
- Set explicit connect/request deadlines on GM and retrieval HTTP adapters.
- Bound local generation with measured `num-predict`, context size, retry count, and one repair budget.
- Record cold/warm TTFT, completion, retrieval, repair-inclusive, and end-to-end p50/p95.
- Persist model name, digest, generation config, hardware profile, sample count, and raw timings in artifact.
- Reject benchmark reports with too few samples or missing deadline metadata.

## Likely files

- `src/ai-game-master-service/src/main/resources/application.yml`
- `src/ai-game-master-service/.../configuration/GmProviderProperties.java`
- `src/adventure-service/.../integration/HttpGmAgentPort.java`
- `src/ai-game-master-service/.../retrieval/HttpRuleRetrievalAdapter.java`
- `src/ai-game-master-service/.../retrieval/HttpStoryRetrievalAdapter.java`
- `src/ai-game-master-service/.../benchmark/*`

## Acceptance criteria

- Default live quality request no longer times out solely because token cap is 4096.
- All network adapters have explicit deadlines and preserve interrupt/cancellation semantics.
- Retry and repair cannot exceed total deadline.
- Configured end-to-end p95 budget passes on declared hardware profile.
- Timeout produces no committed turn, state mutation, or leaked partial output.

## Test contract

- Unit: deadline allocation, exhausted budget, retry, and repair calculations.
- Integration: controlled slow Ollama/retrieval endpoints prove cancellation and no publication.
- Live performance: warmup plus statistically adequate measured runs against configured local model.
- `ui ~ entity` e2e: delayed provider shows stable retryable failure; session entity remains at previous committed version.

## Out of scope

- Model quality comparison; ticket 034-5.

## Execution

- Added shared deadline budget seam with retrieval/total budget validation and exhausted-budget failure.
- Raised structured local generation cap to configured 4096 tokens; retained one repair and configured retry policy.
- Added explicit connect/read deadlines to retrieval adapters and connect deadline to GM HTTP adapter.
- Set local Ollama request timeout to 30 seconds and retrieval timeout to 5 seconds.
- Verified with AI Game Master and Adventure module test suites.
- Rejected latency artifacts whose retrieval deadline exceeds the total deadline.
