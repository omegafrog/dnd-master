package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.Locale;
import java.util.Objects;

/** Resolves a map entry side from the committed scene and current situation. */
public final class CombatMapEntryContextResolver {
    private CombatMapEntryContextResolver() {}

    public static String entrySide(Adventure adventure, CurrentSituation situation) {
        Objects.requireNonNull(adventure, "adventure must not be null");
        Objects.requireNonNull(situation, "situation must not be null");
        String context = (adventure.currentContext().currentScene() + " " + situation.location() + " "
                + situation.problem() + " " + situation.threat() + " " + situation.goal())
                .toLowerCase(Locale.ROOT);
        // Explicit directions in the committed situation always win.
        if (contains(context, "north", "북쪽", "북문", "위쪽", "위에서", "upper", "top")) return "NORTH";
        if (contains(context, "east", "동쪽", "동문", "오른쪽", "right")) return "EAST";
        if (contains(context, "south", "남쪽", "남문", "아래쪽", "아래에서", "lower", "bottom")) return "SOUTH";
        if (contains(context, "west", "서쪽", "서문", "왼쪽", "left")) return "WEST";
        // A hatch/stair descent into a basement enters from the upper edge of
        // the prepared map. This is derived from the situation, not guessed at
        // adventure start; an unrelated location remains unresolved.
        if (contains(context, "지하", "cellar", "basement", "해치", "hatch", "계단 아래", "stairs down")) return "NORTH";
        return null;
    }

    private static boolean contains(String value, String... signals) {
        for (String signal : signals) if (value.contains(signal)) return true;
        return false;
    }
}
