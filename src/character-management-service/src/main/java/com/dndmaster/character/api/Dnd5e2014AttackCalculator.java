package com.dndmaster.character.api;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Server-side D&D 5e 2014 attack derivation for equipped weapons and unarmed strikes. */
final class Dnd5e2014AttackCalculator {
    private static final Map<String, Weapon> WEAPONS = weapons();

    private Dnd5e2014AttackCalculator() {}

    static List<AttackView> calculate(
            String characterClass,
            JsonNode build,
            JsonNode state,
            Map<String, Integer> modifiers,
            int proficiencyBonus) {
        Set<String> owned = textSet(build.path("ownedWeaponIds"));
        JsonNode equipped = state.path("equippedItems");
        List<String> equippedIds = new ArrayList<>();
        addEquipped(equippedIds, equipped, "mainHandWeaponId", owned);
        addEquipped(equippedIds, equipped, "offHandWeaponId", owned);
        addEquipped(equippedIds, equipped, "twoHandedWeaponId", owned);

        List<AttackView> attacks = new ArrayList<>();
        for (String id : equippedIds) {
            Weapon weapon = WEAPONS.get(id);
            if (weapon == null) continue;
            int meleeModifier = weapon.finesse()
                    ? Math.max(modifiers.getOrDefault("strength", 0), modifiers.getOrDefault("dexterity", 0))
                    : modifiers.getOrDefault("strength", 0);
            int attackModifier = weapon.ranged() ? modifiers.getOrDefault("dexterity", 0) : meleeModifier;
            attacks.add(new AttackView(
                    weapon.id(), weapon.label(), attackModifier + proficiencyBonus,
                    damage(weapon.damage(), attackModifier), weapon.damageType(),
                    weapon.range(), weapon.ranged() ? "RANGED" : "MELEE", weapon.ammunition(),
                    weapon.versatileDamage() == null ? null : damage(weapon.versatileDamage(), meleeModifier)));
            if (weapon.thrownRange() != null && !weapon.ranged()) {
                attacks.add(new AttackView(
                        weapon.id() + "-thrown", weapon.label() + " 투척", meleeModifier + proficiencyBonus,
                        damage(weapon.damage(), meleeModifier), weapon.damageType(), weapon.thrownRange(),
                        "THROWN", false, null));
            }
        }

        int unarmedModifier = "몽크".equals(characterClass)
                ? Math.max(modifiers.getOrDefault("strength", 0), modifiers.getOrDefault("dexterity", 0))
                : modifiers.getOrDefault("strength", 0);
        attacks.add(new AttackView(
                "unarmed", "몽크".equals(characterClass) ? "무술 비무장 공격" : "비무장 공격",
                unarmedModifier + proficiencyBonus,
                damage("몽크".equals(characterClass) ? "1d4" : "1", unarmedModifier),
                "타격", null, "UNARMED", false, null));
        return List.copyOf(attacks);
    }

    private static void addEquipped(List<String> target, JsonNode equipped, String field, Set<String> owned) {
        JsonNode value = equipped.get(field);
        if (value == null || value.isNull() || !value.isTextual()) return;
        String id = value.asText();
        if (!id.isBlank() && owned.contains(id) && !target.contains(id)) target.add(id);
    }

    private static Set<String> textSet(JsonNode node) {
        Set<String> values = new java.util.LinkedHashSet<>();
        if (node.isArray()) node.forEach(value -> {
            if (value.isTextual() && !value.asText().isBlank()) values.add(value.asText());
        });
        return values;
    }

    private static String damage(String dice, int modifier) {
        if (modifier == 0) return dice;
        return dice + (modifier > 0 ? "+" : "") + modifier;
    }

    private static Map<String, Weapon> weapons() {
        Map<String, Weapon> values = new LinkedHashMap<>();
        add(values, new Weapon("club", "곤봉", "1d4", "타격", false, false, null, null, false, null));
        add(values, new Weapon("dagger", "대거", "1d4", "관통", false, true, null, "20/60ft", false, null));
        add(values, new Weapon("greatclub", "그레이트클럽", "1d8", "타격", false, false, null, null, false, null));
        add(values, new Weapon("handaxe", "핸드액스", "1d6", "참격", false, false, null, "20/60ft", false, null));
        add(values, new Weapon("javelin", "재블린", "1d6", "관통", false, false, null, "30/120ft", false, null));
        add(values, new Weapon("light-hammer", "라이트 해머", "1d4", "타격", false, false, null, "20/60ft", false, null));
        add(values, new Weapon("mace", "메이스", "1d6", "타격", false, false, null, null, false, null));
        add(values, new Weapon("quarterstaff", "쿼터스태프", "1d6", "타격", false, false, null, null, false, "1d8"));
        add(values, new Weapon("sickle", "낫", "1d4", "참격", false, false, null, null, false, null));
        add(values, new Weapon("spear", "창", "1d6", "관통", false, false, null, "20/60ft", false, "1d8"));
        add(values, new Weapon("light-crossbow", "라이트 크로스보우", "1d8", "관통", true, false, "80/320ft", null, true, null));
        add(values, new Weapon("dart", "다트", "1d4", "관통", true, true, "20/60ft", null, false, null));
        add(values, new Weapon("shortbow", "단궁", "1d6", "관통", true, false, "80/320ft", null, true, null));
        add(values, new Weapon("sling", "슬링", "1d4", "타격", true, false, "30/120ft", null, true, null));
        add(values, new Weapon("battleaxe", "배틀액스", "1d8", "참격", false, false, null, null, false, "1d10"));
        add(values, new Weapon("greataxe", "그레이트액스", "1d12", "참격", false, false, null, null, false, null));
        add(values, new Weapon("greatsword", "그레이트소드", "2d6", "참격", false, false, null, null, false, null));
        add(values, new Weapon("longsword", "롱소드", "1d8", "참격", false, false, null, null, false, "1d10"));
        add(values, new Weapon("rapier", "레이피어", "1d8", "관통", false, true, null, null, false, null));
        add(values, new Weapon("scimitar", "시미터", "1d6", "참격", false, true, null, null, false, null));
        add(values, new Weapon("shortsword", "숏소드", "1d6", "관통", false, true, null, null, false, null));
        add(values, new Weapon("warhammer", "워해머", "1d8", "타격", false, false, null, null, false, "1d10"));
        add(values, new Weapon("longbow", "장궁", "1d8", "관통", true, false, "150/600ft", null, true, null));
        add(values, new Weapon("hand-crossbow", "핸드 크로스보우", "1d6", "관통", true, false, "30/120ft", null, true, null));
        return Map.copyOf(values);
    }

    private static void add(Map<String, Weapon> target, Weapon weapon) { target.put(weapon.id(), weapon); }

    record AttackView(
            String weaponId,
            String label,
            int attackBonus,
            String damage,
            String damageType,
            String range,
            String mode,
            boolean ammunitionRequired,
            String versatileDamage) {}

    private record Weapon(
            String id,
            String label,
            String damage,
            String damageType,
            boolean ranged,
            boolean finesse,
            String range,
            String thrownRange,
            boolean ammunition,
            String versatileDamage) {}
}
