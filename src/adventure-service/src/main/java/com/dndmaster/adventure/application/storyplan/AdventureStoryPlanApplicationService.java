package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStatus;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanGraphValidator;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.Set;
import java.util.UUID;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.ArrayList;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.UUID;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioSourceExcerptPort;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;

public final class AdventureStoryPlanApplicationService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AdventureStoryPlanApplicationService.class);
    private final AdventureStoryPlanRepository plans;
    private final AdventureSessionRepository sessions;
    private final ScenarioPackageRepository packages;
    private final ScenarioBundleRepository bundles;
    private final ScenarioSourceExcerptPort sourceExcerptPort;
    private final AdventureStoryPlanGenerationPort generator;
    private final AdventureStoryPlanStageSourceValidator stageSourceValidator = new AdventureStoryPlanStageSourceValidator();
    private final TacticalScenePlanValidator tacticalSceneValidator = new TacticalScenePlanValidator();

    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions) {
        this(plans, sessions, null, request -> defaultStages(request.configuration()));
    }
    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator) {
        this(plans, sessions, packages, generator, null, null);
    }
    public AdventureStoryPlanApplicationService(AdventureStoryPlanRepository plans, AdventureSessionRepository sessions,
            ScenarioPackageRepository packages, AdventureStoryPlanGenerationPort generator,
            ScenarioBundleRepository bundles, ScenarioSourceExcerptPort sourceExcerptPort) {
        this.plans = Objects.requireNonNull(plans); this.sessions = Objects.requireNonNull(sessions);
        this.packages = packages; this.generator = Objects.requireNonNull(generator);
        this.bundles = bundles; this.sourceExcerptPort = sourceExcerptPort;
    }

    public AdventureStoryPlan read(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner);
        return plans.findBySessionId(sessionId).orElseThrow(() -> new IllegalStateException("adventure story plan not found"));
    }
    public List<AdventureStoryPlan> readHistory(SessionId sessionId, OwnerPlayerId owner) {
        requireSession(sessionId, owner); return plans.readHistory(sessionId);
    }

    public AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner) {
        return generate(sessionId, owner, AdventurePlanConfiguration.defaults());
    }

    public AdventureStoryPlan generate(SessionId sessionId, OwnerPlayerId owner, AdventurePlanConfiguration configuration) {
        AdventureSession session = requireSession(sessionId, owner);
        validateParty(session);
        AdventureStoryPlan previous = plans.findBySessionId(sessionId).orElse(null);
        long version = previous == null ? 1 : previous.version() + 1;
        ScenarioPackage scenarioPackage = packages == null ? null : packages.findById(session.scenarioPackageId()).orElse(null);
        if (scenarioPackage != null && scenarioPackage.report().status() != com.dndmaster.adventure.domain.scenario.ResolutionStatus.COMPLETE) {
            throw new IllegalStateException("story plan requires a COMPLETE scenario package report");
        }
        AdventureStoryPlanGenerationPort.Request request = new AdventureStoryPlanGenerationPort.Request(
                UUID.randomUUID().toString(), session.scenarioPackageRevision(), session.party().size(),
                configuration, sourceDocuments(session), resolutionEvidence(session), mapContexts(scenarioPackage), citations(session, scenarioPackage));
        List<AdventureStoryPlanStage> stages = List.of();
        List<String> outlineViolations = List.of();
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                stages = generator.generate(request);
            } catch (RuntimeException providerFailure) {
                LOGGER.error("story plan generation failed; no fallback will be persisted", providerFailure);
                throw providerFailure;
            }
            validateMaps(stages, request.maps());
            outlineViolations = validateStageSources(stages, request.citations());
            if (outlineViolations.isEmpty()) break;
            if (attempt == 3) {
                AdventureStoryPlan blocked = AdventureStoryPlan.blocked(
                        previous == null ? UUID.randomUUID() : previous.planId(), session.id(),
                        session.scenarioPackageRevision(), session.version(), version, configuration, stages,
                        String.join("; ", outlineViolations));
                plans.save(blocked);
                return blocked;
            }
            request = request.withViolations(outlineViolations);
        }
        AdventureStoryPlanGraphValidator.validate(stages, configuration);
        try {
            stages = generateTacticalScenes(stages, request);
        } catch (TacticalScenePlanBlockedException blocked) {
            AdventureStoryPlan plan = AdventureStoryPlan.blocked(previous == null ? java.util.UUID.randomUUID() : previous.planId(), session.id(),
                    session.scenarioPackageRevision(), session.version(), version, configuration, stages, blocked.getMessage());
            plans.save(plan);
            return plan;
        }
        AdventureStoryPlan plan = AdventureStoryPlan.ready(
                previous == null ? java.util.UUID.randomUUID() : previous.planId(), session.id(),
                session.scenarioPackageRevision(), session.version(), version, configuration, stages);
        plans.save(plan);
        return plan;
    }

    public AdventureStoryPlan retry(SessionId sessionId, OwnerPlayerId owner) {
        return generate(sessionId, owner);
    }

    public boolean isReadyFor(AdventureSession session) {
        return plans.findBySessionId(session.id())
                .map(plan -> plan.status() == AdventureStoryPlanStatus.READY
                        && plan.packageRevision() == session.scenarioPackageRevision()
                        && plan.partyRevision() == session.version())
                .orElse(false);
    }

    private List<AdventureStoryPlanStage> generateTacticalScenes(List<AdventureStoryPlanStage> stages,
            AdventureStoryPlanGenerationPort.Request request) {
        java.util.Map<UUID, AdventureStoryPlanGenerationPort.MapContext> maps = request.maps().stream()
                .collect(java.util.stream.Collectors.toMap(AdventureStoryPlanGenerationPort.MapContext::mapDefinitionId, item -> item));
        List<AdventureStoryPlanStage> result = new ArrayList<>();
        for (AdventureStoryPlanStage stage : stages) {
            if (stage.mapDefinitionId() == null) { result.add(stage); continue; }
            AdventureStoryPlanGenerationPort.MapContext map = maps.get(stage.mapDefinitionId());
            if (map == null) throw new IllegalStateException("story plan references an unknown tactical map");
            List<String> violations = List.of();
            for (int attempt = 1; attempt <= 3; attempt++) {
                TacticalScenePlanCandidate candidate;
                try {
                    var tacticalRequest = new TacticalSceneRequest(stage, map, request.citations(), violations);
                    candidate = generator.generateTacticalScene(tacticalRequest);
                    violations = tacticalSceneValidator.validate(tacticalRequest, candidate);
                } catch (RuntimeException failure) {
                    violations = List.of("tactical scene generation failed: " + failure.getClass().getSimpleName()
                            + (failure.getMessage() == null || failure.getMessage().isBlank() ? "" : ": " + failure.getMessage()));
                    candidate = null;
                }
                if (violations.isEmpty()) {
                    result.add(stage.withTacticalScenePlan(candidate.scene()));
                    break;
                }
                if (attempt == 3) throw new TacticalScenePlanBlockedException(violations);
            }
        }
        return List.copyOf(result);
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
        try {
            sourceExcerptPort.load(bundle).stream().map(ResolutionExtractionPort.SourceExcerpt::text)
                    .filter(s -> s != null && !s.isBlank()).limit(12).forEach(evidence::add);
        } catch (RuntimeException ignored) {
            // Existing compiled evidence remains usable if the retrieval gateway is unavailable.
        }
    }

    private List<AdventureStoryPlanGenerationPort.MapContext> mapContexts(ScenarioPackage scenarioPackage) {
        if (scenarioPackage == null) return List.of();
        List<String> related = new ArrayList<>();
        if (bundles != null && sourceExcerptPort != null) {
            bundles.findById(scenarioPackage.bundleId()).ifPresent(bundle -> {
                try {
                    sourceExcerptPort.load(bundle).stream()
                            .map(ResolutionExtractionPort.SourceExcerpt::text)
                            .filter(text -> text != null && text.matches("(?is).*\\b(cellar|corridor|tower|dungeon|brewery|trap|staircase)\\b.*"))
                            .limit(8).forEach(text -> related.add(text.replaceAll("\\s+", " ").trim()));
                } catch (RuntimeException ignored) { }
            });
        }
        return scenarioPackage.mapDefinitions().stream().filter(com.dndmaster.adventure.domain.scenario.MapDefinition::autoActivatable).map(map -> new AdventureStoryPlanGenerationPort.MapContext(
                map.id(), map.assetId(), map.assetLocator(), map.source().locator(), map.confidence(), map.safetyStatus().name(), List.copyOf(related))).toList();
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
                java.util.Map<UUID, String> bundleTypes = bundle.currentRevision().documents().stream().collect(java.util.stream.Collectors.toMap(
                        document -> document.knowledgeDocumentId().value(), document -> document.documentType(), (left, right) -> left));
                sourceExcerptPort.load(bundle).stream().limit(12).forEach(excerpt -> result.add(new AdventureStoryPlanGenerationPort.SourceCitation(
                        bundleTypes.getOrDefault(excerpt.documentId().value(), "STORYBOOK"), excerpt.documentId().value(),
                        excerpt.extractionVersion(), excerpt.locator(), excerpt.text(), .9)));
            });
        }
        return result.stream().filter(java.util.Objects::nonNull).distinct().limit(20).toList();
    }

    private static AdventureStoryPlanGenerationPort.SourceCitation citation(ScenarioResolutionUnit unit, ScenarioSourceReference reference, String documentType) {
        if (documentType == null || unit.sourceQuote().isBlank()) return null;
        return new AdventureStoryPlanGenerationPort.SourceCitation(documentType, reference.knowledgeDocumentId().value(),
                reference.extractionVersion(), reference.locator(), unit.sourceQuote(), unit.status().name().equals("COMPLETE") ? 1.0 : .5);
    }

    private static void validateMaps(List<AdventureStoryPlanStage> stages, List<AdventureStoryPlanGenerationPort.MapContext> maps) {
        Set<UUID> known = maps.stream().map(AdventureStoryPlanGenerationPort.MapContext::mapDefinitionId).collect(java.util.stream.Collectors.toSet());
        stages.stream().map(AdventureStoryPlanStage::mapDefinitionId).filter(java.util.Objects::nonNull).forEach(id -> {
            if (!known.contains(id)) throw new IllegalStateException("story plan references an unknown map definition");
        });
    }

    private List<String> validateStageSources(
            List<AdventureStoryPlanStage> stages,
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations) {
        List<String> violations = new ArrayList<>();
        for (AdventureStoryPlanStage stage : stages) {
            if (stage.mapDefinitionId() == null) continue;
            violations.addAll(stageSourceValidator.validate(stage, citations));
        }
        return List.copyOf(violations);
    }

    private static AdventureStoryPlanStage stage(int position, String title, String goal, String conflict, String transition, String ending) {
        return new AdventureStoryPlanStage(position, title, goal, conflict, transition, List.of(), List.of(ending));
    }

    private static final class TacticalScenePlanBlockedException extends RuntimeException {
        private TacticalScenePlanBlockedException(List<String> violations) {
            super(String.join("; ", violations));
        }
    }
}
