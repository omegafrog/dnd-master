# Architecture Spec

## 1. Design Scope
| 항목 | 대상 |
|---|---|
| Product Spec | `docs/specs/product-spec.md` |
| Use Cases | UC-01 계획 생성, UC-02 맵 단계 진입, UC-03 작업 재개 |
| Domain | Adventure Story Plan, Tactical Scene Preparation |
| Bounded Contexts | Adventure Runtime, AI Game Master, Combat Map |
| Existing Services | `adventure-service`, `ai-game-master-service`, `combat-map-service` |
| Affected Data | story plan status, tactical scene state, durable generation job |

## 1.2 Product Spec Mapping
| Product Spec | Architecture 요소 |
|---|---|
| UC-01 | Story plan flow persists READY after verification and projection |
| UC-02 | Stage-entry service prepares a mapped stage before map activation |
| UC-03 | Durable generation job repository and idempotent resume command |
| Tactical scene is not a plan prerequisite | Adventure start checks plan/package/party only |
| Tactical failure is retryable | Preparation job owns failure, not AdventureStoryPlan |

## 2. Domain Flow
### Commands
| Command | Actor | Target | Preconditions | Result |
|---|---|---|---|---|
| GenerateAdventureStoryPlan | Solo Player | AdventureStoryPlan | party and package valid | FINAL_EXECUTION_READY plan |
| PrepareTacticalScene | Adventure Runtime | PreparationJob | mapped current stage | READY scene or retryable failure |
| ResumeTacticalPreparation | Runtime | PreparationJob | owner/session match | current durable job state |
| ActivateMappedStage | Runtime | CombatMap | tactical scene READY | active map |

### Events and Policies
| Event | Policy | Result |
|---|---|---|
| StoryPlanProjected | PlanReadinessPolicy | persist READY plan |
| MappedStageEntered | TacticalPreparationPolicy | create or reuse one job for the current stage only |
| TacticalScenePrepared | MapActivationPolicy | activate map |

## 3. DDD Architecture
| Context | Responsibility | Owned Model | Owned Data |
|---|---|---|---|
| Adventure Runtime | plan readiness, stage entry, job ownership | AdventureStoryPlan, TacticalScenePreparationJob | plan and job records |
| AI Game Master | candidate generation and validation | TacticalScenePlanCandidate | none |
| Combat Map | map activation and runtime state | ActiveTacticalMap | active map state |

| Aggregate/Service | Responsibility | Invariant |
|---|---|---|
| AdventureStoryPlan | persist stages and final readiness | tactical scene may be ABSENT when READY |
| TacticalScenePreparationJob | durable stage-specific lifecycle | one active job per session and stage |
| TacticalScenePreparationService | load context, retry candidate, persist result | failure does not block plan |
| StageEntryService | ensure current scene before activation | only current stage activates |

## 4. Program Design
### Affected Seams
- `AdventureStoryPlanApplicationService`: remove eager tactical generation.
- `AdventureSessionApplicationService`: remove the global tactical-scene start gate.
- `AdventureStoryPlanController` or a stage-entry service: prepare the current scene before map activation.
- `AdventureStoryPlanGenerationJobService`: replace process-local state with durable session-owned state.
- Tactical generator gateway: retain typed validation and bounded retries, invoked at stage entry.
- Persistence adapters: store job status, attempt count, failure reason, and prepared scene atomically.

### Failure and Consistency Rules
- Plan persistence occurs only after verification and projection succeed.
- Tactical preparation is independently retryable and idempotent.
- Map activation cannot occur with an absent or failed scene.
- AI candidates cannot directly mutate plans or maps.
- Stage entry exposes the durable job through the Shard CN progress surface and waits for completion.
- Re-entry reads the existing session-and-stage job instead of creating a duplicate.
- Preparation is never proactively started for future stages.

### Test Boundaries
- Unit: READY plan without scenes; job transitions; duplicate preparation; retryable failure.
- Integration: stage entry prepares scene and activates map; persisted job resumes after interruption.
- Browser E2E: Potent Brew reaches plan-ready and adventure-start; mapped stage preparation occurs on entry.

### Non-goal
MCP server and GM tool contracts remain in `docs/issues/mcp-gm-backend-tools.md`.
