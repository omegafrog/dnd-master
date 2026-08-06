# D&D Master Sitemap

Status: Draft

## Product actor

- **Solo Player** is the only human actor.
- **AI Game Master** runs the adventure; it is not a separate user or administration role.
- The Solo Player owns preparation, character creation, session setup, and play.

## Primary journey

```text
Login or registration
  -> Player Dashboard
  -> Upload and index knowledge documents
  -> Build a scenario source bundle
  -> Compile a scenario package
  -> Review and publish the character creation blueprint
  -> Create a character
  -> Create an adventure session
  -> Configure the party to the Storybook-defined capacity
  -> Generate the hidden Adventure Story Plan (backend/AI GM only)
  -> Start the adventure session
  -> Play with the AI Game Master
  -> Save, resume, complete, or delete the adventure
```

## Sitemap

```text
Unauthenticated
└── Login and registration

Authenticated Solo Player
├── Player Dashboard
│   ├── Continue last-played adventure
│   ├── Other active adventures
│   ├── Draft sessions
│   ├── Start from prepared bundle
│   └── Start initial preparation
├── Preparation
│   ├── Knowledge documents
│   │   ├── Upload
│   │   ├── Processing status
│   │   ├── Source preview
│   │   └── Retry failed processing
│   ├── Scenario bundles
│   │   ├── Create bundle
│   │   ├── Select exactly one rulebook
│   │   ├── Optionally select scenario documents and roles
│   │   ├── Bundle detail and revision
│   │   └── Compile scenario package
│   ├── Game System Review
│   │   ├── Character-sheet preview
│   │   ├── Resources, stats, and supported checks
│   │   ├── Unsupported rules and extraction diagnostics
│   │   ├── Blocking errors and acknowledged warnings
│   │   ├── Rulebook evidence
│   │   └── Approve definition
│   └── Character creation blueprint
│       ├── Review generated inputs
│       ├── Resolve diagnostics
│       └── Publish
├── Adventure sessions
│   ├── Create character
│   ├── Character sheet
│   ├── Create adventure session
│   ├── Read Storybook-defined party capacity
│   ├── Configure party until capacity is met
│   ├── Select DIRECT or AGENT character control
│   ├── Generate hidden Adventure Story Plan (backend/AI GM only)
│   └── Start session and lock party/runtime configuration
├── Adventures
│   ├── Saved adventure list
│   ├── Session knowledge set
│   ├── Resume
│   ├── Delete
│   └── Active play
│       ├── AI Game Master conversation
│       ├── Rule-driven character state
│       ├── Rule-driven actions and checks
│       ├── Dice rolls
│       ├── Rule evidence
│       └── Combat map
└── Account
    ├── Profile
    └── Logout
```

## Current UI routes

| Route | Page responsibility |
|---|---|
| `#/login` | Login and registration |
| `#/` or `#/dashboard` | Target route: state-based Player Dashboard; not implemented in the current UI |
| `#/setup` | Knowledge document and scenario preparation |
| `#/bundles/:bundleId` | Bundle contents, packages, and linked sessions |
| `#/scenario-packages/:packageId/character-blueprint` | Explicitly start character creation for a package; creates a session, then opens blueprint review |
| `#/sessions/:sessionId/character-blueprint` | Review and publish blueprint |
| `#/sessions/:sessionId/character` | Create character |
| `#/sessions/:sessionId` | Create/configure the adventure party using the package capacity, then start session |
| `#/character/:sheetId` | Read character sheet |
| `#/adventures` | Saved adventures and session knowledge selection |
| `#/adventures/:adventureId` | Active play workspace |
| `#/profile` | Account information |

## Contract gaps found during inspection

The current UI and controllers expose flows not covered by the canonical OpenAPI files, including scenario bundles, compilation, character blueprints, adventure sessions, agent turns, and runtime bindings. Identity paths also differ: the canonical contract describes `/api/v1/identity/sessions`, while the current UI and controller use `/api/v1/auth/login`, `/registrations`, and `/logout`.

These gaps should be resolved before treating OpenAPI as the complete navigation capability source.

## Navigation decision

- Login lands on the Player Dashboard.
- Dashboard action priority is active adventure, draft session, prepared bundle, then initial preparation.
- Dashboard presents an explicit next action instead of automatically entering an adventure.
- A Solo Player may keep multiple active adventures and draft sessions.
- The last-played adventure receives the primary `Continue` action; other active adventures and drafts remain selectable.
- Completed adventures move to a history or archive area.

## Dynamic UI decision

- An AI-derived declarative Game System Definition may drive character creation and runtime game rules. The canonical representation is validated, versioned JSON; the dynamic schema is the required capability.
- Dashboard, preparation, account, navigation, conversation history, and other application structure remain fixed frontend UI.
- AI-derived definitions must be validated, versioned, and published by the backend before use.
- The frontend renders only an allowlisted component vocabulary; YAML cannot contain executable code or arbitrary HTML.
- The backend rules engine applies system-specific Runtime Rules, including checks, formulas, resources, event conditions, and state changes.
- Runtime Rules use an allowlisted Rule Operation DSL. Generated JavaScript or other arbitrary code is forbidden; rules outside the current vocabulary are marked `UNSUPPORTED` until the DSL is extended.
- Before Bundle Lock, the Solo Player approves the Game System Definition through a rendered review UI. Raw JSON is not the player-facing editing surface.
- Unsupported character essentials, core checks or resource transitions, death, combat, and progression rules block approval. Unsupported optional rules warn but may be explicitly accepted.
- The frontend renders character fields, runtime resources, available actions, and check inputs from normalized API data. It does not execute authoritative rules.
- D&D-specific concepts such as armor class and attack rolls are not universal. Other game systems may define different resources and transitions, such as SAN changes.
- Each bundle revision contains exactly one rulebook. Its Game System Definition is never merged with another rulebook definition.
- Main Scenario and supporting scenario documents are optional. A rulebook-only bundle is valid.
- Every adventure has a complete Adventure Story Plan before the adventure starts. Scenario documents are compiled into the plan when present; otherwise AI generates the start-to-ending outline after the adventure session and party capacity are established.
- During play, the AI Game Master follows the plan's current stage and transition procedure while elaborating narration and scene detail.
- A Story Plan has a main path, conditional branches, and multiple planned endings. Player choices select paths; the AI Game Master does not invent unplanned core plot nodes or endings at runtime.
- A rulebook-only Adventure Brief, when needed, is collected during preparation; expected length and difficulty are required, while premise, tone, and excluded content are optional. Omitted values use rulebook-derived defaults.
- The full Story Plan is visible only to backend runtime and the AI Game Master. No Story Plan review page or spoiler-safe summary is shown to the Solo Player.
- Storybook-derived Adventure Party Capacity is part of the scenario package. Session creation stores that capacity, party creation enforces it, and adventure start requires the party to satisfy it.
- Entering character creation locks the exact bundle, package, document set, Game System Definition, and character schema versions for that adventure preparation flow.
- Before character creation, the Solo Player may add or remove bundle documents and produce a new draft revision.
- After character creation begins, locked bundle content cannot be added, removed, replaced, or reclassified for that flow.
- The lock pins a bundle revision, not the bundle forever. Later edits create a new revision available to future adventures while existing adventures retain their locked revision.
- Starting the adventure separately locks party membership and runtime configuration.

## Open decision

- Whether base D&D rules are supplied by the service or uploaded by the Solo Player.
- Whether Game System Definitions use only an allowlisted declarative DSL or may contain executable scripts.
- Any remaining primary Solo Player flow missing from the sitemap.
