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
        requireDelivery(compilation, delivery);
        ScenarioCompilation waiting = compilation.retry(delivery.deliveryToken(), reason);
        repository.save(waiting);
        queue.retry(delivery, reason);
        return waiting;
    }

    public ScenarioCompilation publish(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery, UUID packageId) {
        requireDelivery(compilation, delivery);
        ScenarioCompilation published = compilation.publish(delivery.deliveryToken(), packageId);
        repository.save(published);
        queue.acknowledge(delivery);
        return published;
    }

    public ScenarioCompilation fail(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery, String reason) {
        requireDelivery(compilation, delivery);
        ScenarioCompilation failed = compilation.fail(delivery.deliveryToken(), reason);
        repository.save(failed);
        queue.acknowledge(delivery);
        return failed;
    }

    private ScenarioCompilation load(WorkQueuePort.Delivery delivery) {
        return repository.findById(delivery.work().aggregateId())
                .orElseThrow(() -> new IllegalStateException("compilation not found"));
    }

    private static void requireDelivery(ScenarioCompilation compilation, WorkQueuePort.Delivery delivery) {
        if (!compilation.id().equals(delivery.work().aggregateId())) {
            throw new IllegalStateException("work does not belong to compilation");
        }
    }
}
