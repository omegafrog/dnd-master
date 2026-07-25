package com.dndmaster.character.application;

import com.dndmaster.character.domain.*;
import java.util.Objects;

public final class CharacterSheetApplicationService {
    private final CharacterSheetRepository repository;
    private final AdventureEditionHttpPort adventureEditionHttpPort;

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.adventureEditionHttpPort = Objects.requireNonNull(adventureEditionHttpPort, "edition HTTP port must not be null");
    }

    public CharacterSheet createSheet(CreateCharacterSheetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        SheetEdition applied = adventureEditionHttpPort.getAppliedEdition(command.adventureId());
        var sheet = new CharacterSheet(
                CharacterSheetId.generate(), command.adventureId(), command.requestedEdition(), command.data());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(command.adventureId(), applied, command.requestedEdition()));
        repository.save(sheet);
        return sheet;
    }

    public CharacterSheet openSheet(CharacterSheetId id, SheetEdition requestedEdition) {
        CharacterSheet sheet = load(id);
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
        SheetEdition applied = adventureEditionHttpPort.getAppliedEdition(sheet.adventureId());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(sheet.adventureId(), applied, update.edition()));
        if (sheet.version() != update.expectedVersion()) {
            throw new IllegalStateException("character sheet version does not match");
        }
        sheet.applyUpdate(update);
        repository.save(sheet, update.expectedVersion() + 1, update.commandId(), update.fingerprint());
        sheet.markPersisted(update.expectedVersion() + 1, update.commandId(), update.fingerprint());
        return sheet;
    }

    private CharacterSheet load(CharacterSheetId id) {
        return repository.findById(Objects.requireNonNull(id, "character sheet id must not be null"))
                .orElseThrow(CharacterSheetNotFoundException::new);
    }
}
