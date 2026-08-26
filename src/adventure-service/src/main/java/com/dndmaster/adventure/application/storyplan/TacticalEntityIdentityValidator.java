package com.dndmaster.adventure.application.storyplan;

import com.dndmaster.adventure.domain.adventure.AdventurePlanEvidence;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage;
import com.dndmaster.adventure.domain.adventure.PlacementGrounding;
import com.dndmaster.adventure.domain.adventure.PlacementGroundingType;
import com.dndmaster.adventure.domain.adventure.TacticalEnvironment;
import com.dndmaster.adventure.domain.adventure.TacticalPlacement;
import com.dndmaster.adventure.domain.adventure.TacticalPlacementKind;
import com.dndmaster.adventure.domain.adventure.TacticalScenePlan;
import java.util.List;
import java.util.Locale;

/** Distinguishes source-backed entity identities from bounded coordinate inference. */
final class TacticalEntityIdentityValidator {
    private TacticalEntityIdentityValidator() {}

    static String validate(
            TacticalSceneRequest request,
            TacticalScenePlanCandidate candidate,
            TacticalScenePlan scene) {
        for (TacticalPlacement placement : scene.players()) {
            if (!identitySupported(request, candidate, placement)) {
                return "tactical player identity is not supported by source or party evidence";
            }
        }
        for (TacticalPlacement placement : java.util.stream.Stream.of(
                scene.allies(), scene.npcs(), scene.enemies(), scene.bosses(), scene.interactiveObjects())
                .flatMap(List::stream).toList()) {
            if (!identitySupported(request, candidate, placement)) {
                return "tactical " + placement.kind().name().toLowerCase(Locale.ROOT).replace('_', ' ')
                        + " identity is not supported by source evidence";
            }
        }
        for (TacticalEnvironment environment : scene.environments()) {
            if (!environmentIdentitySupported(request, candidate, environment)) {
                return "tactical environment identity is not supported by source evidence";
            }
        }
        return null;
    }

    private static boolean identitySupported(
            TacticalSceneRequest request,
            TacticalScenePlanCandidate candidate,
            TacticalPlacement placement) {
        String id = placement.id();
        if (placement.kind() == TacticalPlacementKind.PLAYER && request.partyMemberIds().contains(id)) return true;
        if (declaredByStage(request.stage(), placement.kind(), id)) return true;
        if (placement.grounding().type() == PlacementGroundingType.SOURCE_CITATION
                && supportsClaim(candidate.citations(), placement.grounding(), id)) return true;
        return inferredIdentityEvidence(request, id);
    }

    private static boolean declaredByStage(
            AdventureStoryPlanStage stage,
            TacticalPlacementKind kind,
            String id) {
        String declared = switch (kind) {
            case PLAYER -> "";
            case ALLY, NPC -> String.join(" ", stage.npcOrClues());
            case ENEMY -> String.join(" ", stage.enemies());
            case BOSS -> stage.boss();
            case INTERACTIVE_OBJECT -> String.join(" ", java.util.stream.Stream.concat(
                    stage.rewards().stream(), stage.npcOrClues().stream()).toList());
        };
        return SourceClaimSupport.supports(declared, id);
    }

    private static boolean inferredIdentityEvidence(TacticalSceneRequest request, String identity) {
        return request.citations().stream()
                .map(AdventureStoryPlanGenerationPort.SourceCitation::quote)
                .anyMatch(quote -> SourceClaimSupport.supports(quote, identity))
                || request.stage().evidence().stream()
                .map(AdventurePlanEvidence::quote)
                .anyMatch(quote -> SourceClaimSupport.supports(quote, identity))
                || request.map().relatedEvidence().stream()
                .filter(related -> request.citations().stream().anyMatch(authoritative -> sameEvidence(authoritative, related)))
                .map(AdventureStoryPlanGenerationPort.SourceCitation::quote)
                .anyMatch(quote -> SourceClaimSupport.supports(quote, identity));
    }

    private static boolean sameEvidence(
            AdventureStoryPlanGenerationPort.SourceCitation authoritative,
            AdventureStoryPlanGenerationPort.SourceCitation related) {
        return authoritative.documentType().equals(related.documentType())
                && authoritative.documentId().equals(related.documentId())
                && authoritative.extractionVersion() == related.extractionVersion()
                && authoritative.locator().equals(related.locator())
                && authoritative.quote().equals(related.quote());
    }

    private static boolean environmentIdentitySupported(
            TacticalSceneRequest request,
            TacticalScenePlanCandidate candidate,
            TacticalEnvironment environment) {
        if (environment.grounding().type() == PlacementGroundingType.SOURCE_CITATION
                && (supportsClaim(candidate.citations(), environment.grounding(), environment.id())
                        || supportsClaim(candidate.citations(), environment.grounding(), environment.kind()))) return true;
        return inferredIdentityEvidence(request, environment.id())
                || inferredIdentityEvidence(request, environment.kind());
    }

    private static boolean supportsClaim(
            List<AdventureStoryPlanGenerationPort.SourceCitation> citations,
            PlacementGrounding grounding,
            String claim) {
        if (grounding.type() != PlacementGroundingType.SOURCE_CITATION) return false;
        return citations.stream()
                .filter(citation -> TacticalScenePlanValidator.key(citation).equals(grounding.citation()))
                .map(AdventureStoryPlanGenerationPort.SourceCitation::quote)
                .anyMatch(quote -> SourceClaimSupport.supports(quote, claim));
    }
}
