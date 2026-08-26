package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.scenario.BundleDocumentDraft;
import com.dndmaster.adventure.application.scenario.ScenarioBundleApplicationService;
import com.dndmaster.adventure.application.scenario.ScenarioBundleRepository;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.RulebookEdition;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDeletionConflictException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleValidationException;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScenarioBundleIssue156Test {
    @Test
    void savesANameEditionAndEnforcesRulebookAndMainScenarioCardinality() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        KnowledgeDocumentId rulebook = id();
        KnowledgeDocumentId main = id();
        KnowledgeDocumentId secondMain = id();
        InMemoryRepository repository = new InMemoryRepository();
        ScenarioBundleApplicationService service = new ScenarioBundleApplicationService(repository, lookup(
                record(rulebook, "rules.pdf", "RULEBOOK"),
                record(main, "main.pdf", "STORYBOOK"),
                record(secondMain, "second.pdf", "STORYBOOK")));

        ScenarioSourceBundle bundle = service.createBundle(owner, "The Brew", RulebookEdition.DND_5E_2024, List.of(
                new BundleDocumentDraft(rulebook, ScenarioBundleDocumentRole.RULEBOOK),
                new BundleDocumentDraft(main, ScenarioBundleDocumentRole.MAIN_SCENARIO)));

        assertEquals("The Brew", bundle.name());
        assertEquals(RulebookEdition.DND_5E_2024, bundle.rulebookEdition());
        assertThrows(IllegalStateException.class, () -> service.createBundle(owner, "bad", RulebookEdition.DND_5E_2024,
                List.of(new BundleDocumentDraft(main, ScenarioBundleDocumentRole.MAIN_SCENARIO),
                        new BundleDocumentDraft(secondMain, ScenarioBundleDocumentRole.MAIN_SCENARIO))));
    }

    @Test
    void refusesDeletionWhenAnActiveAdventureReferencesTheBundle() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        InMemoryRepository repository = new InMemoryRepository();
        KnowledgeDocumentId rulebook = id();
        ScenarioBundleApplicationService service = new ScenarioBundleApplicationService(repository, lookup(
                record(rulebook, "rules.pdf", "RULEBOOK")));
        ScenarioSourceBundle bundle = service.createBundle(owner, "Rules", RulebookEdition.DND_5E_2014,
                List.of(new BundleDocumentDraft(rulebook, ScenarioBundleDocumentRole.RULEBOOK)));
        repository.active = true;

        assertThrows(ScenarioBundleDeletionConflictException.class, () -> service.deleteBundle(bundle.id(), owner));
    }

    @Test
    void savesReadyDocumentsWhenAnotherOwnedDocumentNeedsReview() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        KnowledgeDocumentId rulebook = id();
        KnowledgeDocumentId needsReview = id();
        InMemoryRepository repository = new InMemoryRepository();
        ScenarioBundleApplicationService service = new ScenarioBundleApplicationService(repository, lookup(
                record(rulebook, KnowledgeDocumentStatus.INDEXED, "rules.pdf", "RULEBOOK"),
                record(needsReview, KnowledgeDocumentStatus.NEEDS_REVIEW, "review.pdf", "STORYBOOK")));

        ScenarioSourceBundle bundle = service.createBundle(owner, "Rules", RulebookEdition.DND_5E_2014,
                List.of(new BundleDocumentDraft(rulebook, ScenarioBundleDocumentRole.RULEBOOK)));

        assertEquals(List.of(rulebook), bundle.currentRevision().documents().stream()
                .map(document -> document.knowledgeDocumentId()).toList());
    }

    @Test
    void refusesNeedsReviewDocumentsAsBundleInputs() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        KnowledgeDocumentId needsReview = id();
        ScenarioBundleApplicationService service = new ScenarioBundleApplicationService(new InMemoryRepository(), lookup(
                record(needsReview, KnowledgeDocumentStatus.NEEDS_REVIEW, "review.pdf", "RULEBOOK")));

        assertThrows(ScenarioBundleValidationException.class, () -> service.createBundle(owner, "Rules",
                RulebookEdition.DND_5E_2014,
                List.of(new BundleDocumentDraft(needsReview, ScenarioBundleDocumentRole.RULEBOOK))));
    }

    private static KnowledgeDocumentId id() { return new KnowledgeDocumentId(UUID.randomUUID()); }

    private static KnowledgeDocumentLookupPort lookup(KnowledgeDocumentLookupPort.KnowledgeDocumentRecord... values) {
        Map<KnowledgeDocumentId, KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> records = new HashMap<>();
        for (KnowledgeDocumentLookupPort.KnowledgeDocumentRecord value : values) records.put(value.knowledgeDocumentId(), value);
        return owner -> List.copyOf(records.values());
    }

    private static KnowledgeDocumentLookupPort.KnowledgeDocumentRecord record(KnowledgeDocumentId id, String name, String type) {
        return record(id, KnowledgeDocumentStatus.INDEXED, name, type);
    }

    private static KnowledgeDocumentLookupPort.KnowledgeDocumentRecord record(
            KnowledgeDocumentId id, KnowledgeDocumentStatus status, String name, String type) {
        return new KnowledgeDocumentLookupPort.KnowledgeDocumentRecord(id, status, name, type, 1);
    }

    private static final class InMemoryRepository implements ScenarioBundleRepository {
        private final Map<ScenarioBundleId, ScenarioSourceBundle> store = new HashMap<>();
        private boolean active;
        public Optional<ScenarioSourceBundle> findById(ScenarioBundleId id) { return Optional.ofNullable(store.get(id)); }
        public void save(ScenarioSourceBundle bundle) { store.put(bundle.id(), bundle); }
        public boolean hasActiveAdventureReferences(ScenarioBundleId id) { return active; }
        public void deleteById(ScenarioBundleId id) { store.remove(id); }
    }
}
