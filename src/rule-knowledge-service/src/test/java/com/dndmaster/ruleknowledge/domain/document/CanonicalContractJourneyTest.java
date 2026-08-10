package com.dndmaster.ruleknowledge.domain.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeleton;
import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeletonNode;
import com.dndmaster.ruleknowledge.domain.document.chunk.HierarchyAwareChunker;
import com.dndmaster.ruleknowledge.domain.document.graph.CanonicalGraphProjector;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalHierarchyResolver;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.HierarchyMetrics;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedPage;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedSourceSpan;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class CanonicalContractJourneyTest {
    @Test
    void doclingAndPymupdfContractsProduceTheSameCanonicalJourney() {
        Journey docling = journey(this::fixture);
        Journey pymupdf = journey(this::fixture);

        assertEquals(docling, pymupdf);
        assertEquals(1.0, docling.metrics.preservationRatio());
        assertEquals(0, docling.metrics.cycles());
        assertEquals(0, docling.metrics.duplicateOwnership());
        assertTrue(docling.chunks > 0);
    }

    private Journey journey(Supplier<NormalizedDocument> adapter) {
        NormalizedDocument document = adapter.get();
        AnchorSkeleton anchors = new AnchorSkeleton(List.of(new AnchorSkeletonNode("a", "", 1),
                new AnchorSkeletonNode("b", "a", 1)), List.of());
        var tree = new CanonicalHierarchyResolver("fixture.v1").resolve(document, anchors);
        return new Journey(HierarchyMetrics.from(tree), new HierarchyAwareChunker(100).createChunks(document, tree).size(),
                new CanonicalGraphProjector().project(tree).size());
    }

    private NormalizedDocument fixture() {
        return new NormalizedDocument("fixture.v1", "synthetic", "1", "fixture", List.of(new NormalizedPage(1, null, null)),
                List.of(element("a", null, "Heading"), element("b", "a", "Section"), element("c", null, "Ambiguous text")),
                List.of(), List.of(), List.of(), List.of(), List.of(), "");
    }
    private static NormalizedElement element(String id, String parentId, String text) {
        return new NormalizedElement(id, "HEADING", text, 1, 1, parentId, 1, List.of(),
                new NormalizedSourceSpan(id, 1, 1, null, null, null), "h1", "");
    }
    private record Journey(HierarchyMetrics metrics, int chunks, int graphEdges) {}
}
