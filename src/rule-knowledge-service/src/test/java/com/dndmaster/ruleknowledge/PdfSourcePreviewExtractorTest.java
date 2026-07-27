package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
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

class PdfSourcePreviewExtractorTest {

    @Test
    void previewsPagesAndKeepsOrder() throws Exception {
        PdfSourcePreviewExtractor extractor = new PdfSourcePreviewExtractor();

        SourcePreviewResult result = extractor.preview(pdfWithTwoPages());

        assertTrue(result.warnings().isEmpty());
        assertEquals(2, result.spans().size());
        assertEquals("PAGE_LINE", result.spans().get(0).kind());
        assertEquals(List.of("page 1", "line 1"), result.spans().get(0).path());
        assertEquals("page 1 line 1", result.spans().get(0).locator());
        assertEquals("First page", result.spans().get(0).text());
        assertEquals("PAGE_LINE", result.spans().get(1).kind());
        assertEquals(List.of("page 2", "line 1"), result.spans().get(1).path());
        assertTrue(result.assets().isEmpty());
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
