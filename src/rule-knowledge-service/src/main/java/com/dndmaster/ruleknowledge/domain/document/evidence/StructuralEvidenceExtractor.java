package com.dndmaster.ruleknowledge.domain.document.evidence;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Orchestrates independent evidence producers and preserves degraded signals. */
public final class StructuralEvidenceExtractor {
    private final PrintedNavigationExtractor navigationExtractor;
    private final NumberingAnalyzer numberingAnalyzer;
    private final HeadingFeatureExtractor headingFeatureExtractor;

    public StructuralEvidenceExtractor() {
        this(new PrintedNavigationExtractor(), new NumberingAnalyzer(), new HeadingFeatureExtractor());
    }

    public StructuralEvidenceExtractor(PrintedNavigationExtractor navigationExtractor,
                                       NumberingAnalyzer numberingAnalyzer,
                                       HeadingFeatureExtractor headingFeatureExtractor) {
        this.navigationExtractor = navigationExtractor;
        this.numberingAnalyzer = numberingAnalyzer;
        this.headingFeatureExtractor = headingFeatureExtractor;
    }

    public StructuralEvidenceExtractionResult extract(NormalizedDocument document) {
        List<StructuralEvidence> evidence = new ArrayList<>();
        PrintedNavigationResult navigationResult = navigationExtractor.extractWithDiagnostics(document);
        List<NavigationEntry> navigation = navigationResult.entries();
        navigation.forEach(entry -> evidence.add(new StructuralEvidence(EvidenceKind.PRINTED_NAVIGATION,
                entry.id(), entry.title() + "@" + entry.locator(), entry.confidence(), List.of(entry.id(), entry.sourceId()))));
        navigation.forEach(entry -> evidence.add(new StructuralEvidence(EvidenceKind.LOCATOR, entry.id(), entry.locator(),
                entry.locator().isBlank() ? 0.1 : 0.7, List.of(entry.id(), entry.sourceId()))));
        document.outlines().forEach(outline -> evidence.add(new StructuralEvidence(EvidenceKind.OUTLINE, outline.id(),
                outline.title() + "@" + outline.locator() + ":level=" + outline.level(), 0.8, List.of(outline.id()))));
        document.outlines().stream().filter(outline -> !outline.locator().isBlank()).forEach(outline -> evidence.add(
                new StructuralEvidence(EvidenceKind.LOCATOR, outline.id(), outline.locator(), 0.7, List.of(outline.id()))));
        evidence.addAll(numberingAnalyzer.extract(document.elements()));
        evidence.addAll(headingFeatureExtractor.extract(document.elements()));
        document.parserRelations().forEach(relation -> evidence.add(new StructuralEvidence(EvidenceKind.PARSER_HIERARCHY,
                relation.childId(), "parent=" + relation.parentId() + ";level=" + relation.level(), 0.6,
                List.of(relation.childId(), relation.parentId()))));
        List<String> diagnostics = new ArrayList<>(navigationResult.diagnostics());
        if (navigation.isEmpty()) diagnostics.add("printed navigation not detected");
        if (document.outlines().isEmpty()) diagnostics.add("outline unavailable");
        if (document.elements().stream().noneMatch(e -> e.layout().toLowerCase().contains("indent"))) diagnostics.add("indentation unavailable");
        if (document.parserRelations().isEmpty()) diagnostics.add("parser hierarchy unavailable");
        if (evidence.isEmpty()) diagnostics.add("no structural evidence available");
        evidence.sort(Comparator.comparing((StructuralEvidence e) -> e.kind().name())
                .thenComparing(StructuralEvidence::targetId)
                .thenComparing(StructuralEvidence::value)
                .thenComparing(e -> String.join("|", e.provenance())));
        return new StructuralEvidenceExtractionResult(evidence, navigation, diagnostics);
    }
}
