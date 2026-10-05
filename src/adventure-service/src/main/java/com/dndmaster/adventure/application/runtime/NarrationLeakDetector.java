package com.dndmaster.adventure.application.runtime;

import java.util.Locale;
import java.util.regex.Pattern;

/** Detects provider output that appears to reproduce retrieval/source material. */
public final class NarrationLeakDetector {
    private static final String HIT_POINT_TERM = "(?:\\bhp\\b|hit\\s*points?|\\bhealth\\b|체력|생명력|생명점)";
    private static final String KOREAN_UNIT = "(?:하나|둘|셋|넷|한|두|세|네|다섯|여섯|일곱|여덟|아홉)";
    private static final String KOREAN_TENS = "(?:열|스물|서른|마흔|쉰|예순|일흔|여든|아흔)";
    private static final String KOREAN_NUMBER = "(?:영|공|일|이|삼|사|오|육|칠|팔|구|"
            + KOREAN_TENS + KOREAN_UNIT + "?|"
            + "하나|둘|셋|넷|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|스물|서른|마흔|쉰|예순|일흔|여든|아흔|"
            + "[일이삼사오육칠팔구]?천(?:[일이삼사오육칠팔구]?백)?(?:[일이삼사오육칠팔구]?십)?[일이삼사오육칠팔구]?|"
            + "[일이삼사오육칠팔구]?백(?:[일이삼사오육칠팔구]?십)?[일이삼사오육칠팔구]?|"
            + "[일이삼사오육칠팔구]?십[일이삼사오육칠팔구]?)";
    private static final String ENGLISH_UNIT = "(?:one|two|three|four|five|six|seven|eight|nine)";
    private static final String ENGLISH_NUMBER = "(?:zero|" + ENGLISH_UNIT
            + "|ten|eleven|twelve|thirteen|fourteen|fifteen|sixteen|seventeen|eighteen|nineteen|"
            + "twenty|thirty|forty|fifty|sixty|seventy|eighty|ninety)(?:[- ]" + ENGLISH_UNIT + ")?";
    private static final String NUMBER = "(?:(?<!\\d)\\d+(?:/\\d+)?(?!\\d)|\\b" + ENGLISH_NUMBER + "\\b|"
            + "(?<![가-힣])(?:" + KOREAN_NUMBER + ")(?:\\s*(?:마리|명|개|발|번|걸음|회|점))?(?:이라고|이다|다|입니다|이었|였|이|가|은|는|을|를|와|과|도|만|로|으로|의)?(?![가-힣]))";
    private static final Pattern NUMBER_VALUE = Pattern.compile("(?iu)" + NUMBER);
    private static final Pattern HIT_POINT_TERM_PATTERN = Pattern.compile("(?iu)" + HIT_POINT_TERM);
    private static final Pattern REMAINING_CONTEXT = Pattern.compile("(?iu)(?:down\\s+to|\\bleft\\b|\\bremaining\\b|"
            + "\\bremain\\b|남(?:아|은|았|는다|습니다|음)|남겨)");
    private static final String NON_HIT_POINT_UNIT = "(?:arrows?|bolts?|darts?|stones?|spells?|spell slots?|potions?|charges?|"
            + "creatures?|people|화살|볼트|다트|돌멩이|주문 슬롯|물약|충전 횟수)";
    private static final Pattern NON_HIT_POINT_COUNT_CONTEXT = Pattern.compile("(?iu)(?:" + NUMBER
            + "(?:\\s+of\\s+" + NUMBER + ")?\\s*(?:remaining\\s+)?" + NON_HIT_POINT_UNIT
            + "|" + NUMBER + "\\s*(?:발|개|회|점)?\\s*(?:의\\s*)?" + NON_HIT_POINT_UNIT
            + "|(?<![가-힣])(?:" + KOREAN_NUMBER + ")\\s*발(?:의\\s*)?화살)");
    private static final Pattern PAIRED_AMOUNTS = Pattern.compile("(?iu)(?:" + NUMBER + ")\\s+of\\s+(?:" + NUMBER
            + ")|(?:" + NUMBER + ")\\s*중\\s*(?:" + NUMBER + ")");

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
            boolean includesNumber = NUMBER_VALUE.matcher(sentence).find();
            boolean hasNearbyHitPointValue = hasNearbyHitPointValue(sentence);
            boolean describesRemainingAmount = REMAINING_CONTEXT.matcher(sentence).find();
            boolean comparesAmounts = PAIRED_AMOUNTS.matcher(sentence).find();
            boolean namesNonHitPointCount = NON_HIT_POINT_COUNT_CONTEXT.matcher(sentence).find();
            if ((hasNearbyHitPointValue && includesNumber)
                    || (describesRemainingAmount && includesNumber && comparesAmounts)
                    || (namesEnemy && describesRemainingAmount && includesNumber && !namesNonHitPointCount)
                    || (namesEnemy && includesNumber && comparesAmounts && !namesNonHitPointCount)) return true;
        }
        return false;
    }

    private static boolean hasNearbyHitPointValue(String sentence) {
        var hitPointMatcher = HIT_POINT_TERM_PATTERN.matcher(sentence);
        while (hitPointMatcher.find()) {
            var numberMatcher = NUMBER_VALUE.matcher(sentence);
            while (numberMatcher.find()) {
                int gapStart = Math.min(hitPointMatcher.end(), numberMatcher.end());
                int gapEnd = Math.max(hitPointMatcher.start(), numberMatcher.start());
                if (gapEnd <= gapStart) return true;
                String gap = sentence.substring(gapStart, gapEnd).trim();
                if (gap.isEmpty() || gap.split("\\s+").length <= 4) return true;
            }
        }
        return false;
    }

    private static String normalize(String value) { return value == null ? "" : value.replaceAll("\\s+", " ").trim(); }
}
