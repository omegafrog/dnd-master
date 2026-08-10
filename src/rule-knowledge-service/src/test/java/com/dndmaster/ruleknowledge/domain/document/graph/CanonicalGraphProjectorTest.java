package com.dndmaster.ruleknowledge.domain.document.graph;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

class CanonicalGraphProjectorTest {
    @Test
    void emitsOnlyConfirmedCanonicalContainment() {
        NormalizedElement root = element("root", null, "HEADING");
        NormalizedElement confirmed = element("confirmed", "root", "HEADING");
        NormalizedElement unresolved = element("unresolved", null, "PARAGRAPH");
        CanonicalDocumentTree tree = new CanonicalHierarchyResolver("v1").resolve(document(root, confirmed, unresolved),
                new AnchorSkeleton(List.of(new AnchorSkeletonNode("root", "", 1),
                        new AnchorSkeletonNode("confirmed", "root", 1)), List.of()));

        assertEquals(List.of(new CanonicalContainmentEdge("root", "confirmed", "v1")),
                new CanonicalGraphProjector().project(tree));
    }

    private static NormalizedDocument document(NormalizedElement... elements) {
        return new NormalizedDocument("v1", "fixture", "1", "source", List.of(new NormalizedPage(1, null, null)),
                List.of(elements), List.of(), List.of(), List.of(), List.of(), List.of(), "");
    }
    private static NormalizedElement element(String id, String parentId, String type) {
        return new NormalizedElement(id, type, id, 1, 1, parentId, 1, List.of(),
                new NormalizedSourceSpan(id, 1, 1, null, null, null), "h1", "");
    }
}
