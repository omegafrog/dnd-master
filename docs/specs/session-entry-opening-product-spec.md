# Product Spec — Session Entry & Opening Flow

## Problem

Current first GM narration can expose future plot, GM-only facts, or choices players have not made. It can also summarize scenario prose instead of presenting a playable first moment.

## Goals

- Preserve source opening intent and canonical start premise.
- Turn descriptive openings into an immediately playable scene.
- Start without asking user even when source is sparse or ambiguous.
- Insert only a minimal, source-anchored prologue when no reliable entry point exists.
- Keep prologue and main scenario as one natural player experience.

## Terms

- **Source Opening**: source introduction; may not be playable.
- **Playable Entry Point**: first concrete moment player has agency.
- **Start Premise**: source-established context before entry point.
- **Prologue**: generated connector used only when entry point cannot be reliably determined.
- **Canon**: source-verifiable fact.
- **Generated Setting**: non-canonical connector detail; never outranks canon.

## Entry decisions

1. Use an explicit source start scene when present.
2. Otherwise infer earliest reliable interactive moment from source evidence.
3. If alternatives are plausible, prefer earlier safe point to avoid skipping events.
4. If reliable point still unavailable, generate minimal prologue from known region, terrain, purpose, NPC, or opening context.
5. Sparse evidence must still start automatically. Example: Sword Coast only → “small settlement near Sword Coast”, no over-specific lore.

## Rules

- Source canon, start premise, chronology override generated setting.
- Do not treat every source mention as already happened; separate premise, current scene, future summary, and GM secrets.
- Respect source-established player choices; never invent a new completed player choice.
- First narration must state current location, minimum reason for being there, and immediate actionable target/situation.
- Do not pre-summarize unplayed main-plot events or hidden information.
- Prologue is free play, not cutscene; allow deviations while re-offering source entry naturally.
- Prologue creates only connector-scale details: broad place, inn, guide, background figure, rumor, travel context.
- Prologue must not create main NPC motives, faction rewrites, cause of incident, secret interpretation, or main-quest-scale content.
- Generate lazily: establish details only when play needs them.
- No fixed prologue length. End when first reliable source scene is reached.
- Conflict precedence: `CANONICAL_SOURCE > GENERATED_ESTABLISHED > GENERATED_UNEXPOSED`.
- If later canon conflicts, reinterpret generated setting toward canon; preserve player-experienced facts where possible. Never label content as AI-generated to player.

## State flow

`evaluate source → explicit/inferred entry or safe earlier point/prologue → first narration → player action → prologue free play if needed → source-backed scene → normal runtime`

## Acceptance criteria

- Explicit starts retain source state and chronology.
- Descriptive starts become scenes, not plot summaries.
- First narration gives location, minimum context, agency, and no unmade choice.
- Ambiguity/sparse source never requires user confirmation.
- Prologue uses source anchors, stays minimally specific, permits free actions, and ends at reliable source scene.
- Canon wins generated-setting conflicts without exposing generation status.
