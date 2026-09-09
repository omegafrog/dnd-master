package com.dndmaster.combatmap.application.view;

import java.util.List;
import java.util.Objects;

/** 이미지 분석 결과를 검수 화면에 보여주기 위한 후보 설명이다. */
public record MapBoundaryCandidate(int x, int y, String orientation, String kind,
                                   double confidence, List<String> evidence, String source) {
    public MapBoundaryCandidate {
        if (x < 0 || y < 0 || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("invalid map boundary candidate");
        }
        if (!"HORIZONTAL".equals(orientation) && !"VERTICAL".equals(orientation)) {
            throw new IllegalArgumentException("invalid map boundary candidate orientation");
        }
        if (!"WALL".equals(kind) && !"DOOR".equals(kind)) {
            throw new IllegalArgumentException("invalid map boundary candidate kind");
        }
        evidence = List.copyOf(Objects.requireNonNull(evidence, "candidate evidence must not be null"));
        source = source == null || source.isBlank() ? "IMAGE_RULES" : source.trim();
    }
}
