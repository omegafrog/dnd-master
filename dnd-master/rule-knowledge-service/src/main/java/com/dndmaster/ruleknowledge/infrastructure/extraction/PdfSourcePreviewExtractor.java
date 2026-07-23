package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.PreviewSpan;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;

public final class PdfSourcePreviewExtractor extends PdfRulebookContentExtractor
        implements CompositeSourcePreviewExtractor.FormatPreviewExtractor {

    private static final int MIN_COLUMN_CHARS = 50;

    @Override
    public SourcePreviewResult preview(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try (PDDocument document = Loader.loadPDF(content)) {
            StringBuilder text = new StringBuilder();
            List<PreviewSpan> spans = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                try {
                    PagePreview pagePreview = previewPage(document, i);
                    if (!pagePreview.text().isBlank()) {
                        if (text.length() > 0) {
                            text.append("\n\n");
                        }
                        text.append(pagePreview.text());
                        spans.addAll(pagePreview.spans());
                    }
                } catch (IOException exception) {
                    warnings.add("page " + (i + 1));
                }
            }
            return new SourcePreviewResult(text.toString().trim(), warnings, spans);
        } catch (InvalidPasswordException e) {
            return new SourcePreviewResult("", List.of("encrypted"), List.of());
        } catch (IOException e) {
            return new SourcePreviewResult("", List.of("corrupt"), List.of());
        }
    }

    private PagePreview previewPage(PDDocument document, int pageIndex) throws IOException {
        PDPage page = document.getPage(pageIndex);
        float pageWidth = page.getMediaBox().getWidth();
        float midX = pageWidth / 2f;
        float pageHeight = page.getMediaBox().getHeight();

        PDFTextStripperByArea areaStripper = new PDFTextStripperByArea();
        areaStripper.addRegion("left", new Rectangle2D.Float(0, 0, midX, pageHeight));
        areaStripper.addRegion("right", new Rectangle2D.Float(midX, 0, pageWidth - midX, pageHeight));
        areaStripper.extractRegions(page);

        String left = areaStripper.getTextForRegion("left").trim();
        String right = areaStripper.getTextForRegion("right").trim();
        int lineNumber = pageIndex + 1;
        boolean hasTwoColumns = left.length() >= MIN_COLUMN_CHARS && right.length() >= MIN_COLUMN_CHARS;
        if (hasTwoColumns) {
            List<PreviewSpan> spans = List.of(
                    new PreviewSpan("PAGE_COLUMN", pageIndex * 2 + 1, 0, left.length(), left, "page " + lineNumber + " left column"),
                    new PreviewSpan("PAGE_COLUMN", pageIndex * 2 + 2, 0, right.length(), right, "page " + lineNumber + " right column"));
            return new PagePreview(left + "\n\n" + right, spans);
        }

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        String pageText = stripper.getText(document).trim();
        return new PagePreview(pageText, List.of(
                new PreviewSpan("PAGE", lineNumber, 0, pageText.length(), pageText, "page " + lineNumber)));
    }

    private record PagePreview(String text, List<PreviewSpan> spans) {}
}
