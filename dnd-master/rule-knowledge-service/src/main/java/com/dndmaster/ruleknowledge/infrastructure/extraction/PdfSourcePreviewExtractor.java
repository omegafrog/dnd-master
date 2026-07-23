package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewSpan;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewAsset;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public final class PdfSourcePreviewExtractor extends PdfRulebookContentExtractor
        implements CompositeSourcePreviewExtractor.FormatPreviewExtractor {

    @Override
    public SourcePreviewResult preview(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try (PDDocument document = Loader.loadPDF(content)) {
            List<PreviewSpan> spans = new ArrayList<>();
            List<PreviewAsset> assets = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                try {
                    spans.addAll(previewPage(document, i));
                } catch (IOException exception) {
                    warnings.add("page " + (i + 1));
                }
            }
            String text = String.join("\n\n", spans.stream().map(PreviewSpan::text).filter(value -> !value.isBlank()).toList()).trim();
            return new SourcePreviewResult(text, warnings, spans, assets);
        } catch (InvalidPasswordException e) {
            return new SourcePreviewResult("", List.of("encrypted"), List.of(), List.of());
        } catch (IOException e) {
            return new SourcePreviewResult("", List.of("corrupt"), List.of(), List.of());
        }
    }

    private List<PreviewSpan> previewPage(PDDocument document, int pageIndex) throws IOException {
        LayoutTextStripper stripper = new LayoutTextStripper(pageIndex + 1);
        stripper.setSortByPosition(true);
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        stripper.getText(document);
        return stripper.spans();
    }

    private static final class LayoutTextStripper extends PDFTextStripper {
        private final int pageNumber;
        private final List<PreviewSpan> spans = new ArrayList<>();
        private float pageWidth;
        private float pageHeight;
        private int lineNumber = 1;

        private LayoutTextStripper(int pageNumber) throws IOException {
            this.pageNumber = pageNumber;
        }

        @Override
        protected void startPage(PDPage page) throws IOException {
            pageWidth = page.getMediaBox().getWidth();
            pageHeight = page.getMediaBox().getHeight();
            super.startPage(page);
        }

        @Override
        protected void writeString(String text, List<TextPosition> textPositions) throws IOException {
            String normalized = text == null ? "" : text.trim();
            if (normalized.isEmpty() || textPositions.isEmpty()) {
                return;
            }
            spans.add(new PreviewSpan(
                    "PAGE_LINE",
                    List.of("page " + pageNumber, "line " + lineNumber),
                    pageNumber,
                    bounds(textPositions),
                    lineNumber,
                    0,
                    normalized.length(),
                    normalized,
                    "page " + pageNumber + " line " + lineNumber));
            lineNumber++;
        }

        private BoundingBox bounds(List<TextPosition> textPositions) {
            float minX = Float.MAX_VALUE;
            float minY = Float.MAX_VALUE;
            float maxX = 0f;
            float maxY = 0f;
            for (TextPosition position : textPositions) {
                float left = position.getXDirAdj();
                float top = position.getYDirAdj();
                float right = left + position.getWidthDirAdj();
                float bottom = top + position.getHeightDir();
                minX = Math.min(minX, left);
                minY = Math.min(minY, top);
                maxX = Math.max(maxX, right);
                maxY = Math.max(maxY, bottom);
            }
            double left = normalize(minX, pageWidth);
            double top = normalize(minY, pageHeight);
            double right = normalize(maxX, pageWidth);
            double bottom = normalize(maxY, pageHeight);
            if (bottom < top) {
                double swap = top;
                top = bottom;
                bottom = swap;
            }
            return new BoundingBox(left, top, right, bottom);
        }

        private static double normalize(float value, float size) {
            if (size <= 0f) {
                return 0d;
            }
            double normalized = value / size;
            return Math.max(0d, Math.min(1d, normalized));
        }

        private List<PreviewSpan> spans() {
            return List.copyOf(spans);
        }
    }
}
