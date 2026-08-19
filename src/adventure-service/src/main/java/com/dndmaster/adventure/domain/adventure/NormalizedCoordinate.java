package com.dndmaster.adventure.domain.adventure;

/** A source-image-relative location, independent of any runtime grid resolution. */
public record NormalizedCoordinate(double x, double y) {
    public NormalizedCoordinate {
        if (!Double.isFinite(x) || !Double.isFinite(y) || x < 0 || x > 1 || y < 0 || y > 1) {
            throw new IllegalArgumentException("normalized coordinates must be between 0 and 1");
        }
    }
}
