package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.ocr.OcrFailure;
import com.dndmaster.ruleknowledge.application.ocr.OcrLine;
import com.dndmaster.ruleknowledge.application.ocr.OcrPort;
import com.dndmaster.ruleknowledge.application.ocr.OcrRequest;
import com.dndmaster.ruleknowledge.application.ocr.OcrResult;
import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import com.dndmaster.ruleknowledge.infrastructure.extraction.ImageRulebookContentExtractor;
import com.dndmaster.ruleknowledge.infrastructure.extraction.ImageSourcePreviewExtractor;
import java.util.List;
import org.junit.jupiter.api.Test;

class ImageOcrExtractionTest {

    @Test
    void extractsImageTextFromOcr() {
        ImageRulebookContentExtractor extractor = new ImageRulebookContentExtractor(new FakeOcrPort());

        ExtractionResult result = extractor.extract(new byte[] {1, 2, 3});

        assertTrue(result.status() == ExtractionStatus.SUCCESS);
        assertEquals("Hello OCR", result.content().orElseThrow());
    }

    @Test
    void surfacesNeedsInputWhenOcrLanguageMissing() {
        ImageRulebookContentExtractor extractor = new ImageRulebookContentExtractor(request ->
                new OcrResult(List.of(), List.of("missing kor"), OcrFailure.MISSING_LANGUAGE_PACK));

        ExtractionResult result = extractor.extract(new byte[] {1, 2, 3});

        assertTrue(result.status() == ExtractionStatus.FAILED);
        assertEquals(ExtractionFailure.NEEDS_INPUT, result.failure().orElseThrow());
    }

    @Test
    void previewExposesOcrMethodAndConfidence() {
        ImageSourcePreviewExtractor extractor = new ImageSourcePreviewExtractor(new FakeOcrPort());

        SourcePreviewResult result = extractor.preview(new byte[] {1, 2, 3});

        assertEquals(1, result.spans().size());
        assertEquals("OCR", result.spans().get(0).sourceMethod());
        assertTrue(result.spans().get(0).confidence() > 90d);
    }

    private static final class FakeOcrPort implements OcrPort {
        @Override
        public OcrResult recognize(OcrRequest request) {
            return new OcrResult(
                    List.of(new OcrLine(1, "Hello OCR", new BoundingBox(0.1, 0.1, 0.8, 0.2), 96d)),
                    List.of(),
                    OcrFailure.NONE);
        }
    }
}
