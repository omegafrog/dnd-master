# 036-2 Server-owned evidence citation registry

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 036-1
- Product rules: BR-003, BR-004, BR-008, AC-002, AC-003

## 구현 목적

모델이 근거 원문이나 locator를 재작성하지 못하게 하고, 서버가 발급한 짧은 ID만 인용하게 한다. 이를 통해 Storybook 전개와 Rulebook 판정의 실제 근거를 잠긴 bundle에 대해 정확히 검증한다.

## Outcome

The server owns all Storybook and Rulebook evidence identity. The GM returns only short citation handles such as `E1`; it cannot echo, replace, broaden, or invent source evidence.

## Implementation scope

- Build an immutable per-turn citation registry from the locked evidence pack before provider invocation.
- Assign opaque short handles to evidence while retaining source type, locator, bundle/version, visibility, provenance, and protected facts server-side.
- Send the model the handle and the minimum text needed for reasoning; require output to reference handles only.
- Resolve handles after provider parsing and reject unknown, duplicated, stale, cross-session, or visibility-incompatible references.
- Remove full `RuntimeEvidence` objects from the provider response contract.
- Persist canonical server-resolved references, with backward-compatible reads for existing locator strings.
- Ensure Storybook claims cite Storybook evidence and rule judgments cite Rulebook evidence.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeEvidence.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/EvidencePack.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/ModelInputProjection.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/GmFinalValidator.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/integration/HttpGmAgentPort.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/RuntimeTurn.java`
- `src/contracts/ai-game-master/openapi.yaml`

## Acceptance criteria

- Each provider-visible citation handle maps to exactly one locked evidence item for that turn.
- Provider responses contain citation handles only; returned excerpts or evidence objects are rejected.
- Unknown, stale, cross-adventure, and cross-version handles fail closed before persistence.
- Rule judgments cannot be grounded solely in Storybook evidence; story claims cannot cite unrelated evidence.
- Player citations are derived from canonical resolved evidence, never provider-authored locators.
- Hidden evidence contents and protected facts never appear in public citation DTOs.

## Test contract

- Unit: registry identity, stable mapping, source-type policy, visibility policy, unknown/stale handle rejection.
- Integration: fake provider returns valid `E1`, invented `E99`, duplicated IDs, and another turn's ID; only valid resolution commits.
- `UI ~ entity` E2E: a UI turn displays allowed source references matching the committed evidence entities, with no raw hidden excerpt.

## Out of scope

- Browser-wide private payload enforcement; covered by 036-3.
- Rule outcome computation; covered by 036-4.
