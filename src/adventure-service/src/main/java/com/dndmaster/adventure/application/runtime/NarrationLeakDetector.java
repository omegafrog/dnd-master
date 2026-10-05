package com.dndmaster.adventure.application.runtime;

import java.util.Locale;
import java.util.regex.Pattern;

/** Detects provider output that appears to reproduce retrieval/source material. */
public final class NarrationLeakDetector {
    private static final String HIT_POINT_TERM = "(?:\\bhp\\b|hit\\s*points?|health|체력|생명력|생명점)";
    private static final String NUMBER = "(?:\\d+(?:/\\d+)?|zero|one|two|three|four|five|six|seven|eight|nine|ten|"
            + "영|공|일|이|삼|사|오|육|칠|팔|구|십|백|천|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열)";
    private static final Pattern HIT_POINT_VALUE = Pattern.compile("(?iu)" + HIT_POINT_TERM + ".{0,40}" + NUMBER
            + "|" + NUMBER + ".{0,40}" + HIT_POINT_TERM);
    private static final Pattern REMAINING_AMOUNT = Pattern.compile("(?iu)(?:down\\s+to|left|remaining|남은|남아|남았다|남았습니다|남겼다)"
            + ".{0,20}" + NUMBER + "|" + NUMBER + ".{0,20}(?:left|remaining|남은|남아|남았다|남았습니다)");

    private NarrationLeakDetector() {}

    public static boolean isLikelySourceLeak(String narration, EvidencePack evidencePack) {
        if (narration == null || narration.isBlank() || evidencePack == null) return true;
        String normalized = normalize(narration);
        for (RuntimeEvidence evidence : java.util.stream.Stream.of(evidencePack.storybook(), evidencePack.rulebook(), evidencePack.resolution())
                .flatMap(java.util.List::stream).toList()) {
            String excerpt = normalize(evidence.excerpt());
            if (excerpt.length() >= 80 && normalized.contains(excerpt)) return true;
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        return normalized.length() > 6000 || lower.contains("open game license")
                || (lower.contains("basic rules") && normalized.length() > 1800)
                || lower.contains("chapter 1: step-by-step characters")
                || (lower.contains("contents") && normalized.length() > 2500);
    }

    /** Rejects numeric hit-point disclosure in combat narration, including paraphrased values. */
    public static boolean isHitPointValueDisclosure(String narration,
            com.dndmaster.adventure.application.combat.ConfirmedCombatState combatState) {
        if (narration == null || narration.isBlank() || combatState == null || combatState.enemies().isEmpty()) return false;
        String normalizedNarration = normalize(narration).toLowerCase(Locale.ROOT);
        for (String sentence : normalizedNarration.split("(?<=[.!?。！？])|[\\r\\n]")) {
            boolean namesEnemy = combatState.enemies().stream()
                    .map(enemy -> normalize(enemy.displayName()).toLowerCase(Locale.ROOT))
                    .anyMatch(sentence::contains);
            if (HIT_POINT_VALUE.matcher(sentence).find()
                    || (namesEnemy && REMAINING_AMOUNT.matcher(sentence).find())) return true;
        }
        return false;
    }

    private static String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim(); }
}
