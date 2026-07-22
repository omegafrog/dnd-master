package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetApplicationService;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.domain.knowledge.SessionKnowledgeSet;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class SessionKnowledgeSetController {
    private final SessionKnowledgeSetApplicationService service;

    public SessionKnowledgeSetController(SessionKnowledgeSetApplicationService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/adventures/{adventureId}/knowledge-documents")
    SessionKnowledgeSetResponse readSessionKnowledgeSet(
            @PathVariable UUID adventureId,
            @RequestHeader("Authorization") String authorization) {
        OwnerPlayerId owner = new OwnerPlayerId(extractPlayerId(authorization));
        SessionKnowledgeSet set = service.readSessionKnowledgeSet(new AdventureId(adventureId), owner);
        return SessionKnowledgeSetResponse.from(adventureId, set);
    }

    @PutMapping("/api/v1/adventures/{adventureId}/knowledge-documents")
    SessionKnowledgeSetResponse updateSessionKnowledgeSet(
            @PathVariable UUID adventureId,
            @RequestHeader("Authorization") String authorization,
            @RequestBody SessionKnowledgeSetRequest request) {
        UUID playerId = extractPlayerId(authorization);
        if (!playerId.equals(request.playerId())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.FORBIDDEN, "playerId must match Authorization");
        }
        OwnerPlayerId owner = new OwnerPlayerId(playerId);
        SessionKnowledgeSet set = service.updateSessionKnowledgeSet(
                new AdventureId(adventureId),
                owner,
                request.knowledgeDocumentIds().stream().map(KnowledgeDocumentId::new).toList());
        return SessionKnowledgeSetResponse.from(adventureId, set);
    }

    private static UUID extractPlayerId(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Bearer authorization is required");
        }
        return UUID.fromString(authorization.substring("Bearer ".length()));
    }

    public record SessionKnowledgeSetRequest(UUID playerId, List<UUID> knowledgeDocumentIds) {}

    public record SessionKnowledgeSetResponse(UUID adventureId, UUID sessionId, List<UUID> knowledgeDocumentIds) {
        static SessionKnowledgeSetResponse from(UUID adventureId, SessionKnowledgeSet set) {
            return new SessionKnowledgeSetResponse(
                    adventureId,
                    set.sessionId().value(),
                    set.knowledgeDocumentIds().stream().map(KnowledgeDocumentId::value).toList());
        }
    }
}
