package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.PreviewSpan;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

public final class DocxSourcePreviewExtractor implements CompositeSourcePreviewExtractor.FormatPreviewExtractor {
    @Override
    public SourcePreviewResult preview(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            StringBuilder sb = new StringBuilder();
            List<PreviewSpan> spans = new ArrayList<>();
            int paragraphIndex = 0;
            int tableIndex = 0;
            int order = 1;
            for (IBodyElement bodyElement : document.getBodyElements()) {
                if (bodyElement instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text != null && !text.isBlank()) {
                        paragraphIndex++;
                        if (sb.length() > 0) {
                            sb.append('\n');
                        }
                        sb.append(text);
                        spans.add(new PreviewSpan("PARAGRAPH", order++, 0, text.length(), text, "paragraph " + paragraphIndex));
                    }
                    continue;
                }
                if (bodyElement instanceof XWPFTable table) {
                    tableIndex++;
                    order = appendTable(table, sb, spans, tableIndex, order);
                }
            }
            return new SourcePreviewResult(sb.toString().trim(), List.of(), spans);
        } catch (IOException | NotOfficeXmlFileException e) {
            return new SourcePreviewResult("", List.of("corrupt"), List.of());
        }
    }

    private static int appendTable(
            XWPFTable table, StringBuilder sb, List<PreviewSpan> spans, int tableIndex, int order) {
        for (int rowIndex = 0; rowIndex < table.getNumberOfRows(); rowIndex++) {
            var row = table.getRow(rowIndex);
            List<String> cells = new ArrayList<>();
            for (int cellIndex = 0; cellIndex < row.getTableCells().size(); cellIndex++) {
                XWPFTableCell cell = row.getCell(cellIndex);
                if (cell == null) {
                    continue;
                }
                String text = cell.getText();
                if (text != null && !text.isBlank()) {
                    cells.add(text);
                    spans.add(new PreviewSpan(
                            "TABLE_CELL",
                            order++,
                            0,
                            text.length(),
                            text,
                            "table " + tableIndex + " row " + (rowIndex + 1) + " cell " + (cellIndex + 1)));
                }
            }
            if (!cells.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(String.join("\t", cells));
            }
        }
        return order;
    }
}
