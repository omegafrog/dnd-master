package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetApplicationService;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionKnowledgeSetApplicationServiceTest {
    @Test
    void savesAndReadsSessionKnowledgeSet() {
        OwnerPlayerId owner = owner();
        Adventure adventure = adventure(owner);
        InMemoryAdventureRepository adventures = new InMemoryAdventureRepository(adventure);
        InMemorySessionKnowledgeSetRepository sets = new InMemorySessionKnowledgeSetRepository();
        KnowledgeDocumentId rulebook = document();
        KnowledgeDocumentId storybook = document();
        SessionKnowledgeSetApplicationService service = service(
                adventures,
                sets,
                new LookupMock(owner, Map.of(
                        rulebook, KnowledgeDocumentStatus.INDEXED,
                        storybook, KnowledgeDocumentStatus.INDEXED)));

        SessionKnowledgeSet saved = service.updateSessionKnowledgeSet(adventure.id(), owner, List.of(rulebook, storybook));

        assertEquals(adventure.sessionId(), saved.sessionId());
        assertEquals(List.of(rulebook, storybook), saved.knowledgeDocumentIds());
        assertEquals(saved, service.readSessionKnowledgeSet(adventure.id(), owner));
        assertEquals(saved, sets.findBySessionId(adventure.sessionId()).orElseThrow());
    }

    @Test
    void rejectsEmptyDuplicateForeignAndUnindexedSelections() {
        OwnerPlayerId owner = owner();
        Adventure adventure = adventure(owner);
        KnowledgeDocumentId rulebook = document();
        KnowledgeDocumentId storybook = document();
        KnowledgeDocumentId foreign = document();
        SessionKnowledgeSetApplicationService service = service(
                new InMemoryAdventureRepository(adventure),
                new InMemorySessionKnowledgeSetRepository(),
                new LookupMock(owner, Map.of(
                        rulebook, KnowledgeDocumentStatus.INDEXED,
                        storybook, KnowledgeDocumentStatus.UPLOADED)));

        assertThrows(IllegalArgumentException.class, () -> service.updateSessionKnowledgeSet(adventure.id(), owner, List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.updateSessionKnowledgeSet(adventure.id(), owner, List.of(rulebook, rulebook)));
        assertThrows(IllegalArgumentException.class, () -> service.updateSessionKnowledgeSet(adventure.id(), owner, List.of(foreign)));
        assertThrows(IllegalStateException.class, () -> service.updateSessionKnowledgeSet(adventure.id(), owner, List.of(storybook)));
    }

    private static SessionKnowledgeSetApplicationService service(
            AdventureRepository adventures,
            SessionKnowledgeSetRepository repository,
            KnowledgeDocumentLookupPort lookup) {
        return new SessionKnowledgeSetApplicationService(adventures, repository, lookup);
    }

    private static Adventure adventure(OwnerPlayerId owner) {
        return Adventure.create(
                AdventureId.generate(),
                SessionId.generate(),
                owner,
                new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("start", "npc", null, null));
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.randomUUID());
    }

    private static KnowledgeDocumentId document() {
        return new KnowledgeDocumentId(UUID.randomUUID());
    }

    private static final class InMemoryAdventureRepository implements AdventureRepository {
        private final Map<AdventureId, Adventure> values = new HashMap<>();

        private InMemoryAdventureRepository(Adventure... adventures) {
            for (Adventure adventure : adventures) {
                values.put(adventure.id(), adventure);
            }
        }

        @Override
        public Optional<Adventure> findById(AdventureId adventureId) {
            return Optional.ofNullable(values.get(adventureId));
        }

        @Override
        public List<Adventure> findSavedByOwner(OwnerPlayerId ownerPlayerId) {
            return values.values().stream().filter(adventure -> adventure.ownerPlayerId().equals(ownerPlayerId)).toList();
        }

        @Override
        public void save(Adventure adventure) {
            values.put(adventure.id(), adventure);
        }
    }

    private static final class InMemorySessionKnowledgeSetRepository implements SessionKnowledgeSetRepository {
        private final Map<SessionId, SessionKnowledgeSet> values = new HashMap<>();

        @Override
        public Optional<SessionKnowledgeSet> findBySessionId(SessionId sessionId) {
            return Optional.ofNullable(values.get(sessionId));
        }

        @Override
        public void save(SessionKnowledgeSet set) {
            values.put(set.sessionId(), set);
        }
    }

    private static final class LookupMock implements KnowledgeDocumentLookupPort {
        private final UUID ownerId;
        private final Map<KnowledgeDocumentId, KnowledgeDocumentStatus> statuses;

        private LookupMock(OwnerPlayerId owner, Map<KnowledgeDocumentId, KnowledgeDocumentStatus> statuses) {
            this.ownerId = owner.value();
            this.statuses = statuses;
        }

        @Override
        public List<KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> findOwnedDocuments(UUID ownerPlayerId) {
            if (!ownerId.equals(ownerPlayerId)) {
                return List.of();
            }
            return statuses.entrySet().stream()
                    .map(entry -> new KnowledgeDocumentLookupPort.KnowledgeDocumentRecord(
                            entry.getKey(), entry.getValue(), "doc-" + entry.getKey().value(), "RULEBOOK", 1L))
                    .toList();
        }
    }
}
