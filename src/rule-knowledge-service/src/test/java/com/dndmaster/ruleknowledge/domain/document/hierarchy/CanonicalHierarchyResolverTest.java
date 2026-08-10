package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeleton;
import com.dndmaster.ruleknowledge.domain.document.anchor.AnchorSkeletonNode;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedPage;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedSourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class CanonicalHierarchyResolverTest {
    @Test
    void preservesAmbiguousContentUnderUnresolvedWithoutChangingLeafSpan() {
        NormalizedElement parent = element("h1", "HEADING", "Rules", 1, 0);
        NormalizedElement ambiguous = element("p1", "PARAGRAPH", "shared text", 1, 1);
        NormalizedDocument document = document(parent, ambiguous);

        CanonicalDocumentTree tree = new CanonicalHierarchyResolver("resolver.v1").resolve(document,
                new AnchorSkeleton(List.of(new AnchorSkeletonNode("h1", "", 0.9)), List.of()));

        assertEquals(ResolutionStatus.CONFIRMED, tree.edgeFor("h1").orElseThrow().status());
        assertEquals(ResolutionStatus.UNRESOLVED, tree.edgeFor("p1").orElseThrow().status());
        assertEquals("p1", tree.node("p1").orElseThrow().leafSpan().sourceId());
        assertTrue(tree.semanticPath("p1").isEmpty());
    }

    @Test
    void derivesParentSpanFromConfirmedDescendantsWithoutRewritingLeaves() {
        NormalizedElement root = element("z-root", "HEADING", "Rules", 1, 0);
        NormalizedElement child = element("a-child", "HEADING", "Combat", 3, 5);
        CanonicalDocumentTree tree = new CanonicalHierarchyResolver("resolver.v1").resolve(document(root, child),
                new AnchorSkeleton(List.of(new AnchorSkeletonNode("z-root", "", 0.9),
                        new AnchorSkeletonNode("a-child", "z-root", 0.9)), List.of()));

        DerivedSourceSpan span = tree.derivedSpan("z-root").orElseThrow();
        assertEquals(1, span.firstPage());
        assertEquals(3, span.lastPage());
        assertEquals("a-child", tree.node("a-child").orElseThrow().leafSpan().sourceId());
    }

    @Test
    void confirmsNonAnchorOnlyFromParserTypographyAndHeadingSignals() {
        NormalizedElement anchor = element("anchor", "HEADING", "Rules", 1, 0);
        NormalizedElement child = new NormalizedElement("child", "HEADING", "Combat", 2, 1, "anchor", 2, List.of(),
                new NormalizedSourceSpan("child", 2, 1, null, null, null), "heading-2", "");
        CanonicalDocumentTree tree = new CanonicalHierarchyResolver("resolver.v1").resolve(document(anchor, child),
                new AnchorSkeleton(List.of(new AnchorSkeletonNode("anchor", "", 0.9)), List.of()));

        assertEquals(ResolutionStatus.TENTATIVE, tree.edgeFor("child").orElseThrow().status());
        assertEquals("anchor", tree.edgeFor("child").orElseThrow().parentId());
        assertTrue(tree.node("UNRESOLVED").orElseThrow().synthetic());
    }

    private static NormalizedDocument document(NormalizedElement... elements) {
        return new NormalizedDocument("v1", "test", "1", "source", List.of(new NormalizedPage(1, null, null)),
                List.of(elements), List.of(), List.of(), List.of(), List.of(), List.of(), "");
    }
    private static NormalizedElement element(String id, String type, String text, int page, int order) {
        return new NormalizedElement(id, type, text, page, order, null, null, List.of(),
                new NormalizedSourceSpan(id, page, order, null, null, null), "", "");
    }
}
