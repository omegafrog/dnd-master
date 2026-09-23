package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses a complete monster stat block from already-scoped Rulebook RAG evidence. */
public final class RulebookCombatStatBlockResolver {
    private static final Pattern DEFENSE = Pattern.compile(
            "(?is)(?:\\bArmor\\s+Class|방어도)\\s+(\\d+).*?(?:\\bHit\\s+Points|히트\\s+포인트)\\s+(\\d+)");
    private static final Pattern ATTACK = Pattern.compile(
            "(?i)(?:(?:melee|ranged)\\s+weapon\\s+attack:\\s*|(?:근접|원거리)\\s+무기\\s+공격:\\s*(?:명중\\s*)?)([+-]?\\d+)(?:\\s+to\\s+hit)?");
    private static final Pattern DAMAGE = Pattern.compile(
            "(?i)\\bHit:\\s*\\d+\\s*\\(([0-9]+d[0-9]+(?:\\s*[+-]\\s*[0-9]+)?)\\)");

    private RulebookCombatStatBlockResolver() {}

    public static Optional<CombatEnemyStatBlock> resolve(CombatEnemyProposal proposal,
            List<RuntimeEvidence> evidence) {
        if (proposal == null || evidence == null) return Optional.empty();
        String name = proposal.name().toLowerCase(Locale.ROOT);
        String key = proposal.enemyKey().toLowerCase(Locale.ROOT);
        return evidence.stream()
                .filter(item -> item != null && item.evidenceType() == RuntimeEvidenceType.RULEBOOK)
                .filter(item -> containsMonster(item.excerpt(), name, key))
                .map(RulebookCombatStatBlockResolver::parse)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static boolean containsMonster(String text, String name, String key) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return normalized.contains(name) || normalized.contains(key.replace('-', ' '));
    }

    private static Optional<CombatEnemyStatBlock> parse(RuntimeEvidence evidence) {
        Matcher defense = DEFENSE.matcher(evidence.excerpt());
        Matcher attack = ATTACK.matcher(evidence.excerpt());
        if (!defense.find() || !attack.find()) return Optional.empty();
        int armorClass = Integer.parseInt(defense.group(1));
        int hitPoints = Integer.parseInt(defense.group(2));
        int attackModifier = Integer.parseInt(attack.group(1));
        Matcher damage = DAMAGE.matcher(evidence.excerpt());
        String damageDice = damage.find() ? damage.group(1).replaceAll("\\s+", " ") : "";
        return Optional.of(new CombatEnemyStatBlock(armorClass, hitPoints, attackModifier, damageDice,
                new CombatStatBlockSource(evidence.knowledgeDocumentId().value(), evidence.extractionVersion(), evidence.locator())));
    }
}
