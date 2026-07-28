package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.Optional;
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
        this.processManager = Objects.requireNonNull(processManager, "process manager must not be null");
        this.compilationRepository = Objects.requireNonNull(compilationRepository, "compilation repository must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.extractionPort = Objects.requireNonNull(extractionPort, "extraction port must not be null");
        this.excerptPort = Objects.requireNonNull(excerptPort, "excerpt port must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        Objects.requireNonNull(ignoredPackageRepository, "package repository must not be null");
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
            Set<java.util.UUID> bundleDocumentIds = bundle.currentRevision().documents().stream()
                    .map(document -> document.knowledgeDocumentId().value()).collect(java.util.stream.Collectors.toSet());
            List<ResolutionCandidate> candidates = extractionPort.extract(
                    new ResolutionExtractionPort.ResolutionExtractionRequest(
                            claimed.id().toString(), excerpts == null ? List.of() : excerpts.stream()
                                    .filter(excerpt -> bundleDocumentIds.contains(excerpt.documentId().value()))
                                    .limit(3).toList(),
                            "resolution-candidate-v1", "resolution-prompt-v1"));
            ScenarioPackage scenarioPackage = compiler.compile(bundle, candidates == null ? List.of() : candidates,
                    excerpts == null ? List.of() : excerpts);
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
}
