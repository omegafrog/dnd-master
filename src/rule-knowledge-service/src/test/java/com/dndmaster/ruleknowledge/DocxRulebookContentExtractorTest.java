package com.dndmaster.ruleknowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import com.dndmaster.ruleknowledge.infrastructure.extraction.DocxRulebookContentExtractor;
import java.io.ByteArrayOutputStream;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

class DocxRulebookContentExtractorTest {

    @Test
    void extractsBodyElementsInDocumentOrder() throws Exception {
        DocxRulebookContentExtractor extractor = new DocxRulebookContentExtractor();

        ExtractionResult result = extractor.extract(docxWithParagraphTableParagraph());

        assertTrue(result.content().isPresent());
        assertEquals("Intro\nLeft\tRight\nOutro", result.content().orElseThrow());
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
