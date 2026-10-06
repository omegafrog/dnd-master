package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.application.combat.CombatNarrationRequest;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.domain.adventure.ActiveSourceContext;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import com.dndmaster.adventure.domain.adventure.RuntimeBinding;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioModel;
import com.dndmaster.adventure.domain.runtime.RuntimeAddedFact;
import com.dndmaster.adventure.domain.runtime.CompletionProposal;
import com.dndmaster.adventure.domain.runtime.PendingRuntimeState;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeContext;
import com.dndmaster.adventure.domain.runtime.narrative.NarrativeState;
import com.dndmaster.adventure.domain.runtime.narrative.RecentEvent;
import com.dndmaster.adventure.domain.runtime.narrative.StateDelta;
import org.springframework.transaction.annotation.Transactional;

// 근거 수집 -> 계획 -> 안전 검사 -> 세션 저장 순서로 런타임 턴을 처리한다.
public class RuntimeTurnApplicationService {
    private static final String SESSION_OPENING_ACTION = "SESSION_OPENING";
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(RuntimeTurnApplicationService.class);
    private static final RuntimeTurnFailureClassifier FAILURE_CLASSIFIER = new RuntimeTurnFailureClassifier();
    private final AdventureRepository adventureRepository;
    private final RuntimeBindingRepository bindingRepository;
    private final ScenarioPackageRepository scenarioPackageRepository;
    private final RuntimeTurnRepository runtimeTurnRepository;
    private final RuntimeEvidenceSearchPort evidenceSearchPort;
    private final RuntimeEvidenceSelector evidenceSelector;
    private final RuntimePlanningPort planningPort;
    private final NarrationSafetyPort narrationSafetyPort;
    private final SessionKnowledgeSetRepository sessionKnowledgeSetRepository;
    private final GmProviderBindingRepository providerBindingRepository;
    private RuntimeTurnLockService turnLockService;
    private ApprovedPromptConfigurationReadPort approvedPromptConfigurationReadPort;
    private final ApprovedPromptSelectionPolicy approvedPromptSelectionPolicy = new ApprovedPromptSelectionPolicy();
    private TurnWriterPort writerPort;
    private RuntimeTurnFailurePersistence failurePersistence;
    private NarrativeVerifierPort narrativeVerifier;
    private ExemplarRetrieverPort exemplarRetriever;
    private ExemplarRetrievalAuditPort exemplarRetrievalAuditPort;
    private RewritePort rewritePort;
    private NarrativeVerificationAuditPort verificationAuditPort;
    private RuntimeNarrativeStateApplicationService narrativeStateService;
    private RuntimeTurnCommitOrchestrator commitOrchestrator;
    private AdventureCompletionCommitPort adventureCompletionCommitPort;
    private RuntimeTurnCommitGate commitGate = RuntimeTurnCommitGate.none();
    private RuntimeFactLookupService runtimeFactLookupService;
    private RuntimePlayerActionEvidenceAcquirer playerActionEvidenceAcquirer;
    private RuntimeCharacterSheetReadPort characterSheetReadPort;
    private ConversationCompactionJobRepository conversationCompactionJobRepository;
    private com.dndmaster.adventure.application.combat.EnemyCharacterSheetRepository enemyCharacterSheetRepository;
    private final ResolutionPort resolutionPort = new DefaultResolutionPort();
    private final NarrativeVerificationPolicy verificationPolicy = new NarrativeVerificationPolicy();

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository,
            RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository,
            RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort,
            RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort,
            SessionKnowledgeSetRepository sessionKnowledgeSetRepository) {
        this(adventureRepository, bindingRepository, scenarioPackageRepository, runtimeTurnRepository, evidenceSearchPort,
                planningPort, narrationSafetyPort, sessionKnowledgeSetRepository, null, new ScenarioRuntimeWriterAdapter(),
                new DefaultNarrativeVerifier(null), context -> { throw new IllegalStateException("narrative rewrite port is not configured"); },
                audit -> { }, query -> List.of(), audit -> { }, null);
    }

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository, RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort, RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            GmProviderBindingRepository providerBindingRepository) {
        this(adventureRepository, bindingRepository, scenarioPackageRepository, runtimeTurnRepository, evidenceSearchPort,
                planningPort, narrationSafetyPort, sessionKnowledgeSetRepository, providerBindingRepository, new ScenarioRuntimeWriterAdapter(),
                new DefaultNarrativeVerifier(null), context -> { throw new IllegalStateException("narrative rewrite port is not configured"); },
                audit -> { }, query -> List.of(), audit -> { }, null);
    }

    public RuntimeTurnApplicationService(
            AdventureRepository adventureRepository, RuntimeBindingRepository bindingRepository,
            ScenarioPackageRepository scenarioPackageRepository, RuntimeTurnRepository runtimeTurnRepository,
            RuntimeEvidenceSearchPort evidenceSearchPort, RuntimePlanningPort planningPort,
            NarrationSafetyPort narrationSafetyPort, SessionKnowledgeSetRepository sessionKnowledgeSetRepository,
            GmProviderBindingRepository providerBindingRepository, TurnWriterPort writerPort,
            NarrativeVerifierPort narrativeVerifier, RewritePort rewritePort,
            NarrativeVerificationAuditPort verificationAuditPort, ExemplarRetrieverPort exemplarRetriever,
            ExemplarRetrievalAuditPort exemplarRetrievalAuditPort, RuntimeNarrativeStateApplicationService narrativeStateService) {
        this.adventureRepository = Objects.requireNonNull(adventureRepository, "adventure repository must not be null");
        this.bindingRepository = Objects.requireNonNull(bindingRepository, "binding repository must not be null");
        this.scenarioPackageRepository = Objects.requireNonNull(scenarioPackageRepository, "scenario package repository must not be null");
        this.runtimeTurnRepository = Objects.requireNonNull(runtimeTurnRepository, "runtime turn repository must not be null");
        this.evidenceSearchPort = Objects.requireNonNull(evidenceSearchPort, "evidence search port must not be null");
        this.evidenceSelector = new RuntimeEvidenceSelector(this.evidenceSearchPort);
        this.planningPort = Objects.requireNonNull(planningPort, "planning port must not be null");
        this.narrationSafetyPort = Objects.requireNonNull(narrationSafetyPort, "narration safety port must not be null");
        this.sessionKnowledgeSetRepository = Objects.requireNonNull(
                sessionKnowledgeSetRepository, "session knowledge set repository must not be null");
        this.providerBindingRepository = providerBindingRepository;
        this.writerPort = Objects.requireNonNull(writerPort, "writer port must not be null");
        this.failurePersistence = new RuntimeTurnFailurePersistence(runtimeTurnRepository);
        this.narrativeVerifier = new DefaultNarrativeVerifier(null);
        this.rewritePort = context -> { throw new IllegalStateException("narrative rewrite port is not configured"); };
        this.verificationAuditPort = audit -> { };
        this.exemplarRetriever = query -> List.of();
        this.exemplarRetrievalAuditPort = audit -> { };
        this.narrativeStateService = null;
    }

    /** Persists the first GM message through the same runtime pipeline as every later turn. */
    public RuntimeTurnResult openSessionTurn(AdventureId adventureId, OwnerPlayerId ownerPlayerId, UUID requestId) {
        Objects.requireNonNull(requestId, "opening request id must not be null");
        LOGGER.info("session_opening_turn_started adventureId={} requestId={}", adventureId.value(), requestId);
        RuntimeTurn existing = runtimeTurnRepository.findByCommandId(requestId).orElse(null);
        if (existing != null && existing.lifecycle() == RuntimeTurnLifecycle.PRESENTATION_FAILED_RETRYABLE) {
            return retryPresentation(requestId);
        }
        UUID turnId = UUID.nameUUIDFromBytes(("session-turn-1:" + adventureId.value())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return submitTurn(new SubmitRuntimeTurnCommand(adventureId, ownerPlayerId, turnId, requestId,
                SESSION_OPENING_ACTION, -1, null, -1, true, true, false, List.of()));
    }


    public void setEnemyCharacterSheetRepository(
            com.dndmaster.adventure.application.combat.EnemyCharacterSheetRepository repository) {
        this.enemyCharacterSheetRepository = Objects.requireNonNull(repository);
    }

    public void setFailurePersistence(RuntimeTurnFailurePersistence failurePersistence) {
        this.failurePersistence = Objects.requireNonNull(failurePersistence, "failure persistence must not be null");
    }

    public void setAdventureCompletionCommitPort(AdventureCompletionCommitPort port) {
        this.adventureCompletionCommitPort = Objects.requireNonNull(port, "adventure completion commit port must not be null");
    }

    public void setTurnLockService(RuntimeTurnLockService service) { this.turnLockService = service; }

    /** Optional cross-context adapter; when present, only approved active role configurations are captured. */
    public void setApprovedPromptConfigurationReadPort(ApprovedPromptConfigurationReadPort port) {
        this.approvedPromptConfigurationReadPort = Objects.requireNonNull(port, "prompt configuration port must not be null");
    }

    public void setNarrativeVerifier(NarrativeVerifierPort narrativeVerifier) {
        this.narrativeVerifier = Objects.requireNonNull(narrativeVerifier, "narrative verifier must not be null");
    }

    public void setRewritePort(RewritePort rewritePort) {
        this.rewritePort = Objects.requireNonNull(rewritePort, "rewrite port must not be null");
    }

    public void setVerificationAuditPort(NarrativeVerificationAuditPort verificationAuditPort) {
        this.verificationAuditPort = Objects.requireNonNull(verificationAuditPort, "verification audit port must not be null");
    }

    public void setExemplarRetriever(ExemplarRetrieverPort exemplarRetriever) {
        this.exemplarRetriever = Objects.requireNonNull(exemplarRetriever, "exemplar retriever must not be null");
    }

    public void setExemplarRetrievalAuditPort(ExemplarRetrievalAuditPort auditPort) {
        this.exemplarRetrievalAuditPort = Objects.requireNonNull(auditPort, "exemplar audit port must not be null");
    }

    /** Configures the canonical narrative state used by Scenario Model turns. */
    public void setNarrativeStateService(RuntimeNarrativeStateApplicationService narrativeStateService) {
        this.narrativeStateService = Objects.requireNonNull(narrativeStateService, "narrative state service must not be null");
    }

    /** Enables the Scenario Runtime command saga for durable turn commits. */
    public void setCommitOrchestrator(RuntimeTurnCommitOrchestrator commitOrchestrator) {
        this.commitOrchestrator = Objects.requireNonNull(commitOrchestrator, "commit orchestrator must not be null");
    }

    /** Runs required cross-service checks before the local adventure state is saved. */
    public void setCommitGate(RuntimeTurnCommitGate commitGate) {
        this.commitGate = Objects.requireNonNull(commitGate, "runtime turn commit gate must not be null");
    }

    /** Enables the server-owned Game State → Runtime Fact → Scenario Model lookup before GM generation. */
    public void setRuntimeFactLookupService(RuntimeFactLookupService runtimeFactLookupService) {
        this.runtimeFactLookupService = Objects.requireNonNull(runtimeFactLookupService,
                "runtime fact lookup service must not be null");
    }

    public void setPlayerActionEvidenceAcquirer(RuntimePlayerActionEvidenceAcquirer playerActionEvidenceAcquirer) {
        this.playerActionEvidenceAcquirer = Objects.requireNonNull(playerActionEvidenceAcquirer,
                "player action evidence acquirer must not be null");
    }

    public void setCharacterSheetReadPort(RuntimeCharacterSheetReadPort characterSheetReadPort) {
        this.characterSheetReadPort = Objects.requireNonNull(characterSheetReadPort);
    }

    /** Adds only durable metadata after a confirmed turn; it never waits for provider work. */
    public void setConversationCompactionJobRepository(ConversationCompactionJobRepository repository) {
        this.conversationCompactionJobRepository = Objects.requireNonNull(repository);
    }

    /** Returns the saved map movement outcome for duplicate or resumed runtime commands. */
    public com.dndmaster.adventure.application.combat.CombatMapMoveResult movementResultForTurn(UUID turnId) {
        return commitOrchestrator == null ? null : commitOrchestrator.movementResultForTurn(turnId);
    }

    /** Client/recovery entry point sharing the same forward-resume orchestrator. */
    public RuntimeTurnCommitOrchestrator.Result resumeRuntimeTurn(UUID turnId) {
        return resumeRuntimeTurn(turnId, null);
    }

    /** HTTP resume entry point also binds the request key to the saved turn command. */
    public RuntimeTurnCommitOrchestrator.Result resumeRuntimeTurn(UUID turnId, UUID idempotencyKey) {
        if (commitOrchestrator == null) throw new IllegalStateException("runtime turn commit orchestrator is not configured");
        RuntimeTurn turn = runtimeTurnRepository.findByTurnId(Objects.requireNonNull(turnId, "turn id must not be null"))
                .orElseThrow(() -> new IllegalStateException("runtime turn not found"));
        if (idempotencyKey != null && !turn.commandId().equals(idempotencyKey)) {
            throw new IllegalArgumentException("idempotency key does not match runtime turn command");
        }
        return resumeRuntimeTurn(turn);
    }

    private RuntimeTurnCommitOrchestrator.Result resumeRuntimeTurn(RuntimeTurn turn) {
        Adventure adventure = adventureRepository.findById(turn.adventureId())
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        if (turn.pendingState() == null || turn.completionProposal() == null) {
            throw new IllegalStateException("runtime turn has no pending local commit state");
        }
        AdventureContext committedContext = committedContext(turn);
        List<ConversationEntry> committedConversation = committedConversation(turn);
        if (turn.lifecycle() == RuntimeTurnLifecycle.COMMITTED
                && adventure.version() == turn.version() + 1
                && adventure.currentContext().equals(turn.context())
                && adventure.conversation().equals(turn.conversation())
                && adventure.currentSituation().equals(turn.pendingState().situation())) {
            // Older recovery code advanced the aggregate version with the pre-turn
            // context and conversation. The runtime artifact still contains the
            // resolved response, so repair only those presentation fields without
            // applying the already-committed game-state delta a second time.
            adventure.preserveProgress(adventure.ownerPlayerId(), adventure.version(), committedContext,
                    committedConversation);
            saveConfirmedAdventureAndRegister(adventure);
        }
        return commitOrchestrator.resume(turn.turnId(), () -> {
            if (adventure.version() == turn.version()) {
                adventure.commitRuntimeTurn(adventure.ownerPlayerId(), turn.version(), turn.pendingState(), committedContext,
                        committedConversation, turn.completionProposal());
                commitGate.beforeCommit(adventure, turn);
                saveAdventureTurn(adventure, turn.completionProposal());
                return;
            }
            if (adventure.version() == turn.version() + 1
                    && adventure.currentContext().equals(committedContext)
                    && adventure.conversation().equals(committedConversation)) return;
            throw new IllegalStateException("adventure local commit is stale or has unknown outcome");
        });
    }

    private static AdventureContext committedContext(RuntimeTurn turn) {
        return new AdventureContext(turn.plan().scene(), turn.plan().npcState(), turn.action(), turn.plan().judgment());
    }

    private static List<ConversationEntry> committedConversation(RuntimeTurn turn) {
        List<ConversationEntry> conversation = new ArrayList<>(turn.conversation());
        if (!turn.gmOnly()) conversation.add(new ConversationEntry(conversation.size(), "PLAYER", turn.action()));
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", turn.narration()));
        if (!turn.gmOnly() && turn.plan().judgment() != null && !turn.plan().judgment().isBlank()) {
            conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", turn.plan().judgment()));
        }
        return List.copyOf(conversation);
    }

    /**
     * Orchestrates external work without holding a database transaction. Each
     * lifecycle write commits independently so failure persistence cannot block
     * behind this request's turn row lock.
     */
    public RuntimeTurnResult submitTurn(SubmitRuntimeTurnCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Adventure adventure = adventureRepository.findById(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        adventure.reopen(command.ownerPlayerId());
        RuntimeTurn existing = runtimeTurnRepository.findByCommandId(command.commandId()).orElse(null);
        if (existing != null) {
            RuntimeTurnOrigin requestedOrigin = origin(command);
            if (!existing.turnId().equals(command.turnId())
                    || !existing.adventureId().equals(command.adventureId())
                    || !existing.action().equals(command.action())
                    || existing.advancesState() != command.advancesState()
                    || existing.origin() != requestedOrigin
                    || existing.gmOnly() != command.gmOnly()
                    || existing.agentOrigin() != command.agentOrigin()
                    || !java.util.Objects.equals(existing.turnCharacterSheetId(), command.turnCharacterSheetId())
                    || !java.util.Objects.equals(existing.turnIndex(), command.turnIndex() < 0 ? null : command.turnIndex())
                    || !java.util.Objects.equals(existing.expectedVersion() == null ? -1L : existing.expectedVersion(), command.expectedVersion() < 0 ? -1L : command.expectedVersion())) {
                throw new IllegalStateException("runtime command id reused with different payload");
            }
            if (existing.lifecycle() == RuntimeTurnLifecycle.PRESENTATION_FAILED_RETRYABLE) {
                throw new IllegalStateException("runtime turn presentation is not committed; retry presentation explicitly");
            }
            if (existing.lifecycle() == RuntimeTurnLifecycle.PENDING_ROLL) {
                throw new IllegalStateException("PENDING_ROLL_GATE");
            }
            if (existing.lifecycle() == RuntimeTurnLifecycle.DISCARDED
                    || existing.lifecycle() == RuntimeTurnLifecycle.COMMIT_REPAIR_REQUIRED) {
                throw new IllegalStateException("runtime turn is terminal: " + existing.lifecycle());
            }
            if (!existing.lifecycle().isCommitted()) {
                if (existing.lifecycle() == RuntimeTurnLifecycle.COMMITTING && commitOrchestrator != null) {
                    RuntimeTurnCommitOrchestrator.Result resumed = resumeRuntimeTurn(existing.turnId());
                    Adventure recovered = adventureRepository.findById(command.adventureId())
                            .orElseThrow(() -> new IllegalStateException("adventure not found after runtime recovery"));
                    return new RuntimeTurnResult(resumed.turn(), recovered.currentContext(), recovered.conversation(),
                            recovered.version(), null, resumed.movementResult(), preparationRequest(recovered, resumed.turn()));
                }
                RuntimeTurn resumed = resumeCommittedTurn(command, adventure, existing);
                return new RuntimeTurnResult(resumed, resumed.context(), resumed.conversation(), resumed.version(), null,
                        movementResultForTurn(resumed.turnId()), preparationRequest(adventure, resumed));
            }
            return new RuntimeTurnResult(existing, existing.context(), existing.conversation(), existing.version(),
                    publicProjectionForExisting(command, adventure, existing), movementResultForTurn(existing.turnId()),
                    preparationRequest(adventure, existing));
        }
        if (command.expectedVersion() >= 0 && adventure.version() != command.expectedVersion()) {
            throw new IllegalStateException("ADVENTURE_VERSION_CONFLICT expected=" + command.expectedVersion() + " actual=" + adventure.version());
        }
        if (runtimeTurnRepository.findAllByAdventureId(command.adventureId()).stream()
                .anyMatch(turn -> turn.lifecycle() == RuntimeTurnLifecycle.PENDING_ROLL)) {
            throw new IllegalStateException("PENDING_ROLL_GATE");
        }
        // Optimistic adventure-version CAS is the concurrency boundary. Do not
        // acquire a second persistent turn lock around the long provider flow.
        RuntimeBinding binding = bindingRepository.findCurrentByAdventureId(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("runtime binding not found"));
        if (!binding.ownerPlayerId().equals(command.ownerPlayerId())) {
            throw new IllegalStateException("runtime binding owner mismatch");
        }
        ScenarioPackage scenarioPackage = scenarioPackageRepository.findById(binding.scenarioPackageId())
                .orElseThrow(() -> new IllegalStateException("scenario package not found"));

        return submitSafeScenarioRuntimeTurn(command, adventure, binding, scenarioPackage);
    }

    private <T> T stage(UUID turnId, String stage, Supplier<T> operation) {
        stageEnter(turnId, stage);
        long started = System.nanoTime();
        try {
            T result = operation.get();
            stageExit(turnId, stage, started);
            return result;
        } catch (RuntimeException failure) {
            stageExitFailure(turnId, stage, started, failure);
            throw failure;
        }
    }

    private void stageEnter(UUID turnId, String stage) {
        LOGGER.info("runtime_turn_stage_enter turnId={} stage={}", turnId, stage);
    }

    private void stageExit(UUID turnId, String stage, long started) {
        LOGGER.info("runtime_turn_stage_exit turnId={} stage={} elapsedMs={}", turnId, stage,
                started == 0 ? 0 : (System.nanoTime() - started) / 1_000_000);
    }

    private void stageExitFailure(UUID turnId, String stage, long started, RuntimeException failure) {
        LOGGER.error("runtime_turn_stage_exit turnId={} stage={} outcome=FAILED elapsedMs={} exceptionClass={} exceptionMessage={}",
                turnId, stage, started == 0 ? 0 : (System.nanoTime() - started) / 1_000_000,
                failure.getClass().getSimpleName(), safeMessage(failure), failure);
    }

    private static List<String> evidenceReferences(List<RuntimeEvidence> evidence) {
        return evidence.stream().map(item -> "type=" + item.evidenceType()
                + ",citationKey=" + item.citationKey()
                + ",referenceKey=" + item.referenceKey()
                + ",locator=" + item.locator()).toList();
    }

    private static void validateDiceTotal(String diceExpression, int result) {
        TypedCheckRule.DiceExpression dice = TypedCheckRule.DiceExpression.parse(diceExpression, 0);
        if (!dice.acceptsRollTotal(result)) {
            throw new IllegalArgumentException("dice result is outside the requested expression range");
        }
    }

    private static int rollDice(String diceExpression) {
        TypedCheckRule.DiceExpression dice = TypedCheckRule.DiceExpression.parse(diceExpression, 0);
        int total = 0;
        java.security.SecureRandom random = new java.security.SecureRandom();
        for (int index = 0; index < dice.count(); index++) {
            total = Math.addExact(total, random.nextInt(dice.sides()) + 1);
        }
        return total;
    }

    private List<ExemplarResult> retrieveExemplars(RuntimePlan plan, String action) {
        String purpose = plan.scene().equalsIgnoreCase("scene") ? "scene transition" : plan.scene();
        String interaction = action == null || action.isBlank() ? "narration" : "action";
        String tone = plan.warnings().isEmpty() ? "neutral" : "cautious";
        String pacing = false ? "escalating" : "steady";
        String desiredLength = plan.narration().length() > 240 ? "long" : plan.narration().length() < 80 ? "short" : "medium";
        ExemplarQuery query = new ExemplarQuery(purpose, interaction, tone, pacing, desiredLength,
                plan.scene() + " " + plan.judgment() + " " + action, 3);
        long started = System.nanoTime();
        try {
            List<ExemplarResult> results = exemplarRetriever.retrieve(query);
            List<ExemplarResult> bounded = results == null ? List.of() : results.stream().filter(Objects::nonNull).limit(query.limit()).toList();
            exemplarRetrievalAuditPort.append(new ExemplarRetrievalAudit(query.semanticQuery(), query.limit(),
                    bounded.stream().map(result -> result.exemplar().id()).toList(),
                    bounded.stream().map(ExemplarResult::rerankScore).toList(), plan.model(), (System.nanoTime() - started) / 1_000_000));
            return bounded;
        } catch (RuntimeException ignored) {
            exemplarRetrievalAuditPort.append(new ExemplarRetrievalAudit(query.semanticQuery(), query.limit(), List.of(), List.of(),
                    plan.model(), (System.nanoTime() - started) / 1_000_000));
            return List.of();
        }
    }

    private WriterProse writePresentationWithRetry(RuntimeTurn turn, ResolvedTurnPlan resolvedPlan,
                                                    PlayerVisibleTurn visibleTurn,
                                                    NarrativeState narrativeState, NarrativeContext narrativeContext,
                                                    EvidencePack evidencePack, List<ExemplarResult> exemplars) {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                WriterProse prose = writerPort.write(visibleTurn);
                if (writerPort instanceof ScenarioRuntimeWriterAdapter) prose = new WriterProse(turn.plan().narration());
                return verifyAndRewrite(resolvedPlan, prose, turn, narrativeState, narrativeContext, evidencePack);
            } catch (RuntimeException failure) {
                RuntimeTurnFailureArtifact artifact = FAILURE_CLASSIFIER.classify(turn.turnId(),
                        RuntimeTurnFailureStage.PRESENTATION, failure, turn.commandId(), attempt);
                if (attempt == 2 || !FAILURE_CLASSIFIER.allowsAutomaticRetry(artifact)) {
                    failurePersistence.persist(turn, artifact);
                    throw failure;
                }
            }
        }
        throw new IllegalStateException("presentation retry exhausted");
    }

    private PlayerVisibleTurn publicProjection(SubmitRuntimeTurnCommand command, Adventure adventure,
                                               ScenarioPackage scenarioPackage, NarrativeState state, RuntimePlan plan) {
        List<String> knownValues = state.project(command.ownerPlayerId().value().toString(), plan.scene())
                .worldFacts().stream().map(com.dndmaster.adventure.domain.runtime.narrative.WorldFact::value).toList();
        return new PlayerVisibleTurn(plan.narration(), plan.scene(), knownValues, deltaFor(state, command, plan),
                state.project(command.ownerPlayerId().value().toString(), plan.scene()));
    }

    private PlayerVisibleTurn publicProjectionForExisting(SubmitRuntimeTurnCommand command, Adventure adventure,
                                                          RuntimeTurn existing) {
        ScenarioPackage scenario = scenarioPackageRepository.findById(existing.scenarioPackageId()).orElse(null);
        NarrativeState state = narrativeStateService == null ? NarrativeState.empty()
                : narrativeStateService.load(existing.sessionId());
        if (scenario == null) return new PlayerVisibleTurn(existing.plan().narration(), existing.plan().scene(), List.of(), null);
        return publicProjection(command, adventure, scenario, state, existing.plan());
    }

    private ResolvedTurnPlan captureApprovedPromptLineage(ResolvedTurnPlan resolvedPlan) {
        if (approvedPromptConfigurationReadPort == null) return resolvedPlan;
        Map<String, EffectivePromptLineage> lineages = new java.util.LinkedHashMap<>();
        for (String role : List.of("PLANNER", "JUDGE", "WRITER", "VERIFIER")) {
            approvedPromptConfigurationReadPort.current(role).ifPresent(configuration -> {
                EffectivePromptLineage lineage = approvedPromptSelectionPolicy.select(approvedPromptConfigurationReadPort, role);
                lineages.put(role, lineage);
            });
        }
        return lineages.isEmpty() ? resolvedPlan : resolvedPlan.withPromptLineages(lineages);
    }

    private static RuntimePlan withNarration(RuntimePlan plan, String narration) {
        return new RuntimePlan(plan.scene(), plan.npcState(), plan.judgment(), narration,
                plan.proposedActiveSourceContext(), plan.citedEvidence(), plan.warnings(), plan.provider(), plan.model(),
                plan.reasoning(), false, plan.requestedSelectionId(), plan.requestedSelection(),
                plan.effectiveSelection(), plan.attemptCount(), plan.citationBindings(), plan.stateDelta(), plan.combatEnemies(),
                plan.combatStartRequested(), plan.mapEntryRequested());
    }

    private WriterProse verifyAndRewrite(ResolvedTurnPlan resolvedPlan, WriterProse draft, RuntimeTurn turn,
                                         NarrativeState state, NarrativeContext narrativeContext, EvidencePack evidencePack) {
        NarrativeVerificationContext context = NarrativeVerificationContext.from(resolvedPlan, state, narrativeContext, evidencePack);
        if (writerPort instanceof ScenarioRuntimeWriterAdapter) {
            // The local Scenario Runtime writer emits the already-validated planner narration.
            context = new NarrativeVerificationContext(context.turnPlanSummary(), List.of(), context.hiddenFacts(),
                    context.ruleMismatches(), context.agencyViolations(), context.npcKnowledgeViolations(),
                    context.turnPlanDeviations(), context.stateContradictions(), context.unsupportedFacts());
        }
        VerificationResult result = narrativeVerifier.verify(context, draft.prose());
        if (!verificationPolicy.requiresRewrite(result)) {
            if (!verificationPolicy.accepts(result)) throw boundedVerificationFailure(result);
            verificationAuditPort.append(new NarrativeVerificationAudit(turn.turnId().toString(),
                    verificationPolicy.fingerprint(turn.turnId().toString(), resolvedPlan.plan().scene() + "|" + resolvedPlan.plan().judgment(), resolvedPlan.outcomes()),
                    result, result, false, List.of(turn.plan().provider(), turn.plan().model(), turn.plan().reasoning())));
            return draft;
        }
        String fingerprint = verificationPolicy.fingerprint(turn.turnId().toString(),
                resolvedPlan.plan().scene() + "|" + resolvedPlan.plan().judgment(), resolvedPlan.outcomes());
        WriterProse rewritten = rewritePort.rewrite(new RewriteContext(draft.prose(), result.violations(), fingerprint, 0));
        VerificationResult rewrittenResult = narrativeVerifier.verify(context, rewritten.prose()).withRewriteCount(1);
        verificationAuditPort.append(new NarrativeVerificationAudit(turn.turnId().toString(), fingerprint,
                result, rewrittenResult, true, List.of(turn.plan().provider(), turn.plan().model(), turn.plan().reasoning())));
        if (!verificationPolicy.accepts(rewrittenResult)) throw boundedVerificationFailure(rewrittenResult);
        return rewritten;
    }

    private static IllegalStateException boundedVerificationFailure(VerificationResult result) {
        String codes = result.violations().stream().map(v -> v.type().name()).distinct().toList().toString();
        return new IllegalStateException("narrative verification failed after bounded rewrite: " + codes);
    }

    public RuntimeTurnResult submitPlayerRoll(SubmitPlayerRollCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        RuntimeTurn pending = runtimeTurnRepository.findByTurnId(command.pendingTurnId())
                .orElseThrow(() -> new IllegalStateException("pending runtime turn not found"));
        if (!pending.adventureId().equals(command.adventureId()) || pending.origin() != RuntimeTurnOrigin.PLAYER) {
            throw new IllegalStateException("pending turn does not belong to adventure");
        }
        Adventure adventure = adventureRepository.findById(command.adventureId())
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        if (!adventure.ownerPlayerId().equals(command.ownerPlayerId())) throw new IllegalStateException("pending turn owner mismatch");
        if (pending.lifecycle() != RuntimeTurnLifecycle.PENDING_ROLL) throw new IllegalStateException("pending roll is no longer open");
        if (adventure.version() != command.expectedVersion()) throw new IllegalStateException("ADVENTURE_VERSION_CONFLICT");
        RuntimeCheckProposal check = pending.plan().checkProposal();
        if (!check.required() || check.rollMethod() != RuntimeCheckProposal.RollMethod.PLAYER) {
            throw new IllegalStateException("PENDING_CHECK_ARTIFACT_MISSING");
        }
        validateDiceTotal(check.diceExpression(), command.result());
        int total = command.result() + check.modifier();
        ResolutionPort.PlayerCheckResult result = resolutionPort.resolvePlayerCheck(new ResolutionPort.PlayerCheckRequest(
                check.evidenceKeys().getFirst(), check.diceExpression(), check.modifier(), check.difficulty(), total));
        RuntimeTurn resolving = pending.resolvePlayerRoll(command.result(), total, result.success());
        runtimeTurnRepository.save(resolving);
        return retryPresentation(resolving.commandId());
    }

    @Transactional
    public RuntimeTurnResult retryPresentation(UUID commandId) {
        RuntimeTurn turn = runtimeTurnRepository.findByCommandId(commandId)
                .orElseThrow(() -> new IllegalStateException("runtime turn not found"));
        if (turn.resolvedArtifact() == null || turn.lifecycle() == RuntimeTurnLifecycle.PRESENTED) {
            return new RuntimeTurnResult(turn, turn.context(), turn.conversation(), turn.version());
        }
        NarrativeState state = narrativeStateService == null ? NarrativeState.empty()
                : narrativeStateService.load(turn.sessionId());
        Adventure adventure = adventureRepository.findById(turn.adventureId())
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        NarrativeContext narrativeContext = state.project(adventure.ownerPlayerId().value().toString(),
                turn.resolvedArtifact().plan().scene());
        List<ExemplarResult> exemplars = retrieveExemplars(turn.plan(), turn.action());
        PlayerVisibleTurn visibleTurn = new PlayerVisibleTurn(turn.plan().narration(), turn.plan().scene(),
                narrativeContext.worldFacts().stream().map(com.dndmaster.adventure.domain.runtime.narrative.WorldFact::value).toList(),
                deltaFor(state, turn), narrativeContext);
        WriterProse prose = writePresentationWithRetry(turn, turn.resolvedArtifact(), visibleTurn, state, narrativeContext,
                turn.evidencePack(), exemplars);
        try {
            NarrationSafetyAssessment safety = narrationSafetyPort.assess(new NarrationSafetyRequest(
                    prose.prose(), turn.evidencePack(), turn.context(), turn.action()));
            if (!safety.approved()) throw new IllegalStateException("narration safety rejected: " + safety.reason());
        } catch (RuntimeException failure) {
            failurePersistence.persist(turn, FAILURE_CLASSIFIER.classify(turn.turnId(),
                    RuntimeTurnFailureStage.SAFETY, failure, turn.commandId(), 1));
            throw failure;
        }
        RuntimePlan presentedPlan = withNarration(turn.plan(), prose.prose());
        List<ConversationEntry> conversation = new ArrayList<>(turn.conversation());
        if (!turn.gmOnly()) conversation.add(new ConversationEntry(conversation.size(), "PLAYER", turn.action()));
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", prose.prose()));
        if (!turn.gmOnly() && presentedPlan.judgment() != null && !presentedPlan.judgment().isBlank()) {
            conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", presentedPlan.judgment()));
        }
        RuntimeTurn presented = new RuntimeTurn(turn.turnId(), turn.commandId(), turn.adventureId(), turn.sessionId(),
                turn.scenarioPackageId(), turn.bindingVersion(), turn.action(), turn.evidencePack(), presentedPlan,
                turn.activeSourceContext(), new AdventureContext(presentedPlan.scene(), presentedPlan.npcState(), turn.action(), presentedPlan.judgment()),
                conversation, adventure.version() + 1 + (turn.turnCharacterSheetId() == null ? 0 : 1), turn.citations(), turn.warnings(), false, turn.playerOrigin(), turn.origin(),
                turn.advancesState(), turn.turnCharacterSheetId(), turn.turnIndex(), turn.expectedVersion(), turn.gmOnly(), turn.agentOrigin(),
                RuntimeTurnLifecycle.RESOLVED_UNCOMMITTED, turn.resolvedArtifact()).markCommitted();
                if (turn.plan().proposedActiveSourceContext() != null) {
            bindingRepository.findCurrentByAdventureId(turn.adventureId()).ifPresent(binding ->
                    bindingRepository.save(binding.withSelection(turn.plan().proposedActiveSourceContext(), binding.playabilityReport())));
        }
        Adventure progressed = Adventure.rehydrateWithRuntimeState(
                adventure.id(), adventure.sessionId(), adventure.ownerPlayerId(), adventure.scenarioId(), adventure.ruleSetId(), adventure.party(),
                adventure.conversation(), adventure.currentContext(), adventure.status(), adventure.version(), adventure.turnIndex(), adventure.lastTurnKey(),
                adventure.lockedScenarioPackageId(), adventure.lockedScenarioPackageRevision(), adventure.gameState(), adventure.disclosureState(),
                adventure.currentSituation(), adventure.runtimeAddedFacts(), adventure.storyRuntimeState());
        if (turn.pendingState() != null && turn.completionProposal() != null) {
            progressed.commitRuntimeTurn(adventure.ownerPlayerId(), adventure.version(), turn.pendingState(),
                    presented.context(), presented.conversation(), turn.completionProposal());
        } else {
            progressed.preserveProgress(adventure.ownerPlayerId(), adventure.version(), presented.context(), presented.conversation());
        }
        if (turn.turnCharacterSheetId() != null) {
            progressed.advanceTurn(adventure.ownerPlayerId(), turn.turnIndex(), turn.turnCharacterSheetId(), turn.turnId());
        }
        if (turn.pendingState() != null && turn.completionProposal() != null) {
            saveAdventureTurn(progressed, turn.completionProposal());
        } else {
            saveConfirmedAdventureAndRegister(progressed);
        }
        runtimeTurnRepository.save(presented);
        if (narrativeStateService != null) narrativeStateService.commit(turn.sessionId(), visibleTurn.stateDelta());
        return new RuntimeTurnResult(presented, progressed.currentContext(), progressed.conversation(), progressed.version(), visibleTurn);
    }

    /** Canonical Scenario Model runtime turn path. */
    private String currentCharacterSheet(UUID characterSheetId) {
        if (characterSheetReadPort == null) {
            throw new RuntimeCharacterSheetReadException("current character sheet reader is unavailable");
        }
        return characterSheetReadPort.read(characterSheetId);
    }

    /** Reads the latest Runtime situation and, for a party actor, that actor's sheet for a saved combat turn. */
    public CombatTurnRuntimeInputs combatTurnRuntimeInputs(AdventureId adventureId, UUID actorId) {
        Adventure adventure = adventureRepository.findById(adventureId)
                .orElseThrow(() -> new IllegalStateException("combat adventure is unavailable"));
        if (adventure.currentSituation() == null) throw new IllegalStateException("combat current situation is unavailable");
        boolean partyActor = adventure.party().stream().anyMatch(member -> member.characterSheetId().value().equals(actorId));
        String sheet = partyActor ? currentCharacterSheet(actorId) : null;
        GmProviderSelection selection = providerBindingRepository == null ? null
                : providerBindingRepository.current(adventure.sessionId().value())
                        .map(ProviderBinding::selection).orElse(null);
        return new CombatTurnRuntimeInputs(adventure.currentSituation(), sheet,
                adventure.ownerPlayerId().value(), selection, List.of());
    }

    /** Searches pinned Rulebook material only after the first combat proposal needs a missing rule. */
    public List<RuntimeEvidence> combatRuleEvidenceForTurn(AdventureId adventureId, UUID actorId, String action) {
        Adventure adventure = adventureRepository.findById(adventureId)
                .orElseThrow(() -> new IllegalStateException("combat adventure is unavailable"));
        if (adventure.party().stream().noneMatch(member -> member.characterSheetId().value().equals(actorId))) {
            throw new IllegalArgumentException("Rulebook fallback is only available for a party actor");
        }
        RuntimeBinding binding = bindingRepository.findCurrentByAdventureId(adventure.id())
                .orElseThrow(() -> new IllegalStateException("combat runtime binding is unavailable"));
        ScenarioPackage scenarioPackage = adventure.lockedScenarioPackageId() == null ? null
                : scenarioPackageRepository.findById(adventure.lockedScenarioPackageId()).orElse(null);
        if (scenarioPackage == null) throw new IllegalStateException("combat scenario package is unavailable");
        List<UUID> pinnedDocuments = knowledgeDocumentIds(adventure, scenarioPackage);
        List<UUID> rulebookDocuments = documentIdsOfType(scenarioPackage, "RULEBOOK", pinnedDocuments);
        if (rulebookDocuments.isEmpty()) return List.of();
        Map<UUID, Long> extractionVersions = scenarioPackage.documents().stream()
                .filter(document -> rulebookDocuments.contains(document.knowledgeDocumentId().value()))
                .collect(java.util.stream.Collectors.toMap(document -> document.knowledgeDocumentId().value(),
                        com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection::extractionVersion, (a, b) -> a));
        String query = "combat rule needed for " + action + "; current situation: " + adventure.currentSituation();
        RuntimeEvidenceSearchRequest request = new RuntimeEvidenceSearchRequest(adventure.id(), adventure.ownerPlayerId(),
                adventure.sessionId(), binding.scenarioPackageId(), rulebookDocuments, binding.activeSourceContext(),
                query, RuntimeEvidenceType.RULEBOOK, 8, extractionVersions, "combat", "COMBAT_ACTION");
        return scopedSearch(request).stream()
                .filter(evidence -> evidence.evidenceType() == RuntimeEvidenceType.RULEBOOK)
                .filter(evidence -> {
                    Long pinnedVersion = extractionVersions.get(evidence.knowledgeDocumentId().value());
                    return pinnedVersion == null || pinnedVersion == evidence.extractionVersion();
                })
                .limit(request.limit())
                .toList();
    }

    /** Loads the source-scoped reusable sheet for an encounter participant from the pinned adventure materials. */
    public java.util.Optional<com.dndmaster.adventure.application.combat.EnemyCharacterSheet> enemyCharacterSheetForCombat(
            AdventureId adventureId, String enemyKind) {
        if (enemyCharacterSheetRepository == null) throw new IllegalStateException("enemy character sheet repository is unavailable");
        Adventure adventure = adventureRepository.findById(adventureId)
                .orElseThrow(() -> new IllegalStateException("combat adventure is unavailable"));
        RuntimeBinding binding = bindingRepository.findCurrentByAdventureId(adventureId)
                .orElseThrow(() -> new IllegalStateException("combat runtime binding is unavailable"));
        ScenarioPackage scenarioPackage = adventure.lockedScenarioPackageId() == null ? null
                : scenarioPackageRepository.findById(adventure.lockedScenarioPackageId()).orElse(null);
        if (scenarioPackage == null) throw new IllegalStateException("combat scenario package is unavailable");
        return enemyCharacterSheetRepository.find(enemySheetIdentity(adventure, binding, scenarioPackage, enemyKind));
    }

    public record CombatTurnRuntimeInputs(com.dndmaster.adventure.domain.runtime.CurrentSituation situation,
            String characterSheetJson, UUID ownerPlayerId, GmProviderSelection providerSelection,
            List<RuntimeEvidence> ruleEvidence) {
        public CombatTurnRuntimeInputs(com.dndmaster.adventure.domain.runtime.CurrentSituation situation,
                String characterSheetJson, UUID ownerPlayerId, GmProviderSelection providerSelection) {
            this(situation, characterSheetJson, ownerPlayerId, providerSelection, List.of());
        }
        public CombatTurnRuntimeInputs {
            ruleEvidence = List.copyOf(ruleEvidence == null ? List.of() : ruleEvidence);
        }
    }

    /**
     * Reads the same locked Adventure Runtime material as a normal turn and asks
     * the GM for prose after combat has already been committed. This path never
     * stores the returned plan or any proposal contained in it.
     */
    public String narrateConfirmedCombat(CombatNarrationRequest request) {
        Objects.requireNonNull(request, "combat narration request must not be null");
        Adventure adventure = adventureRepository.findById(request.command().adventureId())
                .orElseThrow(() -> new IllegalStateException("adventure not found"));
        if (request.command().ownerPlayerId() != null
                && !adventure.ownerPlayerId().value().equals(request.command().ownerPlayerId())) {
            throw new IllegalStateException("combat narration owner mismatch");
        }
        RuntimeBinding binding = bindingRepository.findCurrentByAdventureId(adventure.id())
                .orElseThrow(() -> new IllegalStateException("runtime binding not found"));
        ScenarioPackage scenarioPackage = scenarioPackageRepository.findById(binding.scenarioPackageId())
                .orElseThrow(() -> new IllegalStateException("scenario package not found"));
        try {
            persistConfirmedCombatResult(adventure, request);
        } catch (RuntimeException exception) {
            throw new com.dndmaster.adventure.application.combat.CombatNarrationPersistenceException(exception);
        }
        SubmitRuntimeTurnCommand contextCommand = new SubmitRuntimeTurnCommand(adventure.id(), adventure.ownerPlayerId(),
                request.command().operationId(), request.command().operationId(), combatNarrationAction(request), -1,
                null, -1, false, true, false, List.of());
        List<String> characterSheets = adventure.party().stream()
                .map(member -> currentCharacterSheet(member.characterSheetId().value())).toList();
        NarrativeState narrativeState = narrativeStateService == null ? NarrativeState.empty()
                : narrativeStateService.load(adventure.sessionId().value());
        ScenarioModel narrationScenarioModel = HiddenScenarioFacts.withoutUnrevealedRevelations(
                scenarioPackage.scenarioModel(), adventure.storyRuntimeState());
        java.util.Set<ScenarioSourceReference> unrevealedSourceRefs = HiddenScenarioFacts.unrevealedRevelationSourceRefs(
                scenarioPackage.scenarioModel(), adventure.storyRuntimeState());
        EvidencePack evidencePack = withoutUnrevealedRevelationEvidence(
                prefetchEvidence(contextCommand, adventure, binding, scenarioPackage), unrevealedSourceRefs);
        List<RuntimeFactLookupResult> factLookupResults = List.of();
        String situation = adventure.currentSituation() == null ? "" : adventure.currentSituation().toString();
        NarrativeContext narrativeContext = narrativeState.project(adventure.ownerPlayerId().value().toString(), situation);
        List<String> recentTurns = new ArrayList<>(recentConversationForPrompt(adventure));
        runtimeTurnRepository.findAllByAdventureId(adventure.id()).stream()
                .filter(turn -> turn.lifecycle() == RuntimeTurnLifecycle.PENDING_ROLL)
                .forEach(turn -> recentTurns.add("PENDING_ROLL: " + turn.action()));
        List<String> hiddenFacts = new ArrayList<>(hiddenFactsForPlayer(scenarioPackage, adventure, narrativeState));
        RuntimePlanningRequest planningRequest = new RuntimePlanningRequest(adventure.id(), adventure.ownerPlayerId(),
                adventure.sessionId().value(), request.command().operationId(), binding.scenarioPackageId(), binding.bindingVersion(),
                adventure.currentContext(), binding.activeSourceContext(), contextCommand.action(), evidencePack, recentTurns,
                characterSheets, lockedScenarioContext(scenarioPackage, narrationScenarioModel), providerEndpointId(adventure.sessionId().value()),
                providerSelection(adventure.sessionId().value(), "provider"), providerSelection(adventure.sessionId().value(), "model"),
                providerSelection(adventure.sessionId().value(), "reasoning"), narrativeContext, adventure.ruleSetId().value(),
                adventure.runtimeAddedFacts().stream().map(RuntimeAddedFact::content).toList(), factLookupResults, situation,
                longTermFactsForPrompt(adventure))
                .withHiddenFacts(hiddenFacts)
                .withScenarioModel(narrationScenarioModel);
        String narration = planningPort.planNarration(planningRequest).narration();
        NarrationSafetyAssessment safety = narrationSafetyPort.assess(new NarrationSafetyRequest(
                narration, evidencePack, adventure.currentContext(), contextCommand.action(), planningRequest.hiddenFacts()));
        if (!safety.approved()) throw new IllegalStateException("combat narration safety rejected: " + safety.reason());
        if (NarrationLeakDetector.isHitPointValueDisclosure(narration, request.combatState())) {
            throw new IllegalStateException("combat narration contains private enemy hit-point information");
        }
        persistCombatNarration(adventure, narration);
        return narration;
    }

    private void persistConfirmedCombatResult(Adventure adventure, CombatNarrationRequest request) {
        List<ConversationEntry> conversation = new ArrayList<>(adventure.conversation());
        if (request.hasPlayerInput()) {
            conversation.add(new ConversationEntry(conversation.size(), "PLAYER", request.playerInput()));
        }
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", confirmedCombatResult(request)));
        adventure.preserveProgress(adventure.ownerPlayerId(), adventure.version(), adventure.currentContext(), conversation);
        saveConfirmedAdventureAndRegister(adventure);
    }

    private void persistCombatNarration(Adventure adventure, String narration) {
        if (narration == null || narration.isBlank()) return;
        List<ConversationEntry> conversation = new ArrayList<>(adventure.conversation());
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", narration));
        adventure.preserveProgress(adventure.ownerPlayerId(), adventure.version(), adventure.currentContext(), conversation);
        saveConfirmedAdventureAndRegister(adventure);
    }

    private static String confirmedCombatResult(CombatNarrationRequest request) {
        return "확정 전투 결과: 행동 주체=" + request.confirmedActor()
                + "; 전투 참여자 식별자=" + request.command().characterSheetId().value()
                + "; 행동=" + request.command().action()
                + "; 전투 버전=" + request.encounterVersion()
                + "; 주사위 결과=" + (request.diceTotal() == null ? "없음" : request.diceTotal())
                + "; 판정=" + request.judgment();
    }

    private static String combatNarrationAction(CombatNarrationRequest request) {
        return "확정된 전투 행동을 플레이어에게 서술합니다. "
                + (request.hasPlayerInput() ? "플레이어 입력=" + request.playerInput() : "전투 참여자 행동=" + request.command().action())
                + "; 적의 현재 상태(정확한 HP 숫자 없이 서술)="
                + request.combatState().enemies().stream().map(enemy -> "식별자=" + enemy.participantId() + " " + enemy.displayName()
                        + " 상태=" + (enemy.defeated() ? "쓰러짐" : enemy.currentHitPoints() < enemy.maximumHitPoints()
                                ? "피해를 입었고 전투 중" : "피해 없이 전투 중"))
                        .collect(java.util.stream.Collectors.joining(", "))
                + ". 이 최신 목록은 과거 대화보다 우선합니다. 쓰러진 적은 행동하지 못하며, 적이나 피해를 새로 만들지 마세요. 정확한 체력 숫자는 출력하지 마세요."
                + ";"
                + " 전투 버전=" + request.encounterVersion()
                + "; 주사위 결과=" + (request.diceTotal() == null ? "없음" : request.diceTotal())
                + "; 판정=" + request.judgment()
                + ". 전투·상황·캐릭터 상태를 바꾸지 말고 플레이어에게 보이는 서술만 제안하세요.";
    }

    private static List<String> hiddenFactsForPlayer(ScenarioPackage scenarioPackage, Adventure adventure,
            NarrativeState narrativeState) {
        List<String> hiddenFacts = new ArrayList<>(HiddenScenarioFacts.unrevealedRevelationValues(
                scenarioPackage.scenarioModel(), adventure.storyRuntimeState()));
        String actorId = adventure.ownerPlayerId().value().toString();
        java.util.Set<String> knownFactIds = narrativeState.factsKnownBy(actorId);
        narrativeState.worldFacts().values().stream()
                .filter(fact -> !knownFactIds.contains(fact.id()))
                .map(com.dndmaster.adventure.domain.runtime.narrative.WorldFact::value)
                .forEach(hiddenFacts::add);
        return hiddenFacts.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).distinct().toList();
    }

    private static String lockedScenarioContext(ScenarioPackage scenarioPackage, ScenarioModel scenarioModel) {
        List<java.util.Map<String, Object>> maps = scenarioPackage.mapDefinitions().stream().map(map -> {
            java.util.Map<String, Object> value = new java.util.LinkedHashMap<>();
            value.put("mapId", map.id());
            value.put("grid", map.grid());
            value.put("walls", map.walls());
            value.put("doors", map.doors());
            value.put("obstacles", map.obstacles());
            return value;
        }).toList();
        java.util.Map<String, Object> locked = new java.util.LinkedHashMap<>();
        locked.put("scenarioModel", scenarioModel);
        locked.put("tacticalMaps", maps);
        locked.put("mapSceneBindings", scenarioPackage.storyMapBindings());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(locked);
        } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("locked scenario and map data could not be serialized", failure);
        }
    }

    private static String enemySheetCandidateInstruction(List<CombatEnemyProposal> enemies, List<String> missingKinds) {
        var requested = enemies.stream().filter(enemy -> missingKinds.contains(enemy.enemyKey().toLowerCase(java.util.Locale.ROOT)))
                .map(enemy -> enemy.enemyKey() + " / " + enemy.name()).toList();
        return "전투 진입 전 적 캐릭터 시트 후보를 작성한다. 요청된 적 종류마다 STR, DEX, CON, INT, WIS, CHA 수치와 룰북에 명시된 모든 전투 행동·기술을 빠짐없이 반환한다. 각 능력치와 각 행동에는 제공된 근거키 중 해당 규칙을 뒷받침하는 인용을 넣는다. 다른 적 종류를 추가하지 않는다. 요청: " + String.join(", ", requested);
    }

    private static List<CombatEnemyProposal> attachEnemySheetCandidates(List<CombatEnemyProposal> grounded,
            List<CombatEnemyProposal> candidates, List<String> requestedKinds) {
        if (candidates == null || candidates.isEmpty()) throw new IllegalStateException("ENEMY_SHEET_CANDIDATE_RETRY_REQUIRED");
        var byKind = candidates.stream().collect(java.util.stream.Collectors.groupingBy(
                candidate -> candidate.enemyKey().toLowerCase(java.util.Locale.ROOT)));
        if (requestedKinds.stream().anyMatch(kind -> byKind.getOrDefault(kind, List.of()).size() != 1)) {
            throw new IllegalStateException("ENEMY_SHEET_CANDIDATE_RETRY_REQUIRED");
        }
        return grounded.stream().map(enemy -> {
            String kind = enemy.enemyKey().toLowerCase(java.util.Locale.ROOT);
            if (!requestedKinds.contains(kind)) return enemy;
            CombatEnemyProposal candidate = byKind.get(kind).get(0);
            if (candidate.abilities().size() != 6 || candidate.actions().isEmpty()) {
                throw new IllegalStateException("ENEMY_SHEET_CANDIDATE_INCOMPLETE_RETRY_REQUIRED");
            }
            return enemy.withSheetCandidates(candidate.abilities(), candidate.actions());
        }).toList();
    }

    private java.util.Map<String, com.dndmaster.adventure.application.combat.EnemyCharacterSheet> findPreparedEnemySheets(
            Adventure adventure, RuntimeBinding binding, ScenarioPackage scenarioPackage,
            List<CombatEnemyProposal> proposals) {
        if (enemyCharacterSheetRepository == null) return java.util.Map.of();
        var found = new java.util.LinkedHashMap<String, com.dndmaster.adventure.application.combat.EnemyCharacterSheet>();
        for (CombatEnemyProposal proposal : proposals) {
            String kind = proposal.enemyKey().toLowerCase(java.util.Locale.ROOT);
            var identity = enemySheetIdentity(adventure, binding, scenarioPackage, kind);
            enemyCharacterSheetRepository.find(identity).ifPresent(sheet -> found.put(kind, sheet));
        }
        return java.util.Map.copyOf(found);
    }

    private java.util.Map<String, com.dndmaster.adventure.application.combat.EnemyCharacterSheet> savePreparedEnemySheets(
            Adventure adventure, RuntimeBinding binding, ScenarioPackage scenarioPackage,
            List<CombatEnemyProposal> proposals, EvidencePack evidencePack) {
        if (enemyCharacterSheetRepository == null) return java.util.Map.of();
        var saved = new java.util.LinkedHashMap<String, com.dndmaster.adventure.application.combat.EnemyCharacterSheet>();
        var cited = java.util.stream.Stream.concat(evidencePack.storybook().stream(), evidencePack.rulebook().stream())
                .collect(java.util.stream.Collectors.toMap(RuntimeEvidence::referenceKey, evidence -> evidence, (left, right) -> left));
        var pinnedKeys = cited.keySet();
        for (CombatEnemyProposal proposal : proposals) {
            String kind = proposal.enemyKey().toLowerCase(java.util.Locale.ROOT);
            var block = proposal.statBlock();
            if (block == null) continue;
            RuntimeEvidence source = cited.get(block.source().citationKey());
            if (source == null) {
                enemyCharacterSheetRepository.find(enemySheetIdentity(adventure, binding, scenarioPackage, kind))
                        .ifPresent(sheet -> saved.put(kind, sheet));
                continue;
            }
            if (proposal.abilities().size() != 6 || proposal.actions().isEmpty()) {
                throw new IllegalStateException("ENEMY_SHEET_CANDIDATE_INCOMPLETE_RETRY_REQUIRED");
            }
            var candidate = new com.dndmaster.adventure.application.combat.EnemyCharacterSheet(
                    enemySheetIdentity(adventure, binding, scenarioPackage, kind), proposal.name(), block,
                    proposal.abilities(), proposal.actions().stream().map(action ->
                            new com.dndmaster.adventure.application.combat.EnemyCharacterSheet.EnemyCombatAction(
                                    action.name(), action.description(), action.citationKeys())).toList());
            var verified = com.dndmaster.adventure.application.combat.EnemyCharacterSheetPolicy.verify(candidate, pinnedKeys);
            saved.put(kind, enemyCharacterSheetRepository.saveIfAbsent(verified));
        }
        return java.util.Map.copyOf(saved);
    }

    private static com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity enemySheetIdentity(
            Adventure adventure, RuntimeBinding binding, ScenarioPackage scenarioPackage, String kind) {
        return new com.dndmaster.adventure.application.combat.EnemyCharacterSheetIdentity(adventure.id().value(),
                scenarioPackage.bundleId().value(), scenarioPackage.bundleRevision(), scenarioPackage.packageId(),
                binding.rulebookIds(), kind);
    }

    private com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest preparationRequest(
            Adventure adventure, RuntimeTurn turn) {
        if (!turn.plan().combatStartRequested() || turn.plan().combatEnemies().isEmpty()
                || enemyCharacterSheetRepository == null) return null;
        RuntimeBinding binding = bindingRepository.findCurrentByAdventureId(turn.adventureId())
                .orElseThrow(() -> new IllegalStateException("runtime binding not found for enemy preparation recovery"));
        ScenarioPackage scenario = scenarioPackageRepository.findById(turn.scenarioPackageId())
                .orElseThrow(() -> new IllegalStateException("scenario package not found for enemy preparation recovery"));
        List<CombatEnemyProposal> enemies = turn.plan().combatEnemies().stream().map(enemy ->
                enemy.sheetIdentity() == null ? enemy.withSheetIdentity(enemySheetIdentity(adventure, binding, scenario,
                        enemy.enemyKey().toLowerCase(java.util.Locale.ROOT))) : enemy).toList();
        List<String> missingKinds = enemies.stream().filter(enemy -> enemyCharacterSheetRepository.find(enemy.sheetIdentity()).isEmpty())
                .map(enemy -> enemy.sheetIdentity().enemyKind()).distinct().toList();
        RuntimePlanningRequest candidateRequest = missingKinds.isEmpty() ? null : new RuntimePlanningRequest(
                turn.adventureId(), adventure.ownerPlayerId(), turn.scenarioPackageId(), turn.bindingVersion(),
                turn.context(), turn.activeSourceContext(), enemySheetCandidateInstruction(enemies, missingKinds),
                turn.evidencePack());
        return new com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest(turn.turnId(),
                turn.adventureId().value(), enemies.stream().map(enemy ->
                new com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest.Enemy(
                        enemy.sheetIdentity(), enemy)).toList(), candidateRequest);
    }

    public void prepareEnemySheetsForWork(
            com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest request) {
        if (request == null || enemyCharacterSheetRepository == null) {
            throw new IllegalStateException("ENEMY_SHEET_CANDIDATE_CONTEXT_MISSING");
        }
        var missing = request.enemies().stream().filter(enemy -> enemyCharacterSheetRepository.find(enemy.identity()).isEmpty()).toList();
        if (missing.isEmpty()) return;
        if (request.planningRequest() == null) throw new IllegalStateException("ENEMY_SHEET_CANDIDATE_CONTEXT_MISSING");
        var evidence = request.planningRequest().evidencePack();
        var pinnedKeys = java.util.stream.Stream.concat(evidence.storybook().stream(), evidence.rulebook().stream())
                .map(RuntimeEvidence::referenceKey).collect(java.util.stream.Collectors.toSet());
        if (pinnedKeys.isEmpty()) throw new AbsentEnemyRuleEvidenceException("ENEMY_SHEET_RULE_EVIDENCE_ABSENT");
        RuntimePlan candidatePlan = planningPort.prepareEnemySheets(request.planningRequest());
        List<String> kinds = missing.stream().map(enemy -> enemy.identity().enemyKind()).toList();
        var candidates = attachEnemySheetCandidates(missing.stream().map(
                        com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest.Enemy::proposal).toList(),
                candidatePlan.combatEnemies(), kinds);
        for (var entry : missing) {
            CombatEnemyProposal candidate = candidates.stream().filter(enemy ->
                    enemy.enemyKey().equalsIgnoreCase(entry.identity().enemyKind())).findFirst().orElseThrow();
            if (candidate.statBlock() == null) throw new IllegalStateException("ENEMY_SHEET_CANDIDATE_INCOMPLETE_RETRY_REQUIRED");
            var profile = new com.dndmaster.adventure.application.combat.EnemyCharacterSheet(entry.identity(),
                    candidate.name(), candidate.statBlock(), candidate.abilities(), candidate.actions().stream()
                    .map(action -> new com.dndmaster.adventure.application.combat.EnemyCharacterSheet.EnemyCombatAction(
                            action.name(), action.description(), action.citationKeys())).toList());
            enemyCharacterSheetRepository.saveIfAbsent(
                    com.dndmaster.adventure.application.combat.EnemyCharacterSheetPolicy.verify(profile, pinnedKeys));
        }
    }

    private RuntimeTurnResult submitSafeScenarioRuntimeTurn(SubmitRuntimeTurnCommand command, Adventure adventure,
            RuntimeBinding binding, ScenarioPackage scenarioPackage) {
        List<String> characterSheets = stage(command.turnId(), "character_sheet_reads", () -> adventure.party().stream()
                .map(member -> currentCharacterSheet(member.characterSheetId().value())).toList());
        EvidencePack initialEvidencePack = stage(command.turnId(), "player_action_evidence_search",
                () -> prefetchEvidence(command, adventure, binding, scenarioPackage));
        List<RuntimeFactLookupResult> factLookupResults = stage(command.turnId(), "runtime_fact_lookup",
                () -> lookupRuntimeFacts(command, adventure, scenarioPackage, initialEvidencePack));
        EvidencePack evidencePack = initialEvidencePack;
        NarrativeState narrativeState = stage(command.turnId(), "narrative_state_read", () -> narrativeStateService == null
                ? NarrativeState.empty() : narrativeStateService.load(adventure.sessionId().value()));
        NarrativeContext narrativeContext = narrativeState.project(command.ownerPlayerId().value().toString(),
                adventure.currentSituation().problem());
        RuntimePlanningRequest planningRequest = new RuntimePlanningRequest(
                command.adventureId(), command.ownerPlayerId(), adventure.sessionId().value(), command.turnId(), binding.scenarioPackageId(), binding.bindingVersion(),
                adventure.currentContext(), binding.activeSourceContext(), command.action(), evidencePack,
                recentConversationForPrompt(adventure),
                characterSheets, lockedScenarioContext(scenarioPackage, scenarioPackage.scenarioModel()), providerEndpointId(adventure.sessionId().value()),
                providerSelection(adventure.sessionId().value(), "provider"), providerSelection(adventure.sessionId().value(), "model"),
                providerSelection(adventure.sessionId().value(), "reasoning"), narrativeContext, adventure.ruleSetId().value(),
                adventure.runtimeAddedFacts().stream().map(RuntimeAddedFact::content).toList(), factLookupResults,
                adventure.currentSituation().toString(), longTermFactsForPrompt(adventure))
                .withScenarioModel(scenarioPackage.scenarioModel())
                .withRagSearchContext(ragSearchContext(playerActionEvidenceScope(command, adventure, binding,
                        scenarioPackage, knowledgeDocumentIds(adventure, scenarioPackage))));
        RuntimePlanningResult planningResult = stage(command.turnId(), "gm_runtime_planning",
                () -> planningPort.planWithOutcomes(planningRequest));
        RuntimePlan plan = planningResult.plan();
        logSituationDiagnostic(command.turnId(), "proposed", planningResult.resolutionProposal(), null);
        EvidencePack groundingEvidencePack = evidencePack;
        RuntimeResolutionProposal proposal = stage(command.turnId(), "resolution_proposal_grounding", () -> {
            try {
                RuntimeResolutionProposal grounded = SituationProposalGroundingPolicy.ground(planningResult.resolutionProposal(),
                        scenarioPackage.scenarioModel(), groundingEvidencePack.rules(), command.turnId());
                logSituationDiagnostic(command.turnId(), "grounded", grounded, null);
                return grounded;
            } catch (IllegalArgumentException failure) {
                SituationProposal situation = planningResult.resolutionProposal().situationProposal();
                if (com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) {
                    LOGGER.error("dev_situation_grounding outcome=rejected turnId={} reason={} basis={} proposedReference={} "
                                    + "storybookEvidence={} rulebookEvidence={} resolutionEvidence={}",
                            command.turnId(), safeMessage(failure), situation == null ? null : situation.basis(),
                            situation == null ? null : situation.reference(), evidenceReferences(groundingEvidencePack.storybook()),
                            evidenceReferences(groundingEvidencePack.rulebook()), evidenceReferences(groundingEvidencePack.resolution()));
                }
                throw failure;
            }
        });
        com.dndmaster.adventure.domain.runtime.CurrentSituation nextSituation = proposal.situationUpdate() == null
                ? adventure.currentSituation()
                : SituationUpdatePolicy.apply(adventure.currentSituation(), proposal.situationUpdate());
        List<CombatEnemyProposal> groundedCombatEnemies = List.of();
        com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest enemySheetPreparationRequest = null;
        if (plan.combatStartRequested()) {
            try {
                var preparedSheets = findPreparedEnemySheets(adventure, binding, scenarioPackage, plan.combatEnemies());
                boolean allPrepared = preparedSheets.size() == plan.combatEnemies().stream()
                        .map(enemy -> enemy.enemyKey().toLowerCase(java.util.Locale.ROOT)).distinct().count();
                if (!allPrepared) {
                    evidencePack = evidencePack.prioritizingCombatEvidence(searchCombatStatEvidence(
                            command, adventure, binding, scenarioPackage, plan.combatEnemies()));
                }
                groundedCombatEnemies = CombatScenarioGroundingPolicy.groundWithPreparedSheets(
                        scenarioPackage.scenarioModel(), nextSituation, plan.combatEnemies(),
                        evidencePack.storybook(), evidencePack.rulebook(), preparedSheets.entrySet().stream()
                                .collect(java.util.stream.Collectors.toMap(java.util.Map.Entry::getKey,
                                        entry -> entry.getValue().statBlock())));
                var missingKinds = groundedCombatEnemies.stream().map(enemy -> enemy.enemyKey().toLowerCase(java.util.Locale.ROOT))
                        .filter(kind -> !preparedSheets.containsKey(kind)).distinct().toList();
                groundedCombatEnemies = groundedCombatEnemies.stream().map(enemy -> {
                    var sheet = preparedSheets.get(enemy.enemyKey().toLowerCase(java.util.Locale.ROOT));
                    var identity = sheet == null ? enemySheetIdentity(adventure, binding, scenarioPackage,
                            enemy.enemyKey().toLowerCase(java.util.Locale.ROOT)) : sheet.identity();
                    if (sheet == null) return enemy.withSheetIdentity(identity);
                    return enemy.withStatBlock(sheet.statBlock()).withSheetCandidates(sheet.abilities(),
                            sheet.actions().stream().map(action -> new CombatEnemyActionProposal(action.name(),
                                    action.description(), action.citationKeys())).toList()).withSheetIdentity(identity);
                }).toList();
                if (enemyCharacterSheetRepository != null) {
                    RuntimePlanningRequest candidateRequest = missingKinds.isEmpty() ? null : planningRequest
                            .withEvidencePack(evidencePack)
                            .withAction(enemySheetCandidateInstruction(groundedCombatEnemies, missingKinds));
                    enemySheetPreparationRequest = new com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest(
                            command.turnId(), adventure.id().value(), groundedCombatEnemies.stream()
                                    .map(enemy -> new com.dndmaster.adventure.application.combat.EnemySheetPreparationRequest.Enemy(
                                            enemy.sheetIdentity(), enemy)).toList(), candidateRequest);
                }
                plan = plan.withCombatEnemies(groundedCombatEnemies);
            } catch (IllegalArgumentException groundingFailure) {
                logCombatGroundingRejected(groundingFailure, plan.combatEnemies().size(), evidencePack.rules().size());
                String reason = "COMBAT_STAT_BLOCK_NOT_FOUND".equals(groundingFailure.getMessage())
                        ? "전투 보류: 룰북에서 적의 방어도·HP·공격 수치를 확인하지 못했습니다."
                        : "전투 보류: 현재 시츄에이션의 적을 이야기 자료에서 확인하지 못했습니다.";
                plan = plan.withoutCombat(reason);
            }
        }
        RuntimePlan checkedPlan = plan;
        EvidencePack checkEvidencePack = evidencePack;
        stage(command.turnId(), "check_proposal_validation", () -> {
            validateCheckProposal(checkedPlan.checkProposal(), checkEvidencePack, adventure, command);
            return null;
        });
        if (plan.checkProposal().required()
                && plan.checkProposal().rollMethod() == RuntimeCheckProposal.RollMethod.SYSTEM) {
            int rolled = rollDice(plan.checkProposal().diceExpression());
            int total = Math.addExact(rolled, plan.checkProposal().modifier());
            plan = plan.withCheckOutcome(total >= plan.checkProposal().difficulty());
        }
        RuntimeTurn requested = new RuntimeTurn(command.turnId(), command.commandId(), adventure.id(), adventure.sessionId().value(),
                binding.scenarioPackageId(), binding.bindingVersion(), command.action(), evidencePack, plan,
                binding.activeSourceContext(), adventure.currentContext(), adventure.conversation(), adventure.version(),
                plan.citedEvidence().stream().map(evidence -> evidence.evidenceType() + ":" + evidence.locator()).toList(), plan.warnings(),
                false, !command.gmOnly(), command.gmOnly() ? RuntimeTurnOrigin.GM : RuntimeTurnOrigin.PLAYER,
                command.advancesState(), command.turnCharacterSheetId(), command.turnIndex() < 0 ? null : command.turnIndex(),
                command.expectedVersion(), command.gmOnly(), command.agentOrigin()).asRequested();
        if (!groundedCombatEnemies.isEmpty()) {
            nextSituation = nextSituation.enterCombatScenario(groundedCombatEnemies.get(0).scenarioId());
        }
        PendingRuntimeState pending = new PendingRuntimeState(proposal.gameStateDelta(), proposal.disclosureState(),
                nextSituation, proposal.runtimeAddedFacts());
        if (plan.checkProposal().required()
                && plan.checkProposal().rollMethod() == RuntimeCheckProposal.RollMethod.PLAYER) {
            if (!command.externalCommands().isEmpty() || !planningResult.runtimeCommands().isEmpty()) {
                throw new IllegalStateException("PLAYER_CHECK_CANNOT_BE_COMBINED_WITH_UNCOMMITTED_RUNTIME_COMMANDS");
            }
            RuntimeTurn parked = requested.pendingPlayerRoll(pending, proposal.completionProposal());
            runtimeTurnRepository.save(parked);
            PlayerRollRequest rollRequest = new PlayerRollRequest(command.turnId(),
                    plan.checkProposal().abilityOrSkill(), plan.checkProposal().diceExpression(),
                    plan.checkProposal().reason(), adventure.version());
            PlayerVisibleTurn waiting = new PlayerVisibleTurn(parked.narration(), plan.scene(), List.of(), null,
                    narrativeContext, rollRequest);
            return new RuntimeTurnResult(parked, adventure.currentContext(), adventure.conversation(), adventure.version(), waiting);
        }
        RuntimeTurnSafetyOrchestrator safetyOrchestrator = new RuntimeTurnSafetyOrchestrator(narrationSafetyPort);
        StateDelta pendingNarrativeDelta = deltaFor(narrativeState, command, plan);
        PlayerVisibleTurn visibleInput = new PlayerVisibleTurn(plan.narration(), plan.scene(), List.of(), pendingNarrativeDelta, narrativeContext);
        RuntimeTurnResolution turnResolution = new RuntimeTurnResolution(plan.judgment(), null,
                planningResult.toolOutcomes().stream().map(RuntimeTurnApplicationService::renderOutcome).toList());
        RuntimeTurn ready = stage(command.turnId(), "narration_safety_and_presentation", () -> safetyOrchestrator.resolveAndNarrate(requested,
                turnResolution,
                pending, proposal.completionProposal(), () -> writerPort.write(visibleInput).prose()));
        if (ready.lifecycle() == RuntimeTurnLifecycle.DISCARDED) {
            runtimeTurnRepository.save(ready);
            throw new IllegalStateException("runtime narration safety retries exhausted");
        }
        AdventureContext nextContext = new AdventureContext(plan.scene(), plan.npcState(), command.action(), plan.judgment());
        List<ConversationEntry> conversation = new ArrayList<>(adventure.conversation());
        if (!command.gmOnly()) conversation.add(new ConversationEntry(conversation.size(), "PLAYER", command.action()));
        conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", ready.narration()));
        if (!command.gmOnly() && plan.judgment() != null && !plan.judgment().isBlank()) {
            conversation.add(new ConversationEntry(conversation.size(), "AI_GAME_MASTER", plan.judgment()));
        }
        RuntimeTurnCommitOrchestrator.Result commitResult;
        if (commitOrchestrator == null) {
            RuntimeTurn committing = ready.beginCommit();
            runtimeTurnRepository.save(committing);
            adventure.commitRuntimeTurn(command.ownerPlayerId(), adventure.version(), pending, nextContext, conversation,
                    proposal.completionProposal());
            commitGate.beforeCommit(adventure, ready);
            saveAdventureTurn(adventure, proposal.completionProposal());
            RuntimeTurn committed = committing.markSafeCommitted();
            runtimeTurnRepository.save(committed);
            commitResult = new RuntimeTurnCommitOrchestrator.Result(
                    RuntimeTurnCommitOrchestrator.Status.COMMITTED, committed, null);
        } else {
            java.util.concurrent.atomic.AtomicInteger commandOrder = new java.util.concurrent.atomic.AtomicInteger();
            List<RuntimeTurnCommand> commands = java.util.stream.Stream.concat(
                            command.externalCommands().stream(), planningResult.runtimeCommands().stream())
                    .map(value -> value.withExecutionOrder(commandOrder.getAndIncrement()))
                    .toList();
            commitResult = stage(command.turnId(), "runtime_turn_commit", () -> commitOrchestrator.commit(ready, commands, () -> {
                adventure.commitRuntimeTurn(command.ownerPlayerId(), adventure.version(), pending, nextContext, conversation,
                        proposal.completionProposal());
                commitGate.beforeCommit(adventure, ready);
                saveAdventureTurn(adventure, proposal.completionProposal());
                if (narrativeStateService != null) narrativeStateService.commit(adventure.sessionId().value(), visibleInput.stateDelta());
            }));
        }
        if (commitResult.status() != RuntimeTurnCommitOrchestrator.Status.COMMITTED) {
            if (commitResult.status() == RuntimeTurnCommitOrchestrator.Status.RETRY_REQUIRED) {
                return new RuntimeTurnResult(commitResult.turn(), adventure.currentContext(), adventure.conversation(),
                        adventure.version(), visibleInput, commitResult.movementResult());
            }
            throw new IllegalStateException("runtime turn commit requires repair: " + commitResult.status());
        }
        RuntimeTurn committed = commitResult.turn();
        PlayerVisibleTurn visible = new PlayerVisibleTurn(ready.narration(), plan.scene(), List.of(), visibleInput.stateDelta(), narrativeContext);
        return new RuntimeTurnResult(committed, adventure.currentContext(), adventure.conversation(), adventure.version(), visible,
                commitResult.movementResult(), enemySheetPreparationRequest);
    }

    private static void logSituationDiagnostic(UUID turnId, String outcome,
            RuntimeResolutionProposal proposal, String reason) {
        if (!com.dndmaster.adventure.infrastructure.diagnostics.DevelopmentDiagnostics.enabled()) return;
        SituationProposal situation = proposal == null ? null : proposal.situationProposal();
        SituationUpdateProposal update = proposal == null ? null : proposal.situationUpdate();
        LOGGER.info("dev_situation_transition outcome={} turnId={} hasProposal={} kind={} location={} basis={} reference={} required={} reason={}",
                outcome, turnId, situation != null,
                update == null ? null : update.kind(), update == null ? null : update.location(),
                situation == null ? null : situation.basis(), situation == null ? null : situation.reference(),
                situation == null ? null : situation.required(), reason);
    }

    private List<String> recentConversationForPrompt(Adventure adventure) {
        List<String> result = new ArrayList<>();
        List<ConversationSummary> selectedSummaries = List.of();
        if (conversationCompactionJobRepository != null) {
            List<ConversationSummary> summaries = conversationCompactionJobRepository.summaries(adventure.id());
            // #347 owns summary selection and reconsolidation. Here every published range remains represented.
            selectedSummaries = summaries;
            for (ConversationSummary summary : selectedSummaries) {
                result.add("압축된 이전 대화: " + summary.text());
            }
        }
        // Without a published summary, every original entry remains available after a failed job.
        final List<ConversationSummary> published = selectedSummaries;
        result.addAll(adventure.conversation().stream().filter(entry -> !coveredByPublishedSummary(entry, published))
                .map(entry -> entry.speaker() + ": " + entry.content()).toList());
        return List.copyOf(result);
    }

    private List<LongTermAdventureFact> longTermFactsForPrompt(Adventure adventure) {
        return conversationCompactionJobRepository == null ? List.of()
                : conversationCompactionJobRepository.longTermFacts(adventure.id());
    }

    static boolean coveredByPublishedSummary(ConversationEntry entry, List<ConversationSummary> summaries) {
        return summaries.stream().anyMatch(summary -> entry.sequence() >= summary.sourceStart() && entry.sequence() <= summary.sourceEnd());
    }

    private void saveConfirmedAdventureAndRegister(Adventure adventure) {
        ConversationCompactionJob job = compactionJob(adventure);
        if (job == null) { adventureRepository.save(adventure); return; }
        if (adventureRepository instanceof AdventureConversationCompactionCommitPort atomic) {
            atomic.saveConfirmedTurnAndRegister(adventure, job);
        } else { throw new IllegalStateException("confirmed adventure storage must atomically register conversation compaction work"); }
    }

    private void saveAdventureTurn(Adventure adventure, com.dndmaster.adventure.domain.runtime.CompletionProposal completion) {
        if (completion != null && completion.complete()) {
            if (adventureCompletionCommitPort == null) {
                throw new IllegalStateException("adventure conclusion requires atomic session completion support");
            }
            adventureCompletionCommitPort.saveCompleted(adventure, compactionJob(adventure));
            return;
        }
        saveConfirmedAdventureAndRegister(adventure);
    }

    private ConversationCompactionJob compactionJob(Adventure adventure) {
        if (conversationCompactionJobRepository == null) return null;
        List<ConversationEntry> conversation = adventure.conversation();
        List<Long> completedEnds = ConversationCompactionCoordinator.completedTurnEnds(conversation);
        if (completedEnds.size() < 3) return null;
        long sourceStart = Math.max(conversation.getFirst().sequence(), conversationCompactionJobRepository.coveredThrough(adventure.id()) + 1);
        long sourceEnd = completedEnds.get(completedEnds.size() - 3);
        return sourceStart > sourceEnd ? null : ConversationCompactionJob.ready(adventure.id(), sourceStart, sourceEnd, adventure.version(), java.time.Instant.now());
    }

    public static StateDelta deltaFor(NarrativeState state, SubmitRuntimeTurnCommand command, RuntimePlan plan) {
        return deltaFor(state, command.turnId(), plan);
    }

    public static StateDelta deltaFor(NarrativeState state, RuntimeTurn turn) {
        return deltaFor(state, turn.turnId(), turn.plan());
    }

    private static StateDelta deltaFor(NarrativeState state, UUID turnId, RuntimePlan plan) {
        if (plan.stateDelta() != null) return plan.stateDelta();
        List<RecentEvent> events = new ArrayList<>(state.recentEvents());
        events.add(new RecentEvent(turnId.toString(), state.version(), plan.judgment()));
        return new StateDelta(state.version(), java.util.Set.of(), java.util.Set.of(),
                List.of(), List.of(), state.relationships(), state.activeThreads(), events);
    }

    private static RuntimeTurnOrigin origin(SubmitRuntimeTurnCommand command) {
        if (command.agentOrigin()) return RuntimeTurnOrigin.AGENT;
        if (command.gmOnly()) return RuntimeTurnOrigin.GM;
        return RuntimeTurnOrigin.PLAYER;
    }

    private String providerSelection(UUID sessionId, String field) {
        if (providerBindingRepository == null) return defaultProviderSelection(field);
        ProviderBinding binding = providerBindingRepository.current(sessionId).orElse(null);
        if (binding == null) return defaultProviderSelection(field);
        return switch (field) {
            case "provider" -> blankOrDefault(binding.selection().provider(), field);
            case "model" -> blankOrDefault(binding.selection().model(), field);
            case "reasoning" -> blankOrDefault(binding.selection().reasoning(), field);
            default -> "";
        };
    }

    private UUID providerEndpointId(UUID sessionId) {
        if (providerBindingRepository == null) return null;
        ProviderBinding binding = providerBindingRepository.current(sessionId).orElse(null);
        return binding == null ? null : binding.selection().endpointId();
    }

    private static String blankOrDefault(String value, String field) {
        return value == null || value.isBlank() ? defaultProviderSelection(field) : value;
    }

    /** Keep runtime turns executable during migration when an old session has no binding row. */
    private static String defaultProviderSelection(String field) {
        return switch (field) {
            case "provider" -> "codex-cli";
            case "model" -> "gpt-5.6-luna";
            case "reasoning" -> "none";
            default -> "";
        };
    }

    private RuntimeTurn resumeCommittedTurn(SubmitRuntimeTurnCommand command, Adventure adventure, RuntimeTurn existing) {
        if (existing.committed()) {
            return existing;
        }
        long expectedProgressDelta = command.turnCharacterSheetId() == null ? 1 : 2;
        if (adventure.version() == existing.version() - expectedProgressDelta) {
            Adventure progressed = Adventure.rehydrate(
                    adventure.id(), adventure.sessionId(), adventure.ownerPlayerId(), adventure.scenarioId(),
                    adventure.ruleSetId(), adventure.party(), adventure.conversation(), adventure.currentContext(),
                    adventure.status(), adventure.version(), adventure.turnIndex(), adventure.lastTurnKey());
            progressed.preserveProgress(command.ownerPlayerId(), adventure.version(), existing.context(), existing.conversation());
            if (command.turnCharacterSheetId() != null) {
                progressed.advanceTurn(command.ownerPlayerId(), command.turnIndex(), command.turnCharacterSheetId(), command.turnId());
            }
            saveConfirmedAdventureAndRegister(progressed);
        }
        RuntimeTurn committed = existing.markCommitted();
        runtimeTurnRepository.save(committed);

        return committed;
    }

    private EvidencePack prefetchEvidence(
            SubmitRuntimeTurnCommand command, Adventure adventure, RuntimeBinding binding, ScenarioPackage scenarioPackage) {
        List<UUID> knowledgeDocumentIds = knowledgeDocumentIds(adventure, scenarioPackage);
        if (playerActionEvidenceAcquirer != null) {
            List<RuntimeEvidence> selected = playerActionEvidenceAcquirer.acquire(
                    playerActionEvidenceScope(command, adventure, binding, scenarioPackage, knowledgeDocumentIds), command.action(),
                    adventure.currentSituation().toString());
            List<RuntimeEvidence> storybook = selected.stream()
                    .filter(evidence -> evidence.evidenceType() == RuntimeEvidenceType.STORYBOOK)
                    .toList();
            List<RuntimeEvidence> rulebook = selected.stream()
                    .filter(evidence -> evidence.evidenceType() == RuntimeEvidenceType.RULEBOOK)
                    .toList();
            return new EvidencePack(storybook, rulebook, List.of());
        }
        List<UUID> storybookDocumentIds = documentIdsOfType(scenarioPackage, "STORYBOOK", knowledgeDocumentIds);
        if (storybookDocumentIds.isEmpty()) {
            storybookDocumentIds = documentIdsOfType(scenarioPackage, "STORYBOOK",
                    scenarioPackage.documents().stream().map(d -> d.knowledgeDocumentId().value()).toList());
        }
        List<UUID> rulebookDocumentIds = documentIdsOfType(scenarioPackage, "RULEBOOK", knowledgeDocumentIds);
        Map<UUID, Long> extractionVersions = scenarioPackage.documents().stream()
                .collect(java.util.stream.Collectors.toMap(document -> document.knowledgeDocumentId().value(),
                        com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection::extractionVersion, (a, b) -> a));
        extractionVersions = extractionVersions.entrySet().stream().filter(entry -> entry.getValue() > 1)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        String currentSituation = adventure.currentSituation() == null ? "" : adventure.currentSituation().toString();
        String turnRulesQuery = RuntimePlayerActionEvidenceAcquirer.turnRulesQuery(command.action(),
                currentSituation, "scene:" + adventure.currentContext().currentScene());
        RuntimeEvidenceSearchRequest request = new RuntimeEvidenceSearchRequest(
                adventure.id(), command.ownerPlayerId(), adventure.sessionId(), binding.scenarioPackageId(), storybookDocumentIds,
                binding.activeSourceContext(), turnRulesQuery, RuntimeEvidenceType.STORYBOOK, 8,
                extractionVersions, "scene:" + adventure.currentContext().currentScene(), "PLAYER_ACTION");
        List<RuntimeEvidence> storybook = bestEffortScopedSearch(request.forType(RuntimeEvidenceType.STORYBOOK, 5));
        List<RuntimeEvidence> rulebook = rulebookDocumentIds.isEmpty() ? List.of()
                : bestEffortScopedSearch(request.withDocumentIds(rulebookDocumentIds, RuntimeEvidenceType.RULEBOOK, 5));
        // No match in either rules source is a normal runtime condition. The
        // GM still receives the locked situation and map context and may decide
        // that the action needs no rule-based check.
        return new EvidencePack(storybook, rulebook, List.of());
    }

    private static com.dndmaster.adventure.evidence.EvidenceSearchScope playerActionEvidenceScope(
            SubmitRuntimeTurnCommand command, Adventure adventure, RuntimeBinding binding, ScenarioPackage scenarioPackage,
            List<UUID> knowledgeDocumentIds) {
        List<com.dndmaster.adventure.evidence.EvidenceSearchScope.Document> documents = scenarioPackage.documents().stream()
                .filter(document -> knowledgeDocumentIds.contains(document.knowledgeDocumentId().value()))
                .map(RuntimeTurnApplicationService::playerActionDocument)
                .filter(Objects::nonNull)
                .toList();
        List<String> activeLocators = binding.activeSourceContext() == null ? List.of()
                : List.of(binding.activeSourceContext().locator());
        return new com.dndmaster.adventure.evidence.EvidenceSearchScope(command.ownerPlayerId().value(), adventure.sessionId().value(),
                binding.scenarioPackageId(), "scene:" + adventure.currentContext().currentScene(), "PLAYER_ACTION",
                documents, activeLocators);
    }

    private static com.dndmaster.adventure.evidence.EvidenceSearchScope.Document playerActionDocument(
            com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection document) {
        String type = document.documentType().trim().toUpperCase(java.util.Locale.ROOT);
        if (!"RULEBOOK".equals(type) && !"STORYBOOK".equals(type)) return null;
        return new com.dndmaster.adventure.evidence.EvidenceSearchScope.Document(document.knowledgeDocumentId().value(),
                document.extractionVersion(), type);
    }

    private static Map<String, Object> ragSearchContext(com.dndmaster.adventure.evidence.EvidenceSearchScope scope) {
        return Map.of("ownerId", scope.ownerId(), "sessionId", scope.sessionId(),
                "scenarioPackageId", scope.scenarioPackageId(), "stageKey", scope.stageKey(),
                "actionIntent", scope.actionIntent(),
                "scope", scope.documents().stream().map(document -> Map.of(
                        "documentId", document.id(), "extractionVersion", document.extractionVersion(),
                        "documentType", document.type())).toList(),
                "activeLocators", scope.activeLocators());
    }

    List<RuntimeEvidence> searchCombatStatEvidence(SubmitRuntimeTurnCommand command, Adventure adventure,
            RuntimeBinding binding, ScenarioPackage scenarioPackage, List<CombatEnemyProposal> proposals) {
        if (proposals == null || proposals.isEmpty()) return List.of();
        List<UUID> selectedDocuments = knowledgeDocumentIds(adventure, scenarioPackage);
        List<UUID> rulebookDocuments = documentIdsOfType(scenarioPackage, "RULEBOOK", selectedDocuments);
        List<UUID> storybookDocuments = documentIdsOfType(scenarioPackage, "STORYBOOK", selectedDocuments);
        if (rulebookDocuments.isEmpty() && storybookDocuments.isEmpty()) return List.of();
        Map<UUID, Long> extractionVersions = scenarioPackage.documents().stream()
                .collect(java.util.stream.Collectors.toMap(document -> document.knowledgeDocumentId().value(),
                        com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection::extractionVersion, (a, b) -> a));
        List<RuntimeEvidence> found = new ArrayList<>();
        for (CombatEnemyProposal proposal : proposals) {
            if (proposal == null) continue;
            String query = proposal.enemyKey() + " " + proposal.name();
            String contextKey = "combat-stat:" + proposal.enemyKey();
            if (!rulebookDocuments.isEmpty()) {
                found.addAll(scopedSearch(new RuntimeEvidenceSearchRequest(
                        adventure.id(), command.ownerPlayerId(), adventure.sessionId(), binding.scenarioPackageId(),
                        rulebookDocuments, binding.activeSourceContext(), query, RuntimeEvidenceType.RULEBOOK,
                        3, extractionVersions, contextKey, "ADJUDICATION")));
            }
            if (!storybookDocuments.isEmpty()) {
                found.addAll(scopedSearch(new RuntimeEvidenceSearchRequest(
                        adventure.id(), command.ownerPlayerId(), adventure.sessionId(), binding.scenarioPackageId(),
                        storybookDocuments, binding.activeSourceContext(), query, RuntimeEvidenceType.STORYBOOK,
                        3, extractionVersions, contextKey, "ADJUDICATION")));
            }
        }
        return found.stream().distinct().toList();
    }

    private static List<UUID> documentIdsOfType(ScenarioPackage scenarioPackage, String type, List<UUID> selected) {
        return scenarioPackage.documents().stream()
                .filter(document -> type.equalsIgnoreCase(document.documentType())
                        || ("STORYBOOK".equalsIgnoreCase(type) && document.role() == ScenarioBundleDocumentRole.MAIN_SCENARIO))
                .map(document -> document.knowledgeDocumentId().value())
                .filter(selected::contains)
                .distinct()
                .toList();
    }

    private List<RuntimeEvidence> scopedSearch(RuntimeEvidenceSearchRequest request) {
        return evidenceSearchPort.search(request).stream()
                .filter(Objects::nonNull)
                .filter(e -> request.knowledgeDocumentIds().contains(e.knowledgeDocumentId().value()))
                .toList();
    }

    private List<RuntimeEvidence> bestEffortScopedSearch(RuntimeEvidenceSearchRequest request) {
        try {
            return scopedSearch(request);
        } catch (RuntimeException failure) {
            if (!isRecoverableLookupFailure(failure)) throw failure;
            LOGGER.warn("runtime_evidence_search_unavailable type={} actionIntent={}",
                    request.evidenceType(), request.actionIntent());
            return List.of();
        }
    }

    private static boolean isRecoverableLookupFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage() == null ? "" : current.getMessage().toLowerCase(java.util.Locale.ROOT);
            if (message.contains("returned 400") || message.contains("returned 401")
                    || message.contains("returned 403") || message.contains("provenance")
                    || message.contains("contract") || message.contains("outside the selected")) {
                return false;
            }
            if (current instanceof java.net.http.HttpTimeoutException
                    || current instanceof java.net.ConnectException
                    || current instanceof java.io.IOException
                    || current instanceof java.util.concurrent.TimeoutException) {
                return true;
            }
        }
        // Unknown failures may indicate a contract, authorization, or data
        // integrity problem. Fail closed instead of silently turning them into
        // a missing-evidence result.
        return false;
    }

    private List<RuntimeFactLookupResult> lookupRuntimeFacts(SubmitRuntimeTurnCommand command, Adventure adventure,
            ScenarioPackage scenarioPackage, EvidencePack evidencePack) {
        return lookupRuntimeFacts(command, adventure, evidencePack, scenarioPackage.scenarioModel());
    }

    private List<RuntimeFactLookupResult> lookupRuntimeFacts(SubmitRuntimeTurnCommand command, Adventure adventure,
            EvidencePack evidencePack, ScenarioModel scenarioModel) {
        if (runtimeFactLookupService == null) return List.of();
        try {
            RuntimeFactLookupResult result = runtimeFactLookupService.lookup(adventure.ownerPlayerId().value(),
                    new RuntimeFactLookupRequest(command.action(), adventure.gameState(), adventure.runtimeAddedFacts(),
                            scenarioModel), evidencePack.storybook());
            return List.of(result);
        } catch (RuntimeException failure) {
            if (!isRecoverableLookupFailure(failure)) throw failure;
            LOGGER.warn("runtime_fact_lookup_unavailable actionIntent=PLAYER_ACTION");
            return List.of(RuntimeFactLookupResult.notFound());
        }
    }

    private static EvidencePack withoutUnrevealedRevelationEvidence(EvidencePack evidencePack,
            java.util.Set<ScenarioSourceReference> unrevealedSourceRefs) {
        if (unrevealedSourceRefs.isEmpty()) return evidencePack;
        List<RuntimeEvidence> storybook = evidencePack.storybook().stream()
                .filter(evidence -> !matchesAnySourceRef(evidence, unrevealedSourceRefs)).toList();
        return new EvidencePack(storybook, evidencePack.rulebook(), evidencePack.resolution());
    }

    private static List<RuntimeFactLookupResult> withoutUnrevealedRevelationLookupResults(
            List<RuntimeFactLookupResult> results, java.util.Set<ScenarioSourceReference> unrevealedSourceRefs,
            java.util.Set<String> unrevealedElementIds) {
        if (results == null || results.isEmpty()) return List.of();
        return results.stream().filter(result -> result.supportingElementIds().stream()
                        .noneMatch(unrevealedElementIds::contains))
                .filter(result -> result.evidence().stream()
                        .noneMatch(evidence -> matchesAnySourceRef(evidence, unrevealedSourceRefs)))
                .toList();
    }

    private static boolean matchesAnySourceRef(RuntimeEvidence evidence,
            java.util.Set<ScenarioSourceReference> sourceRefs) {
        return sourceRefs.stream().anyMatch(reference -> reference.knowledgeDocumentId().equals(evidence.knowledgeDocumentId())
                && reference.extractionVersion() == evidence.extractionVersion()
                && reference.locator().equals(evidence.locator()));
    }

    private static void validateCheckProposal(RuntimeCheckProposal check, EvidencePack evidencePack,
            Adventure adventure, SubmitRuntimeTurnCommand command) {
        if (!check.required()) return;
        if (check.rollMethod() == RuntimeCheckProposal.RollMethod.PLAYER && command.gmOnly()) {
            LOGGER.error("player_check_contract_rejected turnId={} reason=GM_ONLY_TURN rollMethod={} diceExpression={} "
                            + "abilityOrSkill={} difficulty={} modifier={} characterSheetId={} evidenceKeys={}",
                    command.turnId(), check.rollMethod(), check.diceExpression(), check.abilityOrSkill(),
                    check.difficulty(), check.modifier(), check.characterSheetId(), check.evidenceKeys());
            throw new IllegalArgumentException("player check cannot be requested for a GM-only turn");
        }
        boolean characterExists = adventure.party().stream().anyMatch(member ->
                member.characterSheetId().value().equals(check.characterSheetId()));
        if (!characterExists || (command.turnCharacterSheetId() != null
                && !command.turnCharacterSheetId().value().equals(check.characterSheetId()))) {
            throw new IllegalArgumentException("check proposal targets a character outside the current party turn");
        }
        java.util.Set<String> evidenceKeys = evidencePack.all().stream()
                .filter(evidence -> evidence.evidenceType() == RuntimeEvidenceType.RULEBOOK
                        || evidence.evidenceType() == RuntimeEvidenceType.STORYBOOK)
                .map(RuntimeEvidence::referenceKey).collect(java.util.stream.Collectors.toSet());
        if (!evidenceKeys.containsAll(check.evidenceKeys())) {
            throw new IllegalArgumentException("check proposal cites rules evidence outside the turn evidence pack");
        }
    }

    private List<UUID> knowledgeDocumentIds(Adventure adventure, ScenarioPackage scenarioPackage) {
        SessionKnowledgeSet set = sessionKnowledgeSetRepository.findBySessionId(adventure.sessionId())
                .orElseGet(() -> new SessionKnowledgeSet(adventure.sessionId(), List.of()));
        if (!set.sessionId().equals(adventure.sessionId())) {
            throw new IllegalStateException("session knowledge set does not match adventure");
        }
        if (!set.knowledgeDocumentIds().isEmpty()) {
            return set.knowledgeDocumentIds().stream().map(id -> id.value()).toList();
        }
        return scenarioPackage.documents().stream()
                .map(document -> document.knowledgeDocumentId().value())
                .distinct()
                .toList();
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null ? "" : message.replaceAll("[\\r\\n]", " ");
    }

    private static String renderOutcome(RuntimeCommandOutcome outcome) {
        String value = outcome.value() == null ? "" : outcome.value().trim();
        return value.isBlank() ? outcome.status().name() : outcome.status().name() + ": " + value;
    }

    static void logCombatGroundingRejected(IllegalArgumentException failure, int proposalCount, int rulesEvidenceCount) {
        String category = switch (failure.getMessage() == null ? "" : failure.getMessage()) {
            case "COMBAT_SCENARIO_ACTIVE_MISMATCH", "COMBAT_SCENARIO_DUPLICATE", "COMBAT_SCENARIO_ENEMY_MISMATCH",
                    "COMBAT_SCENARIO_NOT_IN_SCENARIO_MODEL", "COMBAT_SCENARIO_REFERENCE_REQUIRED",
                    "COMBAT_SCENARIO_REQUIRED", "COMBAT_STAT_BLOCK_NOT_FOUND", "COMBAT_SCENARIO_NOT_IN_CURRENT_SITUATION" ->
                    failure.getMessage();
            default -> "UNKNOWN_GROUNDING_FAILURE";
        };
        LOGGER.warn("combat grounding rejected category={} proposalCount={} rulesEvidenceCount={}",
                category, proposalCount, rulesEvidenceCount);
    }
}
