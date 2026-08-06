package com.dndmaster.ruleknowledge.application.search;

import java.util.*;
import java.util.regex.Pattern;

public final class QueryDecomposer {
    private QueryDecomposer() {}
    public static List<QueryIntentPart> decompose(String action) {
        if (action == null || action.isBlank()) throw new IllegalArgumentException("action must not be blank");
        String normalized = action.trim().replaceAll("\\s+", " ");
        List<QueryIntentPart> parts = new ArrayList<>();
        String[] clauses = normalized.split("(?i)\\s+(?:and|then|also|while|after|before)\\s+|[,.;!?]+");
        add(parts, DecomposedIntent.RULES, clauses, "rule|rules|spell|ability|check|save|damage|규칙|주문|판정");
        add(parts, DecomposedIntent.SCENE, clauses, "door|room|place|scene|location|문|방|장면|장소");
        add(parts, DecomposedIntent.NPC, clauses, "npc|goblin|merchant|guard|person|인물|경비|상인");
        add(parts, DecomposedIntent.COMBAT, clauses, "attack|fight|combat|weapon|initiative|hit|전투|공격|무기");
        add(parts, DecomposedIntent.RESOURCES, clauses, "slot|spell slot|hp|health|resource|potion|자원|슬롯|체력");
        add(parts, DecomposedIntent.CONTINUITY, clauses, "again|previous|before|earlier|last turn|continuity|과거|이전|연속");
        if (parts.isEmpty()) parts.add(new QueryIntentPart(DecomposedIntent.CONTINUITY, normalized));
        return List.copyOf(parts);
    }
    private static void add(List<QueryIntentPart> parts, DecomposedIntent intent, String[] clauses, String pattern) {
        Pattern matcher = Pattern.compile("(?:" + pattern + ")", Pattern.CASE_INSENSITIVE);
        String query = java.util.Arrays.stream(clauses).map(String::trim).filter(clause -> !clause.isBlank())
                .filter(clause -> matcher.matcher(clause).find()).reduce((left, right) -> left + " " + right).orElse("");
        if (!query.isBlank()) parts.add(new QueryIntentPart(intent, query));
    }
}
