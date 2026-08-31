package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanGraphValidator;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.adventure.SourceConstraint;
import com.dndmaster.adventure.domain.adventure.SourceConstraintPack;
import com.dndmaster.adventure.domain.adventure.StoryPlanGenerationMode;
import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanProjectionViolation.Repairability;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.dndmaster.adventure.application.runtime.EvidencePack;
import com.dndmaster.adventure.application.runtime.RuntimeEvidence;
import com.dndmaster.adventure.application.runtime.RuntimeEvidenceType;
import com.dndmaster.adventure.domain.adventure.RetrievalScope;
import com.dndmaster.adventure.domain.adventure.SemanticVerdict;

public final class AdventureStoryPlanApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdventureStoryPlanApplicationService.class);
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;
    private final ScenarioPackageRepository packages;
    private final ScenarioBundleRepository bundles;
    private final ScenarioSourceExcerptPort sourceExcerptPort;
    private final AdventureStoryPlanGenerationPort generator;
    private final AdventureStoryPlanStageSourceValidator stageSourceValidator = new AdventureStoryPlanStageSourceValidator();
    private final AdventureStoryPlanCombatValidator combatValidator = new AdventureStoryPlanCombatValidator();
    private final StoryPlanStructuralGuard structuralGuard = new StoryPlanStructuralGuard();
    private final ObjectMapper projectionMapper = new ObjectMapper();
    private final StoryPlanSemanticConsistencyJudge semanticJudge;
    private final StoryPlanScopedMerger scopedMerger = new StoryPlanScopedMerger(projectionMapper);
    private final RepairScopeResolver repairScopeResolver = new RepairScopeResolver();

    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions) {
        this(plans, sessions, null, request -> AdventureStoryPlanGenerationPort.ProjectionCandidate
                .fromStages(defaultStages(request.configuration())));
    }
    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator) {
        this(plans, sessions, packages, generator, null, null);
    }
    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator,
            ScenarioBundleRepository bundles, ScenarioSourceExcerptPort sourceExcerptPort) {
        this(plans, sessions, packages, generator, bundles, sourceExcerptPort, null);
    }
    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator,
            ScenarioBundleRepository bundles, ScenarioSourceExcerptPort sourceExcerptPort,
            StoryPlanSemanticConsistencyJudge semanticJudge) {
        this.plans = Objects.requireNonNull(plans); this.sessions = Objects.requireNonNull(sessions);
        this.packages = packages; this.generator = Objects.requireNonNull(generator);
        this.bundles = bundles; this.sourceExcerptPort = sourceExcerptPort;
        this.semanticJudge = semanticJudge;
    }

    public AdventureStoryPlan read(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner);
        return plans.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
    }
    public List<AdventureStoryPlan> readHistory(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner); return plans.readHistory(sessionId);
    }
    public List<AdventureStoryPlanHistoryEntry> readHistoryEntries(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner); return plans.readHistoryEntries(sessionId);
    }

    public AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner) {
        return generate(sessionId, owner, AdventurePlanConfiguration.defaults());
    }

    public AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner, AdventurePlanConfiguration configuration) {
        return generate(sessionId, owner, configuration, (progress, stage) -> { });
    }

    public AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner, AdventurePlanConfiguration configuration,
            BiConsumer<Integer, String> progress) {
        AdventureSession session = requireSession(sessionId, owner);
        progress.accept(15, "파티와 모험 자료 확인");
        if (session.startedAdventureId() != null) {
            throw new IllegalStateException("story plan generation is not allowed after adventure start; use future-stage revision");
        }
        validateParty(session);
        AdventureStoryPlan previous = plans.findBySessionId(sessionId).orElse(null);
        long version = previous == null ? 1 : previous.version() + 1;
        ScenarioPackage scenarioPackage = packages == null ? null : packages.findById(session.scenarioPackageId()).orElse(null);
        if (scenarioPackage != null && scenarioPackage.report().status() != com.dndmaster.adventure.domain.scenario.ResolutionStatus.COMPLETE) {
            throw new IllegalStateException("story plan requires a COMPLETE scenario package report");
        }
        if (scenarioPackage != null && bundles != null && sourceExcerptPort != null) {
            ScenarioSourceBundle sourceBundle = bundles.findById(scenarioPackage.bundleId())
                    .orElseThrow(() -> new IllegalStateException("scenario source bundle not found"));
            try {
                if (planExcerpts(sourceBundle).stream().noneMatch(excerpt ->
                        "STORYBOOK".equalsIgnoreCase(excerpt.documentType())
                                || "RULEBOOK".equalsIgnoreCase(excerpt.documentType()))) {
                    throw new IllegalStateException("story plan requires published evidence");
                }
            } catch (IllegalStateException failure) {
                throw failure;
            } catch (RuntimeException failure) {
                throw new IllegalStateException("published scenario evidence is unavailable", failure);
            }
        }
        List<AdventureStoryPlanGenerationPort.SourceCitation> authoritativeCitations = citations(session, scenarioPackage);
        AdventureStoryPlanGenerationPort.Request request = new AdventureStoryPlanGenerationPort.Request(
                UUID.randomUUID().toString(), session.scenarioPackageRevision(), session.party().size(),
                configuration, sourceDocuments(session), resolutionEvidence(session), mapContexts(scenarioPackage), authoritativeCitations,
                List.of(), "", StoryPlanGenerationMode.fromDocumentTypes(authoritativeCitations.stream()
                        .map(AdventureStoryPlanGenerationPort.SourceCitation::documentType).toList()),
                constraintPack(authoritativeCitations))
                .withCitationKeys()
                .withPreviousCandidate("");
        LOGGER.info("story_plan_generation_input packageId={} citations={} resolutionEvidence={} maps={} sourceDocuments={}",
                session.scenarioPackageId(), request.citations().size(), request.resolutionEvidence().size(),
                request.maps().size(), request.sourceDocuments().size());
        LOGGER.info("story_plan_generation_citation_keys={}", request.citations().stream()
                .map(item -> item.documentType() + ":" + item.documentId() + ":" + item.extractionVersion() + ":" + item.locator())
                .toList());
        progress.accept(25, "모험 개요 생성 중");
        List<AdventureStoryPlanStage> stages = List.of();
        List<AdventureStoryPlanProjectionViolation> outlineViolations = List.of();
        String rejectedCandidate = "";
        String lastFailureFingerprint = "";
        int totalAttempts = 0;
        int regenerationCount = 0;
        int repairsOnCandidate = 0;
        boolean repairNext = false;
        String stopReason = "";
        List<AdventureStoryPlanProjectionViolation> accumulatedViolations = List.of();
        List<SemanticVerdict> semanticVerdicts = new ArrayList<>();
        while (totalAttempts < 5) {
            int attempt = totalAttempts + 1;
            progress.accept(Math.min(85, 25 + ((attempt - 1) * 15)),
                    repairNext ? "모험 개요 제한 복구 중 (시도 " + attempt + "/5)"
                            : attempt == 1 ? "모험 개요 생성 중" : "모험 개요 재생성 중 (재시도 " + attempt + "/5)");
            String candidateForValidation = rejectedCandidate;
            List<AdventureStoryPlanProjectionViolation> activeViolations = outlineViolations;
            outlineViolations = List.of();
            try {
                totalAttempts++;
                if (repairNext) {
                    RepairScope repairScope = repairScopeResolver.resolve(rejectedCandidate, accumulatedViolations);
                    logAttempt(request, AttemptType.REPAIR, totalAttempts, repairScope, accumulatedViolations, "STARTED");
                    LOGGER.info("story_plan_projection_repair_scope operationId={} attempt={} blockers={} dependents={} repairable={}",
                            request.operationId(), attempt,
                            repairScope.blockerPaths(), repairScope.dependentPaths(), repairScope.isRepairable());
                    AdventureStoryPlanGenerationPort.ProjectionCandidate repaired = generator.repair(
                            new AdventureStoryPlanGenerationPort.RepairRequest(
                                    request.operationId(), request.packageRevision(), request.partySize(), configuration,
                                    rejectedCandidate, accumulatedViolations, repairScope, request.sourceDocuments(), request.resolutionEvidence(),
                                    request.maps(), request.citations()));
                    if (repaired == null) throw new AdventureStoryPlanCandidateValidationException(
                            List.of("repair returned no full story plan candidate"), rejectedCandidate);
                    candidateForValidation = repaired.serializedCandidate();
                    if (!rejectedCandidate.isBlank()) {
                        candidateForValidation = scopedMerger.merge(rejectedCandidate, candidateForValidation, repairScope).toString();
                    }
                    stages = readMergedStages(candidateForValidation);
                    repairNext = false;
                } else {
                    boolean initialGeneration = totalAttempts == 1 && regenerationCount == 0;
                    if (!initialGeneration && regenerationCount >= 1) {
                        outlineViolations = activeViolations;
                        stopReason = "REGENERATION_BUDGET_EXHAUSTED";
                        break;
                    }
                    if (!initialGeneration) regenerationCount++;
                    logAttempt(request, initialGeneration ? AttemptType.INITIAL_GENERATION : AttemptType.FULL_REGENERATION,
                            totalAttempts, null, accumulatedViolations, "STARTED");
                    AdventureStoryPlanGenerationPort.ProjectionCandidate generated = generator.generate(request);
                    if (generated == null) throw new AdventureStoryPlanCandidateValidationException(
                            List.of("AI returned no full story plan candidate"));
                    candidateForValidation = generated.serializedCandidate();
                    stages = generated.stages();
                }
                List<AdventureStoryPlanProjectionViolation> deterministicViolations = validateCandidate(
                        stages, request, scenarioPackage, configuration);
                if (!deterministicViolations.isEmpty()) {
                    throw new AdventureStoryPlanCandidateValidationException(
                            deterministicViolations, candidateForValidation, true);
                }
                if (semanticJudge != null) {
                    SemanticVerdict verdict = semanticJudge.judge(
                            evidencePack(request), candidateForValidation);
                    semanticVerdicts.add(verdict);
                    StoryPlanVerdictPolicy.Decision decision = StoryPlanVerdictPolicy.decide(
                            verdict, totalAttempts, 5);
                    if (decision == StoryPlanVerdictPolicy.Decision.RETRY
                            || decision == StoryPlanVerdictPolicy.Decision.BLOCK) {
                        throw new AdventureStoryPlanCandidateValidationException(
                                List.of(semanticViolation(verdict)), candidateForValidation, true);
                    }
                    if (decision == StoryPlanVerdictPolicy.Decision.READY_WITH_WARNING) {
                        LOGGER.warn("story_plan_semantic_verdict operationId={} attempt={} verdict={} confidence={} claimPath={}",
                                request.operationId(), totalAttempts, verdict.type(), verdict.confidence(), verdict.claimPath());
                    } else {
                        LOGGER.info("story_plan_semantic_verdict operationId={} attempt={} verdict={} confidence={} claimPath={}",
                                request.operationId(), totalAttempts, verdict.type(), verdict.confidence(), verdict.claimPath());
                    }
                }
                break;
            } catch (AdventureStoryPlanCandidateValidationException invalidCandidate) {
                outlineViolations = invalidCandidate.structuredViolations();
                accumulatedViolations = appendStructuredViolations(accumulatedViolations, outlineViolations);
                if (invalidCandidate.hasRejectedCandidate()) rejectedCandidate = invalidCandidate.rejectedCandidate();
            } catch (AdventureStoryPlanProjectionRepairPolicy.UnlistedFieldMutation invalidRepair) {
                outlineViolations = List.of(invalidRepair.violation());
                accumulatedViolations = appendStructuredViolations(accumulatedViolations, outlineViolations);
                rejectedCandidate = candidateForValidation;
            } catch (IllegalArgumentException invalidScopeOrMerge) {
                outlineViolations = List.of(new AdventureStoryPlanProjectionViolation(
                        "SCOPED_MERGE_UNAVAILABLE", null, "stages", "", "", Repairability.REGENERATE_REQUIRED,
                        "story plan scoped repair could not be safely merged"));
                accumulatedViolations = appendStructuredViolations(accumulatedViolations, outlineViolations);
            } catch (RuntimeException providerFailure) {
                LOGGER.error("story plan generation failed; no fallback will be persisted providerFailureType={}",
                        providerFailure.getClass().getSimpleName());
                throw providerFailure;
            }
            LOGGER.warn("story_plan_candidate_rejected attempt={} codes={} classifications={}", totalAttempts,
                    outlineViolations.stream().map(AdventureStoryPlanProjectionViolation::code).toList(),
                    outlineViolations.stream().map(AdventureStoryPlanProjectionViolation::repairability).toList());
            progress.accept(Math.min(90, 30 + (totalAttempts * 12)),
                    "계획 검증 실패, 재시도 준비 중 (" + totalAttempts + "/5)");
            String failureFingerprint = AdventureStoryPlanProjectionRepairPolicy.fingerprint(rejectedCandidate, outlineViolations);
            if (failureFingerprint.equals(lastFailureFingerprint)) {
                stopReason = "NO_PROGRESS";
                break;
            }
            lastFailureFingerprint = failureFingerprint;
            if (outlineViolations.stream().anyMatch(item -> item.repairability() == Repairability.SOURCE_EVIDENCE_INSUFFICIENT
                    || item.repairability() == Repairability.SYSTEM_CONTRACT_ERROR)) {
                stopReason = outlineViolations.stream().anyMatch(item -> item.repairability() == Repairability.SOURCE_EVIDENCE_INSUFFICIENT)
                        ? "SOURCE_EVIDENCE_INSUFFICIENT" : "SYSTEM_CONTRACT_ERROR";
                break;
            }
            boolean regenerationRequired = outlineViolations.stream()
                    .anyMatch(item -> item.repairability() == Repairability.REGENERATE_REQUIRED);
            if (regenerationRequired) {
                if (regenerationCount >= 1 || totalAttempts >= 5) {
                    stopReason = "REGENERATION_BUDGET_EXHAUSTED";
                    break;
                }
                request = request.withViolations(outlineViolations.stream()
                        .map(AdventureStoryPlanProjectionViolation::sanitizedMessage).toList()).withPreviousCandidate("");
                rejectedCandidate = "";
                repairsOnCandidate = 0;
                repairNext = false;
                continue;
            }
            if (outlineViolations.stream().allMatch(item -> item.repairability() == Repairability.REPAIRABLE)
                    && !rejectedCandidate.isBlank() && repairsOnCandidate < 2 && totalAttempts < 5) {
                repairsOnCandidate++;
                repairNext = true;
                continue;
            }
            if (outlineViolations.stream().allMatch(item -> item.repairability() == Repairability.REPAIRABLE)
                    && !rejectedCandidate.isBlank() && repairsOnCandidate >= 2
                    && regenerationCount < 1 && totalAttempts < 5) {
                request = request.withViolations(outlineViolations.stream()
                        .map(AdventureStoryPlanProjectionViolation::sanitizedMessage).toList()).withPreviousCandidate("");
                rejectedCandidate = "";
                repairsOnCandidate = 0;
                repairNext = false;
                continue;
            }
            if (rejectedCandidate.isBlank() && totalAttempts < 5) {
                request = request.withViolations(outlineViolations.stream()
                        .map(AdventureStoryPlanProjectionViolation::sanitizedMessage).toList());
                continue;
            }
            stopReason = totalAttempts >= 5 ? "TOTAL_ATTEMPT_BUDGET_EXHAUSTED" : "REPAIR_BUDGET_EXHAUSTED";
            break;
        }
        if (!outlineViolations.isEmpty()) {
            LOGGER.warn("story_plan_projection_outcome operationId={} outcome=BLOCKED attempts={} repairs={} regenerations={} reason={} violationCodes={}",
                    request.operationId(), totalAttempts, repairsOnCandidate, regenerationCount,
                    stopReason.isBlank() ? "VALIDATION_FAILED" : stopReason,
                    outlineViolations.stream().map(AdventureStoryPlanProjectionViolation::code).toList());
            List<AdventureStoryPlanProjectionViolation> finalViolations = accumulatedViolations.isEmpty()
                    ? outlineViolations : accumulatedViolations;
            AdventureStoryPlan blocked = AdventureStoryPlan.blocked(
                    previous == null ? UUID.randomUUID() : previous.planId(), session.id(),
                    session.scenarioPackageRevision(), session.version(), version, configuration, stages,
                    finalViolations.stream().map(AdventureStoryPlanProjectionViolation::sanitizedMessage)
                            .collect(java.util.stream.Collectors.joining("; ")));
            saveWithSemanticHistory(blocked, semanticVerdicts);
            return blocked;
        }
        AdventureStoryPlan plan = AdventureStoryPlan.ready(
                previous == null ? java.util.UUID.randomUUID() : previous.planId(), session.id(),
                session.scenarioPackageRevision(), session.version(), version, configuration, stages);
        saveWithSemanticHistory(plan, semanticVerdicts);
        LOGGER.info("story_plan_projection_outcome operationId={} outcome=READY attempts={} repairs={} regenerations={}",
                request.operationId(), totalAttempts, repairsOnCandidate, regenerationCount);
        progress.accept(100, "플레이 준비 완료");
        return plan;
    }

    private void saveWithSemanticHistory(AdventureStoryPlan plan, List<SemanticVerdict> verdicts) {
        if (verdicts == null || verdicts.isEmpty()) {
            plans.save(plan);
            return;
        }
        plans.save(plan, "STORY_PLAN_SEMANTIC_VERDICTS:" + StoryPlanVerdictJson.serialize(verdicts));
    }

    private EvidencePack evidencePack(AdventureStoryPlanGenerationPort.Request request) {
        List<RuntimeEvidence> storybook = new ArrayList<>();
        List<RuntimeEvidence> rulebook = new ArrayList<>();
        for (AdventureStoryPlanGenerationPort.SourceCitation citation : request.citations()) {
            if (citation.documentId() == null || citation.quote() == null || citation.quote().isBlank()) continue;
            RuntimeEvidence evidence = new RuntimeEvidence(
                    "STORYBOOK".equalsIgnoreCase(citation.documentType()) ? RuntimeEvidenceType.STORYBOOK
                            : "RULEBOOK".equalsIgnoreCase(citation.documentType()) ? RuntimeEvidenceType.RULEBOOK
                            : RuntimeEvidenceType.RESOLUTION,
                    new com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId(citation.documentId()),
                    citation.extractionVersion(), citation.locator(), citation.quote(), citation.citationKey());
            if (evidence.evidenceType() == RuntimeEvidenceType.STORYBOOK) storybook.add(evidence);
            else if (evidence.evidenceType() == RuntimeEvidenceType.RULEBOOK) rulebook.add(evidence);
        }
        return new EvidencePack(storybook.stream().limit(8).toList(), rulebook.stream().limit(8).toList(), List.of());
    }

    private List<AdventureStoryPlanStage> readMergedStages(String serializedCandidate) {
        try {
            return projectionMapper.readValue(projectionMapper.readTree(serializedCandidate).get("stages").toString(),
                    new TypeReference<List<AdventureStoryPlanStage>>() { });
        } catch (Exception failure) {
            throw new IllegalArgumentException("merged story plan candidate could not be parsed", failure);
        }
    }

    private static AdventureStoryPlanProjectionViolation semanticViolation(SemanticVerdict verdict) {
        String code = "JUDGE_UNAVAILABLE".equals(verdict.failureCode()) ? "JUDGE_UNAVAILABLE" : "SOURCE_CONTRADICTION";
        String message = verdict.summary().replaceAll("[\\r\\n\\t]+", " ").trim();
        if (message.length() > 256) message = message.substring(0, 256) + "...";
        return new AdventureStoryPlanProjectionViolation(code, null, verdict.claimPath(), "", "",
                Repairability.REGENERATE_REQUIRED, message);
    }

    public AdventureStoryPlan retry(SessionId sessionId, OwnerPlayerId owner) {
        return generate(sessionId, owner);
    }

    public boolean isReadyFor(AdventureSession session) {
        return plans.findBySessionId(session.id())
                .map(plan -> plan.status() == AdventureStoryPlanStatus.READY
                        && plan.packageRevision() == session.scenarioPackageRevision()
                        && plan.partyRevision() == session.version()
                        && (session.startedAdventureId() != null
                                || plan.stages().stream().allMatch(stage -> stage.schemaVersion() >= AdventureStoryPlanStage.CURRENT_SCHEMA_VERSION)))
                .orElse(false);
    }

    private AdventureSession requireSession(SessionId id, OwnerPlayerId owner) {
        AdventureSession session = sessions.findById(id).orElseThrow(() -> new IllegalArgumentException("adventure session not found"));
        if (!session.ownerPlayerId().equals(owner)) throw new SecurityException("adventure session access denied");
        return session;
    }

    private static void validateParty(AdventureSession session) {
        if (session.status() != AdventureSession.Status.DRAFT) throw new IllegalStateException("story plan requires a draft session");
        if (session.party().size() < session.characterLimit()) throw new IllegalStateException("party must be complete before story plan generation");
        if (session.party().stream().noneMatch(member -> member.controlMode() == ControlMode.DIRECT)) throw new IllegalStateException("story plan requires a direct player character");
    }

    private static List<AdventureStoryPlanStage> defaultStages() {
        return defaultStages(AdventurePlanConfiguration.defaults());
    }

    private static List<AdventureStoryPlanStage> defaultStages(AdventurePlanConfiguration configuration) {
        int stageCount = switch (configuration.adventureLength()) {
            case SHORT -> 3;
            case STANDARD -> 4;
            case LONG -> 7;
        };
        List<AdventureStoryPlanStage> stages = new ArrayList<>();
        for (int position = 1; position <= stageCount; position++) {
            String title = position == 1 ? "Beginning" : position == stageCount ? "Resolution" : position == stageCount - 1 ? "Confrontation" : "Escalation";
            String ending = "ending-" + (((position - 1) % configuration.endingCount()) + 1);
            stages.add(stage(position, title, "Advance the adventure", "Opposition demands a meaningful choice", "The party reaches the next lead", ending));
        }
        return List.copyOf(stages);
    }

    private List<String> sourceDocuments(AdventureSession session) {
        if (packages == null) return List.of("scenario-package:" + session.scenarioPackageId());
        return packages.findById(session.scenarioPackageId()).map(p -> p.documents().stream().map(d -> d.originalFilename()).toList()).orElse(List.of());
    }
    private List<String> resolutionEvidence(AdventureSession session) {
        List<String> evidence = new ArrayList<>();
        if (packages != null) {
            packages.findById(session.scenarioPackageId()).ifPresent(p -> evidence.addAll(p.units().stream()
                    .map(u -> String.valueOf(u.sourceQuote())).filter(s -> !s.equals("null") && !s.isBlank()).limit(8).toList()));
        }
        if (bundles != null && sourceExcerptPort != null && packages != null) {
            packages.findById(session.scenarioPackageId()).flatMap(p -> bundles.findById(p.bundleId()))
                    .ifPresent(bundle -> appendIndexedExcerpts(evidence, bundle));
        }
        return evidence.stream().filter(s -> s != null && !s.isBlank()).distinct().limit(8).toList();
    }

    private void appendIndexedExcerpts(List<String> evidence, ScenarioSourceBundle bundle) {
        planExcerpts(bundle).stream().map(ResolutionExtractionPort.SourceExcerpt::text)
                .filter(s -> s != null && !s.isBlank()).limit(12).forEach(evidence::add);
    }

    private List<AdventureStoryPlanGenerationPort.MapContext> mapContexts(ScenarioPackage scenarioPackage) {
        if (scenarioPackage == null) return List.of();
        List<AdventureStoryPlanGenerationPort.SourceCitation> related = new ArrayList<>();
        if (bundles != null && sourceExcerptPort != null) {
            bundles.findById(scenarioPackage.bundleId()).ifPresent(bundle -> {
                try {
                    sourceExcerptPort.load(bundle).stream()
                            .filter(excerpt -> excerpt.text() != null && excerpt.text().matches(
                                    "(?is).*\\b(cellar|corridor|tower|dungeon|brewery|trap|staircase)\\b.*"))
                            .limit(8).forEach(excerpt -> related.add(new AdventureStoryPlanGenerationPort.SourceCitation(
                                    excerpt.documentType(),
                                    excerpt.documentId().value(), excerpt.extractionVersion(), excerpt.locator(),
                                    excerpt.text().replaceAll("\\s+", " ").trim(), .9, excerpt.provenance())));
                } catch (RuntimeException ignored) { }
            });
        }
        return scenarioPackage.mapDefinitions().stream().filter(com.dndmaster.adventure.domain.scenario.MapDefinition::autoActivatable).map(map -> {
            String bindings = scenarioPackage.storyMapBindings().stream()
                    .filter(binding -> binding.mapDefinitionId().equals(map.id()))
                    .map(binding -> "stage=" + binding.stage() + ", location=" + binding.location()
                            + ", entryCondition=" + binding.entryCondition())
                    .collect(java.util.stream.Collectors.joining("; "));
            var grid = map.grid();
            String context = "grid origin=(" + grid.originX() + "," + grid.originY() + "), cellSize=" + grid.cellSize()
                    + ", rotation=" + grid.rotation() + ", distance=" + grid.distance()
                    + ", walls=" + map.walls() + ", doors=" + map.doors() + ", obstacles=" + map.obstacles()
                    + ", bindings=[" + bindings + "]";
            return new AdventureStoryPlanGenerationPort.MapContext(map.id(), map.assetId(), map.assetLocator(),
                    map.source().locator(), map.confidence(), map.safetyStatus().name(), List.copyOf(related), context);
        }).toList();
    }

    private List<AdventureStoryPlanGenerationPort.SourceCitation> citations(AdventureSession session, ScenarioPackage scenarioPackage) {
        if (scenarioPackage == null) return List.of();
        java.util.Map<UUID, String> types = scenarioPackage.documents().stream().collect(java.util.stream.Collectors.toMap(
                document -> document.knowledgeDocumentId().value(), document -> document.documentType(), (left, right) -> left));
        List<AdventureStoryPlanGenerationPort.SourceCitation> result = new ArrayList<>(scenarioPackage.units().stream()
                .flatMap(unit -> unit.sourceRefs().stream().map(ref -> citation(unit, ref, types.get(ref.knowledgeDocumentId().value()))))
                .filter(java.util.Objects::nonNull).toList());
        if (bundles != null && sourceExcerptPort != null) {
            bundles.findById(scenarioPackage.bundleId()).ifPresent(bundle -> {
                planExcerpts(bundle).forEach(excerpt -> result.add(new AdventureStoryPlanGenerationPort.SourceCitation(
                        excerpt.documentType(), excerpt.documentId().value(),
                        excerpt.extractionVersion(), excerpt.locator(), excerpt.text(), .9, excerpt.provenance())));
            });
        }
        return result.stream().filter(java.util.Objects::nonNull).distinct().limit(20).toList();
    }

    private List<ResolutionExtractionPort.SourceExcerpt> planExcerpts(ScenarioSourceBundle bundle) {
        List<ResolutionExtractionPort.SourceExcerpt> available = sourceExcerptPort.load(bundle);
        return java.util.stream.Stream.of("STORYBOOK", "RULEBOOK", "MAP")
                .flatMap(type -> available.stream().filter(ResolutionExtractionPort.SourceExcerpt::isPublishedEvidence)
                        .filter(excerpt -> type.equalsIgnoreCase(excerpt.documentType())).limit(8))
                .distinct().limit(20).toList();
    }

    private static AdventureStoryPlanGenerationPort.SourceCitation citation(ScenarioResolutionUnit unit, ScenarioSourceReference reference, String documentType) {
        if (documentType == null || unit.sourceQuote().isBlank()) return null;
        return new AdventureStoryPlanGenerationPort.SourceCitation(documentType, reference.knowledgeDocumentId().value(),
                reference.extractionVersion(), reference.locator(), unit.sourceQuote(), unit.status().name().equals("COMPLETE") ? 1.0 : .5);
    }

    private static List<AdventureStoryPlanProjectionViolation> validateMaps(
            List<AdventureStoryPlanStage> stages, List<AdventureStoryPlanGenerationPort.MapContext> maps) {
        Set<UUID> known = maps.stream().map(AdventureStoryPlanGenerationPort.MapContext::mapDefinitionId).collect(java.util.stream.Collectors.toSet());
        List<AdventureStoryPlanProjectionViolation> violations = new ArrayList<>();
        for (AdventureStoryPlanStage stage : stages) {
            UUID id = stage.mapDefinitionId();
            if (id != null && !known.contains(id)) {
                violations.add(new AdventureStoryPlanProjectionViolation(
                        "UNKNOWN_MAP_DEFINITION", stage.position(), "stages[" + (stage.position() - 1) + "].mapDefinitionId",
                        id.toString(), "authoritative map registry", Repairability.SOURCE_EVIDENCE_INSUFFICIENT,
                        "stage " + stage.position() + " map definition is not registered"));
            }
        }
        return List.copyOf(violations);
    }

    private static SourceConstraintPack constraintPack(List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
        List<SourceConstraint> storybook = new ArrayList<>();
        List<SourceConstraint> rulebook = new ArrayList<>();
        int index = 1;
        for (AdventureStoryPlanGenerationPort.SourceCitation citation : citations) {
            if (citation.quote() == null || citation.quote().isBlank()) continue;
            String key = citation.citationKey() == null || citation.citationKey().isBlank() ? "citation-" + index : citation.citationKey();
            SourceConstraint constraint = new SourceConstraint(key, "source", citation.quote(), List.of(key));
            if ("STORYBOOK".equalsIgnoreCase(citation.documentType())) storybook.add(constraint);
            if ("RULEBOOK".equalsIgnoreCase(citation.documentType())) rulebook.add(constraint);
            index++;
        }
        return new SourceConstraintPack(storybook, rulebook);
    }

    private static String candidateValidationMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? "story plan candidate validation failed: " + failure.getClass().getSimpleName()
                : message;
    }

    private List<AdventureStoryPlanProjectionViolation> validateStageSources(
            List<AdventureStoryPlanStage> stages,
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations,
            ScenarioPackage scenarioPackage) {
        List<AdventureStoryPlanProjectionViolation> violations = new ArrayList<>();
        Set<UUID> mapDocumentIds = scenarioPackage == null ? Set.of() : scenarioPackage.documents().stream()
                .filter(document -> document.role() == com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAP)
                .map(document -> document.knowledgeDocumentId().value())
                .collect(java.util.stream.Collectors.toSet());
        for (AdventureStoryPlanStage stage : stages) {
            if (!citations.isEmpty()) {
                violations.addAll(stageSourceValidator.validateStructured(stage, citations, mapDocumentIds));
            }
        }
        return List.copyOf(violations);
    }

    private List<AdventureStoryPlanProjectionViolation> validateCandidate(
            List<AdventureStoryPlanStage> stages,
            AdventureStoryPlanGenerationPort.Request request,
            ScenarioPackage scenarioPackage,
            AdventurePlanConfiguration configuration) {
        List<AdventureStoryPlanProjectionViolation> violations = new ArrayList<>();
        for (String structural : structuralGuard.validate(stages)) {
            violations.add(new AdventureStoryPlanProjectionViolation(
                    "STRUCTURAL_CONTRACT_VIOLATION", null, "stages", "", "", Repairability.REGENERATE_REQUIRED, structural));
        }
        violations.addAll(validateMaps(stages, request.maps()));
        violations.addAll(validateStageSources(stages, request.citations(), scenarioPackage));
        for (AdventureStoryPlanStage stage : stages) {
            violations.addAll(combatValidator.validate(stage, request.citations()));
            if (stage.schemaVersion() < AdventureStoryPlanStage.CURRENT_SCHEMA_VERSION) {
                violations.add(new AdventureStoryPlanProjectionViolation(
                        "LEGACY_PROJECTION_REQUIRES_REGENERATION", stage.position(),
                        "stages[" + (stage.position() - 1) + "].schemaVersion", "", "",
                        Repairability.REGENERATE_REQUIRED,
                        "new story plan READY writes require projection schema v2"));
            }
        }
        for (String coverage : stageSourceValidator.validateCitationCoverage(stages, request.citations())) {
            String requiredType = coverage.contains("RULEBOOK") ? "RULEBOOK"
                    : coverage.contains("STORYBOOK") ? "STORYBOOK" : "";
            violations.add(new AdventureStoryPlanProjectionViolation(
                    "CITATION_COVERAGE_MISSING", null, "stages[*].evidence[*].citationKey", "",
                    requiredType, Repairability.SOURCE_EVIDENCE_INSUFFICIENT,
                    "required citation coverage is missing"));
        }
        try {
            AdventureStoryPlanGraphValidator.validate(stages, configuration);
        } catch (RuntimeException invalidGraph) {
            violations.add(new AdventureStoryPlanProjectionViolation(
                    "GRAPH_VALIDATION_FAILED", null, "stages", "", "", Repairability.REGENERATE_REQUIRED,
                    sanitizeValidationMessage(invalidGraph.getMessage(), "story plan graph validation failed")));
        }
        return List.copyOf(violations);
    }

    private static AdventureStoryPlanProjectionViolation structuredViolation(String raw) {
        String message = raw == null || raw.isBlank() ? "story plan candidate validation failed" : raw.trim();
        String normalized = message.toLowerCase(java.util.Locale.ROOT);
        Integer stagePosition = stagePosition(message);
        String fieldName = normalized.contains("transitioncondition") ? "transitionCondition"
                : normalized.contains("clearcondition") ? "clearCondition"
                : normalized.contains("failurecondition") ? "failureCondition" : "";
        String fieldPath = fieldName.isBlank() ? "stages"
                : "stages[" + (stagePosition == null ? "*" : Math.max(0, stagePosition - 1)) + "]." + fieldName;
        Repairability repairability;
        if (normalized.contains("unknown source") || normalized.contains("source evidence")
                || normalized.contains("citation") || normalized.contains("provenance") || normalized.contains("map")) {
            repairability = Repairability.SOURCE_EVIDENCE_INSUFFICIENT;
        } else if (!fieldName.isBlank() && normalized.contains("not supported")) {
            repairability = Repairability.SOURCE_EVIDENCE_INSUFFICIENT;
        } else if (!fieldName.isBlank() || normalized.contains("missing") || normalized.contains("required")) {
            repairability = Repairability.REPAIRABLE;
        } else {
            repairability = Repairability.REGENERATE_REQUIRED;
        }
        String safeMessage = message.replaceAll("(?i):\\s*.+$", "").trim();
        return new AdventureStoryPlanProjectionViolation(
                fieldName.isBlank() ? "CANDIDATE_VALIDATION_FAILED" : "UNSUPPORTED_" + fieldName.toUpperCase(java.util.Locale.ROOT),
                stagePosition, fieldPath, "", "", repairability, safeMessage);
    }

    private static String sanitizeValidationMessage(String raw, String fallback) {
        String message = raw == null || raw.isBlank() ? fallback : raw.trim();
        int detailSeparator = message.indexOf(':');
        if (detailSeparator >= 0) message = message.substring(0, detailSeparator).trim();
        return message.isBlank() ? fallback : message;
    }

    private static Integer stagePosition(String message) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?i)stage\\s+(\\d+)").matcher(message);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static List<String> appendViolations(List<String> first, List<String> second) {
        if (first.isEmpty()) return second;
        if (second.isEmpty()) return first;
        List<String> combined = new ArrayList<>(first);
        combined.addAll(second);
        return List.copyOf(combined);
    }

    private void logAttempt(AdventureStoryPlanGenerationPort.Request request, AttemptType type, int attempt,
            RepairScope scope, List<AdventureStoryPlanProjectionViolation> violations, String outcome) {
        LOGGER.info("story_plan_attempt operationId={} attemptType={} attempt={} scope={} violations={} outcome={}",
                request.operationId(), type, attempt, scope == null ? List.of() : scope.allowedPaths(),
                violations.stream().map(AdventureStoryPlanProjectionViolation::code).toList(), outcome);
    }

    private static List<AdventureStoryPlanProjectionViolation> appendStructuredViolations(
            List<AdventureStoryPlanProjectionViolation> first, List<AdventureStoryPlanProjectionViolation> second) {
        List<AdventureStoryPlanProjectionViolation> result = new ArrayList<>(first == null ? List.of() : first);
        for (AdventureStoryPlanProjectionViolation violation : second == null ? List.<AdventureStoryPlanProjectionViolation>of() : second) {
            if (result.stream().noneMatch(existing -> existing.code().equals(violation.code())
                    && existing.fieldPath().equals(violation.fieldPath()))) result.add(violation);
        }
        return List.copyOf(result);
    }

    private enum AttemptType {
        INITIAL_GENERATION, REPAIR, FULL_REGENERATION
    }

    private static AdventureStoryPlanStage stage(int position, String title, String goal, String conflict, String transition, String ending) {
        return new AdventureStoryPlanStage(position, title, goal, conflict, transition, List.of(), List.of(ending));
    }

}
