package com.dndmaster.character.domain;

import java.util.Objects;

public final class CharacterSheet {
    private final CharacterSheetId id;
    private final AdventureId adventureId;
    private final SheetEdition edition;
    private CharacterSheetData data;

    public CharacterSheet(
            CharacterSheetId id, AdventureId adventureId, SheetEdition edition, CharacterSheetData data) {
        this.id = Objects.requireNonNull(id, "character sheet id must not be null");
        this.adventureId = Objects.requireNonNull(adventureId, "adventure id must not be null");
        this.edition = Objects.requireNonNull(edition, "edition must not be null");
        this.data = requireMatchingData(edition, data);
    }

    public void authorizeOpen(CharacterSheetOpenRequest request) {
        Objects.requireNonNull(request, "open request must not be null");
        if (!adventureId.equals(request.adventureId())
                || edition != request.appliedEdition()
                || edition != request.requestedEdition()) {
            throw new CharacterSheetEditionMismatchException();
        }
    }

    public void applyUpdate(CharacterSheetUpdate update) {
        Objects.requireNonNull(update, "update must not be null");
        if (update.inputMode() != InputMode.STRUCTURED_SHEET) throw new StructuredSheetRequiredException();
        if (edition != update.edition()) throw new CharacterSheetEditionMismatchException();
        data = requireMatchingData(edition, update.data());
    }

    private static CharacterSheetData requireMatchingData(SheetEdition edition, CharacterSheetData data) {
        Objects.requireNonNull(data, "character sheet data must not be null");
        if (data.edition() != edition) throw new CharacterSheetEditionMismatchException();
        return data;
    }

    public CharacterSheetId id() { return id; }
    public AdventureId adventureId() { return adventureId; }
    public SheetEdition edition() { return edition; }
    public CharacterSheetData data() { return data; }
}
