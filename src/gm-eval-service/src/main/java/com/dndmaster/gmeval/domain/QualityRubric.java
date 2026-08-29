package com.dndmaster.gmeval.domain;
import java.util.*;
public record QualityRubric(String dimension, Map<Integer,String> anchors) {
    public QualityRubric { if (dimension == null || dimension.isBlank() || anchors == null || anchors.size() != 5 ||
            anchors.entrySet().stream().anyMatch(e -> e.getKey() < 1 || e.getKey() > 5 || e.getValue() == null || e.getValue().isBlank()))
        throw new IllegalArgumentException("rubric requires five non-empty anchors"); anchors = Map.copyOf(anchors); }
}
