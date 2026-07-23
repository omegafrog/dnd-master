package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationWorker;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.WorkEnvelope;
import com.dndmaster.adventure.application.scenario.compilation.WorkQueuePort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationProcessManager;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageCompilationService;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScenarioCompilationWorkerTest {
    @Test
    void workerPublishesValidatedPackageFromClaimedJob() {
        ScenarioBundleId bundleId = new ScenarioBundleId(UUID.randomUUID());
        ScenarioSourceBundle bundle = ScenarioSourceBundle.create(bundleId, new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioSourceBundleRevision(1, List.of(new ScenarioBundleDocumentSelection(
                        new KnowledgeDocumentId(UUID.randomUUID()), ScenarioBundleDocumentRole.MAIN_SCENARIO,
                        com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                        "scenario.pdf", "STORYBOOK", 1))));
        InMemoryQueue queue = new InMemoryQueue();
        InMemoryCompilationRepository compilations = new InMemoryCompilationRepository();
        InMemoryPackageRepository packages = new InMemoryPackageRepository();
        ScenarioCompilationProcessManager manager = new ScenarioCompilationProcessManager(compilations, queue);
        manager.start(bundleId, 1, "fp");
        ScenarioCompilationWorker worker = new ScenarioCompilationWorker(
                manager, compilations, queue, new InMemoryBundleRepository(bundle),
                request -> List.of(), new ScenarioPackageCompilationService(packages), packages);

        var published = worker.processNext("worker-1", Duration.ofMinutes(1)).orElseThrow();

        assertEquals("PARTIAL", published.report().status().name());
        assertEquals("PUBLISHED", compilations.findByInputFingerprint("fp").orElseThrow().status().name());
        assertTrue(packages.byFingerprint.containsKey(published.inputFingerprint()));
    }

    @Test
    void workerMarksPermanentFailureAfterThirdAttempt() {
        ScenarioBundleId bundleId = new ScenarioBundleId(UUID.randomUUID());
        ScenarioSourceBundle bundle = ScenarioSourceBundle.create(bundleId, new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioSourceBundleRevision(1, List.of(new ScenarioBundleDocumentSelection(
                        new KnowledgeDocumentId(UUID.randomUUID()), ScenarioBundleDocumentRole.MAIN_SCENARIO,
                        com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                        "scenario.pdf", "STORYBOOK", 1))));
        InMemoryQueue queue = new InMemoryQueue();
        InMemoryCompilationRepository compilations = new InMemoryCompilationRepository();
        InMemoryPackageRepository packages = new InMemoryPackageRepository();
        ScenarioCompilationProcessManager manager = new ScenarioCompilationProcessManager(compilations, queue);
        var requested = ScenarioCompilation.rehydrate(
                UUID.randomUUID(), bundleId, 1, "fp-failure", ScenarioCompilationStatus.REQUESTED,
                2, null, null, null);
        compilations.save(requested);
        queue.pending.add(new WorkEnvelope(UUID.randomUUID(), "retry", requested.id(), 1, "fp-failure", 2));
        ScenarioCompilationWorker worker = new ScenarioCompilationWorker(
                manager, compilations, queue, new InMemoryBundleRepository(bundle),
                request -> { throw new IllegalStateException("AI unavailable"); },
                new ScenarioPackageCompilationService(packages), packages);

        assertThrows(IllegalStateException.class, () -> worker.processNext("worker-1", Duration.ofMinutes(1)));

        assertEquals("FAILED", compilations.findByInputFingerprint("fp-failure").orElseThrow().status().name());
        assertEquals(0, queue.pendingCount());
    }

    private static final class InMemoryCompilationRepository implements ScenarioCompilationRepository {
        private final Map<UUID, ScenarioCompilation> store = new HashMap<>();
        @Override public Optional<ScenarioCompilation> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<ScenarioCompilation> findByInputFingerprint(String fp) { return store.values().stream().filter(c -> c.inputFingerprint().equals(fp)).findFirst(); }
        @Override public void save(ScenarioCompilation c) { store.put(c.id(), c); }
    }
    private static final class InMemoryBundleRepository implements ScenarioBundleRepository {
        private final ScenarioSourceBundle bundle;
        private InMemoryBundleRepository(ScenarioSourceBundle bundle) { this.bundle = bundle; }
        @Override public Optional<ScenarioSourceBundle> findById(ScenarioBundleId id) { return Optional.of(bundle); }
        @Override public void save(ScenarioSourceBundle value) {}
    }
    private static final class InMemoryPackageRepository implements ScenarioPackageRepository {
        private final Map<String, ScenarioPackage> byFingerprint = new HashMap<>();
        @Override public Optional<ScenarioPackage> findByInputFingerprint(String fp) { return Optional.ofNullable(byFingerprint.get(fp)); }
        @Override public Optional<ScenarioPackage> findById(UUID id) { return byFingerprint.values().stream().filter(p -> p.packageId().equals(id)).findFirst(); }
        @Override public void save(ScenarioPackage p) { byFingerprint.put(p.inputFingerprint(), p); }
    }
    private static final class InMemoryQueue implements WorkQueuePort {
        private final Queue<WorkEnvelope> pending = new ArrayDeque<>();
        private int pendingCount() { return pending.size(); }
        @Override public void enqueue(WorkEnvelope w) { pending.add(w); }
        @Override public Optional<Delivery> claim(String worker, Duration lease) {
            WorkEnvelope w = pending.poll(); return w == null ? Optional.empty() : Optional.of(new Delivery(w, UUID.randomUUID(), worker));
        }
        @Override public void acknowledge(Delivery d) {}
        @Override public void retry(Delivery d, String reason) { pending.add(d.work()); }
    }
}
