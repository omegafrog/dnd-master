package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.ocr.OcrLine;
import com.dndmaster.ruleknowledge.application.ocr.OcrPort;
import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewAsset;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewSpan;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

public final class PdfSourcePreviewExtractor extends PdfRulebookContentExtractor
        implements CompositeSourcePreviewExtractor.FormatPreviewExtractor {

    public PdfSourcePreviewExtractor() {
        this(new com.dndmaster.ruleknowledge.infrastructure.ocr.TesseractOcrAdapter());
    }

    public PdfSourcePreviewExtractor(OcrPort ocrPort) {
        super(ocrPort);
    }

    @Override
    public SourcePreviewResult preview(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try (PDDocument document = Loader.loadPDF(content)) {
            List<PreviewSpan> spans = new ArrayList<>();
            List<PreviewAsset> assets = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                try {
                    PagePreview preview = previewPage(document, i);
                    spans.addAll(preview.spans());
                    assets.addAll(preview.assets());
                    warnings.addAll(preview.warnings());
                } catch (IOException exception) {
                    warnings.add(pageLocator(i) + ": " + exception.getMessage());
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

    private PagePreview previewPage(PDDocument document, int pageIndex) throws IOException {
        int pageNumber = pageIndex + 1;
        String nativeText = "";
        try {
            nativeText = extractPage(document, pageIndex).trim();
        } catch (IOException exception) {
            // Fall through to OCR. The content extractor already treats this page as recoverable.
        }
        if (!nativeText.isBlank()) {
            LayoutTextStripper stripper = new LayoutTextStripper(pageNumber);
            stripper.setSortByPosition(true);
            stripper.setStartPage(pageNumber);
            stripper.setEndPage(pageNumber);
            stripper.getText(document);
            return new PagePreview(stripper.spans(), assetForPage(document.getPage(pageIndex), pageNumber, false), List.of());
        }
        BufferedImagePreview preview = ocrPreview(document, pageIndex);
        return new PagePreview(preview.spans(), preview.assets(), preview.warnings());
    }

    private BufferedImagePreview ocrPreview(PDDocument document, int pageIndex) throws IOException {
        var outcome = ocrPage(document, pageIndex);
        int pageNumber = pageIndex + 1;
        List<PreviewSpan> spans = outcome.lines().stream()
                .map(line -> new PreviewSpan(
                        "OCR_LINE",
                        List.of("page " + pageNumber, "line " + line.lineNumber()),
                        pageNumber,
                        line.bounds(),
                        line.lineNumber(),
                        0,
                        line.text().length(),
                        line.text(),
                        "page " + pageNumber + " line " + line.lineNumber(),
                        "OCR",
                        line.confidence()))
                .toList();
        List<PreviewAsset> assets = new ArrayList<>(assetForPage(document.getPage(pageIndex), pageNumber, true));
        return new BufferedImagePreview(spans, assets, outcome.warnings());
    }

    private List<PreviewAsset> assetForPage(PDPage page, int pageNumber, boolean rendered) throws IOException {
        List<PreviewAsset> assets = new ArrayList<>();
        if (rendered) {
            assets.add(new PreviewAsset("RENDERED_PAGE", "page " + pageNumber + " rendered", "image/png", pageNumber));
        }
        PDResources resources = page.getResources();
        if (resources == null) {
            return List.copyOf(assets);
        }
        int imageIndex = 1;
        for (var name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDImageXObject image) {
                assets.add(new PreviewAsset(
                        "IMAGE",
                        "page " + pageNumber + " image " + imageIndex++,
                        mimeType(image.getSuffix()),
                        pageNumber));
            }
        }
        return List.copyOf(assets);
    }

    private static String mimeType(String suffix) {
        if (suffix == null) {
            return null;
        }
        return switch (suffix.toLowerCase()) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "tif", "tiff" -> "image/tiff";
            case "gif" -> "image/gif";
            default -> null;
        };
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
                    "page " + pageNumber + " line " + lineNumber,
                    "TEXT",
                    null));
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

    private record PagePreview(List<PreviewSpan> spans, List<PreviewAsset> assets, List<String> warnings) {}

    private record BufferedImagePreview(List<PreviewSpan> spans, List<PreviewAsset> assets, List<String> warnings) {}
}
