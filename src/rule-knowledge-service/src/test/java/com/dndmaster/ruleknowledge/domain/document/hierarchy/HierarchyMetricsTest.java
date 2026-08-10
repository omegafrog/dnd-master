package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeleton;
import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeletonNode;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedPage;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedSourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class HierarchyMetricsTest {
    @Test
    void defaultPolicyStaysShadowOnlyEvenForPerfectHierarchy() {
        CanonicalDocumentTree tree = tree();
        assertFalse(CanonicalCutoverPolicy.shadowOnly().permits(HierarchyMetrics.from(tree)));
        assertTrue(new CanonicalCutoverPolicy(true, 1).permits(HierarchyMetrics.from(tree)));
    }

    @Test
    void gateRejectsIncompleteOwnership() {
        CanonicalDocumentTree tree = tree();
        CanonicalDocumentTree incomplete = new CanonicalDocumentTree(tree.nodes(), List.of(tree.edges().getFirst()), "v1");
        assertFalse(new CanonicalCutoverPolicy(true, 0).permits(HierarchyMetrics.from(incomplete)));
    }

    private static CanonicalDocumentTree tree() {
        NormalizedElement root = element("root", null);
        NormalizedElement child = element("child", "root");
        NormalizedDocument document = new NormalizedDocument("v1", "test", "1", "source",
                List.of(new NormalizedPage(1, null, null)), List.of(root, child), List.of(), List.of(), List.of(), List.of(), List.of(), "");
        return new CanonicalHierarchyResolver("v1").resolve(document,
                new AnchorSkeleton(List.of(new AnchorSkeletonNode("root", "", 1), new AnchorSkeletonNode("child", "root", 1)), List.of()));
    }

    private static NormalizedElement element(String id, String parentId) {
        return new NormalizedElement(id, "HEADING", id, 1, 1, parentId, 1, List.of(),
                new NormalizedSourceSpan(id, 1, 1, null, null, null), "h1", "");
    }
}
