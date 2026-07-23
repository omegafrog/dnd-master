package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import com.dndmaster.ruleknowledge.infrastructure.extraction.DocxSourcePreviewExtractor;
import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class DocxSourcePreviewExtractorTest {

    @Test
    void previewsBodyElementsAndTablesInDocumentOrder() throws Exception {
        DocxSourcePreviewExtractor extractor = new DocxSourcePreviewExtractor();

        SourcePreviewResult result = extractor.preview(docxWithParagraphTableParagraph());

        assertTrue(result.warnings().isEmpty());
        assertEquals(4, result.spans().size());
        assertEquals("PARAGRAPH", result.spans().get(0).kind());
        assertEquals(List.of("section 1", "paragraph 1"), result.spans().get(0).path());
        assertEquals("section 1 paragraph 1", result.spans().get(0).locator());
        assertEquals("Intro", result.spans().get(0).text());
        assertEquals("TABLE_CELL", result.spans().get(1).kind());
        assertEquals(List.of("section 1", "table 1", "row 1", "cell 1"), result.spans().get(1).path());
        assertEquals("section 1 table 1 row 1 cell 1", result.spans().get(1).locator());
        assertEquals("Left", result.spans().get(1).text());
        assertEquals("PARAGRAPH", result.spans().get(3).kind());
        assertEquals("Outro", result.spans().get(3).text());
        assertTrue(result.assets().isEmpty());
    }

    private static byte[] docxWithParagraphTableParagraph() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Intro");

            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Left");
            table.getRow(0).getCell(1).setText("Right");

            document.createParagraph().createRun().setText("Outro");

            document.write(output);
            return output.toByteArray();
        }
    }
}
