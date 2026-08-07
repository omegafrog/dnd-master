# 036-2 Server-owned evidence citation registry

- Status: `planned`
- Tracker: local Markdown
- Dependencies: 036-1
- Product rules: BR-003, BR-004, BR-008, AC-002, AC-003

## 구현 목적

근거 identity를 서버가 소유한다. provider는 서버가 발급한 짧은 citation handle만 반환하고, 원문·locator·근거 범위를 위조하지 못하게 한다.

## Outcome

Each turn has an immutable server-owned evidence registry scoped to locked session/version and visibility.

## Scope

- Build per-turn registry before provider invocation.
- Map opaque handles to source type, locator, version, visibility, and provenance server-side.
- Reject unknown, duplicate, stale, cross-session, and visibility-incompatible handles.
- Persist canonical resolved references; read legacy locator strings safely.
- Enforce Storybook citations for story claims and Rulebook citations for rule judgments.

## Acceptance

- Provider returns handles only.
- Invented or cross-turn handles fail closed before persistence.
- Player citations derive only from canonical server references.

## Test contract

- Unit: identity, mapping, source-type, visibility, stale-handle rules.
- Integration: valid handle, invented, duplicate, and foreign-turn handles.
- `UI ~ entity` E2E: public source references match committed evidence; no hidden excerpts.

## Likely files

- `src/adventure-service/src/main/java/com/dndmaster/adventure/application/runtime/{RuntimeEvidence,EvidencePack,ModelInputProjection,GmFinalValidator,RuntimeTurn}.java`
- `src/adventure-service/src/main/java/com/dndmaster/adventure/infrastructure/integration/HttpGmAgentPort.java`
- `src/contracts/ai-game-master/openapi.yaml`

## Out of scope

Browser-wide secrecy and rule outcome computation; tickets 036-3 and 036-4.
