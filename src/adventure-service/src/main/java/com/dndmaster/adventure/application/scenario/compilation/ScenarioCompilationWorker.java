package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Map;
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
            List<ResolutionCandidate> candidates = extractionPort.extract(
                    new ResolutionExtractionPort.ResolutionExtractionRequest(
                            claimed.id().toString(), excerpts == null ? List.of() : excerpts.stream()
                                    .filter(excerpt -> bundleSources.contains(excerpt.documentId().value() + ":" + excerpt.extractionVersion()))
                                    .limit(3).toList(),
                            "resolution-candidate-v1", "resolution-prompt-v1"));
            List<CharacterContextSearchPort.Evidence> characterContext = searchCharacterContext(bundle);
            List<com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.SourceExcerpt> tagExcerpts =
                    characterContext.stream()
                            .map(evidence -> new com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.SourceExcerpt(
                                    evidence.documentId(), evidence.extractionVersion(), evidence.locator(), evidence.excerpt()))
                            .toList();
            List<com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.CharacterInputTagCandidate> characterCandidates =
                    extractCharacterTags(claimed.id().toString(), tagExcerpts);
            characterCandidates = refineCharacterTags(claimed.id().toString(), bundle, characterCandidates);
            ScenarioPackage scenarioPackage = compiler.compileWithCharacterCandidates(bundle, candidates == null ? List.of() : candidates,
                    excerpts == null ? List.of() : excerpts, characterCandidates == null ? List.of() : characterCandidates);
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

    private List<CharacterContextSearchPort.Evidence> searchCharacterContext(ScenarioSourceBundle bundle) {
        try {
            List<CharacterContextSearchPort.DocumentScope> documents = bundle.currentRevision().documents().stream()
                    .map(document -> new CharacterContextSearchPort.DocumentScope(
                            document.knowledgeDocumentId(), document.documentType(), document.extractionVersion()))
                    .filter(document -> List.of("RULEBOOK", "STORYBOOK", "HANDOUT")
                            .contains(document.documentType().toUpperCase(java.util.Locale.ROOT)))
                    .toList();
            if (documents.isEmpty()) return List.of();
            List<CharacterContextSearchPort.Evidence> result = characterContextSearchPort.search(new CharacterContextSearchPort.Request(
                    bundle.ownerPlayerId().value(), documents,
                    "Extract character creation choices, fixed values, and required input fields.",
                    java.util.Map.of("RULEBOOK", .35, "STORYBOOK", .25, "HANDOUT", .25), 2000));
            return result == null ? List.of() : List.copyOf(result);
        } catch (CharacterContextSearchPort.CharacterContextSearchException exception) {
            log.warn("character context search failed; continuing with manual character fallback", exception);
            return List.of();
        }
    }

    private List<com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.CharacterInputTagCandidate> extractCharacterTags(
            String operationId,
            List<com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.SourceExcerpt> excerpts) {
        try {
            return characterTagPort.extract(new CharacterInputTagExtractionPort.Request(
                    operationId + ":character-input-tags", excerpts,
                    "character-input-tag-v1", "character-input-tag-prompt-v1",
                    "Determine each field's input mode and return only source-supported options."));
        } catch (RuntimeException exception) {
            log.warn("character input extraction failed; continuing with manual character fallback", exception);
            return List.of();
        }
    }

    private List<com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.CharacterInputTagCandidate> refineCharacterTags(
            String operationId, ScenarioSourceBundle bundle,
            List<com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.CharacterInputTagCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        List<CharacterContextSearchPort.DocumentScope> scopes = bundle.currentRevision().documents().stream()
                .map(document -> new CharacterContextSearchPort.DocumentScope(
                        document.knowledgeDocumentId(), document.documentType(), document.extractionVersion()))
                .filter(document -> List.of("RULEBOOK", "STORYBOOK", "HANDOUT").contains(document.documentType()))
                .toList();
        List<com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.CharacterInputTagCandidate> result = new ArrayList<>();
        for (var candidate : candidates) {
            try {
                List<CharacterContextSearchPort.Evidence> evidence = characterContextSearchPort.search(new CharacterContextSearchPort.Request(
                        bundle.ownerPlayerId().value(), scopes,
                        "Find selectable values and input rules for character field '" + candidate.key() + "' (" + candidate.label() + ").",
                        Map.of("RULEBOOK", .25, "STORYBOOK", .20, "HANDOUT", .20), 700));
                List<CharacterInputTagExtractionPort.SourceExcerpt> excerpts = (evidence == null ? List.<CharacterContextSearchPort.Evidence>of() : evidence).stream()
                        .sorted(java.util.Comparator.comparingDouble(CharacterContextSearchPort.Evidence::similarity).reversed())
                        .map(item -> new CharacterInputTagExtractionPort.SourceExcerpt(item.documentId(), item.extractionVersion(), item.locator(), item.excerpt()))
                        .toList();
                if (excerpts.isEmpty()) { result.add(candidate); continue; }
                List<com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort.CharacterInputTagCandidate> refined = characterTagPort.extract(new CharacterInputTagExtractionPort.Request(
                        operationId + ":character-input-refine:" + candidate.key() + ":" + UUID.randomUUID(), excerpts,
                        "character-input-tag-v1", "character-input-tag-prompt-v1",
                        "Refine only field '" + candidate.key() + "'. Keep its key exactly. Decide FREE_TEXT, SINGLE_SELECT, or MULTI_SELECT; return only directly supported options."));
                result.add(refined == null ? candidate : refined.stream().filter(item -> item.key().equals(candidate.key())).findFirst().orElse(candidate));
            } catch (RuntimeException exception) {
                log.warn("character input refinement failed; retaining first-stage candidate key={}", candidate.key(), exception);
                result.add(candidate);
            }
        }
        return List.copyOf(result);
    }

}
