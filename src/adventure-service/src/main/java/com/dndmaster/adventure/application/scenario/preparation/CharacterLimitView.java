package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import java.util.UUID;

public record CharacterLimitView(int maximumCharacters, UUID sourceDocumentId, Long sourceExtractionVersion,
                                 String sourceLocator, String sourceQuote) {
    public static CharacterLimitView from(CharacterLimit limit) {
        return limit.source().map(source -> new CharacterLimitView(limit.maximumCharacters(),
                source.knowledgeDocumentId().value(), source.extractionVersion(), source.locator(), limit.sourceQuote()))
                .orElseGet(CharacterLimitView::defaultLimit);
    }

    public static CharacterLimitView defaultLimit() {
        return new CharacterLimitView(1, null, null, null, "");
    }
}
