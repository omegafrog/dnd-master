package com.dndmaster.adventure.application.runtime;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Contract adapter used by tests and small deployments: metadata gate, semantic score, rerank, Top-K. */
public final class InMemoryExemplarCatalogIndexAdapter implements ExemplarCatalogIndexPort, ExemplarRetrieverPort {
    private final List<StyleExemplar> catalog;
    public InMemoryExemplarCatalogIndexAdapter(List<StyleExemplar> catalog) {
        this.catalog = List.copyOf(Objects.requireNonNull(catalog));
    }

    public static InMemoryExemplarCatalogIndexAdapter fromCandidates(List<ExemplarAdmissionCandidate> candidates) {
        Objects.requireNonNull(candidates);
        ExemplarAdmissionPolicy policy = new ExemplarAdmissionPolicy();
        return new InMemoryExemplarCatalogIndexAdapter(candidates.stream()
                .filter(policy::admit).map(ExemplarAdmissionCandidate::exemplar).toList());
    }
    @Override public List<ExemplarResult> retrieve(ExemplarQuery query) {
        Objects.requireNonNull(query);
        List<ExemplarResult> ranked = new ArrayList<>();
        for (StyleExemplar exemplar : catalog) {
            if (!exemplar.generic() || !exemplar.scenePurpose().equalsIgnoreCase(query.scenePurpose())
                    || !exemplar.interactionType().equalsIgnoreCase(query.interactionType())) continue;
            double metadata = metadataScore(exemplar, query);
            double semantic = overlap(exemplar.text() + " " + exemplar.scenePurpose() + " " + exemplar.interactionType(), query.semanticQuery());
            ranked.add(new ExemplarResult(exemplar, metadata, semantic, metadata * .6 + semantic * .4, 1));
        }
        ranked.sort(Comparator.comparingDouble(ExemplarResult::rerankScore).reversed()
                .thenComparing(result -> result.exemplar().id()));
        return java.util.stream.IntStream.range(0, Math.min(query.limit(), ranked.size()))
                .mapToObj(i -> { ExemplarResult r = ranked.get(i); return new ExemplarResult(r.exemplar(), r.metadataScore(), r.semanticScore(), r.rerankScore(), i + 1); })
                .toList();
    }
    private static double metadataScore(StyleExemplar e, ExemplarQuery q) {
        int matches = 0;
        if (e.tone().equalsIgnoreCase(q.tone())) matches++;
        if (e.pacing().equalsIgnoreCase(q.pacing())) matches++;
        if (e.desiredLength().equalsIgnoreCase(q.desiredLength())) matches++;
        return matches / 3.0;
    }
    private static double overlap(String text, String query) {
        java.util.Set<String> words = new java.util.HashSet<>(List.of(text.toLowerCase(Locale.ROOT).split("\\W+")));
        String[] requested = query.toLowerCase(Locale.ROOT).split("\\W+");
        if (requested.length == 0) return 0;
        return (double) java.util.Arrays.stream(requested).filter(words::contains).distinct().count() / requested.length;
    }
}
