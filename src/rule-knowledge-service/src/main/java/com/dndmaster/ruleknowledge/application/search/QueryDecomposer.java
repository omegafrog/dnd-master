package com.dndmaster.ruleknowledge.application.search;

import java.util.*;
import java.util.regex.Pattern;

public final class QueryDecomposer {
    private QueryDecomposer() {}
    public static List<QueryIntentPart> decompose(String action) {
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        String normalized = action.trim().replaceAll("\\s+", " ");
        List<QueryIntentPart> parts = new ArrayList<>();
        add(parts, DecomposedIntent.RULES, normalized, "rule|rules|spell|ability|check|save|damage|규칙|주문|판정");
        add(parts, DecomposedIntent.SCENE, normalized, "door|room|place|scene|location|문|방|장면|장소");
        add(parts, DecomposedIntent.NPC, normalized, "npc|goblin|merchant|guard|person|인물|경비|상인");
        add(parts, DecomposedIntent.COMBAT, normalized, "attack|fight|combat|weapon|initiative|hit|전투|공격|무기");
        add(parts, DecomposedIntent.RESOURCES, normalized, "slot|spell slot|hp|health|resource|potion|자원|슬롯|체력");
        add(parts, DecomposedIntent.CONTINUITY, normalized, "again|previous|before|earlier|last turn|continuity|과거|이전|연속");
        if (parts.isEmpty()) parts.add(new QueryIntentPart(DecomposedIntent.CONTINUITY, normalized));
        return List.copyOf(parts);
    }
    private static void add(List<QueryIntentPart> parts, DecomposedIntent intent, String text, String pattern) {
        if (Pattern.compile("(?:" + pattern + ")", Pattern.CASE_INSENSITIVE).matcher(text).find()) parts.add(new QueryIntentPart(intent, text));
    }
}
