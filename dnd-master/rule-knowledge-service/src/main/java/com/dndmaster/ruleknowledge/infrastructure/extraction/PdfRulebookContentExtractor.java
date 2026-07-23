package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.ExtractionResult;
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

public class PdfRulebookContentExtractor implements CompositeRulebookContentExtractor.FormatExtractor {

    private static final int MIN_COLUMN_CHARS = 50;

    @Override
    public ExtractionResult extract(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        try (PDDocument document = Loader.loadPDF(content)) {
            List<String> pages = new ArrayList<>();
            List<String> missingLocations = new ArrayList<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                try {
                    String pageText = extractPage(document, i);
                    if (!pageText.isBlank()) {
                        pages.add(pageText);
                    }
                } catch (IOException exception) {
                    missingLocations.add(pageLocator(i));
                }
            }
            String text = String.join("\n\n", pages).trim();
            if (text.isBlank()) {
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

    private static String pageLocator(int pageIndex) {
        return "page " + (pageIndex + 1);
    }
}
