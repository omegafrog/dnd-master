package com.dndmaster.ruleknowledge.application.search;

import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Puts the concrete opening locations ahead of broad summaries when an opening
 * scene is being searched for. The vector/search score remains untouched.
 */
final class OpeningSceneEvidenceReranker {
    private static final Pattern OPENING_SCENE_REQUEST = Pattern.compile(
            "(?iu)(?:\\b(opening|initial|earliest|numbered\\s+location|first(?:\\s+(?:playable|location|area|room|scene))?|"
                    + "(?:location|area|room)\\s+(?:number\\s+)?1|start(?:ing)?\\s+(?:location|area|scene|of\\s+play))\\b"
                    + "|시작(?:\\s*(?:장면|위치|장소|지역|방|구역|상황))?"
                    + "|첫(?:\\s*번째)?\\s*(?:장면|위치|장소|지역|방|구역|상황|씬)"
                    + "|처음(?:\\s*(?:장면|위치|장소|지역|방|구역|상황))?"
                    + "|최초(?:\\s*(?:장면|위치|장소|지역|방|구역|상황))?"
                    + "|초반(?:\\s*(?:장면|위치|장소|지역|방|구역|상황))?"
                    + "|도입(?:부|\\s*(?:장면|위치|장소|지역|방|구역|상황))?"
                    + "|(?:장소|지역|방|구역|장면|씬)\\s*(?:번호\\s*)?(?:1|1번|첫(?:\\s*번째)?))");
    private static final Pattern LOCATION_HEADING = Pattern.compile(
            "(?imu)^\\s*(?:(?:area|room|location|zone|scene|chamber|장소|지역|방|구역|장면|씬|장)\\s*(?:번호\\s*)?)?"
                    + "(?:제\\s*)?(\\d{1,3}|첫(?:\\s*번째)?|처음)(?:\\s*(?:번|호))?"
                    + "(?:\\s*(?:area|room|location|zone|scene|chamber|장소|지역|방|구역|장면|씬|장))?"
                    + "\\s*[.)\\-:：–—]\\s*([^\\r\\n]{2,100})\\s*$");

    private OpeningSceneEvidenceReranker() {
    }

    static List<StorySourceEvidence> rerankForOpeningScene(StorySourceSearchQuery query,
            List<StorySourceEvidence> candidates) {
        if (!OPENING_SCENE_REQUEST.matcher(query.situation()).find() || candidates.size() < 2) {
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
        String number = matcher.group(1);
        return java.util.Optional.of(number.startsWith("첫") || number.equals("처음")
                ? 1 : Integer.parseInt(number));
    }
}
