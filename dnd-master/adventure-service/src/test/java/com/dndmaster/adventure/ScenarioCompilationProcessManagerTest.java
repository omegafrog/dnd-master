package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationProcessManager;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository;
import com.dndmaster.adventure.application.scenario.compilation.WorkEnvelope;
import com.dndmaster.adventure.application.scenario.compilation.WorkQueuePort;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioCompilation;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioCompilationProcessManagerTest {
    @Test
    void claimsCompilationRetriesTransientFailureAndEventuallyPublishes() {
        InMemoryCompilationRepository repository = new InMemoryCompilationRepository();
        InMemoryWorkQueue queue = new InMemoryWorkQueue();
        ScenarioCompilationProcessManager manager = new ScenarioCompilationProcessManager(repository, queue);
        ScenarioBundleId bundleId = new ScenarioBundleId(UUID.randomUUID());

        ScenarioCompilation started = manager.start(bundleId, 3, "fingerprint-1");
        WorkQueuePort.Delivery delivery = queue.claim("worker-1", Duration.ofMinutes(1)).orElseThrow();
        ScenarioCompilation running = manager.claim(delivery);
        ScenarioCompilation waiting = manager.retry(running, delivery, "AI timeout");
        assertEquals("WAITING_RETRY", waiting.status().name());
        assertEquals(1, waiting.attempt());

        WorkQueuePort.Delivery retryDelivery = queue.claim("worker-2", Duration.ofMinutes(1)).orElseThrow();
        ScenarioCompilation claimedAgain = manager.claim(retryDelivery);
        ScenarioCompilation published = manager.publish(claimedAgain, retryDelivery, UUID.randomUUID());

        assertEquals("PUBLISHED", published.status().name());
        assertEquals(2, published.attempt());
        assertEquals(0, queue.size());
        assertThrows(IllegalStateException.class, () -> manager.publish(published, retryDelivery, UUID.randomUUID()));
    }

    private static final class InMemoryCompilationRepository implements ScenarioCompilationRepository {
        private final Map<UUID, ScenarioCompilation> store = new HashMap<>();

        @Override public Optional<ScenarioCompilation> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public void save(ScenarioCompilation compilation) { store.put(compilation.id(), compilation); }
    }

    private static final class InMemoryWorkQueue implements WorkQueuePort {
        private final Queue<WorkEnvelope> pending = new ArrayDeque<>();
        private final Map<UUID, Delivery> claimed = new HashMap<>();

        @Override public void enqueue(WorkEnvelope work) { pending.add(work); }

        @Override public Optional<Delivery> claim(String workerId, Duration lease) {
            WorkEnvelope work = pending.poll();
            if (work == null) return Optional.empty();
            Delivery delivery = new Delivery(work, UUID.randomUUID(), workerId);
            claimed.put(delivery.deliveryToken(), delivery);
            return Optional.of(delivery);
        }

        @Override public void acknowledge(Delivery delivery) { claimed.remove(delivery.deliveryToken()); }
        @Override public void retry(Delivery delivery, String reason) { claimed.remove(delivery.deliveryToken()); pending.add(delivery.work()); }
        int size() { return pending.size() + claimed.size(); }
    }
}
