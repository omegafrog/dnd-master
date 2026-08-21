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
import java.util.function.BiConsumer;
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
        AdventureStoryPlanGenerationPort.Request request = new AdventureStoryPlanGenerationPort.Request(
                UUID.randomUUID().toString(), session.scenarioPackageRevision(), session.party().size(),
                configuration, sourceDocuments(session), resolutionEvidence(session), mapContexts(scenarioPackage), citations(session, scenarioPackage))
                .withPreviousCandidate(previous == null ? "" : previous.stages().toString());
        progress.accept(25, "모험 개요 생성 중");
        List<AdventureStoryPlanStage> stages = List.of();
        List<String> outlineViolations = List.of();
        for (int attempt = 1; attempt <= 5; attempt++) {
            progress.accept(Math.min(85, 25 + ((attempt - 1) * 15)),
                    attempt == 1 ? "모험 개요 생성 중" : "모험 개요 재생성 중 (재시도 " + attempt + "/5)");
            try {
                List<AdventureStoryPlanStage> candidateStages = generator.generate(request);
                if (candidateStages == null) {
                    throw new AdventureStoryPlanCandidateValidationException(
                            List.of("AI returned no story stages"));
                }
                stages = candidateStages;
            } catch (AdventureStoryPlanCandidateValidationException invalidCandidate) {
                outlineViolations = invalidCandidate.violations();
            } catch (RuntimeException providerFailure) {
                LOGGER.error("story plan generation failed; no fallback will be persisted", providerFailure);
                throw providerFailure;
            }
            if (outlineViolations.isEmpty()) {
                try {
                    validateMaps(stages, request.maps());
                    outlineViolations = validateStageSources(stages, request.citations(), scenarioPackage);
                    if (outlineViolations.isEmpty()) {
                        AdventureStoryPlanGraphValidator.validate(stages, configuration);
                    }
                } catch (RuntimeException invalidCandidate) {
                    outlineViolations = List.of(candidateValidationMessage(invalidCandidate));
                }
            }
            if (outlineViolations.isEmpty()) break;
            LOGGER.warn("story_plan_candidate_rejected attempt={} violations={}", attempt, outlineViolations);
            progress.accept(Math.min(90, 30 + (attempt * 12)),
                    "계획 검증 실패, 재시도 준비 중 (" + attempt + "/5)");
            if (attempt == 5) {
                AdventureStoryPlan blocked = AdventureStoryPlan.blocked(
                        previous == null ? UUID.randomUUID() : previous.planId(), session.id(),
                        session.scenarioPackageRevision(), session.version(), version, configuration, stages,
                        String.join("; ", outlineViolations));
                plans.save(blocked);
                return blocked;
            }
            request = request.withViolations(outlineViolations);
            outlineViolations = List.of();
        }
        AdventureStoryPlan plan = AdventureStoryPlan.ready(
                previous == null ? java.util.UUID.randomUUID() : previous.planId(), session.id(),
                session.scenarioPackageRevision(), session.version(), version, configuration, stages);
        plans.save(plan);
        progress.accept(100, "플레이 준비 완료");
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
                                    excerpt.text().replaceAll("\\s+", " ").trim(), .9)));
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
                sourceExcerptPort.load(bundle).stream().limit(12).forEach(excerpt -> result.add(new AdventureStoryPlanGenerationPort.SourceCitation(
                        excerpt.documentType(), excerpt.documentId().value(),
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

    private static String candidateValidationMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
                ? "story plan candidate validation failed: " + failure.getClass().getSimpleName()
                : message;
    }

    private List<String> validateStageSources(
            List<AdventureStoryPlanStage> stages,
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations,
            ScenarioPackage scenarioPackage) {
        List<String> violations = new ArrayList<>();
        Set<UUID> mapDocumentIds = scenarioPackage == null ? Set.of() : scenarioPackage.documents().stream()
                .filter(document -> document.role() == com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole.MAP)
                .map(document -> document.knowledgeDocumentId().value())
                .collect(java.util.stream.Collectors.toSet());
        for (AdventureStoryPlanStage stage : stages) {
            // The execution projection may intentionally omit evidence and mark
            // connective details as AI suggestions. Only explicit evidence is
            // authoritative enough for source-claim validation here; map
            // identity is still checked separately by validateMaps().
            if (stage.mapDefinitionId() == null || stage.evidence().isEmpty()) continue;
            violations.addAll(stageSourceValidator.validate(stage, citations, mapDocumentIds));
        }
        return List.copyOf(violations);
    }

    private static AdventureStoryPlanStage stage(int position, String title, String goal, String conflict, String transition, String ending) {
        return new AdventureStoryPlanStage(position, title, goal, conflict, transition, List.of(), List.of(ending));
    }

}
