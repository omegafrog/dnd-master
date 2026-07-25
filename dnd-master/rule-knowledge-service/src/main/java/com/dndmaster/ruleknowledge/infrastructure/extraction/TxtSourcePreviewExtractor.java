package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.PreviewSpan;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.util.List;
import java.util.Objects;

public final class TxtSourcePreviewExtractor implements CompositeSourcePreviewExtractor.FormatPreviewExtractor {
    private final TxtRulebookContentExtractor contentExtractor = new TxtRulebookContentExtractor();
    private final TxtSourceSpanTracer tracer = new TxtSourceSpanTracer();

    @Override
    public SourcePreviewResult preview(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        String text = contentExtractor.extract(content).content().orElse("");
        List<PreviewSpan> spans = tracer.trace(text).stream()
                .map(span -> new PreviewSpan(
                        "LINE",
                        List.of("line " + span.lineNumber()),
                        null,
                        null,
                        span.lineNumber(),
                        span.startInclusive(),
                        span.endExclusive(),
                        span.text(),
                        span.locator(),
                        "TEXT",
                        null))
                .toList();
        return new SourcePreviewResult(text, List.of(), spans, List.of());
    }
}
