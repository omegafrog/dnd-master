package com.dndmaster.character.application;

import com.dndmaster.character.domain.*;
import java.util.Objects;

public final class CharacterSheetApplicationService {
    private final CharacterSheetRepository repository;
    private final AdventureEditionHttpPort adventureEditionHttpPort;
    private final SessionCharacterPolicyPort sessionPolicyPort;

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort) {
        this(repository, adventureEditionHttpPort, ignored -> SessionCharacterPolicy.draft());
    }

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort,
            SessionCharacterPolicyPort sessionPolicyPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.adventureEditionHttpPort = Objects.requireNonNull(adventureEditionHttpPort, "edition HTTP port must not be null");
        this.sessionPolicyPort = Objects.requireNonNull(sessionPolicyPort, "session policy port must not be null");
    }

    public CharacterSheet createSheet(CreateCharacterSheetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!sessionPolicyPort.policyFor(command.adventureId()).acceptingCharacterSheets()) {
            throw new IllegalStateException("adventure session no longer accepts character sheets");
        }
        SheetEdition applied = adventureEditionHttpPort.getAppliedEdition(command.adventureId());
        var sheet = new CharacterSheet(
                CharacterSheetId.generate(), command.adventureId(), command.requestedEdition(), command.data());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(command.adventureId(), applied, command.requestedEdition()));
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

    public CharacterSheet manageCharacter(CharacterSheetId id, CharacterSheetUpdate update) {
        CharacterSheet replay = repository.findByCommandId(update.commandId()).orElse(null);
        if (replay != null) {
            if (!update.fingerprint().equals(replay.operationFingerprint())) {
                throw new IllegalStateException("character sheet command id reused with different payload");
            }
            return replay;
        }
        CharacterSheet sheet = load(id);
        SessionCharacterPolicy policy = sessionPolicyPort.policyFor(sheet.adventureId(), sheet.id());
        if (!policy.acceptingCharacterSheets()) throw new IllegalStateException("character sheet belongs to a terminated adventure session");
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
        sheet.applyUpdate(update);
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
