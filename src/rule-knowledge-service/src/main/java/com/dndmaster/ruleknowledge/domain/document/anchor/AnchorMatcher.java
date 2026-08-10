package com.dndmaster.ruleknowledge.domain.document.anchor;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Requires corroboration beyond title equality before confirming a match. */
public final class AnchorMatcher {
    public static final double CONFIRMATION_THRESHOLD = 0.75;
    private static final double AMBIGUITY_MARGIN = 0.10;

    public MatchedAnchor match(StructuralAnchor anchor, List<NormalizedElement> elements,
                               PageLocatorResolver.LocatorMapping locations) {
        List<Candidate> candidates = elements.stream()
                .filter(element -> !anchor.evidenceIds().contains(element.id()))
                .filter(this::headingCandidate)
                .map(element -> candidate(anchor, element, locations))
                .filter(candidate -> candidate.score > 0)
                .sorted(Comparator.comparingDouble(Candidate::score).reversed().thenComparing(c -> c.element.id()))
                .toList();
        if (candidates.isEmpty()) return MatchedAnchor.unresolved(anchor, List.of("no heading candidate"));
        Candidate best = candidates.get(0);
        boolean tied = candidates.size() > 1 && best.score - candidates.get(1).score < AMBIGUITY_MARGIN;
        if (best.score < CONFIRMATION_THRESHOLD || anchor.confidence() < 0.5 || tied) {
            return MatchedAnchor.unresolved(anchor, tied ? List.of("ambiguous candidate tie") : best.evidence);
        }
        return new MatchedAnchor(anchor, best.element.id(), best.score, true, best.evidence);
    }

    private Candidate candidate(StructuralAnchor anchor, NormalizedElement element, PageLocatorResolver.LocatorMapping locations) {
        List<String> evidence = new ArrayList<>();
        double score = 0;
        boolean titleMatch = normal(anchor.title()).equals(normal(stripNumbering(element.text())));
        if (titleMatch) { score += 0.45; evidence.add("title"); }
        ResolvedLocation location = locations.resolve(anchor.locator());
        if (location.confidence() >= 0.5 && location.physicalPage().isPresent() && location.physicalPage().getAsInt() == element.page()) { score += 0.40; evidence.add("locator"); }
        if (!anchor.numbering().isBlank() && element.text().trim().startsWith(anchor.numbering() + " ")) { score += 0.15; evidence.add("numbering"); }
        if (anchor.levelSuggestion() != null && titleMatch) { score += 0.25; evidence.add("toc-level"); }
        if ("HEADING".equalsIgnoreCase(element.type())) { score += 0.05; evidence.add("parser-heading"); }
        if (!element.style().isBlank()) { score += 0.05; evidence.add("style"); }
        return new Candidate(element, Math.min(1, score), List.copyOf(evidence));
    }

    private String stripNumbering(String value) { return value == null ? "" : value.replaceFirst("^[0-9]+(?:\\.[0-9]+)*\\s+", ""); }
    private boolean headingCandidate(NormalizedElement element) {
        return "HEADING".equalsIgnoreCase(element.type()) || !element.style().isBlank()
                && element.text().length() <= 140 && !element.text().matches(".*[.!?].*");
    }
    private String normal(String value) {
        if (value == null) return "";
        return value.replaceFirst("(?i)^ch\\.\\s*", "chapter ")
                .replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
    private record Candidate(NormalizedElement element, double score, List<String> evidence) {}
}
