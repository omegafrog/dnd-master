# 036-1 Provider-native GM response envelope

- Status: `ready-for-agent`
- Tracker: local Markdown
- Dependencies: none
- Product rules: BR-001, BR-002, BR-006, BR-007, AC-001, AC-006

## 구현 목적

GM 응답을 provider 강제 JSON Schema와 버전형 typed envelope로 고정한다. 공개 서술, 비공개 상태, 근거 핸들, 도구 요청을 분리해 형식 이탈이 상태·비밀 경계를 훼손하지 못하게 한다.

## Outcome

Every provider returns one versioned, provider-enforced envelope with distinct `publicOutput`, `privateState`, `citationIds`, and `toolCalls`.

## Scope

- Replace generic `json_object` with strict provider-native JSON Schema where supported.
- Require fields, reject unknown properties, constrain enums and collection sizes.
- Parse into typed records; do not deserialize into mixed `RuntimePlan`.
- Allow exactly one bounded repair with the same command identity; then fail closed.
- Read legacy `runtime_turn_json`; write the new schema version.
- Record schema version/provider/model/attempt/failure category without private payloads.

## Acceptance

- Valid output parses without prose parsing.
- Missing, unknown, invalid, oversized responses reject.
- One repair only; second malformed response commits no mutation and returns safe typed failure.
- Existing serialized turns remain readable.

## Test contract

- Unit: schema, required fields, enums, `additionalProperties: false`, limits, typed parsing.
- Integration: valid, repair, repeated malformed, timeout, legacy reads.
- `UI ~ entity` E2E: valid turn commits once; malformed output creates no turn/state entity.

## Likely files

- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/infrastructure/ai/OpenAiGmProvider.java`
- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/infrastructure/ai/GmCompletionAdapter.java`
- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/api/GmAgentController.java`
- `src/contracts/ai-game-master/openapi.yaml`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/{GmPlanResult,RuntimePlan}.java`

## Out of scope

Citation registry, browser secrecy, and authoritative combat; tickets 036-2 through 036-4.
