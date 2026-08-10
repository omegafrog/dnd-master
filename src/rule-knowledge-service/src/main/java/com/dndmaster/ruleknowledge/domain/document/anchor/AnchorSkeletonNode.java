package com.dndmaster.ruleknowledge.domain.document.anchor;

public record AnchorSkeletonNode(String bodyElementId, String parentBodyElementId, double confidence) {
    public AnchorSkeletonNode {
        if (bodyElementId == null || bodyElementId.isBlank()) throw new IllegalArgumentException("bodyElementId must not be blank");
        parentBodyElementId = parentBodyElementId == null ? "" : parentBodyElementId;
        if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence must be finite and between 0 and 1");
    }
}
