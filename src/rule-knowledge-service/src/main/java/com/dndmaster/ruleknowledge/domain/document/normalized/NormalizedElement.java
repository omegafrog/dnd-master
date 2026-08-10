package com.dndmaster.ruleknowledge.domain.document.normalized;

import java.util.List;

public record NormalizedElement(String id, String type, String text, int page, int order,
                                String parentId, Integer parserLevel, List<String> childIds,
                                NormalizedSourceSpan sourceSpan, String style, String layout) {
    public NormalizedElement {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        if (order < 0) throw new IllegalArgumentException("order must not be negative");
        type = type == null || type.isBlank() ? "UNKNOWN" : type;
        text = text == null ? "" : text;
        childIds = childIds == null ? List.of() : List.copyOf(childIds);
        style = style == null ? "" : style;
        layout = layout == null ? "" : layout;
    }
}
