package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;

public final class DocxRulebookContentExtractor implements CompositeRulebookContentExtractor.FormatExtractor {

    @Override
    public ExtractionResult extract(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder sb = new StringBuilder();
            for (IBodyElement bodyElement : document.getBodyElements()) {
                appendBodyElement(bodyElement, sb);
            }
            String result = sb.toString().trim();
            if (result.isBlank()) {
                return ExtractionResult.failed(ExtractionFailure.UNPROCESSABLE);
            }
            return ExtractionResult.success(result);
        } catch (IOException e) {
            return ExtractionResult.failed(ExtractionFailure.CORRUPT);
        } catch (NotOfficeXmlFileException e) {
            return ExtractionResult.failed(ExtractionFailure.CORRUPT);
        }
    }

    private static void appendBodyElement(IBodyElement bodyElement, StringBuilder sb) {
        if (bodyElement instanceof XWPFParagraph paragraph) {
            appendParagraph(paragraph, sb);
            return;
        }
        if (bodyElement instanceof XWPFTable table) {
            appendTable(table, sb);
        }
    }

    private static void appendParagraph(XWPFParagraph paragraph, StringBuilder sb) {
        String text = paragraph.getText();
        if (text != null && !text.isBlank()) {
            sb.append(text).append('\n');
        }
    }

    private static void appendTable(XWPFTable table, StringBuilder sb) {
        for (var row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String text = cell.getText();
                if (text != null && !text.isBlank()) {
                    cells.add(text);
                }
            }
            if (!cells.isEmpty()) {
                sb.append(String.join("\t", cells)).append('\n');
            }
        }
    }
}
