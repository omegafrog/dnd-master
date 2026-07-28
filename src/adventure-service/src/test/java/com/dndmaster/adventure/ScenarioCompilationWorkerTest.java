package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.compilation.ResolutionExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationRepository;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioCompilationWorker;
import com.dndmaster.adventure.application.scenario.compilation.ScenarioPackageRepository;
import com.dndmaster.adventure.application.scenario.compilation.CharacterContextSearchPort;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
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
    void character_context_search_has_independent_budget_and_feeds_character_extraction() {
        ScenarioBundleId bundleId = new ScenarioBundleId(UUID.randomUUID());
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        KnowledgeDocumentId rulebook = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storybook = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = ScenarioSourceBundle.create(bundleId, owner,
                new ScenarioSourceBundleRevision(1, List.of(
                        new ScenarioBundleDocumentSelection(rulebook, ScenarioBundleDocumentRole.RULEBOOK,
                                com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                                "rules.pdf", "RULEBOOK", 2),
                        new ScenarioBundleDocumentSelection(storybook, ScenarioBundleDocumentRole.MAIN_SCENARIO,
                                com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                                "story.txt", "STORYBOOK", 4))));
        InMemoryQueue queue = new InMemoryQueue();
        InMemoryCompilationRepository compilations = new InMemoryCompilationRepository();
        InMemoryPackageRepository packages = new InMemoryPackageRepository();
        ScenarioCompilationProcessManager manager = new ScenarioCompilationProcessManager(compilations, queue);
        manager.start(bundleId, 1, "character-context-fp");
        RecordingCharacterContextSearch search = new RecordingCharacterContextSearch(rulebook, storybook);
        RecordingCharacterTagExtraction tags = new RecordingCharacterTagExtraction();
        ScenarioCompilationWorker worker = new ScenarioCompilationWorker(
                manager, compilations, queue, new InMemoryBundleRepository(bundle), request -> List.of(),
                ignored -> List.of(), tags, search, new ScenarioPackageCompilationService(packages), packages);

        worker.processNext("worker-1", Duration.ofMinutes(1));

        assertEquals(2, search.request.documents().size());
        assertEquals(.35, search.request.thresholds().get("RULEBOOK"));
        assertEquals(2000, search.request.tokenBudget());
        assertEquals(2, tags.request.excerpts().size());
        assertEquals("rule option", tags.request.excerpts().getFirst().text());
        assertEquals("story option", tags.request.excerpts().get(1).text());
    }
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
                request -> List.of(), ignored -> List.of(), new ScenarioPackageCompilationService(packages), packages);

        var published = worker.processNext("worker-1", Duration.ofMinutes(1)).orElseThrow();

        assertEquals("PARTIAL", published.report().status().name());
        assertEquals("PUBLISHED", compilations.findByInputFingerprint("fp").orElseThrow().status().name());
        assertTrue(packages.byFingerprint.containsKey(published.inputFingerprint()));
    }

    @Test
    void scheduledWorkerProcessesRequestedJob() {
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
        manager.start(bundleId, 1, "scheduled-fp");
        ScenarioCompilationWorker worker = new ScenarioCompilationWorker(
                manager, compilations, queue, new InMemoryBundleRepository(bundle),
                request -> List.of(), ignored -> List.of(), new ScenarioPackageCompilationService(packages), packages);

        worker.processQueuedCompilations();

        assertEquals("PUBLISHED", compilations.findByInputFingerprint("scheduled-fp").orElseThrow().status().name());
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
                UUID.randomUUID(), bundleId, 1, "fp-failure", ScenarioCompilationStatus.RUNNING,
                2, UUID.randomUUID(), null, null);
        compilations.save(requested);
        queue.pending.add(new WorkEnvelope(UUID.randomUUID(), "retry", requested.id(), 1, "fp-failure", 2));
        ScenarioCompilationWorker worker = new ScenarioCompilationWorker(
                manager, compilations, queue, new InMemoryBundleRepository(bundle),
                request -> { throw new IllegalStateException("AI unavailable"); }, ignored -> List.of(),
                new ScenarioPackageCompilationService(packages), packages);

        assertThrows(IllegalStateException.class, () -> worker.processNext("worker-1", Duration.ofMinutes(1)));

        assertEquals("FAILED", compilations.findByInputFingerprint("fp-failure").orElseThrow().status().name());
        assertEquals(0, queue.pendingCount());
    }

    @Test
    void staleDeliveryCannotReplaceReclaimedLease() {
        ScenarioBundleId bundleId = new ScenarioBundleId(UUID.randomUUID());
        UUID activeLease = UUID.randomUUID();
        ScenarioCompilation compilation = ScenarioCompilation.rehydrate(
                UUID.randomUUID(), bundleId, 1, "fp-lease", ScenarioCompilationStatus.RUNNING,
                1, activeLease, null, null);

        assertThrows(IllegalStateException.class, () -> compilation.claim(activeLease));
        assertEquals("RUNNING", compilation.claim(UUID.randomUUID()).status().name());
    }

    @Test
    void staleWorkerCannotPublishAfterAnotherWorkerReclaimsLease() {
        ScenarioBundleId bundleId = new ScenarioBundleId(UUID.randomUUID());
        UUID leaseA = UUID.randomUUID();
        ScenarioCompilation compilationA = ScenarioCompilation.rehydrate(
                UUID.randomUUID(), bundleId, 1, "fp-race", ScenarioCompilationStatus.RUNNING,
                1, leaseA, null, null);
        InMemoryCompilationRepository compilations = new InMemoryCompilationRepository();
        InMemoryQueue queue = new InMemoryQueue();
        ScenarioCompilationProcessManager manager = new ScenarioCompilationProcessManager(compilations, queue);
        compilations.save(compilationA);
        WorkEnvelope work = new WorkEnvelope(UUID.randomUUID(), "retry", compilationA.id(), 1, "fp-race", 1);
        WorkQueuePort.Delivery deliveryA = new WorkQueuePort.Delivery(work, leaseA, "worker-a");
        WorkQueuePort.Delivery deliveryB = new WorkQueuePort.Delivery(work, UUID.randomUUID(), "worker-b");
        manager.claim(deliveryB);

        assertThrows(IllegalStateException.class, () -> manager.publish(compilationA, deliveryA, UUID.randomUUID()));
    }

    private static final class RecordingCharacterContextSearch implements CharacterContextSearchPort {
        private final KnowledgeDocumentId rulebook;
        private final KnowledgeDocumentId storybook;
        private CharacterContextSearchPort.Request request;

        private RecordingCharacterContextSearch(KnowledgeDocumentId rulebook, KnowledgeDocumentId storybook) {
            this.rulebook = rulebook;
            this.storybook = storybook;
        }

        @Override
        public List<CharacterContextSearchPort.Evidence> search(CharacterContextSearchPort.Request request) {
            this.request = request;
            return List.of(
                    new CharacterContextSearchPort.Evidence(rulebook, "RULEBOOK", 2, "rule:1", "rule option", .9),
                    new CharacterContextSearchPort.Evidence(storybook, "STORYBOOK", 4, "story:1", "story option", .8));
        }
    }

    private static final class RecordingCharacterTagExtraction implements CharacterInputTagExtractionPort {
        private Request request;

        @Override
        public List<CharacterInputTagCandidate> extract(Request request) {
            this.request = request;
            return List.of();
        }
    }

    private static final class InMemoryCompilationRepository implements ScenarioCompilationRepository {
        private final Map<UUID, ScenarioCompilation> store = new HashMap<>();
        @Override public Optional<ScenarioCompilation> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public Optional<ScenarioCompilation> findByInputFingerprint(String fp) { return store.values().stream().filter(c -> c.inputFingerprint().equals(fp)).findFirst(); }
        @Override public void save(ScenarioCompilation c) { store.put(c.id(), c); }
        @Override public synchronized boolean saveIfLeaseMatches(ScenarioCompilation next, UUID expectedLeaseToken) {
            ScenarioCompilation current = store.get(next.id());
            if (current == null || !Objects.equals(current.leaseToken(), expectedLeaseToken)) return false;
            store.put(next.id(), next);
            return true;
        }
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
