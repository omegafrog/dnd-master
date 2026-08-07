# 036-3 Private GM state and player projection boundary

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 036-1, 036-2
- Product rules: BR-004, BR-005, BR-008, AC-003, AC-007

## 구현 목적

GM private state를 서버 내부 전용으로 유지하고 browser에는 명시적 public projection만 직렬화한다. 렌더링 후 숨기는 방식이 아니라 network 경계에서 누출을 차단한다.

## Outcome

APIs, SSE, errors, logs, and citations expose only allowlisted public types.

## Scope

- Split public output and private state types.
- Keep hidden facts, secret DCs, planned reveals, and private NPC state internal.
- Build controller/stream/retry/error responses from public projection.
- Audit telemetry and exception mapping for raw provider/private payload leakage.
- Safely project or reject historical mixed `runtime_turn_json`.

## Acceptance

- No browser schema contains private state or hidden identifiers.
- Private canaries absent from responses, SSE, errors, logs, and DOM.
- Trusted next turn can use private state without browser round-trip.

## Test contract

- Unit: allowlist, reveal transition, legacy projection, safe errors.
- Integration: controller/stream canary leak tests.
- `UI ~ entity` E2E: capture network, SSE, DOM; private canary appears nowhere client-side.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/{RuntimePlan,GmPlanResult,PlayerProjection,RuntimeTurn}.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/api/AdventureController.java`
- `src/contracts/adventure/{openapi.yaml,schemas/stream-event.json}`
- `src/web-ui/src/features/adventure/AdventureApi.ts`

## Out of scope

Client CSS hiding and authoritative dice/combat calculation.
