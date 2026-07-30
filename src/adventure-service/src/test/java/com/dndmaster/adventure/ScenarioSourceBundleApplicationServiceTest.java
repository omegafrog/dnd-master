package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.scenario.BundleDocumentDraft;
import com.dndmaster.adventure.application.scenario.ScenarioBundleApplicationService;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioSourceBundleApplicationServiceTest {
    @Test
    void createsAndRevisesAnImmutableBundleFromOwnedStorybookAndRulebookDocuments() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        KnowledgeDocumentId main = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId handout = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId rulebook = new KnowledgeDocumentId(UUID.randomUUID());
        InMemoryBundleRepository repository = new InMemoryBundleRepository();
        ScenarioBundleApplicationService service = new ScenarioBundleApplicationService(repository, new StubLookup(Map.of(
                main, record(main, "main.pdf", KnowledgeDocumentStatus.INDEXED, "STORYBOOK", 3L),
                handout, record(handout, "handout.pdf", KnowledgeDocumentStatus.PARTIAL_CONFIRMED, "STORYBOOK", 7L),
                rulebook, record(rulebook, "rules.pdf", KnowledgeDocumentStatus.INDEXED, "RULEBOOK", 2L)
        )));

        ScenarioSourceBundle created = service.createBundle(owner, List.of(
                new BundleDocumentDraft(main, ScenarioBundleDocumentRole.MAIN_SCENARIO),
                new BundleDocumentDraft(handout, ScenarioBundleDocumentRole.HANDOUT),
                new BundleDocumentDraft(rulebook, ScenarioBundleDocumentRole.RULEBOOK)));

        assertNotNull(created.id());
        assertEquals(owner, created.ownerPlayerId());
        assertEquals(1L, created.currentRevision().revision());
        assertEquals(3, created.currentRevision().documents().size());
        assertTrue(created.currentRevision().documents().stream()
                .anyMatch(document -> document.role() == ScenarioBundleDocumentRole.MAIN_SCENARIO));
        assertTrue(created.currentRevision().documents().stream()
                .anyMatch(document -> document.role() == ScenarioBundleDocumentRole.RULEBOOK));
        assertEquals(created, repository.findById(created.id()).orElseThrow());

        ScenarioSourceBundle revised = service.reviseBundle(
                created.id(),
                owner,
                List.of(new BundleDocumentDraft(main, ScenarioBundleDocumentRole.REFERENCE)));

        assertEquals(2L, revised.currentRevision().revision());
        assertEquals(1, revised.currentRevision().documents().size());
        assertEquals(ScenarioBundleDocumentRole.REFERENCE, revised.currentRevision().documents().get(0).role());
        assertEquals(1L, created.currentRevision().revision());
    }

    @Test
    void rejectsForeignOrUnsupportedDocuments() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        KnowledgeDocumentId foreign = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId rulebook = new KnowledgeDocumentId(UUID.randomUUID());
        KnowledgeDocumentId unindexed = new KnowledgeDocumentId(UUID.randomUUID());
        ScenarioBundleApplicationService service = new ScenarioBundleApplicationService(
                new InMemoryBundleRepository(),
                new StubLookup(Map.of(
                        rulebook, record(rulebook, "rules.pdf", KnowledgeDocumentStatus.INDEXED, "RULEBOOK", 1L),
                        unindexed, record(unindexed, "story.pdf", KnowledgeDocumentStatus.UPLOADED, "STORYBOOK", 0L))));

        assertThrows(IllegalStateException.class, () -> service.createBundle(owner, List.of()));
        assertThrows(IllegalStateException.class, () -> service.createBundle(owner, List.of(
                new BundleDocumentDraft(foreign, ScenarioBundleDocumentRole.MAIN_SCENARIO))));
        assertEquals("RULEBOOK", service.createBundle(owner, List.of(
                new BundleDocumentDraft(rulebook, ScenarioBundleDocumentRole.RULEBOOK))
        ).currentRevision().documents().getFirst().documentType());
        assertThrows(IllegalStateException.class, () -> service.createBundle(owner, List.of(
                new BundleDocumentDraft(unindexed, ScenarioBundleDocumentRole.MAIN_SCENARIO))));
    }

    private static KnowledgeDocumentLookupPort.KnowledgeDocumentRecord record(
            KnowledgeDocumentId id, String filename, KnowledgeDocumentStatus status, String type, long extractionVersion) {
        return new KnowledgeDocumentLookupPort.KnowledgeDocumentRecord(id, status, filename, type, extractionVersion);
    }

    private static final class InMemoryBundleRepository implements ScenarioBundleRepository {
        private final Map<ScenarioBundleId, ScenarioSourceBundle> store = new HashMap<>();

        @Override
        public Optional<ScenarioSourceBundle> findById(ScenarioBundleId bundleId) {
            return Optional.ofNullable(store.get(bundleId));
        }

        @Override
        public void save(ScenarioSourceBundle bundle) {
            store.put(bundle.id(), bundle);
        }
    }

    private record StubLookup(Map<KnowledgeDocumentId, KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> records)
            implements KnowledgeDocumentLookupPort {
        @Override
        public List<KnowledgeDocumentRecord> findOwnedDocuments(UUID ownerPlayerId) {
            return List.copyOf(records.values());
        }
    }
}
