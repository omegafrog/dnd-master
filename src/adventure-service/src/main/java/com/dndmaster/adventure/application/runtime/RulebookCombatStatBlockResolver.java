package com.dndmaster.adventure.application.runtime;

import com.dndmaster.adventure.domain.combat.CombatEnemyStatBlock;
import com.dndmaster.adventure.domain.combat.CombatStatBlockSource;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses complete enemy combat numbers from selected Rulebook or Storybook evidence. */
public final class RulebookCombatStatBlockResolver {
    private static final Pattern DEFENSE = Pattern.compile(
            "(?is)(?:\\bArmor\\s+Class|방어도)\\s+(\\d+).*?(?:\\bHit\\s+Points|히트\\s+포인트)\\s+(\\d+)");
    private static final Pattern ATTACK = Pattern.compile(
            "(?i)(?:(?:melee|ranged)\\s+weapon\\s+attack:\\s*|(?:근접|원거리)\\s+무기\\s+공격:\\s*(?:명중\\s*)?)([+-]?\\d+)(?:\\s+to\\s+hit)?");
    private static final Pattern DAMAGE = Pattern.compile(
            "(?i)(?:\\bHit:|명중시:)\\s*\\d+\\s*\\(([0-9]+d[0-9]+(?:\\s*[+-]\\s*[0-9]+)?)\\)");
    private static final Pattern DEXTERITY_MODIFIER = Pattern.compile("(?i)\\bDEX\\s+\\d+\\s*\\(([+-]\\d+)\\)");
    private static final int MAX_MONSTER_HEADING_LINES = 3;

    private RulebookCombatStatBlockResolver() {}

    public static Optional<CombatEnemyStatBlock> resolve(CombatEnemyProposal proposal,
            List<RuntimeEvidence> evidence) {
        if (proposal == null || evidence == null) return Optional.empty();
        String name = proposal.name().toLowerCase(Locale.ROOT);
        String key = proposal.enemyKey().toLowerCase(Locale.ROOT);
        return evidence.stream()
                .filter(item -> item != null && (item.evidenceType() == RuntimeEvidenceType.RULEBOOK
                        || item.evidenceType() == RuntimeEvidenceType.STORYBOOK))
                .sorted(Comparator.comparingInt(item -> item.evidenceType() == RuntimeEvidenceType.RULEBOOK ? 0 : 1))
                .filter(item -> containsMonster(item.excerpt(), name, key))
                .map(item -> parse(item, name, key))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static boolean containsMonster(String text, String name, String key) {
        if (text == null) return false;
        String normalized = text.toLowerCase(Locale.ROOT);
        return containsName(normalized, name) || containsName(normalized, key.replace('-', ' '));
    }

    private static boolean containsName(String text, String candidate) {
        String normalized = candidate.toLowerCase(Locale.ROOT).trim();
        if (normalized.isBlank() || text.contains(normalized)) return !normalized.isBlank();
        int lastSpace = normalized.lastIndexOf(' ');
        String prefix = lastSpace < 0 ? "" : normalized.substring(0, lastSpace + 1);
        String lastWord = normalized.substring(lastSpace + 1);
        String singular = singularizeEnglish(lastWord);
        if (!singular.equals(lastWord) && text.contains(prefix + singular)) return true;
        String plural = pluralizeEnglish(singular);
        return !plural.equals(singular) && text.contains(prefix + plural);
    }

    private static String singularizeEnglish(String word) {
        if (word.endsWith("ies") && word.length() > 3) return word.substring(0, word.length() - 3) + "y";
        if (word.endsWith("s") && !word.endsWith("ss") && word.length() > 1) return word.substring(0, word.length() - 1);
        return word;
    }

    private static String pluralizeEnglish(String word) {
        if (word.endsWith("y") && word.length() > 1 && !isEnglishVowel(word.charAt(word.length() - 2))) {
            return word.substring(0, word.length() - 1) + "ies";
        }
        if (word.endsWith("s")) return word;
        return word + "s";
    }

    private static boolean isEnglishVowel(char character) {
        return "aeiou".indexOf(character) >= 0;
    }

    private static Optional<CombatEnemyStatBlock> parse(RuntimeEvidence evidence, String name, String key) {
        String rawExcerpt = evidence.excerpt();
        String excerpt = rawExcerpt.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        Matcher defense = DEFENSE.matcher(excerpt);
        Matcher attack = ATTACK.matcher(excerpt);
        if (!defense.find() || !hasMatchingMonsterHeading(rawExcerpt, name, key) || !attack.find()) {
            return Optional.empty();
        }
        int armorClass = Integer.parseInt(defense.group(1));
        int hitPoints = Integer.parseInt(defense.group(2));
        int attackModifier = Integer.parseInt(attack.group(1));
        Matcher damage = DAMAGE.matcher(excerpt);
        String damageDice = damage.find() ? damage.group(1).replaceAll("\\s+", " ") : "";
        Matcher dexterity = DEXTERITY_MODIFIER.matcher(excerpt);
        int initiativeModifier = dexterity.find() ? Integer.parseInt(dexterity.group(1)) : 0;
        return Optional.of(new CombatEnemyStatBlock(armorClass, hitPoints, attackModifier, damageDice,
                new CombatStatBlockSource(evidence.knowledgeDocumentId().value(), evidence.extractionVersion(), evidence.locator(), evidence.referenceKey()),
                initiativeModifier));
    }

    private static boolean hasMatchingMonsterHeading(String excerpt, String name, String key) {
        int checkedLines = 0;
        for (String line : excerpt.split("\\R")) {
            String normalizedLine = line.toLowerCase(Locale.ROOT).trim();
            if (normalizedLine.isEmpty()) continue;
            if (containsName(normalizedLine, name) || containsName(normalizedLine, key.replace('-', ' '))) return true;
            if (++checkedLines >= MAX_MONSTER_HEADING_LINES) break;
        }
        return false;
    }
}
