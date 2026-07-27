package com.dndmaster.ruleknowledge.domain.rulebook;

public record BoundingBox(double left, double top, double right, double bottom) {
    public BoundingBox {
        if (left < 0 || top < 0 || right < 0 || bottom < 0) {
            throw new IllegalArgumentException("bounding box values must not be negative");
        }
        if (right < left) {
            throw new IllegalArgumentException("right must not be before left");
        }
        if (bottom < top) {
            throw new IllegalArgumentException("bottom must not be before top");
        }
    }
}
