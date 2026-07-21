package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;
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
            for (XWPFParagraph paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append('\n');
                }
            }
            for (XWPFTable table : document.getTables()) {
                for (var row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String text = cell.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text).append('\t');
                        }
                    }
                    sb.append('\n');
                }
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
}
