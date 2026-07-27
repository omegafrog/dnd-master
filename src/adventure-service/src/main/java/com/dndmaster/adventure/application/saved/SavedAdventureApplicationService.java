package com.dndmaster.adventure.application.saved;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.ConversationEntry;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import java.util.List;
import java.util.Objects;

public final class SavedAdventureApplicationService {
    private final AdventureRepository repository;

    public SavedAdventureApplicationService(AdventureRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public Adventure createAdventure(CreateAdventureCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        Adventure adventure = Adventure.create(
                AdventureId.generate(), SessionId.generate(), command.ownerPlayerId(), command.scenarioId(),
                command.ruleSetId(), command.characterSheetId(), command.initialContext());
        repository.save(adventure);
        return adventure;
    }

    public Adventure preserveProgress(
            AdventureId adventureId, OwnerPlayerId owner, long expectedVersion,
            AdventureContext context, List<ConversationEntry> conversation) {
        Adventure adventure = load(adventureId);
        adventure.preserveProgress(owner, expectedVersion, context, conversation);
        repository.save(adventure);
        return adventure;
    }

    public Adventure reopenAdventure(AdventureId adventureId, OwnerPlayerId owner) {
        Adventure adventure = load(adventureId);
        adventure.reopen(owner);
        return adventure;
    }

    public void deleteAdventure(AdventureId adventureId, OwnerPlayerId owner, long expectedVersion) {
        Adventure adventure = load(adventureId);
        adventure.delete(owner, expectedVersion);
        repository.save(adventure);
    }

    public List<Adventure> listSavedAdventures(OwnerPlayerId owner) {
        return repository.findSavedByOwner(Objects.requireNonNull(owner, "owner must not be null"));
    }

    private Adventure load(AdventureId id) {
        return repository.findById(Objects.requireNonNull(id, "adventure id must not be null"))
                .orElseThrow(AdventureNotFoundException::new);
    }
}
