package com.dndmaster.ruleknowledge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.ruleknowledge.application.ocr.OcrLine;
import com.dndmaster.ruleknowledge.application.ocr.OcrPort;
import com.dndmaster.ruleknowledge.application.ocr.OcrRequest;
import com.dndmaster.ruleknowledge.application.ocr.OcrResult;
import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class RuleKnowledgeApiConfigurationTest {

    @Test
    void usesConfiguredOcrPortForContentAndPreviewExtractors() throws Exception {
        RecordingOcrPort ocrPort = new RecordingOcrPort();
        RuleKnowledgeApiConfiguration configuration = new RuleKnowledgeApiConfiguration();

        var contentExtractor = configuration.rulebookContentExtractor(ocrPort);
        var previewExtractor = configuration.sourcePreviewExtractor(ocrPort);
        byte[] image = pngImage();

        assertEquals("configured OCR", contentExtractor.extract(RulebookFormat.IMAGE, image).content().orElseThrow());
        assertEquals("configured OCR", previewExtractor.preview(RulebookFormat.IMAGE, image).content());
        assertEquals(2, ocrPort.calls);
    }

    private static byte[] pngImage() throws Exception {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private static final class RecordingOcrPort implements OcrPort {
        private int calls;

        @Override
        public OcrResult recognize(OcrRequest request) {
            calls++;
            return new OcrResult(
                    List.of(new OcrLine(1, "configured OCR", new BoundingBox(0, 0, 1, 1), 99d)),
                    List.of(),
                    com.dndmaster.ruleknowledge.application.ocr.OcrFailure.NONE);
        }
    }
}
