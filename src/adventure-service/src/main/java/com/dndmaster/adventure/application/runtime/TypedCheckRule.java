package com.dndmaster.adventure.application.runtime;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Typed rule data carried across the Adventure rules and dice boundary. */
public record TypedCheckRule(String ruleReference, String diceExpression, int modifier, Integer difficulty) {
    private static final Pattern DICE = Pattern.compile("(?i)^(\\d+)?d(\\d+)([+-]\\d+)?$");

    public TypedCheckRule {
        ruleReference = required(ruleReference, "check rule reference");
        diceExpression = required(diceExpression, "dice expression");
        DiceExpression parsed = DiceExpression.parse(diceExpression, modifier);
        diceExpression = parsed.baseExpression();
        modifier = parsed.modifier();
        if (difficulty == null || difficulty < 0) {
            throw new IllegalArgumentException("check difficulty must be present and non-negative");
        }
    }

    public DiceExpression dice() {
        return DiceExpression.parse(diceExpression, modifier);
    }

    public boolean accepts(int total) {
        return total >= difficulty;
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.trim();
    }

    public record DiceExpression(int count, int sides, int modifier) {
        public DiceExpression {
            if (count < 1 || sides < 2) throw new IllegalArgumentException("invalid dice expression");
        }

        public String baseExpression() { return count + "d" + sides; }

        public static DiceExpression parse(String expression, int modifier) {
            Matcher matcher = DICE.matcher(expression.replace(" ", ""));
            if (!matcher.matches()) throw new IllegalArgumentException("invalid dice expression");
            int count = matcher.group(1) == null ? 1 : Integer.parseInt(matcher.group(1));
            int sides = Integer.parseInt(matcher.group(2));
            int inlineModifier = matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3));
            return new DiceExpression(count, sides, Math.addExact(inlineModifier, modifier));
        }
    }
}
