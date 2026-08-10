package com.dndmaster.ruleknowledge.domain.extraction;

import com.dndmaster.ruleknowledge.domain.rulebook.BoundingBox;
import java.util.List;

public record DocumentNode(
        String id,
        DocumentNodeType type,
        int page,
        BoundingBox boundingBox,
        String text,
        List<DocumentNode> children,
        List<ContentRole> roles) {
    public DocumentNode {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (page < 1) throw new IllegalArgumentException("page must be positive");
        type = type == null ? DocumentNodeType.UNKNOWN : type;
        text = text == null ? "" : text;
        children = children == null ? List.of() : List.copyOf(children);
        roles = roles == null ? List.of() : List.copyOf(roles);
    }

    public static DocumentNode heading(String id, int page, String text) {
        return new DocumentNode(id, DocumentNodeType.HEADING, page, null, text, List.of(), List.of());
    }
}
