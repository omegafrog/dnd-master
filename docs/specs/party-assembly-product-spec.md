# Product Spec: Party Assembly

## 1. Problem and Context

The solo player needs a complete adventure party, not merely one character sheet. Scenario material can prescribe an exact party size, recommend a size, or provide no party-size information. The preparation flow must turn that source-grounded requirement into a playable party while preserving the player's authorship of their own character.

## 2. Goals and Desired Outcomes

- The solo player always creates and completes their own playable character sheet.
- The party includes that character and satisfies an exact scenario party-size condition when one exists.
- Without an exact condition, the solo player chooses the total party size.
- Every remaining slot can be filled by a player-authored character or an AI-generated candidate.
- The player reviews each AI candidate's name, ancestry, class, and sheet summary, then adopts or regenerates it.
- Each adopted party member has an independently chosen control mode: solo player or AI.

## 3. Users and Actors

- **Solo Player**: chooses party size where permitted, creates their own character, adopts/regenerates companions, and assigns control modes.
- **AI Character Generator**: proposes a complete companion candidate; it cannot add a member without explicit adoption.
- **Scenario Package**: supplies a source-grounded exact party-size condition or recommendation.

## 4. Ubiquitous Language and Terminology

- **Party Assembly**: preparation step that completes a party before the adventure starts.
- **Party Capacity**: total party members required or recommended by the scenario, including the solo player's character.
- **Exact Party Condition**: source-grounded condition that fixes Party Capacity.
- **AI Companion Candidate**: reviewable, not-yet-adopted character proposed for one open party slot.
- **Control Mode**: `PLAYER` when the solo player chooses the member's actions; `AI` when an AI controls that member in play.

## 5. Core Use Cases

### UC-PA-001 Assemble a party with an exact condition

1. The system shows the source-grounded exact capacity.
2. The player completes their own character sheet for the first slot.
3. The player fills every remaining slot through direct creation or AI candidate adoption.
4. The player assigns a Control Mode to every member.
5. The party becomes ready only when the exact capacity is filled.

### UC-PA-002 Choose a party size without an exact condition

1. The system shows any scenario recommendation as guidance.
2. The player chooses a total capacity that includes their own character.
3. The player completes and configures all chosen slots as in UC-PA-001.

### UC-PA-003 Review an AI companion

1. The player requests generation for an open slot.
2. The system presents the candidate's name, ancestry, class, and character-sheet summary.
3. The player adopts it or requests regeneration.
4. Only adoption puts the candidate in the party.

## 6. Business Rules and Invariants

- **BR-PA-001** The solo player's character is required and must have a completed sheet before party completion.
- **BR-PA-002** Party Capacity counts all party members, including the solo player's character.
- **BR-PA-003** An Exact Party Condition overrides a recommendation and user preference.
- **BR-PA-004** If no Exact Party Condition exists, the player may choose Party Capacity; a recommendation is non-binding guidance.
- **BR-PA-005** Every party member has exactly one Control Mode selected before Adventure Start Lock.
- **BR-PA-006** An AI Companion Candidate is not a party member until the player adopts it.
- **BR-PA-007** Party Assembly cannot start the adventure with fewer or more members than an exact capacity.

## 7. States and State Transitions

`OPEN` → `FILLING` → `READY` → `LOCKED`

- Changing capacity, a member, or control mode returns `READY` to `FILLING`.
- An AI candidate follows `PROPOSED` → `ADOPTED|REGENERATED|DISCARDED`.
- `LOCKED` begins with Adventure Start Lock and is immutable.

## 8. Failures, Exceptions, and Boundary Conditions

- If source extraction cannot determine whether a party-size statement is exact, show it as a recommendation and allow player choice.
- If AI generation fails, preserve already adopted members and offer retry or direct creation.
- If the player's own sheet is incomplete, block party readiness with a direct link to finish it.
- If a selected control mode is missing, block party readiness without changing member data.

## 9. Inputs and Outputs

Inputs: package party-capacity evidence, character-creation schema, chosen capacity, direct character sheets, AI generation requests, candidate adoption, Control Modes.

Outputs: source-labelled capacity guidance, slot checklist, AI candidate summaries, adopted party members, readiness diagnostics, immutable party at start.

## 10. Scope and Non-goals

In scope: one solo player's mandatory character, AI/direct companion creation, adoption/regeneration, and per-member Control Mode.

Out of scope: concurrent human players, AI candidate auto-adoption, and party changes after the adventure starts.

## 11. Priorities and Trade-offs

1. Player ownership of their own character and explicit adoption of AI characters.
2. Exact scenario conditions.
3. A usable fast path for filling companion slots.

## 12. Success Conditions and Acceptance Criteria

- **AC-PA-001** An exact source-grounded capacity prevents starting until precisely that many members, including the player's character, are configured.
- **AC-PA-002** With no exact condition, the player can select capacity and proceed with that number.
- **AC-PA-003** The player's own incomplete sheet blocks party readiness.
- **AC-PA-004** An AI candidate visibly exposes name, ancestry, class, and sheet summary and cannot enter the party before adoption.
- **AC-PA-005** Every adopted member can independently use `PLAYER` or `AI` Control Mode.
