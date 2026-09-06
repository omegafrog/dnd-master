# Architecture Spec — 전용 Combat UI

# 1. Design Scope

## 1.1 Target

| 항목 | 대상 |
|---|---|
| Product Spec | `docs/specs/combat-ui/product-spec.md` |
| Use Cases | UC-001~UC-010 |
| Domain | Adventure Runtime의 전투 lifecycle과 Combat UI projection |
| Bounded Contexts | Adventure Runtime, AI Game Master, Dice Roll, Character Management, Combat Map |
| Existing Services | `adventure-service`, `ai-game-master-service`, `dice-roll-service`, `character-management-service`, `combat-map-service`, `web-ui` |
| External Dependencies | PostgreSQL, 기존 내부 HTTP API, SSE |
| Affected Data | CombatEncounter snapshot, 참가자·Initiative, Turn 자원, Reaction interrupt, Narrative Combat Position, CombatActionOperation, CombatEvent |

Combat은 새 Bounded Context나 배포 서비스가 아니다. `adventure-service`의 Adventure Runtime 내부 capability이며, `CombatEncounter`가 전투 lifecycle 정본 Aggregate다.

## 1.2 Product Spec Mapping

| Product Spec 항목 | Architecture 요소 |
|---|---|
| UC-001 전투 진입 | GM Turn의 구조화된 `CombatStartProposal`을 검증하고 같은 commit에서 `CombatEncounter` 생성 |
| UC-002 플레이어 Turn | `CombatEncounter`의 현재 참가자·TurnResources·명시적 `EndPlayerTurn` |
| UC-003 정형 행동 | `CombatRulesEngine` 사전 검증 → 자원 예약 → 멱등 Runtime Command Saga → commit |
| UC-004 이동 | 맵 존재 시 `CombatMapPort`, 부재 시 `NarrativeCombatPosition` |
| UC-005 자유 행동 | `AiCombatDecisionPort.interpretFreeForm` 제안 → `CombatRulesEngine` 검증 |
| UC-006 Reaction | Engine Trigger 판정 → `REACTION_PENDING` → 명시적 사용/넘김 또는 AI 자동 결정 |
| UC-007/008 AI Turn | durable `CombatAutoProgressionService` |
| UC-009 전투 종료 | GM 종료 제안 검증 → pending 작업 없음 guard → 최종 상태 적용과 종료 event commit |
| UC-010 전투 재개 | Combat snapshot 조회 + event cursor 기반 SSE 재연결 |
| BR-001~004 | `CombatEncounter` lifecycle, InitiativeOrder, RoundNumber, current participant guard |
| BR-005~009 | `TurnResources`와 `CombatRulesEngine`; 거부 시 예약 해제·자원 미소비 |
| BR-010 | `PlayerCombatProjectionAssembler`에서 적 비공개 정보 제거 |
| BR-011 | Auto-progression과 Reaction interrupt 정책 |
| BR-012 | nullable map binding과 `NarrativeCombatPosition` |
| AC-001~022 | API·projection·state transition·복구·보안·검증 계약 |

## 1.3 Architecture Coverage

| Topic | 상태 | 근거 |
|---|---|---|
| Design Scope / Product Mapping | SETTLED | Product Spec와 본 문서 1.2 |
| Domain Flow / Hotspots | SETTLED | Product Spec, 합의된 lifecycle·Reaction·retry |
| Boundary Promotion | SETTLED | 새 BC/service 없음; Adventure Runtime 내부 capability |
| Context Map / Rule Ownership | SETTLED | ADR-003, ADR-012, ADR-013, `CONTEXT-MAP.md` |
| Entities / Value Objects / Services | SETTLED | `CombatEncounter` 설계 |
| State / Repository Boundaries | SETTLED | snapshot + append-only Event, state diagram |
| Program Design | SETTLED | 전용 Controller와 application/domain seams |
| Technical Boundary Mapping | SETTLED | 기존 service ownership 유지 |
| Runtime | SETTLED | optimistic version, command idempotency, durable worker |
| Error / Recovery | SETTLED | 자동 재시도 2회 후 `PROCESSING_FAILED` |
| Security / Observability | SETTLED | owner projection, internal token, metrics/traces |
| Change / Verification | SETTLED | 10장과 11장 |
| Alternatives / Risks / Questions | SETTLED | 12장과 13장; blocking open question 없음 |

---

# 2. Domain Flow

## 2.1 Event Storming Flow

1. GM Turn이 `CombatStartProposal`을 생성한다.
2. Adventure Runtime이 제안을 검증하고 GM Turn commit과 함께 `CombatEncounter`를 `PREPARING`으로 생성한다.
3. Game Engine이 참가자와 Initiative를 확정한다.
4. `CombatEncounter`가 Round 1과 첫 Combat Turn을 시작한다.
5. Human Turn은 명령을 기다린다. AI Turn은 durable auto-progression work item을 만든다.
6. 행동은 사전 검증 후 자원을 예약하고 외부 상태 명령을 멱등 Saga로 실행한다.
7. Trigger가 있으면 현재 행동을 `REACTION_PENDING`으로 중단한다.
8. Reaction 해결 후 동일 행동의 중단 지점부터 재개한다.
9. 마지막 참가자 Turn 종료 시 다음 Round를 시작한다.
10. AI GM 종료 제안이 검증되면 최종 외부 상태와 `CombatEnded`를 확정하고 일반 세션 projection으로 복귀한다.

Product 흐름: [UC-CUI.activity.svg](diagrams/product/UC-CUI.activity.svg)

## 2.2 Commands

| Command | Actor | Target | Preconditions | Result |
|---|---|---|---|---|
| `StartCombat` | Adventure Runtime from committed GM Turn | Combat lifecycle | AI GM 제안 유효, active encounter 없음 | `CombatStarted` |
| `SubmitCombatAction` | Human Player | CombatEncounter | owner, PLAYER_TURN, current actor, version 일치 | 예약 또는 구조화된 거부 |
| `SubmitFreeFormAction` | Human Player | CombatEncounter | PLAYER_TURN, 입력 유효 | AI 해석 후 동일 action pipeline |
| `EndPlayerTurn` | Human Player | CombatEncounter | PLAYER_TURN, pending action/reaction 없음 | `TurnEnded` |
| `ResolveReaction` | Human Player / Auto-progression | CombatEncounter | REACTION_PENDING, eligible actor | `ReactionResolved` |
| `AdvanceAiTurn` | Auto-progression worker | CombatEncounter | AI_TURN | AI 행동·Turn 진행 |
| `RetryCombatOperation` | Human Player / operator path | CombatActionOperation | PROCESSING_FAILED | 같은 command 재개 |
| `EndCombat` | Adventure Runtime from GM proposal | CombatEncounter | pending 없음, 종료 제안 유효 | `CombatEnded` |

모든 public command는 `Idempotency-Key`와 `If-Match-Version`을 요구한다. 내부 자동 command도 고유 `commandId`와 encounter expected version을 갖는다.

## 2.3 Domain Events

| Domain Event | Producer | Trigger | 주요 Payload | Consumer |
|---|---|---|---|---|
| `CombatStarted` | CombatEncounter | 전투 시작 commit | encounterId, participants, initiative | projection, SSE, auto-progression |
| `RoundStarted` | CombatEncounter | 첫 Round 또는 마지막 Turn 종료 | round, first participant | projection |
| `TurnStarted` | CombatEncounter | current participant 변경 | round, participant, resources | projection, auto-progression |
| `ActionReserved` | CombatEncounter | 검증 성공 | commandId, actor, cost | saga worker |
| `ActionRejected` | CombatEncounter | 규칙 거부 | stable violations, no cost | Combat Log projection |
| `ActionResolved` | CombatEncounter | 외부 효과와 local commit 성공 | roll breakdown, public outcome | projection, Reaction policy |
| `ReactionRequested` | CombatEncounter | Engine Trigger | reactionId, options, suspended command | UI / auto-progression |
| `ReactionResolved` | CombatEncounter | 사용 또는 넘김 | choice, public result | suspended action |
| `TurnEnded` | CombatEncounter | 명시적 Human 종료 또는 AI 종료 | participant, next index | Round policy |
| `CombatProcessingFailed` | CombatActionOperation | 총 3회 실행 실패 | commandId, failure code | UI, metrics |
| `CombatEnded` | CombatEncounter | 종료 commit | reason, final summary | projection, 일반 세션 UI |

`CombatEvent`는 full event-sourcing source of truth가 아니다. 현재 상태 정본은 `CombatEncounter` snapshot이다.

## 2.4 Policies

| Policy | Trigger | Decision | Follow-up | Owner |
|---|---|---|---|---|
| Combat Start Policy | committed GM Turn | 제안 검증, active encounter 중복 차단 | `StartCombat` | Adventure Runtime |
| Action Commit Policy | action request | 검증 → 예약 → 외부 Saga → local commit | `ActionResolved` | Adventure Runtime |
| Reaction Interrupt Policy | Engine Trigger | 현재 command·resumeState 보존 | `ReactionRequested` | CombatEncounter |
| Auto-progression Policy | AI `TurnStarted` | 다음 Human/Reaction/End까지 자동 실행 | `AdvanceAiTurn` | Adventure Runtime |
| Round Advancement Policy | `TurnEnded` | 마지막 참가자면 round 증가 | `RoundStarted` | CombatEncounter |
| Combat End Policy | GM end proposal | pending action/reaction 없음 확인 | `EndCombat` | Adventure Runtime |
| Retry Policy | transient failure | 1초, 2초 backoff로 2회 재시도 | 실패 시 `PROCESSING_FAILED` | application worker |

## 2.5 Read Models

| Read Model | Consumer | Source | 주요 Fields | Owner |
|---|---|---|---|---|
| `PlayerCombatSnapshot` | Combat UI | CombatEncounter + player-safe external projections | status, round, current turn, initiative, own resources, public participants, reaction, map/narrative position, event cursor | Adventure Runtime |
| `PlayerCombatEvent` | SSE client | CombatEvent | sequence, type, player-safe payload | Adventure Runtime |
| `CombatLogProjection` | Combat Log | player-safe CombatEvent | ordered action, roll breakdown, damage/heal/status, Reaction, end summary | Adventure Runtime |
| `InternalCombatDiagnostic` | operator/metrics | operation + internal Event | command, retry, failed step, versions | Adventure Runtime |

Player API와 SSE는 raw `CombatEvent.internalPayload`를 직렬화하지 않는다.

## 2.6 External Interactions

| External System | Trigger | Input | Output | Failure |
|---|---|---|---|---|
| AI Game Master | start/end, free-form, AI action/reaction | player-safe context + rules references | typed proposal/plan | invalid plan 또는 timeout |
| Dice Roll | initiative/action resolution | commandId, expression, actor | immutable roll breakdown | transient/permanent failure |
| Character Management | validation and mutation | sheetId, expectedVersion, structured mutation | snapshot/outcome/version | conflict/rejection/unavailable |
| Combat Map | mapped movement/action | mapId, tokenId, path/target, expectedVersion | updated map version | conflict/invalid path/unavailable |
| Game System Definition | rule evaluation | bound ruleset revision | normalized Runtime Rules | missing/unsupported blocking rule |

## 2.7 Hotspots

| Hotspot | Decision |
|---|---|
| Combat boundary | Adventure Runtime 내부 capability; 새 BC/service 없음 |
| AI automatic flow | durable state machine; Human/Reaction/End에서만 정지 |
| Reaction authority | Game Engine Trigger, CombatEncounter interrupt, Human 무기한 대기 |
| Client synchronization | REST command + SSE event + REST snapshot recovery |
| Persistence | snapshot + append-only Event; full event sourcing 아님 |
| Partial failure | reservation + idempotent Saga; full success 후 소비 |
| API placement | 전용 `CombatController`, 같은 `adventure-service` |
| Mapless space | structured `NarrativeCombatPosition` |
| Retry exhaustion | 초기 1회 + 자동 재시도 2회 후 `PROCESSING_FAILED` |
| Post-combat log | 내부 Event 유지, player-facing detailed replay 없음 |

---

# 3. DDD Architecture

## 3.1 Bounded Contexts

| Bounded Context | Responsibility | Owned Model/Data |
|---|---|---|
| Adventure Runtime | Combat lifecycle, ordering, action orchestration, player-safe projection | CombatEncounter, operation journal, CombatEvent |
| AI Game Master | 구조화된 판단·계획 제안 | model interaction result; authoritative game state 없음 |
| Dice Roll | 주사위 결과 정본 | DiceRoll |
| Character Management | HP, inventory, effect, character resource 정본 | CharacterSheet |
| Combat Map | map, token, position, visibility 정본 | CombatMap |

## 3.1.1 Boundary Decisions

| Capability | Owner | Candidate | Chosen Boundary | Why Not Weaker? | Why Not Stronger? |
|---|---|---|---|---|---|
| Combat lifecycle | Adventure Runtime | methods / Aggregate / BC / service | `CombatEncounter` Aggregate | Adventure aggregate의 narrative turn cursor만으로 독립 전투 lifecycle·resume·version을 보호할 수 없음 | 독립 language/data lifecycle/deployment 필요 없음 |
| Combat orchestration | Adventure Runtime | class / package / module / service | internal application package | 여러 port와 durable Saga를 단일 class에 두면 책임·테스트 seam 불충분 | 새 Gradle module/service는 build/runtime 비용만 추가 |
| Combat UI | web-ui | component / feature package / app | feature package | dedicated screen과 상태 store 필요 | 별도 frontend app 필요 없음 |
| Rule evaluation | Adventure Runtime | helper / domain service / service | internal domain service | 규칙 검증·Reaction detection을 AI/Controller에 둘 수 없음 | ADR-012/013상 별도 배포 service 요구 없음 |

## 3.2 Context Map

| Upstream | Downstream | Relationship | Contract | Translation |
|---|---|---|---|---|
| Adventure Runtime | Dice Roll | Customer/Supplier | internal roll command API | `DiceCombatPort` |
| Adventure Runtime | Character Management | Customer/Supplier | runtime character read/mutation API | `CharacterCombatPort` |
| Adventure Runtime | Combat Map | Customer/Supplier | map view/command API | optional `CombatMapPort` |
| AI Game Master | Adventure Runtime | Provider/Customer with validation | typed combat proposal API | `AiCombatDecisionPort` ACL |
| Game System Definition | Adventure Runtime | Published Language | versioned normalized rules | `ResolvedCombatRules` adapter |

AI outputs are proposals. Adventure Runtime validates every proposal before state mutation.

## 3.3 Aggregates

| Aggregate | Root | Responsibility | Commands | Events | Invariants |
|---|---|---|---|---|---|
| Combat Encounter | `CombatEncounter` | lifecycle, participant order, Round/Turn, own action resources, interrupt | start, reserve/commit/reject action, resolve reaction, end turn, fail/retry, end | CombatStarted through CombatEnded | 한 Adventure에 active 1개; current actor만 행동; Human Turn 명시 종료; pending interrupt 보존 |
| Character Sheet | existing `CharacterSheet` | character-owned mutation | existing versioned commands | existing events | CombatEncounter가 직접 변경 금지 |
| Combat Map | existing `CombatMap` | map/token/location | existing map commands | existing events/history | map binding optional |

`CombatActionOperation`은 cross-context execution을 재개하기 위한 application saga record다. 별도 Bounded Context나 business Aggregate로 승격하지 않는다.

## 3.4 Entities

| Entity | Aggregate | Identity | Responsibility | State |
|---|---|---|---|---|
| CombatParticipant | CombatEncounter | participantId | controller, character reference, initiative, turn resources | active/incapacitated/left |
| CombatEvent | CombatEncounter stream | encounterId + sequence | immutable ordered audit/projection input | type, payload, visibility |

## 3.4.1 Class Diagram

원본: `docs/specs/combat-ui/diagrams/architecture/adventure-runtime.class.puml`  
SVG: [Adventure Runtime Combat Class Design](diagrams/architecture/adventure-runtime.class.svg)

## 3.5 Value Objects

| Value Object | Aggregate | Values | Validation / Behavior |
|---|---|---|---|
| InitiativeOrder | CombatEncounter | ordered participant IDs + scores + tie-break | 모든 참가자 정확히 1회, deterministic tie-break |
| TurnResources | CombatEncounter | movement, action, bonus action, reaction, rule-defined extensions | reserve/commit/release; 음수 금지 |
| ReactionInterrupt | CombatEncounter | reactionId, trigger, options, eligible actor, resumeState | pending 1개, eligible actor만 resolve |
| NarrativeCombatPosition | CombatEncounter | subject/target, range band, cover | mapless에서만 authoritative; AI proposal + Engine validation |
| CombatCommandIdentity | operation | commandId, fingerprint, expectedVersion | commandId 재사용 시 fingerprint 동일 |
| EncounterVersion | CombatEncounter | non-negative long | successful state transition마다 증가 |

## 3.6 Domain Services

| Domain Service | Responsibility | Input | Output | Collaborators |
|---|---|---|---|---|
| CombatRulesEngine | Initiative/action/resource/Reaction의 결정론적 규칙 | resolved rules, encounter, external snapshots, intent | evaluation/resolution/options | Game System Definition |
| CombatEndGuard | 종료 전 pending·외부 적용 완료 검증 | encounter, end proposal | accepted/rejected | 없음 |
| PlayerCombatProjectionPolicy | hidden info 제거 규칙 | internal state/event + visibility | player-safe projection | external public views |

AI GM은 Domain Service가 아니다. typed proposal를 제공하는 외부 context다.

## 3.7 Business Rule Ownership

| Business Rule | Owner | Enforcement Point |
|---|---|---|
| BR-001 | Combat Start Policy | `startFromCommittedGmTurn` |
| BR-002~004 | CombatEncounter | `start`, `endCurrentTurn` |
| BR-005~009 | CombatRulesEngine + TurnResources | `validateAction`, `reserve/commit/release` |
| BR-010 | PlayerCombatProjectionPolicy | `toPlayerSnapshot` / `toPlayerEvent` |
| BR-011 | Auto-progression + Reaction policy | `advanceUntilDecision` |
| BR-012 | CombatActionApplicationService | map binding 분기 |

## 3.8 Aggregate State Transitions

| Current | Trigger | Next | Guard | Event |
|---|---|---|---|---|
| none | accepted StartCombat | PREPARING | committed GM Turn, no active encounter | CombatStarted |
| PREPARING | initiative complete | PLAYER_TURN / AI_TURN | all participants ordered | RoundStarted, TurnStarted |
| PLAYER_TURN / AI_TURN | accepted action | ACTION_PROCESSING | actor/version/rules valid | ActionReserved |
| ACTION_PROCESSING | Reaction Trigger | REACTION_PENDING | eligible Reaction exists | ReactionRequested |
| REACTION_PENDING | use/pass/AI decision | ACTION_PROCESSING | eligible actor | ReactionResolved |
| ACTION_PROCESSING | full Saga success | resume Turn / ENDING | all required effects done | ActionResolved |
| ACTION_PROCESSING | retries exhausted | PROCESSING_FAILED | total attempts = 3 | CombatProcessingFailed |
| PROCESSING_FAILED | manual retry | ACTION_PROCESSING | same command/reservation | retry diagnostic event |
| PLAYER_TURN | explicit EndPlayerTurn | next Turn | no pending work | TurnEnded |
| AI_TURN | automatic completion | next Turn | no pending work | TurnEnded |
| active Turn | accepted end proposal | ENDING | no pending work | CombatEnding |
| ENDING | final state committed | ENDED | required effects applied | CombatEnded |

## 3.8.1 State Diagram

원본: `docs/specs/combat-ui/diagrams/architecture/combat-encounter.state.puml`  
SVG: [CombatEncounter Design State](diagrams/architecture/combat-encounter.state.svg)

Product business-state diagram은 별도 생성하지 않았다. Product activity diagram이 사용자 lifecycle을 이미 표현한다. 본 state diagram은 persistence·retry·interrupt guard를 표현하는 독립 설계 관점이다.

## 3.9 Repository Boundaries

| Repository | Aggregate/Data | Operations | Consistency Boundary |
|---|---|---|---|
| CombatEncounterRepository | CombatEncounter snapshot + participants + narrative positions | findActive, getForUpdate, save(expectedVersion) | encounter row/version |
| CombatEventRepository | CombatEvent | append(nextSequence), after(sequence), finalSummary | encounter event sequence |
| CombatOperationRepository | CombatActionOperation | findByCommandId, save, claimRetryable | commandId |
| AdventureRepository | Adventure | existing load/save | GM Turn과 combat start/end local transaction |

---

# 4. Program Design

## 4.1 Program Structure

`CombatController` → `CombatQueryApplicationService` / `CombatActionApplicationService` / `CombatReactionApplicationService` / `CombatLifecycleApplicationService` → `CombatEncounter` + `CombatRulesEngine` → existing output ports.

`CombatAutoProgressionWorker`는 persisted work item을 claim하고 `CombatAutoProgressionService.advanceUntilDecision`을 호출한다. 외부 broker를 추가하지 않는다.

## 4.2 Major Components and Responsibilities

| Component | Responsibility | Dependencies | Must Not Do |
|---|---|---|---|
| CombatController | REST/SSE adapter, auth, DTO mapping | application services | 규칙 판단, raw hidden payload 노출 |
| CombatLifecycleApplicationService | GM Turn과 start/end 원자적 조정 | encounter/adventure repos, AI proposal | AI 제안을 직접 신뢰 |
| CombatActionApplicationService | validation, reservation, saga, commit | rules engine, repos, ports | 검증 전 dice/mutation |
| CombatReactionApplicationService | Reaction 선택 검증·재개 | encounter repo, rules engine | Human 선택 timeout |
| CombatAutoProgressionService | AI Turn/Reaction을 decision point까지 진행 | AI port, action service | Human Turn 자동 종료 |
| CombatAutoProgressionWorker | durable work claim/retry/backoff | operation repo | in-memory-only queue |
| CombatProjectionService | snapshot/log/event player projection | repos, external public views | enemy private stat 반환 |
| CombatRulesEngine | deterministic rule authority | resolved rules | narration·persistence·HTTP |
| AiCombatDecisionPort | typed AI proposal ACL | AI GM adapter | state mutation |

## 4.3 Application Flow

### Human action

1. Controller가 owner, headers, DTO 형식을 검증한다.
2. Application Service가 commandId duplicate/fingerprint를 확인한다.
3. Encounter를 version 조건으로 lock한다.
4. Rules Engine이 현재 actor, range, cost, target, slot을 검증한다.
5. 거부면 `ActionRejected`를 기록하고 예약/외부 명령 없이 structured violations를 반환한다.
6. 수락이면 cost를 예약하고 operation과 외부 command steps를 저장한다.
7. worker 또는 현재 request가 idempotent steps를 실행한다.
8. 모든 step 성공 후 local transaction에서 예약을 소비하고 `ActionResolved`를 append한다.
9. Reaction 또는 end condition을 평가하고 다음 state/work item을 저장한다.
10. SSE가 committed player event를 전달한다.

### AI auto-progression

1. AI_TURN work item을 claim한다.
2. AI GM이 typed action plan을 제안한다.
3. 동일 Human action pipeline으로 검증·실행한다.
4. Reaction pending이면 정지한다.
5. AI Turn 종료 후 다음 actor가 AI면 새 durable work를 저장한다.
6. Human actor 또는 Combat End면 자동 진행을 멈춘다.

## 4.4 Component Call Contracts

| Order | Caller | Callee | Operation | Result | Failure |
|---:|---|---|---|---|---|
| 1 | CombatController | CombatActionApplicationService | submit(command) | accepted/rejected/existing | auth, validation, conflict |
| 2 | Action Service | CombatRulesEngine | validateAction(context, intent) | evaluation | unsupported blocking rule |
| 3 | Action Service | Encounter/Operation repos | reserveAndCreate | versioned operation | conflict |
| 4 | worker | Dice/Character/Map ports | execute idempotent step | step result | transient/permanent |
| 5 | Action Service | Encounter/Event repos | commitAction | new version/events | local transaction failure |
| 6 | Auto-progression | AiCombatDecisionPort | plan typed action | plan | invalid/timeout |
| 7 | Projection Service | repos/public ports | snapshot/after | player-safe DTO/events | unavailable projection |

## 4.5 Major Types

| Type | Kind | Responsibility |
|---|---|---|
| CombatEncounter | Aggregate Root | lifecycle/order/resources/interrupt |
| CombatParticipant | Entity | actor identity/control/initiative/resources |
| CombatActionIntent | sealed Domain Type | Movement, Structured, FreeForm |
| CombatActionEvaluation | Domain Type | accepted cost/roll/effects or violations |
| CombatActionOperation | Application Saga Record | durable execution/retry |
| CombatEvent | Immutable Record | ordered internal audit/projection input |
| PlayerCombatSnapshot | DTO | player-safe current view |
| PlayerCombatEvent | DTO | player-safe SSE event |

## 4.6 Type Design

### CombatEncounter

| Field | Type | Constraint |
|---|---|---|
| id / adventureId | IDs | 한 Adventure에 active encounter 최대 1 |
| status | CombatEncounterStatus | state diagram 전이만 허용 |
| participants | ordered entities | InitiativeOrder와 동일 cardinality |
| round / turnIndex | positive int / index | round ≥ 1, index in range |
| pendingReaction | nullable ReactionInterrupt | REACTION_PENDING일 때 필수 |
| activeOperationId | nullable UUID | ACTION_PROCESSING/FAILED일 때 필수 |
| mapId | nullable UUID | 없으면 NarrativeCombatPosition 사용 |
| version / eventSequence | long | 단조 증가 |

주요 behavior: `start`, `reserveAction`, `rejectAction`, `commitAction`, `requestReaction`, `resolveReaction`, `endCurrentTurn`, `markProcessingFailed`, `end`.

### CombatActionOperation

| Field | Meaning |
|---|---|
| commandId / fingerprint | idempotency identity |
| encounterId / expectedVersion | target concurrency contract |
| status | PENDING, COMMITTING, RETRY_WAIT, PROCESSING_FAILED, COMMITTED |
| reservedCost | local reservation reused across retries |
| steps | ordered external commands with idempotency keys |
| attemptCount / nextAttemptAt | initial + 2 retries |
| lastFailure | stable category/code, no secret payload |

## 4.7 Interfaces and Function Signatures

```java
interface CombatEncounterRepository {
    Optional<CombatEncounter> findActiveByAdventure(AdventureId adventureId);
    CombatEncounter getForUpdate(CombatEncounterId encounterId);
    void save(CombatEncounter encounter, long expectedVersion);
}

interface CombatRulesEngine {
    InitiativeOrder determineInitiative(CombatPreparationContext context);
    CombatActionEvaluation validateAction(CombatRulesContext context, CombatActionIntent intent);
    CombatActionResolution resolveAction(CombatRulesContext context, CombatActionEvaluation evaluation, List<RollResult> rolls);
    List<ReactionOption> detectReactions(CombatRulesContext context, CombatActionResolution resolution);
    TurnResources initializeTurnResources(ResolvedGameSystemRules rules, CombatParticipant participant);
}

interface AiCombatDecisionPort {
    CombatStartProposal proposeStart(CombatStartContext context);
    FreeFormActionPlan interpretFreeForm(FreeFormCombatContext context);
    AiTurnPlan planTurn(AiCombatTurnContext context);
    ReactionDecision decideReaction(AiReactionContext context);
    CombatEndProposal proposeEnd(CombatEndContext context);
}
```

모든 AI 반환형은 schema validation 대상이다. 구현 adapter만 AI provider DTO를 알고 domain/application은 provider 형식에 의존하지 않는다.

## 4.8 Error Propagation

| Failure Point | Source | Converted Error | Result |
|---|---|---|---|
| Controller | malformed/auth | `INVALID_COMBAT_COMMAND` / 401 / 403 | state unchanged |
| Encounter | stale/current actor | `COMBAT_VERSION_CONFLICT` / `NOT_CURRENT_ACTOR` | 409/422 |
| Rules Engine | impossible action | `ACTION_NOT_ALLOWED` + violations | 422, no cost |
| AI adapter | timeout/invalid schema | transient/permanent integration failure | retry or PROCESSING_FAILED |
| Dice/Character/Map | unavailable/conflict | typed command failure | retry/replan/fail |
| Projection | hidden field attempt | `PROJECTION_POLICY_VIOLATION` | 500 + alert, no payload |

## 4.9 State Transition Implementation

| Transition | Owner | Method | Persistence | Published Event |
|---|---|---|---|---|
| no combat → PREPARING | Lifecycle Service + CombatEncounter | startFromCommittedGmTurn/start | Adventure + encounter transaction | CombatStarted |
| Turn → ACTION_PROCESSING | CombatEncounter | reserveAction | encounter + operation transaction | ActionReserved |
| ACTION_PROCESSING → Turn | CombatEncounter | commitAction | encounter + event transaction | ActionResolved |
| ACTION_PROCESSING → REACTION_PENDING | CombatEncounter | requestReaction | encounter + event transaction | ReactionRequested |
| REACTION_PENDING → ACTION_PROCESSING | CombatEncounter | resolveReaction | encounter + event transaction | ReactionResolved |
| Turn → next Turn/Round | CombatEncounter | endCurrentTurn | encounter + event transaction | TurnEnded/RoundStarted |
| active → ENDED | Lifecycle Service + CombatEncounter | endFromCommittedGmTurn/end | encounter + Adventure transaction | CombatEnded |

## 4.10 Dependency Rules

Allowed: api → application → domain; application → ports; infrastructure → ports/domain mapping; web-ui combat feature → Combat API DTO.

Forbidden:

- domain → Spring/HTTP/JDBC/AI SDK
- CombatController → external clients directly
- AI Game Master → repositories or Character/Map mutation APIs
- web-ui → authoritative rule calculation
- CombatEncounter → foreign context persistence model

---

# 5. Technical Architecture

## 5.1 Boundary Mapping

| Bounded Context | Capability | Code Boundary | Deployment Unit | Rationale |
|---|---|---|---|---|
| Adventure Runtime | combat domain | `domain/combat` package | adventure-service / app-all | Aggregate isolation sufficient |
| Adventure Runtime | combat application | `application/combat` package | adventure-service / app-all | existing ports and saga evolution |
| Adventure Runtime | combat API/SSE | `api/CombatController` | adventure-service / app-all | dedicated API without service split |
| AI Game Master | combat plans | internal typed endpoint/adapter | ai-game-master-service | existing provider boundary |
| Combat Map | mapped spatial state | existing APIs | combat-map-service | existing data lifecycle |
| web-ui | Combat UI | `features/combat` | web-ui | dedicated screen/state package |

## 5.2 Boundary Promotion Decisions

| Candidate | Chosen | Why Not Weaker? | Why Not Stronger? | Cost |
|---|---|---|---|---|
| CombatEncounter | Aggregate | methods on Adventure cannot isolate versioned lifecycle | BC/service lacks independent lifecycle/deploy need | repository/schema |
| combat application | package | current monolithic service lacks seams | module/service gives no concrete isolation benefit | more classes/contracts |
| auto-progression | in-process durable worker | request-only execution cannot resume | broker/new service unnecessary | polling/claim table |
| Combat UI | feature package | generic stream cannot express dedicated layout/state | separate app unnecessary | frontend state/API layer |

## 5.3 System Interaction Flow

Browser REST command → CombatController → local validation/reservation → persisted operation/work item → in-process worker → AI/Dice/Character/Map adapters → local finalization → player-safe CombatEvent → SSE. Cursor gap/reconnect는 REST snapshot으로 복구한다.

## 5.4 Synchronous Communication

| Caller | Provider | Protocol | Operation | Timeout |
|---|---|---|---|---|
| web-ui | adventure-service | HTTPS REST/SSE | snapshot, commands, events | REST 15s; SSE reconnect |
| adventure-service | AI GM | internal HTTP | typed proposals/plans | configurable 30s |
| adventure-service | Dice Roll | internal HTTP | roll command/read | configurable 5s |
| adventure-service | Character Management | internal HTTP | snapshot/mutation | configurable 5s |
| adventure-service | Combat Map | internal HTTP | view/move/action | configurable 5s |

## 5.5 API Contracts

| Method / Path | Purpose | Success |
|---|---|---|
| `GET /api/v1/adventures/{adventureId}/combat` | active snapshot | 200 |
| `GET /api/v1/adventures/{adventureId}/combat/events?afterSequence=N` | player-safe SSE | 200 stream |
| `POST /api/v1/adventures/{adventureId}/combat/actions` | Movement/Structured/FreeForm | 202 or duplicate 200 |
| `POST /api/v1/adventures/{adventureId}/combat/turn/end` | Human Turn 종료 | 202 |
| `POST /api/v1/adventures/{adventureId}/combat/reactions/{reactionId}` | use/pass | 202 |
| `POST /api/v1/adventures/{adventureId}/combat/retry` | failed operation 재개 | 202 |

모든 POST는 Bearer auth, `Idempotency-Key`, `If-Match-Version`을 요구한다. 응답은 encounterId, operationId, encounterVersion, status, structured violations를 포함한다.

Snapshot 최소 계약:

```json
{
  "encounterId": "uuid",
  "status": "PLAYER_TURN",
  "version": 12,
  "eventSequence": 48,
  "round": 2,
  "currentParticipantId": "uuid",
  "initiative": [],
  "playerResources": {},
  "participants": [],
  "reaction": null,
  "battlefield": { "mode": "MAP" },
  "processingFailure": null
}
```

| Condition | Status / Code |
|---|---|
| active encounter 없음 | 404 / `COMBAT_NOT_ACTIVE` |
| owner 아님 | 403 / `OWNERSHIP_DENIED` |
| stale version | 409 / `COMBAT_VERSION_CONFLICT` |
| command fingerprint 충돌 | 409 / `COMBAT_IDEMPOTENCY_CONFLICT` |
| actor/state 불일치 | 422 / `COMBAT_STATE_REJECTED` |
| 규칙상 불가능 | 422 / `ACTION_NOT_ALLOWED` |

SSE id는 event sequence다. `Last-Event-ID` 또는 afterSequence를 사용한다. cursor gap이면 `SNAPSHOT_REQUIRED` 후 stream을 닫는다.

## 5.6 Asynchronous Communication

| Producer | Consumer | Channel | Delivery | Ordering |
|---|---|---|---|---|
| local combat transaction | CombatAutoProgressionWorker | PostgreSQL work item | at-least-once claim | encounter + operation |
| CombatEventRepository | browser | SSE | at-least-once reconnect | event sequence |

외부 broker 없음. duplicate event는 sequence로 무시한다.

## 5.7 Message Contracts

Combat work item: workItemId, encounterId, operationId, expectedEncounterVersion, workType, dueAt, attemptCount. DB lease로 at-least-once 전달하며 commandId로 중복 제거한다. 초기 실행과 1초/2초 후 재시도까지 총 3회다.

## 5.8 Data Ownership

| Data | Owner | Storage | Writers |
|---|---|---|---|
| Encounter/participants/resources | Adventure Runtime | adventure DB | combat application |
| CombatEvent/audit | Adventure Runtime | adventure DB | local commit |
| operation/work | Adventure Runtime | adventure DB | action service/worker |
| HP/inventory/effects | Character Management | character DB | Character Management |
| DiceRoll | Dice Roll | dice DB | Dice Roll |
| map/token/location | Combat Map | map DB | Combat Map |

## 5.9 Schema Changes

Adventure migration: `V60__combat_encounter_runtime.sql`.

| Table | Action | Key constraints |
|---|---|---|
| `combat_encounter` | Add | PK; partial unique active adventure; version/status/round/turn |
| `combat_participant` | Add | encounter + participant; unique initiative position |
| `combat_narrative_position` | Add | encounter + subject + target |
| `combat_action_operation` | Add/evolve | unique commandId/fingerprint/status/attempts |
| `combat_action_step` | Add | operation + execution order; idempotency key |
| `combat_event` | Add | encounter + sequence; internal JSONB + visibility |
| `combat_work_item` | Add | lease/due/status |

Migration은 additive다. 기존 `/dice-rolls` route는 전환 기간 adapter/deprecation 후 제거한다.

## 5.10 Consistency Model

| Operation | Consistency | Source | Recovery |
|---|---|---|---|
| lifecycle/order/resources | strong local | CombatEncounter | version conflict |
| external effects | Saga | owning services | idempotent resume, no reroll |
| UI projection | eventual after commit | snapshot + events | SSE replay/snapshot |
| mapless position | strong local | CombatEncounter | snapshot |
| mapped position | service-local strong | Combat Map | conflict/replan |

## 5.11 Infrastructure Dependencies

PostgreSQL은 encounter/event/operation/work 정본, Spring MVC SSE는 player stream, Java HTTP adapter는 internal calls, Game System Definition adapter는 rule input을 제공한다.

## 5.12 External Dependency Isolation

| Dependency | Port | Adapter | Internal Model |
|---|---|---|---|
| AI GM | AiCombatDecisionPort | HttpAiCombatDecisionAdapter | typed proposal |
| Dice | DiceCombatPort | target HTTP adapter | RollResult |
| Character | CharacterCombatPort | target HTTP adapter | CombatCharacterSnapshot |
| Map | CombatMapPort | target HTTP adapter | CombatSpatialSnapshot |
| Game System Definition | CombatRuleDefinitionPort | rule binding adapter | ResolvedGameSystemRules |

## 5.13 File and Module Structure

Target:

```text
adventure-service/
  domain/combat/{CombatEncounter,CombatParticipant,TurnResources,ReactionInterrupt,NarrativeCombatPosition,CombatEvent,CombatRulesEngine}.java
  application/combat/{CombatLifecycle,CombatAction,CombatReaction,CombatAutoProgression,CombatProjection}*.java
  api/CombatController.java
  infrastructure/persistence/PostgresCombat*.java
  infrastructure/integration/Http*Combat*.java
web-ui/src/features/combat/{CombatScreen,CombatApi,useCombatSession}.tsx|ts
```

| Path | Action | Responsibility |
|---|---|---|
| `src/adventure-service/.../domain/combat/**` | Add | Aggregate/domain/rules |
| `src/adventure-service/.../application/combat/**` | Evolve | lifecycle/action/reaction/worker |
| `src/adventure-service/.../api/CombatController.java` | Add | REST/SSE |
| `src/adventure-service/.../api/AdventureController.java` | Modify | legacy delegation/deprecation |
| `src/adventure-service/.../db/migration/V60__combat_encounter_runtime.sql` | Add | schema |
| `src/contracts/adventure/openapi.yaml` | Modify | contracts |
| `src/web-ui/src/features/combat/**` | Add | dedicated UI |
| `src/web-ui/src/app/AppShell.tsx` | Modify | Combat Mode switch |
| `src/web-ui/src/features/combat-map/**` | Modify | reusable battlefield |
| `src/*/test/**` | Add/Modify | verification |

---

# 6. Runtime Design

## 6.1 Runtime Flow

Duplicate command는 fingerprint 확인 후 기존 결과를 반환한다. 새 command는 encounter lock/version 검증 → rule validation → local reservation → durable operation → ordered external steps → local final commit → player event 순서다. AI Turn은 다음 decision boundary까지만 진행한다.

## 6.2 Concurrent Access

| Resource | Actors | Conflict |
|---|---|---|
| CombatEncounter | Human, AI worker, retry | lost update/order |
| CombatActionOperation | duplicate request/workers | duplicate execution |
| CharacterSheet/CombatMap | combat and other runtime | stale version |
| SSE cursor | reconnect/tabs | duplicate event |

## 6.3 Concurrency Control

| Target | Control | Strategy |
|---|---|---|
| CombatEncounter | encounterId | row lock + expected version |
| active encounter | adventureId | partial unique constraint |
| operation | commandId | unique fingerprint + status CAS |
| work | lease | `FOR UPDATE SKIP LOCKED` + expiry |
| external state | aggregate ID | expected version + idempotency |

## 6.4 Ordering

Encounter state는 version, CombatEvent는 sequence, external steps는 executionOrder, AI progression은 단일 active lease로 보장한다.

## 6.5 Transaction Boundaries

| Transaction | Operations | Commit |
|---|---|---|
| combat start/end | Adventure + Encounter + Event | all local state valid |
| reservation | Encounter + Operation + Work | external call 전 |
| finalization | consume reservation + snapshot + Event + next Work | all required steps DONE |
| reaction | pending/decision + Event + resumed Work | eligible decision |

## 6.6 Idempotency

Public command는 Idempotency-Key, 외부 step은 operationId+step, AI work는 workItemId/commandId, SSE는 encounterId+sequence를 사용한다.

## 6.7 Partial Failure

| Situation | State | Recovery |
|---|---|---|
| Dice success 후 실패 | roll step DONE | 같은 roll 재사용 |
| external success 후 response lost | step 미확정 | commandId 조회 |
| local commit 실패 | steps DONE, reservation 유지 | local finalization 재실행 |
| retries exhausted | PROCESSING_FAILED | manual retry same command |
| browser disconnect | server unaffected | snapshot + cursor |

---

# 7. Error Handling and Recovery

## 7.1 Failure and Recovery Flow

Validation/domain conflict는 즉시 structured rejection. transient failure는 1초, 2초 backoff로 자동 재시도한다. 총 3회 실패 시 `PROCESSING_FAILED`. Turn skip, resource consume, reroll 금지.

## 7.2 Error Classification

| Error | Category | Retryable | Result |
|---|---|---:|---|
| malformed/ownership | validation/security | No | 400/401/403 |
| stale/current actor | conflict/domain | No | 409/422 |
| impossible action | domain | No | violations, no consume |
| AI invalid/timeout | integration | Yes | retry/failed |
| HTTP 5xx/lost response | infrastructure | Yes | retry/failed |
| foreign version conflict | conflict | conditional | Human reload; AI replan |
| projection leak | security defect | No | block response + alert |

## 7.3 Retry Policy

AI, Dice, Character, Map transient failure: 총 3회, backoff 1초/2초. DB transient error는 bounded jitter retry. Exhausted 결과는 PROCESSING_FAILED.

## 7.4 Compensation

Dice 결과 삭제/재굴림 없음. 이미 적용된 Character/Map command를 임의 반전하지 않는다. 남은 step과 local finalization을 재개한다. 취소 기능은 별도 Product 결정 필요.

## 7.5 Recovery

PROCESSING_FAILED는 같은 operation/command/reservation으로 재개한다. expired lease는 다른 worker가 claim한다. service restart 시 due/COMMITTING 작업을 scan한다. SSE gap은 snapshot으로 복구한다.

## 7.6 Rollback

V60은 additive. feature flag는 새 encounter 생성을 막을 수 있으나 active V60 encounter를 legacy CombatOperation으로 downgrade하지 않는다.

---

# 8. Security

## 8.1 Authentication and Authorization

Public Combat REST/SSE는 기존 Bearer/JWT와 Adventure owner 검사를 사용한다. action/end/reaction은 current actor guard도 적용한다. 내부 AI/Dice/Character/Map call은 `X-Internal-Token`과 scoped IDs를 사용한다.

## 8.2 Input Validation

| Input | Validation | Limit |
|---|---|---|
| free-form | nonblank UTF-8, control chars reject | 4 KiB |
| IDs | UUID, participant membership/current actor | fixed |
| movement | map/version ownership 또는 narrative schema | 256 cells/edges |
| Reaction | pending ID + allowlisted choice | one |
| headers | UUID commandId, non-negative version | required |

## 8.3 Sensitive Data

Enemy exact HP/AC/private abilities, hidden map layers, AI prompts, internal evidence와 raw rule context는 player snapshot/event/log/metric label에 포함하지 않는다. raw internal Event를 public DTO로 재사용하지 않는다.

## 8.4 Secrets

새 secret 없음. 기존 `INTERNAL_SERVICE_TOKEN`을 사용하며 Event/log에 기록하지 않는다.

---

# 9. Observability

## 9.1 Logs

Lifecycle, operation, retry, worker lease, version conflict를 encounterId/operationId/commandId/version과 기록한다. player text, hidden stats, token은 기록하지 않는다.

## 9.2 Metrics

`combat_encounter_active`, `combat_action_total`, `combat_action_duration_seconds`, `combat_auto_retry_total`, `combat_processing_failed_total`, `combat_reaction_pending_seconds`, `combat_sse_connections`를 제공한다. high-cardinality ID는 label 금지.

## 9.3 Tracing

Root `combat.command`; child validate/reserve/ai.plan/dice.roll/character.mutate/map.command/finalize/sse.publish. encounterId, operationId, version, dependency, retryAttempt만 attribute로 허용한다.

## 9.4 Alerts

processing failure rate, 장기 FAILED encounter, work backlog age, projection policy violation, version conflict spike를 alert한다. Projection leak은 Critical.

---

# 10. Change Boundaries

## 10.1 Allowed Changes

`adventure-service` combat domain/application/api/persistence, existing typed ports, additive V60, `web-ui/features/combat`, AppShell transition, OpenAPI와 tests.

## 10.2 Forbidden Changes

- 새 Combat BC/Gradle module/deployment service
- foreign authoritative data 복제
- AI/frontend의 authoritative rule mutation
- distributed transaction/2PC
- Human Turn/Reaction timeout 또는 auto skip
- hidden enemy data 노출
- full event sourcing
- 종료 후 detailed player replay

## 10.3 Conditional Changes

| Target | Condition | Decision |
|---|---|---|
| external broker | DB worker가 SLO 불충족 | 별도 ADR |
| service extraction | independent deployment/data lifecycle 증거 | promotion review |
| cancellation/compensation | Product UX 정의 | Product/Architecture update |
| post-combat replay | Product scope 변경 | Product revision |

---

# 11. Verification Requirements

## 11.1 Domain Verification

Active uniqueness, Initiative/Round/Turn order, explicit Human end, reservation consume-once, Reaction exact resume, mapless persistence, end guard를 unit/property/integration test한다.

## 11.2 Program Verification

Controller thinness, AI proposal validation, auto-progression stop boundaries, pure Rules Engine dependency, legacy route delegation을 검증한다.

## 11.3 Technical Contract Verification

| Contract | Level |
|---|---|
| REST/OpenAPI headers/status/schema | contract |
| SSE order/duplicate/reconnect/gap | integration/UI |
| external idempotency/lost response | cross-context |
| V60 constraints/locks/migration | PostgreSQL |
| hidden projection | negative security contract |

## 11.4 Runtime Verification

Duplicate same command는 one execution/same result, fingerprint mismatch는 409, stale version은 no mutation, worker crash after dice는 same roll, AI Reaction은 exact pause/resume, session 재진입은 round/turn/resources 복원을 기대한다.

## 11.5 Recovery Verification

Transient adapter를 주입해 1초/2초 retry, 3회 실패 PROCESSING_FAILED, manual retry continuation, SSE reconnect, external success 뒤 local commit failure를 검증한다.

## 11.6 Agent Verifier Criteria

- [ ] capability와 BC 구분
- [ ] Aggregate/state/rule ownership 준수
- [ ] foreign data 복제 없음
- [ ] API→application→domain dependency
- [ ] REST/SSE/schema 계약
- [ ] new service/module/broker 없음
- [ ] version/idempotency/Saga/retry 준수
- [ ] reroll/turn skip/hidden leak 없음

Human Review: Combat UI 정보 밀도, Reaction 접근성, hidden-info 시각 검토.

---

# 12. Alternatives and Trade-offs

| Decision | Option | Result | 이유 |
|---|---|---|---|
| boundary | 새 service/BC | Reject | 독립 lifecycle/deploy 증거 없음 |
| Aggregate | Adventure fields | Reject | version/state/recovery 경계 약함 |
| updates | polling only | Reject | AI/Reaction latency |
| updates | WebSocket | Reject | 양방향 연결 불필요 |
| persistence | full event sourcing | Reject | 복잡도 불필요 |
| async | external broker | Defer | DB worker가 weakest sufficient |
| mapless | prose only | Reject | 재현 가능한 판정 불가 |
| failure | turn skip | Reject | 일관성 훼손 |

---

# 13. Risks and Open Questions

## 13.1 Risks

| Risk | Impact | Probability | Mitigation |
|---|---|---|---|
| legacy/new combat write 중복 | High | High | adapter/deprecation, one write path |
| Character/Map partial success | High | Medium | command lookup/idempotent resume |
| SSE/snapshot race | Medium | Medium | sequence/version reconciliation |
| AI invalid plan | Medium | Medium | schema + Rules Engine |
| hidden data leak | High | Medium | projection policy + negative tests |
| auto-progression runaway | High | Low | decision stop, lease, max steps |
| Event retention growth | Medium | Medium | indexes, payload bounds, retention review |

## 13.2 Open Questions

모든 blocking decision 해결됨:

- Combat은 Adventure Runtime 내부 capability.
- AI Turn은 durable auto-progression.
- Reaction은 Engine Trigger와 persisted interrupt.
- REST + SSE + snapshot recovery.
- snapshot + append-only Event.
- reservation + idempotent Saga.
- 전용 CombatController.
- GM Turn과 start/end atomic commit.
- structured mapless position.
- 초기 실행 + 자동 재시도 2회.
- internal Event 유지, detailed player replay 없음.

Blocking open question 없음.
