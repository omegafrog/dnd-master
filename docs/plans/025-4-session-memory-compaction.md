# 025-4 Session Memory Compaction

- Issue: #105
- Status: blocked
- Dependencies: #102 / plan 025-1

## Goal

Keep GM input under the 6,000-token budget without deleting raw history or treating a model summary as authoritative game state.

## Scope

- Add `SessionMemory` and checkpoint persistence, migration, versioning, and range metadata.
- Build prompt context from fixed policy, authoritative state snapshots, latest checkpoint, six newest original turns, and action-scoped evidence.
- Trigger compaction over 6,000 estimated input tokens; compact only turns older than newest six.
- Preserve raw conversation and recover relevant old facts with state/evidence search.
- Show long-session continuity in UI without exposing internal prompt text.

## Acceptance Criteria

- At 6,001 estimated tokens a checkpoint is created and latest six raw turns remain unchanged.
- At or below budget no unnecessary compaction occurs.
- A checkpoint cannot override inventory, HP, conditions, quest status, NPC relation, map state, or raw history.
- Checkpoint failure never rolls back an already committed player turn.

## Test Contract

- Unit: deterministic budget policy, exact six-turn window, checkpoint authority rules.
- Integration: migration of existing adventure conversation and compare-and-swap checkpoint updates.
- UI ↔ entity E2E: long adventure compacts repeatedly yet an old relevant quest fact is recovered while current state remains correct.

## Implementation Areas

- `adventure-service` SessionMemory domain/application/persistence and prompt context assembly.
- `ai-game-master-service` bounded prompt request support if required.
- `rule-knowledge-service` existing scoped retrieval contract only.
- `web-ui` session continuity/regression coverage.

