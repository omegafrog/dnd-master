package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.runtime.TypedCheckRule;
import org.junit.jupiter.api.Test;

class TypedCheckRuleTest {
    @Test
    void normalizes_an_expression_modifier_once() {
        TypedCheckRule rule = new TypedCheckRule("dnd5e.perception", "1d20+2", 0, 15);

        assertEquals("1d20", rule.diceExpression());
        assertEquals(2, rule.modifier());
        assertEquals(new TypedCheckRule.DiceExpression(1, 20, 2), rule.dice());
    }

    @Test
    void combines_an_external_modifier_once_with_a_plain_expression() {
        TypedCheckRule rule = new TypedCheckRule("dnd5e.perception", "1d20", 3, 15);

        assertEquals(3, rule.modifier());
        assertEquals(new TypedCheckRule.DiceExpression(1, 20, 3), rule.dice());
    }

    @Test
    void combines_expression_and_external_modifiers_without_double_application() {
        TypedCheckRule rule = new TypedCheckRule("dnd5e.perception", "1d20+2", 3, 15);

        assertEquals(5, rule.modifier());
        assertEquals(new TypedCheckRule.DiceExpression(1, 20, 5), rule.dice());
    }
}
