package com.dndmaster.ruleknowledge.application.search;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts the concrete opening locations ahead of broad summaries when an opening
 * location is being searched for. The vector/search score remains untouched.
 */
final class StorySourceEvidenceReranker {
    private static final Pattern OPENING_REQUEST = Pattern.compile(
            "(?i)\\b(opening|initial|earliest|numbered\\s+location|first(?:\\s+(?:playable|location|area|room))?|"
                    + "(?:location|area|room)\\s+(?:number\\s+)?1|start(?:ing)?\\s+(?:location|area|scene|of\\s+play))\\b");
    private static final Pattern LOCATION_HEADING = Pattern.compile(
            "(?im)^\\s*(?:(?:area|room|location|zone|scene|chamber)\\s+)?(\\d{1,3})\\s*[.)\\-:]\\s+([^\\r\\n]{2,100})\\s*$");

    private StorySourceEvidenceReranker() {
    }

    static List<StorySourceEvidence> rerankForOpening(StorySourceSearchQuery query,
            List<StorySourceEvidence> candidates) {
        if (!OPENING_REQUEST.matcher(query.situation()).find() || candidates.size() < 2) {
            return candidates;
        }

        return candidates.stream()
                .sorted(Comparator
                        .comparing((StorySourceEvidence evidence) -> locationHeading(evidence).isPresent())
                        .reversed()
                        .thenComparing(evidence -> locationHeading(evidence).orElse(Integer.MAX_VALUE))
                        .thenComparingInt(evidence -> evidence.provenance().pageNumber())
                        .thenComparing(Comparator.comparingDouble(StorySourceEvidence::score).reversed()))
                .toList();
    }

    private static java.util.Optional<Integer> locationHeading(StorySourceEvidence evidence) {
        Matcher matcher = LOCATION_HEADING.matcher(evidence.excerpt());
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(Integer.parseInt(matcher.group(1)));
    }
}
