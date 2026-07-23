package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.application.ocr.OcrFailure;
import com.dndmaster.ruleknowledge.application.ocr.OcrLine;
import com.dndmaster.ruleknowledge.application.ocr.OcrPort;
import com.dndmaster.ruleknowledge.application.ocr.OcrRequest;
import com.dndmaster.ruleknowledge.application.ocr.OcrResult;
import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import com.dndmaster.ruleknowledge.infrastructure.extraction.PdfRulebookContentExtractor;
import com.dndmaster.ruleknowledge.infrastructure.extraction.PdfSourcePreviewExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

class PdfOcrFallbackTest {

    @Test
    void usesOcrOnlyForBlankPage() throws Exception {
        RecordingOcrPort ocrPort = new RecordingOcrPort();
        PdfRulebookContentExtractor extractor = new PdfRulebookContentExtractor(ocrPort);

        ExtractionResult result = extractor.extract(pdfWithTextAndBlankPage());

        assertTrue(result.status() == ExtractionStatus.SUCCESS);
        String content = result.content().orElseThrow();
        assertTrue(content.contains("Native page"));
        assertTrue(content.contains("OCR fallback"));
        assertEquals(1, ocrPort.calls);
    }

    @Test
    void previewsOcrPageWithMethodAndConfidence() throws Exception {
        RecordingOcrPort ocrPort = new RecordingOcrPort();
        PdfSourcePreviewExtractor extractor = new PdfSourcePreviewExtractor(ocrPort);

        SourcePreviewResult result = extractor.preview(pdfWithTextAndBlankPage());

        assertEquals(2, result.spans().size());
        assertEquals("TEXT", result.spans().get(0).sourceMethod());
        assertEquals("OCR", result.spans().get(1).sourceMethod());
        assertTrue(result.spans().get(1).confidence() > 80d);
    }

    private static byte[] pdfWithTextAndBlankPage() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addPage(document, "Native page");
            document.addPage(new PDPage(PDRectangle.LETTER));
            document.save(output);
            return output.toByteArray();
        }
    }

    private static void addPage(PDDocument document, String text) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        document.addPage(page);
        try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
            contentStream.beginText();
            contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
            contentStream.newLineAtOffset(72, 700);
            contentStream.showText(text);
            contentStream.endText();
        }
    }

    private static final class RecordingOcrPort implements OcrPort {
        private int calls;

        @Override
        public OcrResult recognize(OcrRequest request) {
            calls++;
            if (request.sourceLabel().contains("page 2")) {
                return new OcrResult(
                        List.of(new OcrLine(1, "OCR fallback", new BoundingBox(0.1, 0.1, 0.8, 0.2), 91d)),
                        List.of(),
                        OcrFailure.NONE);
            }
            return new OcrResult(List.of(), List.of(), OcrFailure.NONE);
        }
    }
}
