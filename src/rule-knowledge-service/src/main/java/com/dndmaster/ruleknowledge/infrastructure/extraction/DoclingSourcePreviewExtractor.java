package com.dndmaster.ruleknowledge.infrastructure.extraction;

import com.dndmaster.ruleknowledge.application.extraction.DocumentExtractionPort;
import com.dndmaster.ruleknowledge.application.registration.SourcePreviewExtractor;
import com.dndmaster.ruleknowledge.domain.extraction.DocumentNode;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewAsset;
import com.dndmaster.ruleknowledge.domain.rulebook.PreviewSpan;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.SourcePreviewResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DoclingSourcePreviewExtractor implements SourcePreviewExtractor {
    private final DocumentExtractionPort port;
    private final SourcePreviewExtractor fallback;

    public DoclingSourcePreviewExtractor(DocumentExtractionPort port, SourcePreviewExtractor fallback) {
        this.port = Objects.requireNonNull(port, "port must not be null");
        this.fallback = Objects.requireNonNull(fallback, "fallback must not be null");
    }

    @Override
    public SourcePreviewResult preview(RulebookFormat format, byte[] content) {
        if (format != RulebookFormat.PDF && format != RulebookFormat.DOCX) return fallback.preview(format, content);
        var extraction = port.extract(format, content);
        String text = extraction.rawText();
        List<PreviewSpan> spans = new ArrayList<>();
        int offset = 0;
        int order = 0;
        for (DocumentNode node : extraction.nodes()) {
            int end = offset + node.text().length();
            spans.add(new PreviewSpan(node.type().name(), List.of(node.id()), node.page(), node.boundingBox(),
                    order + 1, offset, end, node.text(), "node " + node.id(), "docling", null));
            offset = end + 1;
            order++;
        }
        List<PreviewAsset> assets = extraction.images().stream()
                .map(image -> new PreviewAsset("IMAGE", "image " + image.id(), image.mimeType(), image.page()))
                .toList();
        return new SourcePreviewResult(text, extraction.warnings().stream().map(w -> w.message()).toList(), spans, assets);
    }
}
