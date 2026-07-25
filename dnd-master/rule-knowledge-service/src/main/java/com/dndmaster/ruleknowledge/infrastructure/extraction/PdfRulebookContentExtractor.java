package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.ocr.OcrFailure;
import com.dndmaster.ruleknowledge.application.ocr.OcrPort;
import com.dndmaster.ruleknowledge.application.ocr.OcrRequest;
import com.dndmaster.ruleknowledge.application.ocr.OcrResult;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import java.awt.geom.Rectangle2D;

public class PdfRulebookContentExtractor implements CompositeRulebookContentExtractor.FormatExtractor {

    private static final int MIN_COLUMN_CHARS = 50;
    private final OcrPort ocrPort;

    public PdfRulebookContentExtractor() {
        this(new com.dndmaster.ruleknowledge.infrastructure.ocr.TesseractOcrAdapter());
    }

    public PdfRulebookContentExtractor(OcrPort ocrPort) {
        this.ocrPort = Objects.requireNonNull(ocrPort, "ocrPort must not be null");
    }

    @Override
    public ExtractionResult extract(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try (PDDocument document = Loader.loadPDF(content)) {
            List<String> pages = new ArrayList<>();
            List<String> missingLocations = new ArrayList<>();
            boolean needsInput = false;
            boolean timedOut = false;
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                String pageLocator = pageLocator(i);
                try {
                    String pageText = extractPage(document, i);
                    if (!pageText.isBlank()) {
                        pages.add(pageText);
                        continue;
                    }
                    OcrOutcome outcome = ocrPage(document, i);
                    if (!outcome.text().isBlank()) {
                        pages.add(outcome.text());
                    } else {
                        missingLocations.add(pageLocator);
                    }
                    needsInput |= outcome.failure() == ExtractionFailure.NEEDS_INPUT;
                    timedOut |= outcome.failure() == ExtractionFailure.TIMEOUT;
                } catch (IOException exception) {
                    OcrOutcome outcome = ocrPage(document, i);
                    if (!outcome.text().isBlank()) {
                        pages.add(outcome.text());
                    } else {
                        missingLocations.add(pageLocator);
                    }
                    needsInput |= outcome.failure() == ExtractionFailure.NEEDS_INPUT;
                    timedOut |= outcome.failure() == ExtractionFailure.TIMEOUT;
                }
            }
            String text = String.join("\n\n", pages).trim();
            if (text.isBlank()) {
                if (needsInput) {
                    return ExtractionResult.failed(ExtractionFailure.NEEDS_INPUT);
                }
                if (timedOut) {
                    return ExtractionResult.failed(ExtractionFailure.TIMEOUT);
                }
                return ExtractionResult.failed(ExtractionFailure.UNPROCESSABLE);
            }
            if (!missingLocations.isEmpty()) {
                return ExtractionResult.partial(text, missingLocations);
            }
            return ExtractionResult.success(text);
        } catch (InvalidPasswordException e) {
            return ExtractionResult.failed(ExtractionFailure.ENCRYPTED);
        } catch (IOException e) {
            return ExtractionResult.failed(ExtractionFailure.CORRUPT);
        }
    }

    protected String extractPage(PDDocument document, int pageIndex) throws IOException {
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

        boolean hasTwoColumns = left.length() >= MIN_COLUMN_CHARS && right.length() >= MIN_COLUMN_CHARS;
        if (hasTwoColumns) {
            return left + "\n\n" + right;
        }

        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(pageIndex + 1);
        stripper.setEndPage(pageIndex + 1);
        return stripper.getText(document);
    }

    protected OcrOutcome ocrPage(PDDocument document, int pageIndex) throws IOException {
        BufferedImage image = new PDFRenderer(document).renderImageWithDPI(pageIndex, 200f, ImageType.RGB);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            OcrResult result = ocrPort.recognize(new OcrRequest(output.toByteArray(), pageLocator(pageIndex), "image/png"));
            String text = result.lines().stream()
                    .map(line -> line.text().trim())
                    .filter(value -> !value.isBlank())
                    .reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right)
                    .trim();
            ExtractionFailure failure = switch (result.failure()) {
                case MISSING_LANGUAGE_PACK -> ExtractionFailure.NEEDS_INPUT;
                case TIMEOUT -> ExtractionFailure.TIMEOUT;
                case UNAVAILABLE, CORRUPT -> ExtractionFailure.UNPROCESSABLE;
                case NONE -> ExtractionFailure.UNPROCESSABLE;
            };
            return new OcrOutcome(text, result.warnings(), failure, result.lines());
        }
    }

    protected static String pageLocator(int pageIndex) {
        return "page " + (pageIndex + 1);
    }

    protected static record OcrOutcome(String text, List<String> warnings, ExtractionFailure failure, List<com.dndmaster.ruleknowledge.application.ocr.OcrLine> lines) {
        public OcrOutcome {
            text = text == null ? "" : text;
            warnings = warnings == null ? List.of() : List.copyOf(warnings);
            failure = failure == null ? ExtractionFailure.UNPROCESSABLE : failure;
            lines = lines == null ? List.of() : List.copyOf(lines);
        }
    }
}
