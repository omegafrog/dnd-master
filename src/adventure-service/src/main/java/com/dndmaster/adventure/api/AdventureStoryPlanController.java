package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.storyplan.AdventureStoryPlanApplicationService;
import com.dndmaster.adventure.domain.adventure.AdventureStoryPlan;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.AdventurePlanConfiguration;
import com.dndmaster.adventure.domain.adventure.AdventureLength;
import java.util.UUID;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/adventure-sessions/{sessionId}/story-plan")
public final class AdventureStoryPlanController {
    private final AdventureStoryPlanApplicationService service;
    private final AuthenticatedPlayerResolver playerResolver;

    public AdventureStoryPlanController(AdventureStoryPlanApplicationService service, AuthenticatedPlayerResolver playerResolver) { this.service = service; this.playerResolver = playerResolver; }

    @GetMapping
    PlanView read(@PathVariable UUID sessionId) { return PlanView.from(service.read(new SessionId(sessionId), owner())); }

    @GetMapping("/history")
    List<PlanView> history(@PathVariable UUID sessionId) { return service.readHistory(new SessionId(sessionId), owner()).stream().map(PlanView::from).toList(); }

    @GetMapping("/gm")
    GmPlanView gm(@PathVariable UUID sessionId) { return GmPlanView.from(service.read(new SessionId(sessionId), owner())); }

    @PostMapping
    PlanView generate(@PathVariable UUID sessionId, @RequestBody(required = false) ConfigurationRequest request) {
        return PlanView.from(service.generate(new SessionId(sessionId), owner(), request == null ? AdventurePlanConfiguration.defaults() : request.toDomain()));
    }

    @PostMapping("/retry")
    PlanView retry(@PathVariable UUID sessionId, @RequestBody(required = false) ConfigurationRequest request) {
        return PlanView.from(service.generate(new SessionId(sessionId), owner(), request == null ? AdventurePlanConfiguration.defaults() : request.toDomain()));
    }

    private OwnerPlayerId owner() { return new OwnerPlayerId(playerResolver.playerId()); }

    public record PlanView(UUID planId, long packageRevision, long partyRevision, long version, String status, int currentStage, int stageCount,
            int endingCount, String adventureLength, List<StageView> stages, String failureReason) {
        static PlanView from(AdventureStoryPlan plan) { return new PlanView(plan.planId(), plan.packageRevision(), plan.partyRevision(), plan.version(), plan.status().name(), plan.currentStage(), plan.stageCount(), plan.configuration().endingCount(), plan.configuration().adventureLength().name(), plan.stages().stream().map(StageView::from).toList(), plan.failureReason()); }
    }

    public record StageView(int position, String title, String stageType, String location, String goal,
            List<String> rewards, UUID mapDefinitionId, String mapAssetId, String mapAssetLocator,
            String groundingStatus, List<String> aiSuggestions, String mapSafetyStatus, Double mapConfidence, int evidenceCount) {
        static StageView from(com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage stage) {
            return new StageView(stage.position(), stage.title(), stage.stageType().name(), stage.location(), stage.goal(), stage.rewards(), stage.mapDefinitionId(), stage.mapAssetId(), stage.mapAssetLocator(),
                    stage.groundingStatus().name(), stage.aiSuggestions(), stage.mapSafetyStatus(), stage.mapConfidence(), stage.evidence().size());
        }
    }
    public record EvidenceView(String documentType, UUID documentId, long extractionVersion, String locator, String quote, double confidence) {}

    public record GmPlanView(UUID planId, long packageRevision, long partyRevision, long version, String status, int currentStage,
            int endingCount, String adventureLength, List<GmStageView> stages, String failureReason) {
        static GmPlanView from(AdventureStoryPlan plan) {
            return new GmPlanView(plan.planId(), plan.packageRevision(), plan.partyRevision(), plan.version(), plan.status().name(), plan.currentStage(),
                    plan.configuration().endingCount(), plan.configuration().adventureLength().name(), plan.stages().stream().map(GmStageView::from).toList(), plan.failureReason());
        }
    }
    public record GmStageView(int position, String title, String stageType, String location, String goal, String conflict,
            String clearCondition, String failureCondition, List<String> enemies, String boss, List<String> rewards, List<String> branchIds,
            UUID mapDefinitionId, String mapAssetId, String mapAssetLocator, String mapSafetyStatus, Double mapConfidence,
            java.util.Map<String, String> branchTargets,
            String groundingStatus, List<String> aiSuggestions, List<EvidenceView> evidence) {
        static GmStageView from(com.dndmaster.adventure.domain.adventure.AdventureStoryPlanStage stage) {
            return new GmStageView(stage.position(), stage.title(), stage.stageType().name(), stage.location(), stage.goal(), stage.conflict(), stage.clearCondition(), stage.failureCondition(),
                    stage.enemies(), stage.boss(), stage.rewards(), stage.branchIds(), stage.mapDefinitionId(), stage.mapAssetId(), stage.mapAssetLocator(), stage.mapSafetyStatus(), stage.mapConfidence(), stage.branchTargets(),
                    stage.groundingStatus().name(), stage.aiSuggestions(), stage.evidence().stream().map(item -> new EvidenceView(item.documentType(), item.documentId(), item.extractionVersion(), item.locator(), item.quote(), item.confidence())).toList());
        }
    }

    public record ConfigurationRequest(Integer endingCount, String adventureLength) {
        AdventurePlanConfiguration toDomain() {
            try {
                return new AdventurePlanConfiguration(endingCount == null ? 2 : endingCount,
                        adventureLength == null ? AdventureLength.STANDARD : AdventureLength.valueOf(adventureLength));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid adventure plan configuration", exception);
            }
        }
    }
}
