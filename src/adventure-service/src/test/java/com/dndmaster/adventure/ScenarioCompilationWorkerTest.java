package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.application.scenario.blueprint.CharacterInputTagExtractionPort;
import com.dndmaster.adventure.application.scenario.compilation.*;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;

class ScenarioCompilationWorkerTest {
    @Test
    void excludesBaseRulebookFromCharacterOverlaySearch() {
        KnowledgeDocumentId rulebook = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId storybook = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(List.of(
                document(rulebook, ScenarioBundleDocumentRole.RULEBOOK, "RULEBOOK", 2),
                document(storybook, ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK", 4)));
        Fixture fixture = new Fixture(bundle);
        fixture.search.result = List.of(
                new CharacterContextSearchPort.Evidence(rulebook, "RULEBOOK", 2, "rule:1", "rule option", .9),
                new CharacterContextSearchPort.Evidence(storybook, "STORYBOOK", 4, "story:1", "story option", .8));

        fixture.worker().processNext("worker", Duration.ofMinutes(1));

        assertEquals(1, fixture.search.request.documents().size());
        assertEquals(storybook, fixture.search.request.documents().getFirst().documentId());
        assertEquals("STORYBOOK", fixture.search.request.documents().getFirst().documentType());
        assertEquals(1200, fixture.search.request.tokenBudget());
        assertEquals(1, fixture.tags.request.excerpts().size());
        assertEquals(storybook, fixture.tags.request.excerpts().getFirst().documentId());
        assertTrue(fixture.tags.request.operationId().contains("character-story-overlays"));
    }

    @Test
    void mapsHandoutBundleRoleToItsRegisteredStorybookDocumentTypeForOverlaySearch() {
        KnowledgeDocumentId handout = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(List.of(
                document(handout, ScenarioBundleDocumentRole.HANDOUT, "STORYBOOK", 7)));
        Fixture fixture = new Fixture(bundle);

        ScenarioPackage result = fixture.worker().processNext("worker", Duration.ofMinutes(1)).orElseThrow();

        assertEquals(1, fixture.search.request.documents().size());
        assertEquals(handout, fixture.search.request.documents().getFirst().documentId());
        assertEquals("STORYBOOK", fixture.search.request.documents().getFirst().documentType());
        assertEquals(7, fixture.search.request.documents().getFirst().extractionVersion());
        assertEquals("COMPLETE", result.report().status().name());
        assertEquals("PUBLISHED", fixture.compilations.values.values().iterator().next().status().name());
        assertNull(fixture.tags.request);
    }

    @Test
    void rulebookOnlyBundleUsesBaseSchemaWithoutCharacterAi() {
        KnowledgeDocumentId rulebook = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(List.of(
                document(rulebook, ScenarioBundleDocumentRole.RULEBOOK, "RULEBOOK", 2)));
        Fixture fixture = new Fixture(bundle);

        ScenarioPackage result = fixture.worker().processNext("worker", Duration.ofMinutes(1)).orElseThrow();

        assertNull(fixture.search.request);
        assertNull(fixture.tags.request);
        assertTrue(result.characterCreationBlueprint().fields().stream()
                .anyMatch(field -> field.key().equals("race") && field.sourceType().equals("TEMPLATE")));
    }

    @Test
    void failsCompilationWithDiagnosticWhenScenarioOverlaySearchReturnsBadRequest() {
        KnowledgeDocumentId storybook = new KnowledgeDocumentId(UUID.randomUUID());
        Fixture fixture = new Fixture(bundle(List.of(document(storybook, ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK", 1))));
        fixture.search.failure = new CharacterContextSearchPort.CharacterContextSearchException("HTTP 400: invalid search request");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> fixture.worker().processNext("worker", Duration.ofMinutes(1)));

        assertTrue(failure.getMessage().contains("character overlay search failed: HTTP 400"));
        assertEquals("WAITING_RETRY", fixture.compilations.values.values().iterator().next().status().name());
    }

    @Test
    void workerPublishesValidatedPackageFromClaimedJob() {
        ScenarioSourceBundle bundle = bundle(List.of(document(
                new KnowledgeDocumentId(UUID.randomUUID()),
                ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK", 1)));
        Fixture fixture = new Fixture(bundle);
        String fingerprint = fixture.compilations.values.values().iterator().next().inputFingerprint();

        ScenarioPackage published = fixture.worker().processNext("worker", Duration.ofMinutes(1)).orElseThrow();

        assertEquals("COMPLETE", published.report().status().name());
        assertEquals("PUBLISHED", fixture.compilations.findByInputFingerprint(fingerprint).orElseThrow().status().name());
        assertTrue(fixture.packages.values.containsKey(published.inputFingerprint()));
    }

    @Test
    void missingResolutionKindBecomesAValidationFailureAndSchedulesCompilationRetry() {
        KnowledgeDocumentId storybook = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioSourceBundle bundle = bundle(List.of(document(
                storybook, ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK", 1)));
        Fixture fixture = new Fixture(bundle);
        com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate missingKind =
                new com.dndmaster.adventure.application.scenario.compilation.ResolutionCandidate(
                null, null, null, "1d20", ResolutionVisibility.GM_REFERENCE,
                "Roll on the table.",
                List.of(new ScenarioSourceReference(storybook, 1, "page:1")),
                "schema-v1", null);
        ScenarioCompilationWorker worker = new ScenarioCompilationWorker(
                fixture.manager, fixture.compilations, fixture.queue, new Bundles(bundle),
                request -> List.of(missingKind), ignored -> List.of(), fixture.tags, fixture.search,
                new ScenarioPackageCompilationService(fixture.packages), fixture.packages);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> worker.processNext("worker", Duration.ofMinutes(1)));

        assertEquals("scenario compilation is not publishable: INVALID", failure.getMessage());
        assertEquals("WAITING_RETRY", fixture.compilations.values.values().iterator().next().status().name());
        assertEquals(1, fixture.queue.pending.size());
    }

    @Test
    void scheduledWorkerProcessesRequestedJob() {
        ScenarioSourceBundle bundle = bundle(List.of(document(
                new KnowledgeDocumentId(UUID.randomUUID()),
                ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK", 1)));
        Fixture fixture = new Fixture(bundle);
        String fingerprint = fixture.compilations.values.values().iterator().next().inputFingerprint();

        fixture.worker().processQueuedCompilations();

        assertEquals("PUBLISHED", fixture.compilations.findByInputFingerprint(fingerprint).orElseThrow().status().name());
    }

    @Test
    void workerMarksPermanentFailureAfterThirdAttempt() {
        ScenarioSourceBundle bundle = bundle(List.of(document(
                new KnowledgeDocumentId(UUID.randomUUID()),
                ScenarioBundleDocumentRole.MAIN_SCENARIO, "STORYBOOK", 1)));
        Fixture fixture = new Fixture(bundle);
        ScenarioCompilation requested = ScenarioCompilation.rehydrate(
                UUID.randomUUID(), bundle.id(), bundle.currentRevision().revision(), "fp-failure",
                ScenarioCompilationStatus.RUNNING, 2, UUID.randomUUID(), null, null);
        fixture.compilations.save(requested);
        fixture.queue.pending.clear();
        fixture.queue.pending.add(new WorkEnvelope(UUID.randomUUID(), "retry", requested.id(),
                bundle.currentRevision().revision(), "fp-failure", 2));
        ScenarioCompilationWorker worker = new ScenarioCompilationWorker(
                fixture.manager, fixture.compilations, fixture.queue, new Bundles(bundle),
                request -> { throw new IllegalStateException("AI unavailable"); }, ignored -> List.of(),
                fixture.tags, fixture.search, new ScenarioPackageCompilationService(fixture.packages), fixture.packages);

        assertThrows(IllegalStateException.class, () -> worker.processNext("worker", Duration.ofMinutes(1)));

        assertEquals("FAILED", fixture.compilations.findByInputFingerprint("fp-failure").orElseThrow().status().name());
        assertEquals(0, fixture.queue.pending.size());
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
        Compilations compilations = new Compilations();
        Queue queue = new Queue();
        ScenarioCompilationProcessManager manager = new ScenarioCompilationProcessManager(compilations, queue);
        compilations.save(compilationA);
        WorkEnvelope work = new WorkEnvelope(UUID.randomUUID(), "retry", compilationA.id(), 1, "fp-race", 1);
        WorkQueuePort.Delivery deliveryA = new WorkQueuePort.Delivery(work, leaseA, "worker-a");
        WorkQueuePort.Delivery deliveryB = new WorkQueuePort.Delivery(work, UUID.randomUUID(), "worker-b");
        manager.claim(deliveryB);

        assertThrows(IllegalStateException.class, () -> manager.publish(compilationA, deliveryA, UUID.randomUUID()));
    }

    private static ScenarioSourceBundle bundle(List<ScenarioBundleDocumentSelection> documents) {
        return ScenarioSourceBundle.create(new ScenarioBundleId(UUID.randomUUID()), new OwnerPlayerId(UUID.randomUUID()),
                new ScenarioSourceBundleRevision(1, documents));
    }

    private static ScenarioBundleDocumentSelection document(
            KnowledgeDocumentId id, ScenarioBundleDocumentRole role, String type, int version) {
        return new ScenarioBundleDocumentSelection(id, role,
                com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus.INDEXED,
                id + ".txt", type, version);
    }

    private static final class Fixture {
        final ScenarioSourceBundle bundle;
        final Queue queue = new Queue();
        final Compilations compilations = new Compilations();
        final Packages packages = new Packages();
        final Search search = new Search();
        final Tags tags = new Tags();
        final ScenarioCompilationProcessManager manager;

        Fixture(ScenarioSourceBundle bundle) {
            this.bundle = bundle;
            manager = new ScenarioCompilationProcessManager(compilations, queue);
            manager.start(bundle.id(), bundle.currentRevision().revision(), "fp-" + UUID.randomUUID());
        }

        ScenarioCompilationWorker worker() {
            return new ScenarioCompilationWorker(manager, compilations, queue, new Bundles(bundle),
                    request -> List.of(), ignored -> List.of(), tags, search,
                    new ScenarioPackageCompilationService(packages), packages);
        }
    }

    private static final class Search implements CharacterContextSearchPort {
        Request request;
        List<Evidence> result = List.of();
        RuntimeException failure;
        @Override public List<Evidence> search(Request request) { this.request = request; if (failure != null) throw failure; return result; }
    }

    private static final class Tags implements CharacterInputTagExtractionPort {
        Request request;
        @Override public List<CharacterInputTagCandidate> extract(Request request) { this.request = request; return List.of(); }
    }

    private record Bundles(ScenarioSourceBundle bundle) implements ScenarioBundleRepository {
        @Override public Optional<ScenarioSourceBundle> findById(ScenarioBundleId id) { return Optional.of(bundle); }
        @Override public void save(ScenarioSourceBundle value) {}
    }

    private static final class Compilations implements ScenarioCompilationRepository {
        final Map<UUID, ScenarioCompilation> values = new HashMap<>();
        @Override public Optional<ScenarioCompilation> findById(UUID id) { return Optional.ofNullable(values.get(id)); }
        @Override public Optional<ScenarioCompilation> findByInputFingerprint(String fingerprint) {
            return values.values().stream().filter(value -> value.inputFingerprint().equals(fingerprint)).findFirst();
        }
        @Override public void save(ScenarioCompilation value) { values.put(value.id(), value); }
        @Override public boolean saveIfLeaseMatches(ScenarioCompilation value, UUID token) {
            ScenarioCompilation current = values.get(value.id());
            if (current == null || !Objects.equals(current.leaseToken(), token)) return false;
            values.put(value.id(), value); return true;
        }
    }

    private static final class Packages implements ScenarioPackageRepository {
        final Map<String, ScenarioPackage> values = new HashMap<>();
        @Override public Optional<ScenarioPackage> findByInputFingerprint(String fingerprint) {
            return Optional.ofNullable(values.get(fingerprint));
        }
        @Override public Optional<ScenarioPackage> findById(UUID id) {
            return values.values().stream().filter(value -> value.packageId().equals(id)).findFirst();
        }
        @Override public void save(ScenarioPackage value) { values.put(value.inputFingerprint(), value); }
    }

    private static final class Queue implements WorkQueuePort {
        final java.util.Queue<WorkEnvelope> pending = new ArrayDeque<>();
        @Override public void enqueue(WorkEnvelope work) { pending.add(work); }
        @Override public Optional<Delivery> claim(String workerId, Duration lease) {
            WorkEnvelope work = pending.poll();
            return work == null ? Optional.empty() : Optional.of(new Delivery(work, UUID.randomUUID(), workerId));
        }
        @Override public void acknowledge(Delivery delivery) {}
        @Override public void retry(Delivery delivery, String reason) { pending.add(delivery.work()); }
    }
}
