package com.dndmaster.ruleknowledge.domain.document.evidence;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedDocument;
import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        boolean dnd5e = isDnd5e(document);
        Set<Integer> tocPages = dnd5e ? dnd5eContentsPages(document) : Set.of();
        document.tables().stream().filter(table -> !dnd5e || tocPages.contains(table.page()))
                .sorted(Comparator.comparingInt(t -> t.page())).forEach(table ->
                table.rows().forEach((row) -> addTableRow(entries, row, table.id())));
        document.elements().stream().sorted(Comparator.comparingInt(NormalizedElement::page)
                .thenComparingInt(NormalizedElement::order)).forEach(element -> {
                    if (!dnd5e || tocPages.contains(element.page())) addLine(entries, diagnostics, element.text(), element.id());
                    if (dnd5e && tocPages.contains(element.page())) addDnd5eTocDivider(entries, element);
                });
        List<NavigationEntry> deduplicated = applyDnd5eLevels(deduplicate(entries), dnd5e);
        if (deduplicated.size() == 1 && document.tables().isEmpty()) {
            diagnostics.add("single printed-navigation-shaped line rejected without corroboration");
            String onlySourceId = deduplicated.get(0).sourceId();
            deduplicated = deduplicated.stream().filter(e -> !e.sourceId().equals(onlySourceId)).toList();
        }
        return new PrintedNavigationResult(deduplicated, diagnostics);
    }

    private void addDnd5eTocDivider(List<NavigationEntry> entries, NormalizedElement element) {
        String title = element.text() == null ? "" : element.text().trim();
        if (title.equals("Introduction") || title.equals("Appendices") || title.matches("Part\\s+\\d+:.+")) {
            entries.add(new NavigationEntry(element.id() + "#toc-divider", title, "", null,
                    element.id(), title, 0.95));
        }
    }

    private List<NavigationEntry> applyDnd5eLevels(List<NavigationEntry> entries, boolean dnd5e) {
        if (!dnd5e) return entries;
        return entries.stream().map(entry -> new NavigationEntry(entry.id(), entry.title(), entry.locator(),
                dnd5eLevel(entry.title()), entry.sourceId(), entry.rawText(), entry.confidence())).toList();
    }

    private Integer dnd5eLevel(String title) {
        String value = title == null ? "" : title.trim();
        if (value.equals("Introduction") || value.equals("Appendices") || value.equals("Character Sheet")
                || value.replace(".", "").equals("What Comes Next?") || value.matches("Part\\s+\\d+:.+")) return 1;
        if (value.matches("Ch\\.?\\s*\\d+:.*") || value.matches("Appendix\\s+[A-Z]:.*")) return 2;
        return 3;
    }

    private boolean isDnd5e(NormalizedDocument document) {
        return document.rawText().contains("D&D Basic Rules")
                || document.elements().stream().anyMatch(element -> element.text().contains("D&D Basic Rules"));
    }

    private Set<Integer> dnd5eContentsPages(NormalizedDocument document) {
        Map<Integer, Integer> navigationLines = new HashMap<>();
        Set<Integer> pages = new HashSet<>();
        for (NormalizedElement element : document.elements()) {
            String text = element.text() == null ? "" : element.text().trim();
            if (text.equalsIgnoreCase("Contents")) pages.add(element.page());
            if (LINE.matcher(text).matches()) navigationLines.merge(element.page(), 1, Integer::sum);
        }
        navigationLines.forEach((page, count) -> { if (count >= 2) pages.add(page); });
        return Set.copyOf(pages);
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
