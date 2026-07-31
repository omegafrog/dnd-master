package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

/** Executes one queued scenario compilation delivery. */
public final class ScenarioCompilationWorker {
    private static final int MAX_ATTEMPTS = 3;
    private static final String WORKER_ID = "scenario-compilation-worker";
    private static final Duration LEASE = Duration.ofMinutes(5);
    private static final Logger log = LoggerFactory.getLogger(ScenarioCompilationWorker.class);

    private final ScenarioCompilationProcessManager processManager;
    private final ScenarioCompilationRepository compilationRepository;
    private final WorkQueuePort queue;
    private final ScenarioBundleRepository bundleRepository;
    private final ResolutionExtractionPort extractionPort;
    private final ScenarioSourceExcerptPort excerptPort;
    private final CharacterContextSearchPort characterContextSearchPort;
    private final CharacterInputTagExtractionPort characterTagPort;
    private final ScenarioPackageCompilationService compiler;

    public ScenarioCompilationWorker(
            ScenarioCompilationProcessManager processManager,
            ScenarioCompilationRepository compilationRepository,
            WorkQueuePort queue,
            ScenarioBundleRepository bundleRepository,
            ResolutionExtractionPort extractionPort,
            ScenarioSourceExcerptPort excerptPort,
            ScenarioPackageCompilationService compiler,
            ScenarioPackageRepository ignoredPackageRepository) {
        this(processManager, compilationRepository, queue, bundleRepository, extractionPort, excerptPort,
                ignored -> List.of(), ignored -> List.of(), compiler, ignoredPackageRepository);
    }

    public ScenarioCompilationWorker(
            ScenarioCompilationProcessManager processManager,
            ScenarioCompilationRepository compilationRepository,
            WorkQueuePort queue,
            ScenarioBundleRepository bundleRepository,
            ResolutionExtractionPort extractionPort,
            ScenarioSourceExcerptPort excerptPort,
            CharacterInputTagExtractionPort characterTagPort,
            CharacterContextSearchPort characterContextSearchPort,
            ScenarioPackageCompilationService compiler,
            ScenarioPackageRepository ignoredPackageRepository) {
        this.processManager = Objects.requireNonNull(processManager);
        this.compilationRepository = Objects.requireNonNull(compilationRepository);
        this.queue = Objects.requireNonNull(queue);
        this.bundleRepository = Objects.requireNonNull(bundleRepository);
        this.extractionPort = Objects.requireNonNull(extractionPort);
        this.excerptPort = Objects.requireNonNull(excerptPort);
        this.characterTagPort = Objects.requireNonNull(characterTagPort);
        this.characterContextSearchPort = Objects.requireNonNull(characterContextSearchPort);
        this.compiler = Objects.requireNonNull(compiler);
        Objects.requireNonNull(ignoredPackageRepository);
    }

    public ScenarioCompilationWorker(
            ScenarioCompilationProcessManager processManager,
            ScenarioCompilationRepository compilationRepository,
            WorkQueuePort queue,
            ScenarioBundleRepository bundleRepository,
            ResolutionExtractionPort extractionPort,
            ScenarioSourceExcerptPort excerptPort,
            CharacterInputTagExtractionPort characterTagPort,
            ScenarioPackageCompilationService compiler,
            ScenarioPackageRepository ignoredPackageRepository) {
        this(processManager, compilationRepository, queue, bundleRepository, extractionPort, excerptPort,
                characterTagPort, ignored -> List.of(), compiler, ignoredPackageRepository);
    }

    @Scheduled(fixedDelayString = "${adventure.scenario-compilation.poll-delay-ms:1000}")
    public void processQueuedCompilations() {
        try { processNext(WORKER_ID, LEASE); }
        catch (RuntimeException exception) { log.warn("scenario compilation worker delivery failed", exception); }
    }

    public Optional<ScenarioPackage> processNext(String workerId, Duration lease) {
        WorkQueuePort.Delivery delivery = queue.claim(Objects.requireNonNull(workerId), Objects.requireNonNull(lease)).orElse(null);
        if (delivery == null) return Optional.empty();
        var queued = compilationRepository.findById(delivery.work().aggregateId())
                .orElseThrow(() -> new IllegalStateException("compilation not found"));
        log.info("scenario compilation worker claimed work workerId={} compilationId={} attempt={} bundleId={}",
                workerId, queued.id(), queued.attempt(), queued.bundleId());
        var claimed = processManager.claim(delivery);
        try {
            ScenarioSourceBundle bundle = bundleRepository.findById(claimed.bundleId())
                    .orElseThrow(() -> new IllegalStateException("scenario bundle not found"));
            List<ResolutionExtractionPort.SourceExcerpt> excerpts = safe(excerptPort.load(bundle));
            Set<String> bundleSources = bundle.currentRevision().documents().stream()
                    .map(document -> scopeKey(document.knowledgeDocumentId(), document.extractionVersion()))
                    .collect(java.util.stream.Collectors.toSet());
            List<ResolutionCandidate> resolutionCandidates = safe(extractionPort.extract(
                    new ResolutionExtractionPort.ResolutionExtractionRequest(
                            claimed.id().toString(), excerpts.stream()
                                    .filter(excerpt -> bundleSources.contains(scopeKey(excerpt.documentId(), excerpt.extractionVersion())))
                                    .limit(3).toList(), "resolution-candidate-v1", "resolution-prompt-v1")));

            List<CharacterContextSearchPort.Evidence> overlayEvidence = searchCharacterOverlays(bundle);
            List<CharacterInputTagExtractionPort.SourceExcerpt> overlayExcerpts = overlayEvidence.stream()
                    .map(item -> new CharacterInputTagExtractionPort.SourceExcerpt(
                            item.documentId(), item.extractionVersion(), item.locator(), item.excerpt()))
                    .toList();
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> overlayCandidates = overlayExcerpts.isEmpty()
                    ? List.of() : extractCharacterOverlays(claimed.id().toString(), overlayExcerpts);
            overlayCandidates = refineCharacterOverlays(claimed.id().toString(), bundle, overlayCandidates);

            ScenarioPackage scenarioPackage = compiler.compileWithCharacterCandidates(
                    bundle, resolutionCandidates, excerpts, overlayCandidates);
            processManager.publish(claimed, delivery, scenarioPackage.packageId());
            log.info("scenario compilation worker published compilationId={} packageId={}", claimed.id(), scenarioPackage.packageId());
            return Optional.of(scenarioPackage);
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "scenario compilation failed" : exception.getMessage();
            if (claimed.attempt() >= MAX_ATTEMPTS) processManager.fail(claimed, delivery, reason);
            else processManager.retry(claimed, delivery, reason);
            log.warn("scenario compilation worker failed compilationId={} attempt={} reason={}",
                    claimed.id(), claimed.attempt(), reason, exception);
            throw exception;
        }
    }

    private List<CharacterContextSearchPort.Evidence> searchCharacterOverlays(ScenarioSourceBundle bundle) {
        List<CharacterContextSearchPort.DocumentScope> scopes = overlayScopes(bundle);
        if (scopes.isEmpty()) {
            log.info("character overlay search skipped bundleId={} reason=no-story-sources", bundle.id());
            return List.of();
        }
        try {
            Set<String> allowed = scopeKeys(scopes);
            List<CharacterContextSearchPort.Evidence> found = safe(characterContextSearchPort.search(
                    new CharacterContextSearchPort.Request(bundle.ownerPlayerId().value(), scopes,
                            "Extract only scenario-specific character creation constraints, defaults, fixed values, and additional fields. Do not reconstruct base edition rules.",
                            Map.of("STORYBOOK", .25, "HANDOUT", .25), 1200)));
            List<CharacterContextSearchPort.Evidence> filtered = found.stream()
                    .filter(item -> allowed.contains(scopeKey(item.documentId(), item.extractionVersion())))
                    .toList();
            log.info("character overlay search completed bundleId={} scopes={} evidence={}",
                    bundle.id(), scopes.size(), filtered.size());
            return filtered;
        } catch (CharacterContextSearchPort.CharacterContextSearchException exception) {
            log.warn("character overlay search failed; using edition base schema", exception);
            return List.of();
        }
    }

    private List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> extractCharacterOverlays(
            String operationId, List<CharacterInputTagExtractionPort.SourceExcerpt> excerpts) {
        try {
            return safe(characterTagPort.extract(new CharacterInputTagExtractionPort.Request(
                    operationId + ":character-story-overlays", excerpts,
                    "character-input-tag-v1", "character-story-overlay-prompt-v1",
                    "Extract only scenario-specific changes to character creation. Never reconstruct base edition fields or turn example names into options.")));
        } catch (RuntimeException exception) {
            log.warn("character overlay extraction failed; using edition base schema", exception);
            return List.of();
        }
    }

    private List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> refineCharacterOverlays(
            String operationId, ScenarioSourceBundle bundle,
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<CharacterContextSearchPort.DocumentScope> scopes = overlayScopes(bundle);
        if (scopes.isEmpty()) return List.copyOf(candidates);
        Set<String> allowed = scopeKeys(scopes);
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> result = new ArrayList<>();
        for (var candidate : candidates) {
            if (isBaseFreeTextField(candidate)) { result.add(candidate); continue; }
            try {
                List<CharacterContextSearchPort.Evidence> found = safe(characterContextSearchPort.search(
                        new CharacterContextSearchPort.Request(bundle.ownerPlayerId().value(), scopes,
                                "Find only scenario-specific changes for character field '" + candidate.key()
                                        + "'. Do not retrieve base rulebook definitions.",
                                Map.of("STORYBOOK", .20, "HANDOUT", .20), 500)));
                List<CharacterInputTagExtractionPort.SourceExcerpt> excerpts = found.stream()
                        .filter(item -> allowed.contains(scopeKey(item.documentId(), item.extractionVersion())))
                        .sorted(java.util.Comparator.comparingDouble(CharacterContextSearchPort.Evidence::similarity).reversed())
                        .map(item -> new CharacterInputTagExtractionPort.SourceExcerpt(
                                item.documentId(), item.extractionVersion(), item.locator(), item.excerpt()))
                        .toList();
                if (excerpts.isEmpty()) { result.add(candidate); continue; }
                List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> refined = safe(characterTagPort.extract(
                        new CharacterInputTagExtractionPort.Request(
                                operationId + ":character-overlay-refine:" + candidate.key() + ":" + UUID.randomUUID(),
                                excerpts, "character-input-tag-v1", "character-story-overlay-prompt-v1",
                                "Refine only scenario-specific changes for field '" + candidate.key()
                                        + "'. Keep its key exactly. If the story does not change it, return no candidate.")));
                result.add(refined.stream().filter(item -> item.key().equals(candidate.key())).findFirst().orElse(candidate));
            } catch (RuntimeException exception) {
                log.warn("character overlay refinement failed; retaining candidate key={}", candidate.key(), exception);
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

    private static boolean isBaseFreeTextField(CharacterInputTagExtractionPort.CharacterInputTagCandidate candidate) {
        if (candidate.inputMode() != InputMode.FREE_TEXT) return false;
        String key = candidate.key().toLowerCase(java.util.Locale.ROOT);
        return key.equals("name") || key.startsWith("appearance.")
                || key.equals("personality_traits") || key.equals("ideals")
                || key.equals("bonds") || key.equals("flaws");
    }

    private static List<CharacterContextSearchPort.DocumentScope> overlayScopes(ScenarioSourceBundle bundle) {
        return bundle.currentRevision().documents().stream()
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType())
                        || document.role() == ScenarioBundleDocumentRole.HANDOUT)
                .map(document -> new CharacterContextSearchPort.DocumentScope(
                        document.knowledgeDocumentId(),
                        document.role() == ScenarioBundleDocumentRole.HANDOUT ? "HANDOUT" : "STORYBOOK",
                        document.extractionVersion()))
                .toList();
    }

    private static Set<String> scopeKeys(List<CharacterContextSearchPort.DocumentScope> scopes) {
        return scopes.stream().map(scope -> scopeKey(scope.documentId(), scope.extractionVersion()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String scopeKey(
            com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId documentId, long extractionVersion) {
        return documentId.value() + ":" + extractionVersion;
    }

    private static <T> List<T> safe(List<T> values) { return values == null ? List.of() : List.copyOf(values); }
}
