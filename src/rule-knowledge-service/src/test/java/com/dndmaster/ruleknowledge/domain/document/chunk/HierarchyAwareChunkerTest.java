package com.dndmaster.ruleknowledge.domain.document.chunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeleton;
import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeletonNode;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalDocumentTree;
import com.dndmaster.ruleknowledge.domain.document.hierarchy.CanonicalHierarchyResolver;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedPage;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedSourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class HierarchyAwareChunkerTest {
    @Test
    void emitsConfirmedPathAndSourceIdentityButExcludesUnresolvedContent() {
        NormalizedElement heading = element("h", "HEADING", "Rules", 1, 0, null);
        NormalizedElement text = element("p", "PARAGRAPH", "Confirmed rule text", 1, 1, "h");
        NormalizedElement unresolved = element("u", "PARAGRAPH", "Unsafe text", 2, 2, null);
        NormalizedDocument document = new NormalizedDocument("v1", "test", "1", "source", List.of(new NormalizedPage(1, null, null)),
                List.of(heading, text, unresolved), List.of(), List.of(), List.of(), List.of(), List.of(), "");
        CanonicalDocumentTree tree = new CanonicalHierarchyResolver("v1").resolve(document,
                new AnchorSkeleton(List.of(new AnchorSkeletonNode("h", "", .9)), List.of()));

        List<RagChunk> chunks = new HierarchyAwareChunker(100).createChunks(document, tree);

        assertEquals(2, chunks.size());
        assertEquals(List.of("h"), chunks.getFirst().canonicalPath());
        assertTrue(chunks.getFirst().sourceNodeIds().contains("h"));
        assertTrue(chunks.get(1).canonicalPath().isEmpty());
    }
    private static NormalizedElement element(String id, String type, String text, int page, int order, String parent) {
        return new NormalizedElement(id, type, text, page, order, parent, null, List.of(),
                new NormalizedSourceSpan(id, page, order, null, null, null), "", "");
    }
}
