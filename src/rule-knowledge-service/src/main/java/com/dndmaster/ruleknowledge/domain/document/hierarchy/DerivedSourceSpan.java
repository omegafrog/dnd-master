package com.dndmaster.ruleknowledge.domain.document.hierarchy;

import java.util.List;

/** Derived bounds; leaf NormalizedSourceSpan values remain untouched. */
public record DerivedSourceSpan(List<String> sourceIds, int firstPage, int lastPage, int firstOrder, int lastOrder) {
    public DerivedSourceSpan { sourceIds = sourceIds == null ? List.of() : List.copyOf(sourceIds); }
}
