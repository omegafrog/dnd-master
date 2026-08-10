package com.dndmaster.ruleknowledge.domain.document.evidence;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NumberingAnalyzer {
    private static final Pattern PREFIX = Pattern.compile("^(([0-9]+(?:\\.[0-9]+)*)|([IVXLC]+(?:\\.[IVXLC]+)*)|([A-Z](?:\\.[0-9]+)*))\\s+", Pattern.CASE_INSENSITIVE);

    public List<StructuralEvidence> extract(List<NormalizedElement> elements) {
        List<NormalizedElement> numbered = elements.stream().filter(e -> PREFIX.matcher(e.text().trim()).find()).toList();
        if (numbered.isEmpty()) return List.of();
        List<StructuralEvidence> result = new ArrayList<>();
        double confidence = numbered.size() > 1 ? 0.85 : 0.55;
        for (NormalizedElement element : numbered) {
            Matcher matcher = PREFIX.matcher(element.text().trim());
            matcher.find();
            String token = matcher.group(1);
            int depth = token.split("\\.").length;
            result.add(new StructuralEvidence(EvidenceKind.NUMBERING, element.id(), token + ":depth=" + depth,
                    confidence, List.of(element.id())));
        }
        return List.copyOf(result);
    }
}
