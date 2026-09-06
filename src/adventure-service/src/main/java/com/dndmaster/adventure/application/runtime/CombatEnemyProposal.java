package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;

/** A GM reference to a scenario combat, never an arbitrary enemy name. */
public record CombatEnemyProposal(String scenarioId, String enemyKey, String name, int count,
        CombatStartMode mode, CombatEnemyStatBlock statBlock) {
    public CombatEnemyProposal(String scenarioId, String enemyKey, String name, int count,
            CombatStartMode mode) {
        this(scenarioId, enemyKey, name, count, mode, null);
    }

    public CombatEnemyProposal(String scenarioId, String enemyKey, String name, int count) {
        this(scenarioId, enemyKey, name, count, CombatStartMode.SCENARIO, null);
    }

    public CombatEnemyProposal(String scenarioId, String name, int count) {
        this(scenarioId, canonicalKey(name), name, count, CombatStartMode.SCENARIO, null);
    }

    public CombatEnemyProposal(String scenarioId, String name, int count, CombatStartMode mode,
            CombatEnemyStatBlock statBlock) {
        this(scenarioId, canonicalKey(name), name, count, mode, statBlock);
    }

    /** Legacy constructor retained for old plan fixtures; runtime grounding rejects it. */
    public CombatEnemyProposal(String name) {
        this("", canonicalKey(name), name, 1, CombatStartMode.SCENARIO, null);
    }

    public CombatEnemyProposal {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("combat enemy name must not be blank");
        scenarioId = scenarioId == null ? "" : scenarioId.trim();
        enemyKey = enemyKey == null ? "" : enemyKey.trim();
        name = name.trim();
        if (count < 1) throw new IllegalArgumentException("combat enemy count must be positive");
        if (count > 100) throw new IllegalArgumentException("combat enemy count is too large");
        mode = mode == null ? CombatStartMode.SCENARIO : mode;
    }

    public static CombatEnemyProposal instant(String enemyKey, String name, int count) {
        return new CombatEnemyProposal("", enemyKey, name, count, CombatStartMode.INSTANT, null);
    }

    public CombatEnemyProposal withStatBlock(CombatEnemyStatBlock resolved) {
        return new CombatEnemyProposal(scenarioId, enemyKey, name, count, mode, resolved);
    }

    private static String canonicalKey(String name) {
        if (name == null || name.isBlank()) return "";
        String[] words = name.trim().toLowerCase(java.util.Locale.ROOT).split("\\s+");
        if (words.length > 0 && words[words.length - 1].endsWith("s") && !words[words.length - 1].endsWith("ss")) {
            words[words.length - 1] = words[words.length - 1].substring(0, words[words.length - 1].length() - 1);
        }
        return String.join("-", words);
    }
}
