package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/adventure-sessions")
public final class AdventureSessionController {
    private final AdventureSessionApplicationService service;
    private final AuthenticatedPlayerResolver playerResolver;
    public AdventureSessionController(AdventureSessionApplicationService service, AuthenticatedPlayerResolver playerResolver) { this.service = service; this.playerResolver = playerResolver; }
    @PostMapping SessionView create(@RequestBody CreateSessionRequest request) { return SessionView.from(service.create(owner(), request.scenarioPackageId())); }
    @GetMapping("/{sessionId}") SessionView read(@PathVariable UUID sessionId) { return SessionView.from(service.read(new SessionId(sessionId), owner())); }
    @PostMapping("/{sessionId}/party") SessionView add(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version, @RequestBody PartyMemberRequest request) { return SessionView.from(service.addMember(new SessionId(sessionId), owner(), version, request.toDomain())); }
    @PutMapping("/{sessionId}/party/{characterSheetId}") SessionView replace(@PathVariable UUID sessionId, @PathVariable UUID characterSheetId, @RequestHeader("If-Match-Version") long version, @RequestBody PartyMemberRequest request) { return SessionView.from(service.replaceMember(new SessionId(sessionId), owner(), version, request.toDomain(characterSheetId))); }
    @DeleteMapping("/{sessionId}/party/{characterSheetId}") SessionView remove(@PathVariable UUID sessionId, @PathVariable UUID characterSheetId, @RequestHeader("If-Match-Version") long version) { return SessionView.from(service.removeMember(new SessionId(sessionId), owner(), version, new CharacterSheetId(characterSheetId))); }
    private OwnerPlayerId owner() { return new OwnerPlayerId(playerResolver.playerId()); }
    public record CreateSessionRequest(UUID scenarioPackageId) {}
    public record PartyMemberRequest(UUID characterSheetId, ControlMode controlMode, boolean nameMutableAfterStart, boolean raceMutableAfterStart, boolean characterClassMutableAfterStart, boolean backgroundMutableAfterStart, boolean startingAbilitiesMutableAfterStart, boolean levelMutableAfterStart) {
        AdventurePartyMember toDomain() { return toDomain(characterSheetId); }
        AdventurePartyMember toDomain(UUID id) { return new AdventurePartyMember(new CharacterSheetId(id), controlMode, nameMutableAfterStart, raceMutableAfterStart, characterClassMutableAfterStart, backgroundMutableAfterStart, startingAbilitiesMutableAfterStart, levelMutableAfterStart); }
    }
    public record SessionView(UUID sessionId, int characterLimit, long version, List<PartyMemberRequest> party) {
        static SessionView from(AdventureSession session) { return new SessionView(session.id().value(), session.characterLimit(), session.version(), session.party().stream().map(m -> new PartyMemberRequest(m.characterSheetId().value(), m.controlMode(), m.nameMutableAfterStart(), m.raceMutableAfterStart(), m.characterClassMutableAfterStart(), m.backgroundMutableAfterStart(), m.startingAbilitiesMutableAfterStart(), m.levelMutableAfterStart())).toList()); }
    }
}
