# 036-1 Provider-native GM response envelope

- Status: `planned`
- Tracker: local Markdown
- Dependencies: none
- Product rules: BR-001, BR-002, BR-006, BR-007, AC-001, AC-006

## 구현 목적

GM 응답 형식을 프롬프트 권고가 아닌 provider 수준의 강제 계약으로 만든다. 공개 서술, 비공개 상태, 근거 ID, 도구 요청을 처음부터 분리해 소형 모델의 형식 이탈이 게임 상태나 비밀 정보 경계를 훼손하지 못하게 한다.

## Outcome

Every GM provider must return one provider-enforced, versioned response envelope. Public narration, private GM state, citation handles, and proposed tool calls are structurally distinct before application code consumes them.

## Implementation scope

- Replace generic `json_object` output with provider-native JSON Schema where supported.
- Define a versioned envelope with `publicOutput`, `privateState`, `citationIds`, and `toolCalls`.
- Make required properties explicit, reject unknown properties, and constrain enums and collection sizes.
- Keep provider-specific request construction behind the existing completion adapter boundary.
- Parse into distinct typed records; do not deserialize into the current mixed `RuntimePlan` shape.
- Permit one bounded schema-repair attempt using the same command identity, then fail closed with a typed safe error.
- Preserve compatibility when reading already-persisted `runtime_turn_json`; new writes use the new schema version.
- Record schema version, provider, model, attempt count, and failure category without logging private payload values.

## Likely files

- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/infrastructure/ai/OpenAiGmProvider.java`
- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/infrastructure/ai/GmCompletionAdapter.java`
- `src/ai-game-master-service/src/main/java/com/dndmaster/aigamemaster/api/GmAgentController.java`
- `src/contracts/ai-game-master/openapi.yaml`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/GmPlanResult.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimePlan.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/persistence/PostgresRuntimeTurnRepository.java`

## Acceptance criteria

- OpenAI-compatible providers receive a strict JSON Schema, not only `format=json` or `json_object`.
- A valid response maps to typed public, private, citation, and tool-call values without prose parsing.
- Missing required fields, unknown fields, invalid enums, and oversized arrays are rejected.
- Exactly one repair is allowed; both attempts retain one logical command identity.
- A second malformed response returns a stable player-safe error and commits no runtime mutation.
- Existing serialized turns remain readable after deployment.

## Test contract

- Unit: schema definition, required fields, enums, `additionalProperties: false`, size limits, and typed parsing.
- Integration: provider request/response tests for valid output, one repair, repeated malformed output, timeout, and legacy turn reads.
- `UI ~ entity` E2E: submit one UI turn and verify one committed runtime turn; malformed provider output produces no turn/state entity.

## Out of scope

- Citation identity validation; covered by 036-2.
- Player-network secrecy; covered by 036-3.
- Dice or combat state mutation; covered by 036-4.
