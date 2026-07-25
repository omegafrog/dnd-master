package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.ocr.OcrPort;
import com.dndmaster.ruleknowledge.application.ocr.OcrRequest;
import com.dndmaster.ruleknowledge.application.ocr.OcrResult;
import com.dndmaster.ruleknowledge.application.ocr.OcrFailure;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewAsset;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewSpan;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.util.List;
import java.util.Objects;

public final class ImageSourcePreviewExtractor implements CompositeSourcePreviewExtractor.FormatPreviewExtractor {
    private final OcrPort ocrPort;

    public ImageSourcePreviewExtractor() {
        this(new com.dndmaster.ruleknowledge.infrastructure.ocr.TesseractOcrAdapter());
    }

    public ImageSourcePreviewExtractor(OcrPort ocrPort) {
        this.ocrPort = Objects.requireNonNull(ocrPort, "ocrPort must not be null");
    }

    @Override
    public SourcePreviewResult preview(byte[] content) {
        Objects.requireNonNull(content, "content must not be null");
        OcrResult result = ocrPort.recognize(new OcrRequest(content, "image", "image/png"));
        List<PreviewSpan> spans = result.lines().stream()
                .map(line -> new PreviewSpan(
                        "OCR_LINE",
                        List.of("image", "line " + line.lineNumber()),
                        1,
                        line.bounds(),
                        line.lineNumber(),
                        0,
                        line.text().length(),
                        line.text(),
                        "image line " + line.lineNumber(),
                        "OCR",
                        line.confidence()))
                .toList();
        List<PreviewAsset> assets = List.of(new PreviewAsset("SOURCE_IMAGE", "image", "image/png", 1));
        List<String> warnings = result.warnings();
        if (result.failure() != OcrFailure.NONE) {
            java.util.ArrayList<String> mutable = new java.util.ArrayList<>(warnings);
            mutable.add(result.failure().name().toLowerCase());
            warnings = List.copyOf(mutable);
        }
        String text = result.lines().stream().map(line -> line.text().trim()).filter(value -> !value.isBlank()).reduce("", (left, right) -> left.isBlank() ? right : left + "\n" + right).trim();
        return new SourcePreviewResult(text, warnings, spans, assets);
    }
}
