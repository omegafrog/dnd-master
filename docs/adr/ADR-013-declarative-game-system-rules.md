# ADR-013: Declarative Game System Definitions

## Status

Accepted

## Context

Uploaded rulebooks may describe different game systems. Their character fields, resources, checks, formulas, and turn-time state changes cannot be modeled as D&D-specific frontend behavior. Examples include attack rolls and armor class in one system, or SAN and system-specific checks in another.

## Decision

The AI extracts source-grounded rules into a versioned **Game System Definition**. Because users do not edit definition files directly, validated and normalized JSON is the canonical persistence and API representation. A separate YAML authoring format is not introduced.

Each Scenario Bundle Revision contains exactly one Rulebook. Its Game System Definition is extracted from that Rulebook alone; definitions from multiple rulebooks are not merged.

A Main Scenario is optional. A Rulebook-Only Bundle is valid and still produces a Game System Definition, while its narrative starting point must come from a separate preparation decision.

The definition may describe:

- Character creation fields and constraints.
- Runtime resources and derived values.
- Checks, rolls, modifiers, and formulas.
- Event conditions and state transitions.
- Player-visible actions and presentation hints.
- Source evidence for every extracted rule.

The backend validates, normalizes, versions, and publishes the definition. A backend rules engine is authoritative for applying Runtime Rules. The frontend receives normalized definitions and current state through APIs, then renders only allowlisted components. It does not execute arbitrary YAML code or make authoritative rule decisions.

For the initial implementation, Runtime Rules use an allowlisted **Rule Operation DSL** supporting dice rolls, comparisons, arithmetic, bounds, conditional branches, status changes, lifecycle events, and resource changes. Generated scripts are never executed. A rule outside the current vocabulary is published only as `UNSUPPORTED` diagnostic data until the DSL is deliberately extended.

Before Bundle Lock, the Solo Player completes **Game System Review**. The UI renders the generated character sheet, resources, stats, supported checks, unsupported rules, extraction diagnostics, confidence, and source evidence. The player approves this rendered contract rather than viewing or editing raw JSON.

Unsupported rules carry severity. Missing character essentials, core checks or resource transitions, death, combat, or progression rules are `BLOCKING` and prevent approval. Unsupported optional or rare rules are `WARNING`; the player may approve only after explicitly acknowledging them.

Dynamic behavior is limited to character creation and game-system mechanics during play. Application navigation, document management, account pages, conversation history, and other shell structure remain fixed frontend code.

Conflicts, unsupported constructs, and low-confidence extraction require review before publication. A running session remains bound to a specific published Game System Definition version.

## Consequences

- Multiple tabletop systems can expose different character sheets and runtime mechanics without redeploying system-specific frontend pages.
- A safe declarative rule language and deterministic backend interpreter are required.
- Definitions need schema versioning, source evidence, compatibility tests, and migration rules.
- Unexpressible rules require manual review or a future extension of the declarative vocabulary; model-produced executable code is not trusted.
