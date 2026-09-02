package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.ruleset.AppliedRuleSetApplicationService;
import com.dndmaster.adventure.application.ruleset.CreateAppliedRuleSetCommand;
import com.dndmaster.adventure.domain.ruleset.AdventureId;
import com.dndmaster.adventure.domain.ruleset.DndEdition;
import com.dndmaster.adventure.domain.ruleset.OwnerPlayerId;
import com.dndmaster.adventure.domain.ruleset.RulebookId;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Persists the catalog revision IDs chosen for an adventure. They are never silently replaced. */
@RestController
@RequestMapping("/api/v1/adventures/{adventureId}/applied-rule-set")
public final class AppliedRuleSetController {
    private final AppliedRuleSetApplicationService service; private final AuthenticatedPlayerResolver playerResolver;
    public AppliedRuleSetController(AppliedRuleSetApplicationService service, AuthenticatedPlayerResolver playerResolver) { this.service = service; this.playerResolver = playerResolver; }
    @PostMapping RuleSetView create(@PathVariable UUID adventureId, @RequestBody CreateRequest request) {
        if (request.rulebookIds() == null || request.rulebookIds().isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "at least one catalog rulebook is required");
        UUID owner = playerResolver.playerId();
        try {
            var saved = service.saveRuleSet(new com.dndmaster.adventure.domain.ruleset.RuleSetId(request.ruleSetId()), new CreateAppliedRuleSetCommand(new AdventureId(adventureId), new OwnerPlayerId(owner), new DndEdition(request.edition()), request.rulebookIds().stream().map(RulebookId::new).toList()));
            return new RuleSetView(saved.id().value(), adventureId, saved.edition().value(), saved.selectedRulebooks().values().stream().map(value -> value.rulebookId().value()).toList());
        } catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage(), error); }
    }
    @GetMapping RuleSetView read(@PathVariable UUID adventureId, @RequestParam UUID ruleSetId) {
        var saved = service.readRuleSet(new com.dndmaster.adventure.domain.ruleset.RuleSetId(ruleSetId), new OwnerPlayerId(playerResolver.playerId()));
        return new RuleSetView(saved.id().value(), adventureId, saved.edition().value(), saved.selectedRulebooks().values().stream().map(value -> value.rulebookId().value()).toList());
    }
    record CreateRequest(UUID ruleSetId, String edition, List<UUID> rulebookIds) {}
    record RuleSetView(UUID ruleSetId, UUID adventureId, String edition, List<UUID> rulebookIds) {}
}
