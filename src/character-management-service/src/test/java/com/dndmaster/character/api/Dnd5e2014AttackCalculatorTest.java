package com.dndmaster.character.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Dnd5e2014AttackCalculatorTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void usesDexterityForFinesseAndRangedWeapons() throws Exception {
        var attacks = Dnd5e2014AttackCalculator.calculate(
                "로그",
                JSON.readTree("{\"ownedWeaponIds\":[\"rapier\",\"shortbow\"]}"),
                JSON.readTree("{\"equippedItems\":{\"mainHandWeaponId\":\"rapier\",\"offHandWeaponId\":null,\"twoHandedWeaponId\":\"shortbow\"}}"),
                Map.of("strength", 0, "dexterity", 3),
                2);

        var rapier = attacks.stream().filter(attack -> attack.weaponId().equals("rapier")).findFirst().orElseThrow();
        var shortbow = attacks.stream().filter(attack -> attack.weaponId().equals("shortbow")).findFirst().orElseThrow();
        assertEquals(5, rapier.attackBonus());
        assertEquals("1d8+3", rapier.damage());
        assertEquals(5, shortbow.attackBonus());
        assertTrue(shortbow.ammunitionRequired());
    }

    @Test
    void addsThrownAndVersatileViews() throws Exception {
        var attacks = Dnd5e2014AttackCalculator.calculate(
                "파이터",
                JSON.readTree("{\"ownedWeaponIds\":[\"spear\"]}"),
                JSON.readTree("{\"equippedItems\":{\"mainHandWeaponId\":\"spear\",\"offHandWeaponId\":null,\"twoHandedWeaponId\":null}}"),
                Map.of("strength", 2, "dexterity", 1),
                2);

        var melee = attacks.stream().filter(attack -> attack.weaponId().equals("spear")).findFirst().orElseThrow();
        var thrown = attacks.stream().filter(attack -> attack.weaponId().equals("spear-thrown")).findFirst().orElseThrow();
        assertEquals("1d6+2", melee.damage());
        assertEquals("1d8+2", melee.versatileDamage());
        assertEquals("THROWN", thrown.mode());
        assertEquals("20/60ft", thrown.range());
    }

    @Test
    void derivesMonkMartialArtsUnarmedAttack() throws Exception {
        var attacks = Dnd5e2014AttackCalculator.calculate(
                "몽크",
                JSON.readTree("{\"ownedWeaponIds\":[]}"),
                JSON.readTree("{\"equippedItems\":{}}"),
                Map.of("strength", 0, "dexterity", 3),
                2);

        var unarmed = attacks.stream().filter(attack -> attack.weaponId().equals("unarmed")).findFirst().orElseThrow();
        assertEquals("무술 비무장 공격", unarmed.label());
        assertEquals(5, unarmed.attackBonus());
        assertEquals("1d4+3", unarmed.damage());
    }
}
