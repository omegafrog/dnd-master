package com.dndmaster.ruleknowledge.domain.document.evidence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedOutlineEntry;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedPage;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedSourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralEvidenceExtractorTest {
    @Test
    void extractsPrintedNavigationWithoutContentsLabelAndKeepsIndependentSignals() {
        NormalizedDocument document = document(
                new NormalizedElement("n1", "PARAGRAPH", "Introduction ........ 1", 1, 0, null, null, null,
                        span("n1", 1, 0), "body", "column=1"),
                new NormalizedElement("n2", "PARAGRAPH", "Combat ............ 7", 1, 1, null, null, null,
                        span("n2", 1, 1), "body", "column=2"),
                new NormalizedElement("h1", "HEADING", "1 Introduction", 2, 2, null, null, null,
                        span("h1", 2, 2), "large", "column=1"));

        StructuralEvidenceExtractionResult result = new StructuralEvidenceExtractor().extract(document);

        assertEquals(List.of("Introduction", "Combat"),
                result.navigationEntries().stream().map(NavigationEntry::title).toList());
        assertTrue(result.evidence().stream().anyMatch(e -> e.kind() == EvidenceKind.PRINTED_NAVIGATION));
        assertTrue(result.evidence().stream().anyMatch(e -> e.kind() == EvidenceKind.NUMBERING));
        assertTrue(result.evidence().stream().anyMatch(e -> e.kind() == EvidenceKind.TYPOGRAPHY));
    }

    private static NormalizedDocument document(NormalizedElement... elements) {
        return new NormalizedDocument("normalized-document.v1", "test", "1", "test-source",
                List.of(new NormalizedPage(1, null, null), new NormalizedPage(2, null, null)),
                List.of(elements), List.of(), List.of(), List.of(new NormalizedOutlineEntry("o1", "Introduction", 1, "1")),
                List.of(), List.of(), "");
    }

    private static NormalizedSourceSpan span(String id, int page, int order) {
        return new NormalizedSourceSpan(id, page, order, null, null, null);
    }
}
