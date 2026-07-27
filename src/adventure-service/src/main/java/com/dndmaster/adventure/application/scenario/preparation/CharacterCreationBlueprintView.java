package com.dndmaster.adventure.application.scenario.preparation;

import java.util.List;

public record CharacterCreationBlueprintView(
        boolean available,
        String summary,
        int rulebookDocumentCount,
        int storybookDocumentCount,
        List<String> diagnostics) {
    public CharacterCreationBlueprintView {
        diagnostics = List.copyOf(diagnostics);
    }

    public static CharacterCreationBlueprintView blocked(List<String> diagnostics) {
        return new CharacterCreationBlueprintView(false, null, 0, 0, diagnostics);
    }
}
