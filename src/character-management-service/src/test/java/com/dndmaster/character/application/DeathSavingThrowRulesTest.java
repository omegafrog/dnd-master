package com.dndmaster.character.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DeathSavingThrowRulesTest {
    @Test
    void appliesNaturalOneTwentyAndThreeSuccessOrFailureRules() {
        assertEquals(new DeathSavingThrowRules.Result(0, 2, false, false, false),
                DeathSavingThrowRules.resolve(1, 0, 0));
        assertEquals(new DeathSavingThrowRules.Result(0, 0, false, false, true),
                DeathSavingThrowRules.resolve(20, 1, 1));
        assertEquals(new DeathSavingThrowRules.Result(0, 0, true, false, false),
                DeathSavingThrowRules.resolve(10, 2, 0));
        assertEquals(new DeathSavingThrowRules.Result(0, 3, false, true, false),
                DeathSavingThrowRules.resolve(9, 0, 2));
        assertTrue(DeathSavingThrowRules.resolve(10, 0, 0).successes() == 1);
    }
}
