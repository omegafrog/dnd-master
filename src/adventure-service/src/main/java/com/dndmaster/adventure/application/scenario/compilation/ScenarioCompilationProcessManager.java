package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ScenarioCompilationProcessManager {
    private static final String WORK_TYPE = "SCENARIO_RESOLUTION_CANDIDATE_EXTRACTION";
    private static final Logger log = LoggerFactory.getLogger(ScenarioCompilationProcessManager.class);
    private final ScenarioCompilationRepository repository;
    private final WorkQueuePort queue;

    public ScenarioCompilationProcessManager(ScenarioCompilationRepository repository, WorkQueuePort queue) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
    }

    public ScenarioCompilation start(ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint) {
        return start(bundleId, bundleRevision, inputFingerprint, inputFingerprint);
    }
    public ScenarioCompilation start(ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint, String idempotencyKey) {
        var existing = repository.findByIdempotencyKey(idempotencyKey).or(() -> repository.findByInputFingerprint(inputFingerprint));
        if (existing.isPresent()) {
            log.info("scenario compilation reuse bundleId={} revision={} inputFingerprint={} compilationId={}",
                    bundleId.value(), bundleRevision, inputFingerprint, existing.get().id());
            return existing.get();
        }
        ScenarioCompilation compilation = ScenarioCompilation.request(bundleId, bundleRevision, inputFingerprint, idempotencyKey);
        repository.save(compilation);
        queue.enqueue(new WorkEnvelope(
                UUID.randomUUID(), WORK_TYPE, compilation.id(), bundleRevision, inputFingerprint, 0));
        log.info("scenario compilation queued bundleId={} revision={} inputFingerprint={} compilationId={}",
                bundleId.value(), bundleRevision, inputFingerprint, compilation.id());
        return compilation;
    }

    public ScenarioCompilation claim(WorkQueuePort.Delivery delivery) {
        ScenarioCompilation compilation = load(delivery);
        ScenarioCompilation claimed = compilation.claim(delivery.deliveryToken());
        if (!repository.saveIfLeaseMatches(claimed, compilation.leaseToken())) {
            throw new IllegalStateException("compilation lease was superseded");
        }
        log.info("scenario compilation claimed compilationId={} attempt={} deliveryToken={}",
                claimed.id(), claimed.attempt(), delivery.deliveryToken());
        return claimed;
    }

    public ScenarioCompilation retry(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery, String reason) {
        ScenarioCompilation current = requireDelivery(compilation, delivery);
        ScenarioCompilation waiting = current.retry(delivery.deliveryToken(), reason);
        saveOwned(waiting, delivery);
        queue.retry(delivery, reason);
        log.info("scenario compilation retry compilationId={} attempt={} reason={}",
                current.id(), waiting.attempt(), reason);
        return waiting;
    }

    public ScenarioCompilation publish(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery, UUID packageId) {
        ScenarioCompilation current = requireDelivery(compilation, delivery);
        ScenarioCompilation published = current.publish(delivery.deliveryToken(), packageId);
        saveOwned(published, delivery);
        queue.acknowledge(delivery);
        log.info("scenario compilation published compilationId={} packageId={}", current.id(), packageId);
        return published;
    }

    public ScenarioCompilation fail(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery, String reason) {
        ScenarioCompilation current = requireDelivery(compilation, delivery);
        ScenarioCompilation failed = current.fail(delivery.deliveryToken(), reason);
        saveOwned(failed, delivery);
        queue.acknowledge(delivery);
        log.info("scenario compilation failed compilationId={} reason={}", current.id(), reason);
        return failed;
    }

    private ScenarioCompilation load(WorkQueuePort.Delivery delivery) {
        return repository.findById(delivery.work().aggregateId())
                .orElseThrow(() -> new IllegalStateException("compilation not found"));
    }

    private ScenarioCompilation requireDelivery(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery) {
        ScenarioCompilation current = load(delivery);
        if (!compilation.id().equals(delivery.work().aggregateId())
                || !compilation.id().equals(current.id())
                || !Objects.equals(current.leaseToken(), delivery.deliveryToken())) {
            throw new IllegalStateException("work does not belong to compilation");
        }
        return current;
    }

    private void saveOwned(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery) {
        if (!repository.saveIfLeaseMatches(compilation, delivery.deliveryToken())) {
            throw new IllegalStateException("compilation lease was superseded");
        }
    }
}
