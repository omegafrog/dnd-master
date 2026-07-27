package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import java.util.Map;
import java.util.Objects;

public final class CompositeRulebookContentExtractor implements RulebookContentExtractor {
    private final Map<RulebookFormat, FormatExtractor> extractors;

    public CompositeRulebookContentExtractor(Map<RulebookFormat, FormatExtractor> extractors) {
        this.extractors = Map.copyOf(Objects.requireNonNull(extractors, "extractors must not be null"));
    }

    @Override
    public ExtractionResult extract(RulebookFormat format, byte[] content) {
        FormatExtractor extractor = extractors.get(format);
        if (extractor == null) {
            throw new IllegalArgumentException("unsupported format: " + format);
        }
        return extractor.extract(content);
    }

    @FunctionalInterface
    public interface FormatExtractor {
        ExtractionResult extract(byte[] content);
    }
}
