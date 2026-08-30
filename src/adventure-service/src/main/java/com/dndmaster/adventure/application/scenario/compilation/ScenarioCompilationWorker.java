package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
import com.dndmaster.adventure.domain.scenario.InputMode;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioResolutionUnit;
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
    private static final int MAX_RESOLUTION_RECOVERY_ATTEMPTS = 3;
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
    private final CompilationCandidateRepository candidateRepository;

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
                ignored -> List.of(), ignored -> List.of(), compiler, ignoredPackageRepository,
                new NoopCompilationCandidateRepository());
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
        this(processManager, compilationRepository, queue, bundleRepository, extractionPort, excerptPort,
                characterTagPort, characterContextSearchPort, compiler, ignoredPackageRepository,
                new NoopCompilationCandidateRepository());
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
            ScenarioPackageRepository ignoredPackageRepository,
            CompilationCandidateRepository candidateRepository) {
        this.processManager = Objects.requireNonNull(processManager, "process manager must not be null");
        this.compilationRepository = Objects.requireNonNull(compilationRepository, "compilation repository must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.extractionPort = Objects.requireNonNull(extractionPort, "extraction port must not be null");
        this.excerptPort = Objects.requireNonNull(excerptPort, "excerpt port must not be null");
        this.characterContextSearchPort = Objects.requireNonNull(characterContextSearchPort, "character context search port must not be null");
        this.characterTagPort = Objects.requireNonNull(characterTagPort, "character tag port must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        Objects.requireNonNull(ignoredPackageRepository, "package repository must not be null");
        this.candidateRepository = Objects.requireNonNull(candidateRepository, "candidate repository must not be null");
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
        try {
            processNext(WORKER_ID, LEASE);
        } catch (RuntimeException exception) {
            log.warn("scenario compilation worker delivery failed", exception);
        }
    }

    public Optional<ScenarioPackage> processNext(String workerId, Duration lease) {
        WorkQueuePort.Delivery delivery = queue.claim(
                Objects.requireNonNull(workerId, "worker id must not be null"),
                Objects.requireNonNull(lease, "lease must not be null"))
                .orElse(null);
        if (delivery == null) return Optional.empty();

        var compilation = compilationRepository.findById(delivery.work().aggregateId())
                .orElseThrow(() -> new IllegalStateException("compilation not found"));
        log.info("scenario compilation worker claimed work workerId={} compilationId={} attempt={} bundleId={}",
                workerId, compilation.id(), compilation.attempt(), compilation.bundleId());
        var claimed = processManager.claim(delivery);
        try {
            ScenarioSourceBundle bundle = bundleRepository.findById(claimed.bundleId())
                    .orElseThrow(() -> new IllegalStateException("scenario bundle not found"));
            List<ResolutionExtractionPort.SourceExcerpt> excerpts = excerptPort.load(bundle);
            Set<String> bundleSources = bundle.currentRevision().documents().stream()
                    .map(document -> document.knowledgeDocumentId().value() + ":" + document.extractionVersion())
                    .collect(java.util.stream.Collectors.toSet());
            List<ResolutionExtractionPort.SourceExcerpt> resolutionExcerpts = selectResolutionExcerpts(excerpts, bundleSources);
            List<ResolutionCandidate> candidates = extractionPort.extract(
                    new ResolutionExtractionPort.ResolutionExtractionRequest(
                            claimed.id().toString(), excerpts == null ? List.of() : excerpts.stream()
                                    .filter(excerpt -> bundleSources.contains(excerpt.documentId().value() + ":" + excerpt.extractionVersion()))
                                    .filter(excerpt -> resolutionExcerpts.contains(excerpt))
                                    .toList(),
                            "resolution-candidate-v1", "resolution-prompt-v1"));
            candidates = recoverInvalidResolutions(
                    claimed.id().toString(), bundle, candidates == null ? List.of() : candidates, resolutionExcerpts);

            List<CharacterContextSearchPort.Evidence> characterContext = searchCharacterContext(bundle);
            List<CharacterInputTagExtractionPort.SourceExcerpt> tagExcerpts = characterContext.stream()
                    .map(evidence -> new CharacterInputTagExtractionPort.SourceExcerpt(
                            evidence.documentId(), evidence.extractionVersion(), evidence.locator(), evidence.excerpt()))
                    .toList();
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> characterCandidates = tagExcerpts.isEmpty()
                    ? List.of()
                    : extractCharacterTags(claimed.id().toString(), tagExcerpts);
            characterCandidates = refineCharacterTags(claimed.id().toString(), bundle, characterCandidates);

            ScenarioPackage scenarioPackage = compiler.compileWithCharacterCandidates(
                    bundle, candidates == null ? List.of() : candidates,
                    excerpts == null ? List.of() : excerpts,
                    characterCandidates == null ? List.of() : characterCandidates);
            candidateRepository.saveAll(claimed.id(), CompilationCandidateFactory.from(
                    claimed.id(), candidates == null ? List.of() : candidates, scenarioPackage.units()));

            if (scenarioPackage.report().outcome()
                    == com.dndmaster.adventure.domain.scenario.CompilationOutcome.FAILED) {
                throw new IllegalStateException(
                        "scenario package compilation report is " + scenarioPackage.report().status());
            }

            processManager.publish(claimed, delivery, scenarioPackage.packageId());
            log.info("scenario compilation worker published compilationId={} packageId={}",
                    claimed.id(), scenarioPackage.packageId());
            return Optional.of(scenarioPackage);
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "scenario compilation failed" : exception.getMessage();
            if (claimed.attempt() >= MAX_ATTEMPTS) {
                processManager.fail(claimed, delivery, reason);
            } else {
                processManager.retry(claimed, delivery, reason);
            }
            log.warn("scenario compilation worker failed compilationId={} attempt={} reason={}",
                    claimed.id(), claimed.attempt(), reason, exception);
            throw exception;
        }
    }

    private List<ResolutionCandidate> recoverInvalidResolutions(
            String operationId, ScenarioSourceBundle bundle, List<ResolutionCandidate> original,
            List<ResolutionExtractionPort.SourceExcerpt> excerpts) {
        List<ResolutionCandidate> recovered = new ArrayList<>(original);
        for (int attempt = 1; attempt <= MAX_RESOLUTION_RECOVERY_ATTEMPTS; attempt++) {
            List<ScenarioResolutionUnit> units = compiler.validateResolutionCandidates(bundle, recovered, excerpts);
            List<Integer> invalidIndexes = new ArrayList<>();
            for (int index = 0; index < units.size(); index++) {
                if (units.get(index).status() == com.dndmaster.adventure.domain.scenario.ResolutionStatus.INVALID) {
                    invalidIndexes.add(index);
                }
            }
            if (invalidIndexes.isEmpty()) return recovered;

            List<ResolutionExtractionPort.SourceExcerpt> targets = invalidIndexes.stream()
                    .map(recovered::get)
                    .flatMap(candidate -> excerpts.stream().filter(excerpt -> references(candidate, excerpt)))
                    .distinct().toList();
            if (targets.isEmpty()) return recovered;

            List<ResolutionCandidate> retry = extractionPort.extract(new ResolutionExtractionPort.ResolutionExtractionRequest(
                    operationId + ":resolution-recovery-" + attempt, targets,
                    "resolution-candidate-v1", "resolution-recovery-prompt-v1"));
            if (retry == null || retry.isEmpty()) {
                log.warn("resolution recovery returned no candidates operationId={} attempt={} invalid={}",
                        operationId, attempt, invalidIndexes.size());
                continue;
            }
            for (int index : invalidIndexes) {
                ResolutionCandidate replacement = retry.stream()
                        .filter(candidate -> candidate != null && targets.stream().anyMatch(target -> references(candidate, target)))
                        .filter(candidate -> compiler.validateResolutionCandidates(bundle, List.of(candidate), excerpts).getFirst().status()
                                != com.dndmaster.adventure.domain.scenario.ResolutionStatus.INVALID)
                        .findFirst().orElse(null);
                if (replacement != null) recovered.set(index, replacement);
            }
            log.info("resolution recovery completed operationId={} attempt={} invalidBefore={} recovered={}",
                    operationId, attempt, invalidIndexes.size(), invalidIndexes.stream()
                            .filter(index -> recovered.get(index) != original.get(index)).count());
        }
        return recovered;
    }

    private static boolean references(
            ResolutionCandidate candidate, ResolutionExtractionPort.SourceExcerpt excerpt) {
        return candidate != null && candidate.sourceRefs() != null && candidate.sourceRefs().stream().anyMatch(ref ->
                ref != null && ref.knowledgeDocumentId().equals(excerpt.documentId())
                        && ref.extractionVersion() == excerpt.extractionVersion()
                        && ref.locator().equals(excerpt.locator()));
    }

    private static List<ResolutionExtractionPort.SourceExcerpt> selectResolutionExcerpts(
            List<ResolutionExtractionPort.SourceExcerpt> excerpts, Set<String> bundleSources) {
        List<ResolutionExtractionPort.SourceExcerpt> scoped = excerpts == null ? List.of() : excerpts.stream()
                .filter(Objects::nonNull)
                .filter(excerpt -> bundleSources.contains(excerpt.documentId().value() + ":" + excerpt.extractionVersion()))
                .toList();
        List<ResolutionExtractionPort.SourceExcerpt> relevant = scoped.stream()
                .filter(excerpt -> excerpt.text() != null && excerpt.text().matches("(?is).*\\b(?:DC|check(?:s)?|saving throw(?:s)?|attack(?:s)?|damage(?:s)?|roll(?:s)?|recharge)\\b.*"))
                .limit(12).toList();
        if (relevant.size() >= 12) return relevant;
        return java.util.stream.Stream.concat(relevant.stream(), scoped.stream().filter(excerpt -> !relevant.contains(excerpt)))
                .limit(12).toList();
    }

    private List<CharacterContextSearchPort.Evidence> searchCharacterContext(ScenarioSourceBundle bundle) {
        try {
            List<CharacterContextSearchPort.DocumentScope> documents = characterOverlayScopes(bundle);
            if (documents.isEmpty()) {
                log.info("character overlay search skipped; no storybook or handout documents bundleId={}", bundle.id());
                return List.of();
            }
            Set<String> allowed = scopeKeys(documents);
            List<CharacterContextSearchPort.Evidence> result = characterContextSearchPort.search(
                    new CharacterContextSearchPort.Request(
                            bundle.ownerPlayerId().value(), documents,
                            "Extract only scenario-specific character creation constraints, defaults, fixed values, and additional input fields. Do not reconstruct base edition rules.",
                            Map.of("STORYBOOK", .25, "HANDOUT", .25), 1200));
            List<CharacterContextSearchPort.Evidence> filtered = (result == null
                    ? List.<CharacterContextSearchPort.Evidence>of() : result).stream()
                    .filter(evidence -> allowed.contains(scopeKey(
                            evidence.documentId(), evidence.extractionVersion())))
                    .toList();
            log.info("character overlay search completed bundleId={} scopes={} evidence={}",
                    bundle.id(), documents.size(), filtered.size());
            return filtered;
        } catch (CharacterContextSearchPort.CharacterContextSearchException exception) {
            log.warn("character overlay search failed; continuing with edition base schema", exception);
            return List.of();
        }
    }

    private List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> extractCharacterTags(
            String operationId, List<CharacterInputTagExtractionPort.SourceExcerpt> excerpts) {
        try {
            return characterTagPort.extract(new CharacterInputTagExtractionPort.Request(
                    operationId + ":character-story-overlays", excerpts,
                    "character-input-tag-v1", "character-story-overlay-prompt-v1",
                    "Extract only scenario-specific changes to character creation. Return constraints, defaults, fixed values, or additional fields supported by the storybook or handout. Never return base edition fields merely because the rulebook defines them, and never turn example character names into options."));
        } catch (RuntimeException exception) {
            log.warn("character overlay extraction failed; continuing with edition base schema", exception);
            return List.of();
        }
    }

    private List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> refineCharacterTags(
            String operationId, ScenarioSourceBundle bundle,
            List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<CharacterContextSearchPort.DocumentScope> scopes = characterOverlayScopes(bundle);
        if (scopes.isEmpty()) return List.copyOf(candidates);
        Set<String> allowed = scopeKeys(scopes);
        List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> result = new ArrayList<>();
        for (var candidate : candidates) {
            if (isBaseFreeTextField(candidate)) {
                result.add(candidate);
                continue;
            }
            try {
                List<CharacterContextSearchPort.Evidence> evidence = characterContextSearchPort.search(
                        new CharacterContextSearchPort.Request(
                                bundle.ownerPlayerId().value(), scopes,
                                "Find only scenario-specific values or constraints for character field '"
                                        + candidate.key() + "' (" + candidate.label() + "). Do not retrieve base rulebook definitions.",
                                Map.of("STORYBOOK", .20, "HANDOUT", .20), 500));
                List<CharacterInputTagExtractionPort.SourceExcerpt> excerpts = (evidence == null
                        ? List.<CharacterContextSearchPort.Evidence>of() : evidence).stream()
                        .filter(item -> allowed.contains(scopeKey(item.documentId(), item.extractionVersion())))
                        .sorted(java.util.Comparator.comparingDouble(CharacterContextSearchPort.Evidence::similarity).reversed())
                        .map(item -> new CharacterInputTagExtractionPort.SourceExcerpt(
                                item.documentId(), item.extractionVersion(), item.locator(), item.excerpt()))
                        .toList();
                if (excerpts.isEmpty()) {
                    result.add(candidate);
                    continue;
                }
                List<CharacterInputTagExtractionPort.CharacterInputTagCandidate> refined = characterTagPort.extract(
                        new CharacterInputTagExtractionPort.Request(
                                operationId + ":character-overlay-refine:" + candidate.key() + ":" + UUID.randomUUID(), excerpts,
                                "character-input-tag-v1", "character-story-overlay-prompt-v1",
                                "Refine only scenario-specific changes for field '" + candidate.key()
                                        + "'. Keep its key exactly. Do not add edition base options. If the story does not change this field, return no candidate."));
                result.add(refined == null ? candidate : refined.stream()
                        .filter(item -> item.key().equals(candidate.key()))
                        .findFirst().orElse(candidate));
            } catch (RuntimeException exception) {
                log.warn("character overlay refinement failed; retaining first-stage candidate key={}",
                        candidate.key(), exception);
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

    private static List<CharacterContextSearchPort.DocumentScope> characterOverlayScopes(ScenarioSourceBundle bundle) {
        return bundle.currentRevision().documents().stream()
                .filter(document -> "STORYBOOK".equalsIgnoreCase(document.documentType())
                        || document.role() == ScenarioBundleDocumentRole.HANDOUT)
                .map(document -> new CharacterContextSearchPort.DocumentScope(
                        document.knowledgeDocumentId(),
                        document.documentType(),
                        document.extractionVersion()))
                .toList();
    }

    private static Set<String> scopeKeys(List<CharacterContextSearchPort.DocumentScope> scopes) {
        return scopes.stream().map(scope -> scopeKey(scope.documentId(), scope.extractionVersion()))
                .collect(java.util.stream.Collectors.toSet());
    }

    private static String scopeKey(
            com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId documentId,
            long extractionVersion) {
        return documentId.value() + ":" + extractionVersion;
    }
}
