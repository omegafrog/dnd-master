package com.dndmaster.adventure.application.runtime;

/** Deterministic checks applied before narration is returned to the player. */
public final class DefaultNarrationSafetyPolicy implements NarrationSafetyPort {
    private static final java.util.regex.Pattern COORDINATE = java.util.regex.Pattern.compile("^(-?\\d+)\\s*,\\s*(-?\\d+)$");

    @Override
    public NarrationSafetyAssessment assess(NarrationSafetyRequest request) {
        String narration = request.narration();
        String reason = "approved";
        if (narration == null || narration.isBlank()) reason = "blank narration";
        else if (NarrationLeakDetector.isLikelySourceLeak(narration, request.evidencePack())) reason = "source leak or prohibited reference detected";
        else if (request.hiddenFacts().stream().anyMatch(fact -> mentionsHiddenFact(narration, fact))) reason = "unrevealed scenario fact detected";
        return new NarrationSafetyAssessment("approved".equals(reason), reason);
    }

    private static boolean mentionsHiddenFact(String narration, String fact) {
        var coordinate = COORDINATE.matcher(fact);
        if (coordinate.matches()) {
            return java.util.regex.Pattern.compile("(?<!\\d)" + coordinate.group(1) + "\\s*,\\s*"
                    + coordinate.group(2) + "(?!\\d)").matcher(narration).find();
        }
        return narration.toLowerCase(java.util.Locale.ROOT).contains(fact.toLowerCase(java.util.Locale.ROOT));
    }
}
