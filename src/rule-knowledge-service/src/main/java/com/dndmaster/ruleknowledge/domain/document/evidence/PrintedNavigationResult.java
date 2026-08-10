package com.dndmaster.ruleknowledge.domain.document.evidence;

import java.util.List;

public record PrintedNavigationResult(List<NavigationEntry> entries, List<String> diagnostics) {
    public PrintedNavigationResult {
        entries = entries == null ? List.of() : List.copyOf(entries);
        diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
    }
}
