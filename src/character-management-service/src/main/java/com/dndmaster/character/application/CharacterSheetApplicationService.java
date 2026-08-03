package com.dndmaster.character.application;

import com.dndmaster.character.domain.*;
import java.util.Objects;

public final class CharacterSheetApplicationService {
    private final CharacterSheetRepository repository;
    private final AdventureEditionHttpPort adventureEditionHttpPort;
    private final SessionCharacterPolicyPort sessionPolicyPort;
    private final CharacterMutationRulesResolver mutationRulesResolver;

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort) {
        this(repository, adventureEditionHttpPort, ignored -> SessionCharacterPolicy.draft(),
                CharacterMutationRulesResolver.standard());
    }

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort,
            SessionCharacterPolicyPort sessionPolicyPort) {
        this(repository, adventureEditionHttpPort, sessionPolicyPort, CharacterMutationRulesResolver.standard());
    }

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository,
            AdventureEditionHttpPort adventureEditionHttpPort,
            SessionCharacterPolicyPort sessionPolicyPort,
            CharacterMutationRulesResolver mutationRulesResolver) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.adventureEditionHttpPort = Objects.requireNonNull(adventureEditionHttpPort, "edition HTTP port must not be null");
        this.sessionPolicyPort = Objects.requireNonNull(sessionPolicyPort, "session policy port must not be null");
        this.mutationRulesResolver = Objects.requireNonNull(mutationRulesResolver, "mutation rules resolver must not be null");
    }

    public CharacterSheet createSheet(CreateCharacterSheetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!sessionPolicyPort.policyFor(command.sessionId()).acceptingCharacterSheets()) {
            throw new IllegalStateException("adventure session no longer accepts character sheets");
        }
        SheetEdition applied = adventureEditionHttpPort.getAppliedEdition(command.sessionId().asAdventureId());
        var sheet = new CharacterSheet(
                CharacterSheetId.generate(), command.sessionId(), command.ownerPlayerId(), command.requestedEdition(), command.data());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(command.sessionId().asAdventureId(), applied, command.requestedEdition()));
        repository.save(sheet);
        return sheet;
    }

    public CharacterSheet openSheet(CharacterSheetId id, SheetEdition requestedEdition) {
        CharacterSheet sheet = load(id);
        requireSessionActive(sheet);
        SheetEdition applied = adventureEditionHttpPort.getAppliedEdition(sheet.adventureId());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(sheet.adventureId(), applied, requestedEdition));
        return sheet;
    }

    public CharacterSheet verifySessionOwnership(CharacterSheetId id, SessionId sessionId, java.util.UUID ownerPlayerId) {
        CharacterSheet sheet = load(id);
        if (!sheet.sessionId().equals(sessionId)) throw new IllegalStateException("character sheet belongs to another session");
        if (sheet.ownerPlayerId() == null || !sheet.ownerPlayerId().equals(ownerPlayerId)) throw new IllegalStateException("character sheet belongs to another owner");
        requireSessionActive(sheet);
        return sheet;
    }

    public CharacterSheet manageCharacter(CharacterSheetId id, CharacterSheetUpdate update) {
        CharacterSheet sheet = load(id);
        SessionCharacterPolicy policy = sessionPolicyPort.policyFor(sheet.adventureId(), sheet.id());
        if (!policy.acceptingCharacterSheets()) throw new IllegalStateException("character sheet belongs to a terminated adventure session");
        CharacterSheet replay = repository.findByCommandId(update.commandId()).orElse(null);
        if (replay != null) {
            if (!update.fingerprint().equals(replay.operationFingerprint())) {
                throw new IllegalStateException("character sheet command id reused with different payload");
            }
            return replay;
        }
        SheetEdition applied = adventureEditionHttpPort.getAppliedEdition(sheet.adventureId());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(sheet.adventureId(), applied, update.edition()));
        if (sheet.version() != update.expectedVersion()) {
            throw new IllegalStateException("character sheet version does not match");
        }
        if (!policy.nameMutable() && !sheet.data().characterName().equals(update.data().characterName())) {
            throw new IllegalStateException("character name is fixed for this adventure session");
        }
        if (!policy.levelMutable() && sheet.data().level() != update.data().level()) {
            throw new IllegalStateException("character level is fixed for this adventure session");
        }
        if (!policy.raceMutable() && !sheet.data().race().equals(update.data().race())) throw new IllegalStateException("character race is fixed for this adventure session");
        if (!policy.characterClassMutable() && !sheet.data().characterClass().equals(update.data().characterClass())) throw new IllegalStateException("character class is fixed for this adventure session");
        if (!policy.backgroundMutable() && !sheet.data().background().equals(update.data().background())) throw new IllegalStateException("character background is fixed for this adventure session");
        if (!policy.startingAbilitiesMutable() && !sheet.data().startingAbilities().equals(update.data().startingAbilities())) throw new IllegalStateException("starting abilities are fixed for this adventure session");
        sheet.applyUpdate(update, mutationRulesResolver.rulesFor(sheet.edition()));
        repository.save(sheet, update.expectedVersion() + 1, update.commandId(), update.fingerprint());
        sheet.markPersisted(update.expectedVersion() + 1, update.commandId(), update.fingerprint());
        return sheet;
    }

    private void requireSessionActive(CharacterSheet sheet) {
        if (!sessionPolicyPort.policyFor(sheet.adventureId(), sheet.id()).acceptingCharacterSheets()) {
            throw new IllegalStateException("character sheet belongs to a terminated adventure session");
        }
    }

    private CharacterSheet load(CharacterSheetId id) {
        return repository.findById(Objects.requireNonNull(id, "character sheet id must not be null"))
                .orElseThrow(CharacterSheetNotFoundException::new);
    }
}
