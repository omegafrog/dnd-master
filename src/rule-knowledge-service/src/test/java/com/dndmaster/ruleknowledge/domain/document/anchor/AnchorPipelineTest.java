package com.dndmaster.ruleknowledge.domain.document.anchor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dndmaster.ruleknowledge.domain.document.evidence.NavigationEntry;
import com.dndmaster.ruleknowledge.domain.document.evidence.StructuralEvidenceExtractor;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedPage;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedTable;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedSourceSpan;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnchorPipelineTest {
    @Test
    void matchesDuplicateTitlesOnlyWhenLocatorCorroboratesBodyPage() {
        StructuralAnchor anchor = new AnchorBuilder().fromNavigation(new NavigationEntry(
                "nav-1", "Actions", "12", null, "toc-1", "Actions .... 12", 0.9));
        List<NormalizedElement> headings = List.of(
                heading("h-early", "Actions", 3, 1), heading("h-target", "Actions", 12, 8));

        MatchedAnchor result = new AnchorMatcher().match(anchor, headings, new PageLocatorResolver().direct());

        assertTrue(result.confirmed());
        assertEquals("h-target", result.bodyElementId());
        assertTrue(result.score() >= AnchorMatcher.CONFIRMATION_THRESHOLD);
    }

    @Test
    void leavesDuplicateTitleTieUnresolvedWithoutCorroboratingLocator() {
        StructuralAnchor anchor = new AnchorBuilder().fromNavigation(new NavigationEntry(
                "nav-1", "Actions", "", null, "toc-1", "Actions", 0.8));

        MatchedAnchor result = new AnchorMatcher().match(anchor,
                List.of(heading("h-1", "Actions", 3, 1), heading("h-2", "Actions", 4, 2)),
                new PageLocatorResolver().direct());

        assertFalse(result.confirmed());
        assertEquals("", result.bodyElementId());
    }

    @Test
    void resolvesDirectOffsetAndRomanLocatorsWithoutTreatingRawLocatorAsPhysicalPage() {
        PageLocatorResolver resolver = new PageLocatorResolver();

        assertEquals(12, resolver.direct().resolve("12").physicalPage().orElseThrow());
        assertEquals(15, resolver.offset(3).resolve("12").physicalPage().orElseThrow());
        assertEquals(4, resolver.direct().resolve("iv").physicalPage().orElseThrow());
        assertEquals(103, resolver.segmented(List.of(new LocatorSegment(1, 20, 0), new LocatorSegment(1, 20, 100)))
                .resolve("3", 1).physicalPage().orElseThrow());
    }

    @Test
    void rejectsLowConfidenceAnchorAndKeepsDuplicateOwnershipUnresolved() {
        StructuralAnchor weak = new StructuralAnchor("weak", "Actions", "12", "", null, List.of("bad"), 0.2);
        MatchedAnchor match = new AnchorMatcher().match(weak, List.of(heading("h", "Actions", 12, 1)), new PageLocatorResolver().direct());
        assertFalse(match.confirmed());

        StructuralAnchor first = new StructuralAnchor("first", "Actions", "12", "1", null, List.of(), 0.9);
        StructuralAnchor duplicate = new StructuralAnchor("duplicate", "Actions", "12", "2", null, List.of(), 0.9);
        AnchorSkeleton skeleton = new AnchorTreeBuilder().build(List.of(
                new MatchedAnchor(first, "h", 0.9, true, List.of()),
                new MatchedAnchor(duplicate, "h", 0.9, true, List.of())));
        assertEquals(1, skeleton.nodes().size());
        assertEquals(1, skeleton.unresolved().size());
    }

    @Test
    void publishesValidatedShadowSkeletonFromNormalizedDocument() {
        NormalizedElement body = heading("body", "Actions", 12, 1);
        NormalizedDocument document = new NormalizedDocument("v1", "test", "1", "source",
                List.of(new NormalizedPage(1, null, null), new NormalizedPage(12, null, null)),
                List.of(body), List.of(new NormalizedTable("toc", 1, List.of(List.of("Actions", "12")))),
                List.of(), List.of(), List.of(), List.of(), "");

        AnchorSkeletonResolution result = new AnchorSkeletonResolver().resolve(document, new StructuralEvidenceExtractor().extract(document));

        assertEquals(List.of("body"), result.skeleton().nodes().stream().map(AnchorSkeletonNode::bodyElementId).toList());
    }

    @Test
    void derivesBasicRulesPartChapterAndSectionParentsFromItsPrintedContents() {
        NormalizedDocument document = new NormalizedDocument("v1", "docling", "1", "basic-rules",
                List.of(new NormalizedPage(2, null, null), new NormalizedPage(7, null, null), new NormalizedPage(8, null, null), new NormalizedPage(12, null, null)),
                List.of(
                        heading("toc-introduction", "Introduction", 2, 0),
                        heading("toc-part", "Part 1: Creating a Character", 2, 1),
                        heading("toc-chapter", "Ch. 1: Step-by-Step Characters..............8", 2, 2),
                        heading("toc-section", "Beyond 1st Level....................................................................12", 2, 3),
                        heading("part", "Part 1: Creating a Character", 7, 0),
                        heading("chapter", "Chapter 1: Step-by-Step Characters", 8, 0),
                        heading("section", "Beyond 1st Level", 12, 0)),
                List.of(), List.of(), List.of(), List.of(), List.of(), "D&D Basic Rules");

        AnchorSkeleton skeleton = new AnchorSkeletonResolver().resolve(document,
                new StructuralEvidenceExtractor().extract(document)).skeleton();

        assertEquals("", parentOf(skeleton, "part"));
        assertEquals("part", parentOf(skeleton, "chapter"));
        assertEquals("chapter", parentOf(skeleton, "section"));
    }

    @Test
    void derivesDnd5eContentsHierarchyWithoutAssumingTheContentsIsOnPageTwo() {
        NormalizedDocument document = new NormalizedDocument("v1", "docling", "1", "dnd5e",
                List.of(new NormalizedPage(7, null, null), new NormalizedPage(20, null, null), new NormalizedPage(21, null, null)),
                List.of(
                        heading("contents", "Contents", 7, 0),
                        heading("toc-part", "Part 2: Playing the Game", 7, 1),
                        heading("toc-chapter", "Ch. 7: Using Ability Scores ................. 20", 7, 2),
                        heading("toc-section", "Ability Scores and Modifiers...................20", 7, 3),
                        heading("part", "Part 2: Playing the Game", 20, 0),
                        heading("chapter", "Chapter 7: Using Ability Scores", 20, 1),
                        heading("section", "Ability Scores and Modifiers", 21, 0)),
                List.of(), List.of(), List.of(), List.of(), List.of(), "D&D Basic Rules");

        AnchorSkeleton skeleton = new AnchorSkeletonResolver().resolve(document,
                new StructuralEvidenceExtractor().extract(document)).skeleton();

        assertEquals("part", parentOf(skeleton, "chapter"));
        assertEquals("chapter", parentOf(skeleton, "section"));
    }

    @Test
    void keepsDnd5eTailMatterOutOfTheFinalAppendix() {
        NormalizedDocument document = new NormalizedDocument("v1", "docling", "1", "dnd5e",
                List.of(new NormalizedPage(7, null, null), new NormalizedPage(171, null, null), new NormalizedPage(177, null, null)),
                List.of(
                        heading("contents", "Contents", 7, 0),
                        heading("toc-appendices", "Appendices", 7, 1),
                        heading("toc-appendix", "Appendix C: The Five Factions.............174", 7, 2),
                        heading("toc-sheet", "Character Sheet...............................177", 7, 3),
                        heading("appendices", "Appendices", 171, 0),
                        heading("appendix", "Appendix C: The Five Factions", 171, 1),
                        heading("sheet", "Character Sheet", 177, 0)),
                List.of(), List.of(), List.of(), List.of(), List.of(), "D&D Basic Rules");

        AnchorSkeleton skeleton = new AnchorSkeletonResolver().resolve(document,
                new StructuralEvidenceExtractor().extract(document)).skeleton();

        assertEquals("", parentOf(skeleton, "sheet"));
    }

    private static String parentOf(AnchorSkeleton skeleton, String id) {
        return skeleton.nodes().stream().filter(node -> node.bodyElementId().equals(id)).findFirst().orElseThrow().parentBodyElementId();
    }

    private static NormalizedElement heading(String id, String text, int page, int order) {
        return new NormalizedElement(id, "HEADING", text, page, order, null, null, List.of(),
                new NormalizedSourceSpan(id, page, order, null, null, null), "heading", "");
    }
}
