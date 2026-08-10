package com.dndmaster.ruleknowledge.domain.document.evidence;

import com.dndmaster.ruleknowledge.domain.document.normalized.NormalizedElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class HeadingFeatureExtractor {
    public List<StructuralEvidence> extract(List<NormalizedElement> elements) {
        Map<String, Long> styles = elements.stream().map(NormalizedElement::style).filter(s -> !s.isBlank())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        List<StructuralEvidence> result = new ArrayList<>();
        for (NormalizedElement element : elements) {
            boolean heading = "HEADING".equalsIgnoreCase(element.type()) || !element.style().isBlank()
                    && styles.getOrDefault(element.style(), 0L) <= 2
                    && element.text().length() <= 120
                    && !element.text().matches(".*[.!?].*");
            if (heading) {
                double confidence = "HEADING".equalsIgnoreCase(element.type()) ? 0.8 : 0.55;
                result.add(new StructuralEvidence(EvidenceKind.TYPOGRAPHY, element.id(),
                        "style=" + element.style() + ";type=" + element.type(), confidence, List.of(element.id())));
            }
            if (!element.layout().isBlank()) result.add(new StructuralEvidence(EvidenceKind.LAYOUT, element.id(), element.layout(), 0.5, List.of(element.id())));
            if (element.layout().toLowerCase().contains("indent")) result.add(new StructuralEvidence(EvidenceKind.INDENTATION,
                    element.id(), element.layout(), 0.5, List.of(element.id())));
            result.add(new StructuralEvidence(EvidenceKind.SOURCE_ORDER, element.id(), "page=" + element.page() + ";order=" + element.order(), 1.0, List.of(element.id())));
        }
        return List.copyOf(result);
    }
}
