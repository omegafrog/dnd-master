package com.dndmaster.ruleknowledge.domain.document.normalized;

import java.util.HashSet;
import java.util.Set;

public final class NormalizedDocumentPreservationValidator {
    private NormalizedDocumentPreservationValidator() {}

    public static void validate(NormalizedDocument document) {
        Set<String> ids = new HashSet<>();
        document.elements().forEach(element -> add(ids, element.id()));
        document.tables().forEach(table -> add(ids, table.id()));
        document.pictures().forEach(picture -> add(ids, picture.id()));
        document.outlines().forEach(outline -> add(ids, outline.id()));
        document.elements().forEach(element -> {
            if (element.sourceSpan() == null) throw new IllegalArgumentException("element source span missing: " + element.id());
        });
    }

    private static void add(Set<String> ids, String id) {
        if (!ids.add(id)) throw new IllegalArgumentException("duplicate normalized source id: " + id);
    }
}
