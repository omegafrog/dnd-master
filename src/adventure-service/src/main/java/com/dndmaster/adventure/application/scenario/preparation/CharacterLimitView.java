package com.dndmaster.adventure.application.scenario.preparation;

import com.dndmaster.adventure.domain.scenario.CharacterLimit;
import java.util.UUID;

public record CharacterLimitView(int maximumCharacters, UUID sourceDocumentId, Long sourceExtractionVersion,
                                 String sourceLocator, String sourceQuote, boolean exactPartySize) {
    public static CharacterLimitView from(CharacterLimit limit) {
        return limit.source().map(source -> new CharacterLimitView(limit.maximumCharacters(),
                source.knowledgeDocumentId().value(), source.extractionVersion(), source.locator(), limit.sourceQuote(), limit.isExactPartySize()))
                .orElseGet(() -> new CharacterLimitView(limit.maximumCharacters(), null, null, null, "", false));
    }

    public static CharacterLimitView defaultLimit() {
        return new CharacterLimitView(6, null, null, null, "", false);
    }
}
