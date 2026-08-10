package com.dndmaster.ruleknowledge.domain.document.anchor;

import com.dndmaster.ruleknowledge.domain.document.evidence.NavigationEntry;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnchorBuilder {
    private static final Pattern NUMBERING = Pattern.compile("^([0-9]+(?:\\.[0-9]+)*)\\s+");

    public StructuralAnchor fromNavigation(NavigationEntry entry) {
        Matcher matcher = NUMBERING.matcher(entry.title());
        String numbering = matcher.find() ? matcher.group(1) : "";
        String title = numbering.isEmpty() ? entry.title() : entry.title().substring(matcher.end()).trim();
        return new StructuralAnchor("anchor:" + entry.id(), title, entry.locator(), numbering,
                entry.levelSuggestion(), List.of(entry.id(), entry.sourceId()), entry.confidence());
    }
}
