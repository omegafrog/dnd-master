package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small deterministic command language. Narrative prose has no authoritative mutation. */
public final class DeterministicRuleResolver implements Function<DeterministicAdjudicationRequest, AuthoritativeResolution> {
    private static final Pattern ROLL = Pattern.compile("^roll\\s+(\\d+)d(\\d+)([+-]\\d+)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DAMAGE = Pattern.compile("^damage\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOVE = Pattern.compile("^move\\s+(\\d+)$", Pattern.CASE_INSENSITIVE);

    @Override
    public AuthoritativeResolution apply(DeterministicAdjudicationRequest request) {
        String action = request.action().trim();
        Matcher roll = ROLL.matcher(action);
        if (roll.matches()) return roll(request, roll);
        Matcher damage = DAMAGE.matcher(action);
        if (damage.matches()) return AuthoritativeResolution.resolved(
                "damage-applied", List.of("target.hp=-" + damage.group(1)), provenance(request, action));
        Matcher move = MOVE.matcher(action);
        if (move.matches()) return AuthoritativeResolution.resolved(
                "movement-applied", List.of("movement.distance=" + move.group(1)), provenance(request, action));
        if (action.equalsIgnoreCase("end turn")) return AuthoritativeResolution.resolved(
                "turn-ended", List.of("turn.advance=true"), provenance(request, action));
        return AuthoritativeResolution.resolved("narration-only", List.of(), provenance(request, action));
    }

    private AuthoritativeResolution roll(DeterministicAdjudicationRequest request, Matcher match) {
        int count = bounded(match.group(1), 100);
        int sides = bounded(match.group(2), 1000);
        int modifier = match.group(3) == null ? 0 : Integer.parseInt(match.group(3));
        SplittableRandom random = new SplittableRandom(request.seed());
        int total = modifier;
        StringBuilder faces = new StringBuilder();
        for (int index = 0; index < count; index++) {
            int face = random.nextInt(sides) + 1;
            total += face;
            if (index > 0) faces.append(',');
            faces.append(face);
        }
        return AuthoritativeResolution.resolved("roll-total=" + total,
                List.of("roll.faces=" + faces, "roll.total=" + total), provenance(request, match.group()));
    }

    private static List<String> provenance(DeterministicAdjudicationRequest request, String action) {
        return List.of("action=" + action, "seed=" + request.seed(), "state=" + request.stateFingerprint());
    }

    private static int bounded(String value, int max) {
        int parsed = Integer.parseInt(value);
        if (parsed < 1 || parsed > max) throw new IllegalArgumentException("rule command value out of range");
        return parsed;
    }
}
