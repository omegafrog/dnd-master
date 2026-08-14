package com.dndmaster.adventure.application.scenario;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentLookupPort;
import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.scenario.OwnerPlayerId;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentRole;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleId;
import com.dndmaster.adventure.domain.scenario.RulebookEdition;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleDeletionConflictException;
import com.dndmaster.adventure.domain.scenario.ScenarioBundleValidationException;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundle;
import com.dndmaster.adventure.domain.scenario.ScenarioSourceBundleRevision;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ScenarioBundleApplicationService {
    private final ScenarioBundleRepository repository;
    private final KnowledgeDocumentLookupPort lookupPort;

    public ScenarioBundleApplicationService(
            ScenarioBundleRepository repository, KnowledgeDocumentLookupPort lookupPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.lookupPort = Objects.requireNonNull(lookupPort, "lookup port must not be null");
    }

    public ScenarioSourceBundle createBundle(OwnerPlayerId ownerPlayerId, List<BundleDocumentDraft> documents) {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        ScenarioSourceBundle bundle = ScenarioSourceBundle.create(
                ScenarioBundleId.generate(), ownerPlayerId, newRevision(1, ownerPlayerId, documents));
        repository.save(bundle);
        return bundle;
    }

    public ScenarioSourceBundle createBundle(OwnerPlayerId ownerPlayerId, String name,
            RulebookEdition rulebookEdition, List<BundleDocumentDraft> documents) {
        Objects.requireNonNull(ownerPlayerId, "owner player id must not be null");
        if (name == null || name.isBlank()) throw new ScenarioBundleValidationException("bundle name is required");
        if (rulebookEdition == null) throw new ScenarioBundleValidationException("rulebook edition is required");
        ScenarioSourceBundle bundle = ScenarioSourceBundle.create(
                ScenarioBundleId.generate(), ownerPlayerId, name, rulebookEdition,
                newRevision(1, ownerPlayerId, documents));
        repository.save(bundle);
        return bundle;
    }

    public ScenarioSourceBundle reviseBundle(
            ScenarioBundleId bundleId, OwnerPlayerId ownerPlayerId, List<BundleDocumentDraft> documents) {
        return reviseBundle(bundleId, ownerPlayerId, null, null, documents);
    }

    public ScenarioSourceBundle reviseBundle(
            ScenarioBundleId bundleId, OwnerPlayerId ownerPlayerId, String name, RulebookEdition rulebookEdition,
            List<BundleDocumentDraft> documents) {
        ScenarioSourceBundle bundle = loadOwned(bundleId, ownerPlayerId);
        ScenarioSourceBundle revised = bundle.revise(
                name == null ? bundle.name() : name,
                rulebookEdition == null ? bundle.rulebookEdition() : rulebookEdition,
                newRevision(bundle.currentRevision().revision() + 1, ownerPlayerId, documents));
        repository.save(revised);
        return revised;
    }

    public ScenarioSourceBundle readBundle(ScenarioBundleId bundleId, OwnerPlayerId ownerPlayerId) {
        ScenarioSourceBundle bundle = loadOwned(bundleId, ownerPlayerId);
        bundle.authorize(ownerPlayerId);
        return bundle;
    }

    public List<ScenarioSourceBundle> listBundles(OwnerPlayerId ownerPlayerId) {
        return repository.findByOwnerId(ownerPlayerId.value()).stream()
                .filter(bundle -> bundle.ownerPlayerId().value().equals(ownerPlayerId.value()))
                .toList();
    }

    public void deleteBundle(ScenarioBundleId bundleId, OwnerPlayerId ownerPlayerId) {
        ScenarioSourceBundle bundle = loadOwned(bundleId, ownerPlayerId);
        if (repository.hasActiveAdventureReferences(bundle.id())) {
            throw new ScenarioBundleDeletionConflictException();
        }
        repository.deleteById(bundle.id());
    }

    private ScenarioSourceBundle loadOwned(ScenarioBundleId bundleId, OwnerPlayerId ownerPlayerId) {
        ScenarioSourceBundle bundle = repository.findById(Objects.requireNonNull(bundleId, "bundle id must not be null"))
                .orElseThrow(com.dndmaster.adventure.domain.scenario.ScenarioBundleNotFoundException::new);
        bundle.authorize(ownerPlayerId);
        return bundle;
    }

    private ScenarioSourceBundleRevision newRevision(
            long revisionNumber, OwnerPlayerId ownerPlayerId, List<BundleDocumentDraft> drafts) {
        List<BundleDocumentDraft> requested = List.copyOf(Objects.requireNonNull(drafts, "documents must not be null"));
        if (requested.isEmpty()) {
            throw new ScenarioBundleValidationException("documents must not be empty");
        }
        if (new HashSet<>(requested.stream().map(BundleDocumentDraft::knowledgeDocumentId).toList()).size() != requested.size()) {
            throw new ScenarioBundleValidationException("documents must be unique");
        }

        Map<KnowledgeDocumentId, KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> owned = new HashMap<>();
        for (KnowledgeDocumentLookupPort.KnowledgeDocumentRecord record : lookupPort.findOwnedDocuments(ownerPlayerId.value())) {
            owned.put(record.knowledgeDocumentId(), record);
        }

        List<com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection> selections = requested.stream()
                .map(draft -> toSelection(ownerPlayerId, owned, draft))
                .toList();
        long rulebooks = selections.stream().filter(selection -> selection.role() == ScenarioBundleDocumentRole.RULEBOOK).count();
        long mainScenarios = selections.stream().filter(selection -> selection.role() == ScenarioBundleDocumentRole.MAIN_SCENARIO).count();
        if (rulebooks != 1) throw new ScenarioBundleValidationException("exactly one rulebook is required");
        if (mainScenarios > 1) throw new ScenarioBundleValidationException("at most one main scenario is allowed");
        return new ScenarioSourceBundleRevision(revisionNumber, selections);
    }

    private static com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection toSelection(
            OwnerPlayerId ownerPlayerId,
            Map<KnowledgeDocumentId, KnowledgeDocumentLookupPort.KnowledgeDocumentRecord> owned,
            BundleDocumentDraft draft) {
        KnowledgeDocumentLookupPort.KnowledgeDocumentRecord record = owned.get(draft.knowledgeDocumentId());
        if (record == null) {
            throw new ScenarioBundleValidationException("document is not owned by " + ownerPlayerId.value());
        }
        if (!"STORYBOOK".equalsIgnoreCase(record.documentType())
                && !"RULEBOOK".equalsIgnoreCase(record.documentType())) {
            throw new ScenarioBundleValidationException("document must be STORYBOOK or RULEBOOK");
        }
        if (!isUsable(record.status())) {
            throw new ScenarioBundleValidationException("document is not ready for bundling");
        }
        if (record.extractionVersion() <= 0) {
            throw new ScenarioBundleValidationException("document extraction version must be positive");
        }
        return new com.dndmaster.adventure.domain.scenario.ScenarioBundleDocumentSelection(
                draft.knowledgeDocumentId(),
                draft.role(),
                record.status(),
                record.originalFilename(),
                record.documentType(),
                record.extractionVersion());
    }

    private static boolean isUsable(KnowledgeDocumentStatus status) {
        return switch (status) {
            case EXTRACTED, INDEXED, PARTIAL_AWAITING_CONFIRMATION, PARTIAL_CONFIRMED -> true;
            default -> false;
        };
    }
}
