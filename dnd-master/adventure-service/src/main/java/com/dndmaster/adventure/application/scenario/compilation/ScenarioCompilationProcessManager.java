package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public final class ScenarioCompilationProcessManager {
    private static final String WORK_TYPE = "SCENARIO_RESOLUTION_CANDIDATE_EXTRACTION";
    private final ScenarioCompilationRepository repository;
    private final WorkQueuePort queue;

    public ScenarioCompilationProcessManager(ScenarioCompilationRepository repository, WorkQueuePort queue) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.queue = Objects.requireNonNull(queue, "queue must not be null");
    }

    public ScenarioCompilation start(ScenarioBundleId bundleId, long bundleRevision, String inputFingerprint) {
        var existing = repository.findByInputFingerprint(inputFingerprint);
        if (existing.isPresent()) return existing.get();
        ScenarioCompilation compilation = ScenarioCompilation.request(bundleId, bundleRevision, inputFingerprint);
        repository.save(compilation);
        queue.enqueue(new WorkEnvelope(
                UUID.randomUUID(), WORK_TYPE, compilation.id(), bundleRevision, inputFingerprint, 0));
        return compilation;
    }

    public ScenarioCompilation claim(WorkQueuePort.Delivery delivery) {
        ScenarioCompilation compilation = load(delivery);
        ScenarioCompilation claimed = compilation.claim(delivery.deliveryToken());
        repository.save(claimed);
        return claimed;
    }

    public ScenarioCompilation retry(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery, String reason) {
        ScenarioCompilation current = requireDelivery(compilation, delivery);
        ScenarioCompilation waiting = current.retry(delivery.deliveryToken(), reason);
        saveOwned(waiting, delivery);
        queue.retry(delivery, reason);
        return waiting;
    }

    public ScenarioCompilation publish(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery, UUID packageId) {
        ScenarioCompilation current = requireDelivery(compilation, delivery);
        ScenarioCompilation published = current.publish(delivery.deliveryToken(), packageId);
        saveOwned(published, delivery);
        queue.acknowledge(delivery);
        return published;
    }

    public ScenarioCompilation fail(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery, String reason) {
        ScenarioCompilation current = requireDelivery(compilation, delivery);
        ScenarioCompilation failed = current.fail(delivery.deliveryToken(), reason);
        saveOwned(failed, delivery);
        queue.acknowledge(delivery);
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
