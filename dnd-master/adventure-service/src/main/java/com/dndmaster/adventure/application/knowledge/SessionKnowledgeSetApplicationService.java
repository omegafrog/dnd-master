package com.dndmaster.adventure.application.knowledge;

import com.dndmaster.adventure.application.saved.AdventureNotFoundException;
import com.dndmaster.adventure.application.saved.AdventureRepository;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureAccessDeniedException;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SessionKnowledgeSetApplicationService {
    private final AdventureRepository adventureRepository;
    private final SessionKnowledgeSetRepository repository;
    private final KnowledgeDocumentLookupPort lookupPort;

    public SessionKnowledgeSetApplicationService(
            AdventureRepository adventureRepository,
            SessionKnowledgeSetRepository repository,
            KnowledgeDocumentLookupPort lookupPort) {
        this.adventureRepository = Objects.requireNonNull(adventureRepository, "adventure repository must not be null");
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.lookupPort = Objects.requireNonNull(lookupPort, "lookup port must not be null");
    }

    public SessionKnowledgeSet updateSessionKnowledgeSet(
            AdventureId adventureId, OwnerPlayerId owner, List<KnowledgeDocumentId> knowledgeDocumentIds) {
        Adventure adventure = loadOwnedAdventure(adventureId, owner);
        List<KnowledgeDocumentId> requested = normalizeSelection(knowledgeDocumentIds);
        Map<KnowledgeDocumentId, KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> ownedDocuments = lookupPort.findOwnedDocuments(owner).stream()
                .collect(LinkedHashMap::new, (map, record) -> map.put(record.knowledgeDocumentId(), record), Map::putAll);

        for (KnowledgeDocumentId knowledgeDocumentId : requested) {
            KnowledgeDocumentLookupPort.KnowledgeDocumentRecord document = ownedDocuments.get(knowledgeDocumentId);
            if (document == null) {
                throw new ForeignKnowledgeDocumentSelectionException();
            }
            if (document.status() != KnowledgeDocumentStatus.INDEXED) {
                throw new UnindexedKnowledgeDocumentSelectionException(knowledgeDocumentId);
            }
        }

        SessionKnowledgeSet set = new SessionKnowledgeSet(adventure.sessionId(), requested);
        repository.save(set);
        return set;
    }

    public SessionKnowledgeSet readSessionKnowledgeSet(AdventureId adventureId, OwnerPlayerId owner) {
        Adventure adventure = loadOwnedAdventure(adventureId, owner);
        return repository.findBySessionId(adventure.sessionId())
                .orElseGet(() -> new SessionKnowledgeSet(adventure.sessionId(), List.of()));
    }

    private Adventure loadOwnedAdventure(AdventureId adventureId, OwnerPlayerId owner) {
        Adventure adventure = adventureRepository.findById(Objects.requireNonNull(adventureId, "adventure id must not be null"))
                .orElseThrow(AdventureNotFoundException::new);
        if (!adventure.ownerPlayerId().equals(Objects.requireNonNull(owner, "owner must not be null"))) {
            throw new AdventureAccessDeniedException();
        }
        return adventure;
    }

    private static List<KnowledgeDocumentId> normalizeSelection(List<KnowledgeDocumentId> knowledgeDocumentIds) {
        List<KnowledgeDocumentId> requested = List.copyOf(Objects.requireNonNull(knowledgeDocumentIds, "knowledge document ids must not be null"));
        if (requested.isEmpty()) {
            throw new EmptySessionKnowledgeSelectionException();
        }
        if (new HashSet<>(requested).size() != requested.size()) {
            throw new DuplicateKnowledgeDocumentSelectionException();
        }
        return requested;
    }
}
