package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.extraction.DocumentExtractionPort;
import com.dndmaster.ruleknowledge.application.registration.RulebookContentExtractor;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import java.util.Objects;

public final class DoclingRulebookContentExtractor implements RulebookContentExtractor {
    private final DocumentExtractionPort documentExtractionPort;
    private final RulebookContentExtractor fallback;

    public DoclingRulebookContentExtractor(DocumentExtractionPort documentExtractionPort, RulebookContentExtractor fallback) {
        this.documentExtractionPort = Objects.requireNonNull(documentExtractionPort, "documentExtractionPort must not be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    @Override
    public ExtractionResult extract(RulebookFormat format, byte[] content) {
        if (format != RulebookFormat.PDF && format != RulebookFormat.DOCX) {
            return fallback.extract(format, content);
        }
        var result = documentExtractionPort.extract(format, content);
        if (result.rawText().isBlank()) {
            return ExtractionResult.failed(com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure.UNPROCESSABLE);
        }
        if (!result.warnings().isEmpty()) {
            return ExtractionResult.partial(result.rawText(), result.warnings().stream().map(w -> w.code()).toList());
        }
        return ExtractionResult.success(result.rawText());
    }
}
