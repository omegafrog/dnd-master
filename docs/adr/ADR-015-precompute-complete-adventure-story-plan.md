# ADR-015: Precompute a Complete Adventure Story Plan

## Status

Accepted

## Context

The AI Game Master must not invent the entire adventure independently on every turn. It needs a durable procedure covering the broad story from beginning to ending, including when a bundle contains no Main Scenario.

Existing plan 031 assumes a STORYBOOK is mandatory. That assumption does not support a valid Rulebook-Only Bundle.

## Decision

Every adventure session produces an **Adventure Story Plan** before the adventure starts, after the session and its Storybook-derived party capacity have been established.

The plan contains a main stage path, conditional branches, and multiple planned endings. Each stage defines broad scene context, goals, conflicts, important NPCs or clues, and transition conditions.

- When scenario documents exist, the plan is compiled from their source-grounded content.
- For a Rulebook-Only Bundle, AI generates the complete story outline using the selected Game System Definition and player-provided preparation inputs.

The rulebook-only **Adventure Brief** requires expected length and difficulty. Premise, tone, and excluded content are optional; when omitted, generation uses defaults inferred from the Rulebook.

At runtime, the AI Game Master receives the current stage and its transition procedure. It performs **GM Elaboration** by adding narration, dialogue, atmosphere, and local detail without discarding the durable plan or bypassing validated game rules.

Player choices may select a planned branch. The AI Game Master may elaborate local detail but may not create unplanned core plot stages or endings at runtime.

The accepted plan version is included in the adventure start lock. Runtime progress advances a persisted stage cursor instead of regenerating the full plot.

The full Story Plan is never exposed to the Solo Player. There is no Story Plan review page or spoiler-safe summary. The AI Game Master and backend runtime are the only consumers; after adventure start the selected plan version is immutable.

## Consequences

- Rulebook-only adventures have a coherent beginning, progression, and ending instead of only an improvised first scene.
- Resume and retry use the same Story Plan and stage cursor.
- Scenario-backed and AI-generated adventures share one runtime model.
- AI-generated story facts do not claim scenario-document evidence; provenance distinguishes generated plan content from extracted source content.
- Player-facing APIs must not leak hidden stages, branch conditions, or endings.
- Storybook-derived party capacity is stored on the scenario package/session and must be satisfied before adventure start.
- Plans 031-1 through 031-3 must be revised because 031-1 currently requires a STORYBOOK.
