package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.registration.SourcePreviewExtractor;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.util.Map;
import java.util.Objects;

public final class CompositeSourcePreviewExtractor implements SourcePreviewExtractor {
    private final Map<RulebookFormat, FormatPreviewExtractor> extractors;

    public CompositeSourcePreviewExtractor(Map<RulebookFormat, FormatPreviewExtractor> extractors) {
        this.extractors = Map.copyOf(Objects.requireNonNull(extractors, "extractors must not be null"));
    }

    @Override
    public SourcePreviewResult preview(RulebookFormat format, byte[] content) {
        FormatPreviewExtractor extractor = extractors.get(format);
        if (extractor == null) {
            throw new IllegalArgumentException("unsupported format: " + format);
        }
        return extractor.preview(content);
    }

    @FunctionalInterface
    public interface FormatPreviewExtractor {
        SourcePreviewResult preview(byte[] content);
    }
}
