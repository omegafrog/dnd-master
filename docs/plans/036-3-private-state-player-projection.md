# 036-3 Private GM state and player projection boundary

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 036-1, 036-2
- Product rules: BR-004, BR-005, BR-008, AC-003, AC-007

## 구현 목적

비밀 정보를 응답에 포함한 뒤 프런트에서 숨기는 구조를 제거한다. GM 전용 상태는 서버 내부 경로에만 두고, 브라우저에는 명시적인 공개 projection만 직렬화해 네트워크 수준의 누출을 차단한다.

## Outcome

Private GM state has a server-owned storage and processing path. Browser APIs, streams, errors, logs, and public citations are constructed only from explicit public types, so private values cannot reach the frontend and merely be hidden by rendering.

## Implementation scope

- Replace mixed public/private runtime plan fields with explicit internal `PublicOutput` and `PrivateState` types.
- Store NPC state, hidden facts, planned reveals, secret DCs, and unrevealed outcomes only in the private path.
- Construct `AdventureController.RuntimeTurnResponse` and stream events from an allowlisted public projection.
- Remove private fields before serialization at every browser-facing boundary, including retry and error responses.
- Keep protected-text overlap checks as defense in depth, not as the primary separation mechanism.
- Audit telemetry and exception mapping so provider bodies and private payloads are never logged or returned.
- Define backward-compatible handling for historical mixed `runtime_turn_json` records.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimePlan.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/GmPlanResult.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/PlayerProjection.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/api/AdventureController.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeTurn.java`
- `src/contracts/adventure/openapi.yaml`
- `src/contracts/adventure/schemas/stream-event.json`
- `src/web-ui/src/features/adventure/AdventureApi.ts`

## Acceptance criteria

- No browser-facing schema contains `privateState`, NPC internal state, hidden facts, planned reveals, or protected evidence.
- Public narration may reveal a fact only through an explicit authoritative reveal transition.
- Network responses, SSE payloads, safe errors, source references, and retry responses pass structural and canary-value leak checks.
- Private state remains available to the next trusted GM turn without browser round-tripping.
- Legacy mixed records are projected safely or rejected without exposing their private fields.
- UI rendering does not receive hidden values even when developer tools inspect raw traffic.

## Test contract

- Unit: allowlist projection, reveal transition, legacy record projection, protected-text defense, safe exception mapping.
- Integration: controller and stream contract tests inject unique private canaries and assert absence from all serialized output and logs captured by the test harness.
- `UI ~ entity` E2E: Playwright records response bodies, SSE messages, and visible DOM while a private-state entity exists; the canary appears nowhere client-side.

## Out of scope

- Client-side CSS hiding as a secrecy mechanism.
- Authoritative dice/combat calculation; covered by 036-4.
