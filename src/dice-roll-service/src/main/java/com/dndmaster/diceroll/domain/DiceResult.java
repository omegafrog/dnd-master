package com.dndmaster.diceroll.domain;

import java.util.List;
import java.util.Objects;

public record DiceResult(List<Integer> faces, int total) {
    public DiceResult {
        faces = List.copyOf(Objects.requireNonNull(faces, "faces must not be null"));
        if (faces.isEmpty() || faces.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("faces must not be empty or contain null");
        }
    }

    public static DiceResult forExpression(DiceExpression expression, List<Integer> faces) {
        Objects.requireNonNull(expression, "expression must not be null");
        List<Integer> copy = List.copyOf(Objects.requireNonNull(faces, "faces must not be null"));
        if (copy.size() != expression.count()) throw new IllegalArgumentException("face count must match dice count");
        if (copy.stream().anyMatch(face -> face == null || face < 1 || face > expression.sides())) {
            throw new IllegalArgumentException("face must be within die sides");
        }
        int total = Math.addExact(copy.stream().mapToInt(Integer::intValue).sum(), expression.modifier());
        return new DiceResult(copy, total);
    }

    public void validateAgainst(DiceExpression expression) {
        DiceResult validated = forExpression(expression, faces);
        if (validated.total != total) throw new IllegalArgumentException("total must equal faces plus modifier");
    }
}
