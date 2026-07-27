package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.ocr.OcrFailure;
import com.dndmaster.ruleknowledge.application.ocr.OcrPort;
import com.dndmaster.ruleknowledge.application.ocr.OcrRequest;
import com.dndmaster.ruleknowledge.application.ocr.OcrResult;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class ImageRulebookContentExtractor implements CompositeRulebookContentExtractor.FormatExtractor {
    private final OcrPort ocrPort;

    public ImageRulebookContentExtractor() {
        this(new com.dndmaster.ruleknowledge.infrastructure.ocr.TesseractOcrAdapter());
    }

    public ImageRulebookContentExtractor(OcrPort ocrPort) {
        this.ocrPort = Objects.requireNonNull(ocrPort, "ocrPort must not be null");
    }

    @Override
    public ExtractionResult extract(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        OcrResult result = ocrPort.recognize(new OcrRequest(content, "image", "image/png"));
        String text = result.lines().stream().map(line -> line.text().trim()).filter(value -> !value.isBlank()).reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right).trim();
        if (!text.isBlank()) {
            if (result.failure() == OcrFailure.TIMEOUT) {
                return ExtractionResult.partial(text, List.of("image"));
            }
            return ExtractionResult.success(text);
        }
        return switch (result.failure()) {
            case MISSING_LANGUAGE_PACK -> ExtractionResult.failed(ExtractionFailure.NEEDS_INPUT);
            case TIMEOUT -> ExtractionResult.failed(ExtractionFailure.TIMEOUT);
            case UNAVAILABLE, CORRUPT -> ExtractionResult.failed(ExtractionFailure.UNPROCESSABLE);
            case NONE -> ExtractionResult.failed(ExtractionFailure.UNPROCESSABLE);
        };
    }
}
