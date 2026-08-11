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
            int endingCount, String adventureLength, String failureReason) {
        static PlanView from(AdventureStoryPlan plan) { return new PlanView(plan.planId(), plan.packageRevision(), plan.partyRevision(), plan.version(), plan.status().name(), plan.currentStage(), plan.stageCount(), plan.configuration().endingCount(), plan.configuration().adventureLength().name(), plan.failureReason()); }
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
