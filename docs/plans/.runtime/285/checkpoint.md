# #285 Combat UI 07 checkpoint

## Status

- Tracker: GitHub issue #285 / Project 5 `Workflow Status=In Progress`
- Branch: `plan/combat-ui`
- Baseline: `056e880b` (`feat(combat): add AI auto progression recovery`)
- Existing unrelated worktree change: `skills-lock.json` (preserve and exclude)

## Contract loaded

- Product Spec: `docs/specs/combat-ui/product-spec.md`
- Architecture Spec: `docs/specs/combat-ui/architecture-spec.md`
- Plan source: GitHub issue #285; dependencies #280 and #284 are complete
- Canonical root-level `docs/specs/product-spec.md` and `docs/specs/architecture-spec.md` are absent; ticket-scoped specs above are the referenced sources.

## Required behavior

- Typed `CombatEndProposal` is validated by a deterministic policy and only an authorized GM proposal may end combat.
- `CombatEndGuard` rejects pending action, reaction, or durable work and requires external effects to be applied.
- End transition appends `CombatEnded`, moves the encounter to `ENDED`, and preserves the active-encounter uniqueness invariant.
- Character and Map remain authoritative in their owning services; final state survives reload.
- AppShell returns to ordinary session UI with a final summary; detailed combat replay is not exposed and legacy routes are cut over.

## Existing seams inspected

- `domain/combat/CombatEncounter`, `CombatEvent`, `PlayerCombatSnapshot`, `PlayerCombatProjectionPolicy`
- `application/combat/CombatLifecycleApplicationService`, action/reaction/auto-progression services and repositories
- `api/CombatController` and `AdventureController`
- `web-ui/src/app/AppShell.tsx`, `features/combat/CombatApi.ts`, `CombatScreen.tsx`
- #280/#284 tests and commits through `056e880b`

## TDD checklist

- [x] Add failing `CombatEndGuardTest`
- [x] Add failing `CombatEndProposalPolicyTest`
- [x] Add failing `PostCombatProjectionPolicyTest`
- [x] Implement domain/application end flow and owning-service final projection
- [x] Implement AppShell ordinary-session return and summary/no-replay contract
- [x] Add relevant integration/UI coverage
- [x] Run focused and full adventure-service tests, web-ui tests and typecheck
- [x] Run `graphify update .`
- [x] Run code review and commit only #285 changes (exclude `skills-lock.json`)

## Verification notes

No headed/live E2E is planned for this slot.

## First TDD checkpoint

- Added the three requested policy tests before production implementation.
- Red baseline command: `cd src && ./gradlew :adventure-service:test --tests com.dndmaster.adventure.combat.CombatEndGuardTest --tests com.dndmaster.adventure.combat.CombatEndProposalPolicyTest --tests com.dndmaster.adventure.combat.PostCombatProjectionPolicyTest`
- Result: expected compile failure because `CombatEndProposal`, `CombatEndProposalPolicy`, `CombatEndGuard`, and `PostCombatProjectionPolicy` do not exist yet.

## Implementation and verification checkpoint

- Added typed GM-only end proposal/policy, pending action/reaction/work guard, terminal `CombatEncounter.end`, and `COMBAT_ENDED` event projection.
- Added Adventure terminal summary commit, Character/Map finalization boundary command, persisted ended-encounter lookup, pending-operation/work queries, and `V65__combat_end_invariant.sql` active uniqueness constraint.
- Added internal committed-GM-turn end controller, final-summary REST projection, legacy `endCombat` rejection, and exception mapping.
- Added AppShell SSE/reload return to ordinary Adventure UI and summary-only terminal display; detailed combat replay is not exposed.
- Focused backend: `CombatEndCommitWiringTest`, `CombatEndGuardTest`, `CombatEndProposalPolicyTest`, `PostCombatProjectionPolicyTest`, `CombatEncounterPolicyTest` — passed.
- Full backend: `./gradlew :adventure-service:test` — 383 tests passed.
- Full web UI: `npm run typecheck && npm test -- --run` — typecheck passed; 31 files / 128 tests passed. Existing AuthFlow act warning remains non-failing.
- `graphify update .` completed; graph rebuilt with 15,677 nodes and 37,530 edges. SQL extraction warning is environmental (`tree_sitter_sql` unavailable).
- Read-only code review completed against the default standards and combat-ui architecture/product specs; no unresolved #285-scope blocker found. Dedicated review subagent execution was unavailable in this runtime.
- Commit: `7a339fe5` (`feat(combat): finalize combat and return to session (#285)`); `skills-lock.json` remains preserved and uncommitted.
