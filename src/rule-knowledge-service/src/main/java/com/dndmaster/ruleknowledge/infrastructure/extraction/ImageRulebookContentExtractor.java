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
    private static final String IMAGE_WITHOUT_EXTRACTED_TEXT = "이미지 자료(텍스트 추출 없음)";
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
        if (result.failure() == OcrFailure.CORRUPT) {
            return ExtractionResult.failed(ExtractionFailure.UNPROCESSABLE);
        }
        return ExtractionResult.success(IMAGE_WITHOUT_EXTRACTED_TEXT);
    }
}
