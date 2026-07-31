package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        @Override public List<Evidence> search(Request request) { this.request = request; return result; }
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
