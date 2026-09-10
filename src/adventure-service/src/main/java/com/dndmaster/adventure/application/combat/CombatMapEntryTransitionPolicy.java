package com.dndmaster.adventure.application.combat;

import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.runtime.CurrentSituation;
import java.util.Locale;
import java.util.Objects;

/**
 * Decides whether a committed runtime turn entered a map-bearing location.
 *
 * Map visibility is tied to the committed story location, not to combat
 * start.  The transition guard prevents the opening scene from activating a
 * prepared map while still allowing a scene-only transition (for example,
 * the runtime moving from the brewery into the cellar) to activate it.
 */
public final class CombatMapEntryTransitionPolicy {
    private CombatMapEntryTransitionPolicy() {}

    public static boolean enteredMap(Adventure before, Adventure after) {
        if (before == null || after == null) return false;
        CurrentSituation previous = before.currentSituation();
        String previousScene = before.currentContext() == null ? null : before.currentContext().currentScene();
        return enteredMap(previousScene, previous, after);
    }

    /** Uses an immutable pre-turn snapshot so in-memory repositories cannot hide a transition by mutating the same aggregate instance. */
    public static boolean enteredMap(String previousScene, CurrentSituation previous, Adventure after) {
        if (after == null || previous == null) return false;
        CurrentSituation current = after.currentSituation();
        if (current == null || after.currentContext() == null) return false;
        boolean situationChanged = !Objects.equals(previous.situationId(), current.situationId())
                || !Objects.equals(previous.location(), current.location());
        boolean sceneChanged = !Objects.equals(previousScene, after.currentContext().currentScene());
        return (situationChanged || sceneChanged) && hasMapLocationCue(after, current);
    }

    /** Returns whether the committed player-facing situation is map-bearing. */
    public static boolean isMapBearing(Adventure adventure) {
        return adventure != null && adventure.currentSituation() != null
                && adventure.currentContext() != null && hasMapLocationCue(adventure, adventure.currentSituation());
    }

    private static boolean hasMapLocationCue(Adventure adventure, CurrentSituation situation) {
        String context = (adventure.currentContext().currentScene() + " " + situation.location() + " "
                + situation.problem() + " " + situation.threat() + " " + situation.goal())
                .toLowerCase(Locale.ROOT);
        return contains(context, "지하", "지하실", "저장고", "창고", "회랑", "던전", "납골당",
                "cellar", "basement", "warehouse", "corridor", "dungeon", "crypt", "hatch", "해치",
                "stairs down", "계단 아래");
    }

    private static boolean contains(String value, String... signals) {
        for (String signal : signals) if (value.contains(signal)) return true;
        return false;
    }
}
