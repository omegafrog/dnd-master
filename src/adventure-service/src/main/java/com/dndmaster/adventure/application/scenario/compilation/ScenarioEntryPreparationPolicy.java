package com.dndmaster.adventure.application.scenario.compilation;

import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection;
import com.dndmaster.adventure.domain.scenario.ScenarioEntryResult;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceReference;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Converts published source excerpts into a safe, source-backed session entry. */
public final class ScenarioEntryPreparationPolicy {
    public ScenarioEntryResult prepare(ScenarioSourceBundle bundle, List<ResolutionExtractionPort.SourceExcerpt> excerpts) {
        Objects.requireNonNull(bundle, "bundle must not be null");
        List<ResolutionExtractionPort.SourceExcerpt> usable = List.copyOf(Objects.requireNonNull(excerpts, "excerpts must not be null"))
                .stream().filter(this::usable).filter(excerpt -> belongsToBundle(bundle, excerpt)).toList();
        var explicit = usable.stream().filter(this::isExplicitOpening).filter(this::notFutureOrChoice).findFirst();
        if (explicit.isPresent()) return sourceResult(ScenarioEntryResult.Decision.EXPLICIT_SOURCE, explicit.get());

        var inferred = usable.stream().filter(this::isInteractiveCurrentMoment).filter(this::notFutureOrChoice).findFirst();
        if (inferred.isPresent()) return sourceResult(ScenarioEntryResult.Decision.INFERRED_SOURCE, inferred.get());

        String anchor = bundle.currentRevision().documents().stream().map(ScenarioBundleDocumentSelection::documentType)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse("the source region");
        return new ScenarioEntryResult(ScenarioEntryResult.Decision.MINIMAL_PROLOGUE,
                "A safe first moment near the source material", "The available source establishes " + anchor + ".", List.of(), anchor);
    }

    private ScenarioEntryResult sourceResult(ScenarioEntryResult.Decision decision, ResolutionExtractionPort.SourceExcerpt excerpt) {
        String text = excerpt.text().strip();
        return new ScenarioEntryResult(decision, firstSentence(text), firstSentence(text),
                List.of(new ScenarioSourceReference(excerpt.documentId(), excerpt.extractionVersion(), excerpt.locator())),
                excerpt.locator());
    }

    private boolean usable(ResolutionExtractionPort.SourceExcerpt excerpt) {
        return excerpt != null && excerpt.isPublishedEvidence() && excerpt.text() != null && !excerpt.text().isBlank();
    }

    private boolean belongsToBundle(ScenarioSourceBundle bundle, ResolutionExtractionPort.SourceExcerpt excerpt) {
        return bundle.currentRevision().documents().stream().anyMatch(document ->
                document.knowledgeDocumentId().equals(excerpt.documentId())
                        && document.extractionVersion() == excerpt.extractionVersion());
    }

    private boolean isExplicitOpening(ResolutionExtractionPort.SourceExcerpt excerpt) {
        return excerpt.text().toLowerCase(Locale.ROOT).matches("(?s).*\\b(?:opening|start(?:s|ing)?|begins?|first scene|arrival)\\b.*");
    }

    private boolean isInteractiveCurrentMoment(ResolutionExtractionPort.SourceExcerpt excerpt) {
        return excerpt.text().matches("(?is).*\\b(?:you|the party|characters?)\\b.*\\b(?:enter|arrive|stand|meet|see|hear|search|approach|wait|reach)\\w*.*");
    }

    private boolean notFutureOrChoice(ResolutionExtractionPort.SourceExcerpt excerpt) {
        return !excerpt.text().matches("(?is).*\\b(?:later|eventually|will|must choose|choose|decide|if you decide|secret|behind the scenes)\\b.*");
    }

    private String firstSentence(String text) {
        int end = text.indexOf('.');
        return (end < 0 ? text : text.substring(0, end + 1)).trim();
    }
}
