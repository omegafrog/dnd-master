package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionStatus;
import com.dndmaster.ruleknowledge.infrastructure.extraction.PdfRulebookContentExtractor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;

class PdfRulebookContentExtractorTest {

    @Test
    void keepsSuccessfulPagesAndMarksFailedPagesAsPartial() throws Exception {
        PdfRulebookContentExtractor extractor = new PdfRulebookContentExtractor() {
            @Override
            protected String extractPage(PDDocument document, int pageIndex) throws IOException {
                if (pageIndex == 1) {
                    throw new IOException("boom");
                }
                return super.extractPage(document, pageIndex);
            }
        };

        ExtractionResult result = extractor.extract(pdfWithTwoPages());

        assertEquals("First page", result.content().orElseThrow());
        assertEquals(1, result.missingLocations().size());
        assertEquals("page 2", result.missingLocations().get(0));
        assertTrue(result.status() == ExtractionStatus.PARTIAL);
    }

    private static byte[] pdfWithTwoPages() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addPage(document, "First page");
            addPage(document, "Second page");
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
}
