package com.dndmaster.ruleknowledge.domain.document.evidence;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds navigation by repeated title/locator shape, not by heading text or page position. */
public final class PrintedNavigationExtractor {
    private static final Pattern LINE = Pattern.compile("^(.+?)(?:\\.{2,}\\s*|\\s{2,}|\\t+)([0-9]+(?:[-–][0-9]+)?|[ivxlcdm]+)$", Pattern.CASE_INSENSITIVE);

    public List<NavigationEntry> extract(NormalizedDocument document) {
        return extractWithDiagnostics(document).entries();
    }

    public PrintedNavigationResult extractWithDiagnostics(NormalizedDocument document) {
        List<NavigationEntry> entries = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        document.tables().stream().sorted(Comparator.comparingInt(t -> t.page())).forEach(table ->
                table.rows().forEach((row) -> addTableRow(entries, row, table.id())));
        document.elements().stream().sorted(Comparator.comparingInt(NormalizedElement::page)
                .thenComparingInt(NormalizedElement::order)).forEach(element -> addLine(entries, diagnostics, element.text(), element.id()));
        List<NavigationEntry> deduplicated = deduplicate(entries);
        if (deduplicated.size() == 1 && document.tables().isEmpty()) {
            diagnostics.add("single printed-navigation-shaped line rejected without corroboration");
            String onlySourceId = deduplicated.get(0).sourceId();
            deduplicated = deduplicated.stream().filter(e -> !e.sourceId().equals(onlySourceId)).toList();
        }
        return new PrintedNavigationResult(deduplicated, diagnostics);
    }

    private void addTableRow(List<NavigationEntry> entries, List<String> row, String sourceId) {
        if (row == null || row.size() < 2) return;
        String locator = row.get(row.size() - 1) == null ? "" : row.get(row.size() - 1).trim();
        if (!isLocator(locator)) return;
        String title = row.subList(0, row.size() - 1).stream().filter(v -> v != null && !v.isBlank())
                .reduce((a, b) -> a + " " + b).orElse("").trim();
        if (!title.isBlank()) entries.add(entry(title, locator, sourceId, String.join(" | ", row), 0.9, entries.size()));
    }

    private void addLine(List<NavigationEntry> entries, List<String> diagnostics, String raw, String sourceId) {
        if (raw == null) return;
        Matcher matcher = LINE.matcher(raw.trim());
        if (matcher.matches()) addEntry(entries, matcher.group(1), matcher.group(2), sourceId, raw.trim(), entries.size());
        else if (raw.contains(".") && raw.trim().matches(".*\\d+$")) {
            diagnostics.add("malformed navigation candidate " + sourceId + ": " + raw.trim());
            entries.add(new NavigationEntry(sourceId + "#malformed-navigation-" + entries.size(), raw.trim(), "", null,
                    sourceId, raw.trim(), 0.1));
        }
    }

    private void addEntry(List<NavigationEntry> entries, String title, String locator, String sourceId, String raw, int index) {
        if (!title.isBlank() && isLocator(locator)) entries.add(entry(title, locator, sourceId, raw, 0.8, index));
    }

    private NavigationEntry entry(String title, String locator, String sourceId, String raw, double confidence, int index) {
        return new NavigationEntry(sourceId + "#navigation-" + index, title, locator, null, sourceId, raw, confidence);
    }

    private List<NavigationEntry> deduplicate(List<NavigationEntry> entries) {
        List<NavigationEntry> result = new ArrayList<>();
        entries.forEach(candidate -> {
            boolean duplicate = result.stream().anyMatch(existing -> existing.sourceId().equals(candidate.sourceId())
                    && existing.title().equals(candidate.title()) && existing.locator().equals(candidate.locator()));
            if (!duplicate) result.add(candidate);
        });
        return List.copyOf(result);
    }

    private static boolean isLocator(String value) {
        return value != null && value.matches("(?i)([0-9]+(?:[-–][0-9]+)?|[ivxlcdm]+)");
    }
}
