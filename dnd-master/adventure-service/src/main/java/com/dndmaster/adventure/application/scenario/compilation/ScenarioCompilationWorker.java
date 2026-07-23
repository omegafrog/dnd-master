package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.scenario.ScenarioPackage;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Executes one queued scenario compilation delivery. */
public final class ScenarioCompilationWorker {
    private static final int MAX_ATTEMPTS = 3;
    private final ScenarioCompilationProcessManager processManager;
    private final ScenarioCompilationRepository compilationRepository;
    private final WorkQueuePort queue;
    private final ScenarioBundleRepository bundleRepository;
    private final ResolutionExtractionPort extractionPort;
    private final ScenarioPackageCompilationService compiler;

    public ScenarioCompilationWorker(
            ScenarioCompilationProcessManager processManager,
            ScenarioCompilationRepository compilationRepository,
            WorkQueuePort queue,
            ScenarioBundleRepository bundleRepository,
            ResolutionExtractionPort extractionPort,
            ScenarioPackageCompilationService compiler,
            ScenarioPackageRepository ignoredPackageRepository) {
        this.processManager = Objects.requireNonNull(processManager, "process manager must not be null");
        this.compilationRepository = Objects.requireNonNull(compilationRepository, "compilation repository must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
        this.bundleRepository = Objects.requireNonNull(bundleRepository, "bundle repository must not be null");
        this.extractionPort = Objects.requireNonNull(extractionPort, "extraction port must not be null");
        this.compiler = Objects.requireNonNull(compiler, "compiler must not be null");
        Objects.requireNonNull(ignoredPackageRepository, "package repository must not be null");
    }

    public Optional<ScenarioPackage> processNext(String workerId, Duration lease) {
        WorkQueuePort.Delivery delivery = queue.claim(
                Objects.requireNonNull(workerId, "worker id must not be null"),
                Objects.requireNonNull(lease, "lease must not be null"))
                .orElse(null);
        if (delivery == null) return Optional.empty();

        var compilation = compilationRepository.findById(delivery.work().aggregateId())
                .orElseThrow(() -> new IllegalStateException("compilation not found"));
        var claimed = processManager.claim(delivery);
        try {
            ScenarioSourceBundle bundle = bundleRepository.findById(claimed.bundleId())
                    .orElseThrow(() -> new IllegalStateException("scenario bundle not found"));
            List<ResolutionCandidate> candidates = extractionPort.extract(
                    new ResolutionExtractionPort.ResolutionExtractionRequest(
                            claimed.id().toString(), List.of(), "resolution-candidate-v1", "resolution-prompt-v1"));
            ScenarioPackage scenarioPackage = compiler.compile(bundle, candidates == null ? List.of() : candidates);
            processManager.publish(claimed, delivery, scenarioPackage.packageId());
            return Optional.of(scenarioPackage);
        } catch (RuntimeException exception) {
            String reason = exception.getMessage() == null || exception.getMessage().isBlank()
                    ? "scenario compilation failed" : exception.getMessage();
            if (claimed.attempt() >= MAX_ATTEMPTS) {
                processManager.fail(claimed, delivery, reason);
            } else {
                processManager.retry(claimed, delivery, reason);
            }
            throw exception;
        }
    }
}
