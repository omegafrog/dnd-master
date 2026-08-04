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
    @PostMapping SessionView create(@RequestBody CreateSessionRequest request) { return SessionView.from(service.create(owner(), request.scenarioPackageId(), request.blueprintId(), request.blueprintRevision(), request.runtimeConfiguration())); }
    @GetMapping List<SessionView> list(@RequestParam UUID scenarioPackageId) { return service.listByScenarioPackageId(scenarioPackageId, owner()).stream().map(SessionView::from).toList(); }
    @GetMapping("/{sessionId}") SessionView read(@PathVariable UUID sessionId) { return SessionView.from(service.read(new SessionId(sessionId), owner())); }
    @PostMapping("/{sessionId}/party") SessionView add(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version, @RequestBody PartyMemberRequest request) { return SessionView.from(service.addMember(new SessionId(sessionId), owner(), version, request.toDomain())); }
    @PutMapping("/{sessionId}/party/{characterSheetId}") SessionView replace(@PathVariable UUID sessionId, @PathVariable UUID characterSheetId, @RequestHeader("If-Match-Version") long version, @RequestBody PartyMemberRequest request) { return SessionView.from(service.replaceMember(new SessionId(sessionId), owner(), version, request.toDomain(characterSheetId))); }
    @DeleteMapping("/{sessionId}/party/{characterSheetId}") SessionView remove(@PathVariable UUID sessionId, @PathVariable UUID characterSheetId, @RequestHeader("If-Match-Version") long version) { return SessionView.from(service.removeMember(new SessionId(sessionId), owner(), version, new CharacterSheetId(characterSheetId))); }
    @PostMapping("/{sessionId}/start") SessionView start(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version, @RequestHeader("Idempotency-Key") UUID requestId, @RequestBody StartRequest request) { return SessionView.from(service.start(new SessionId(sessionId), owner(), version, requestId, new AdventureId(request.adventureId()))); }
    @PostMapping("/{sessionId}/complete") SessionView complete(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version) { return SessionView.from(service.complete(new SessionId(sessionId), owner(), version)); }
    @DeleteMapping("/{sessionId}") SessionView delete(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version) { return SessionView.from(service.delete(new SessionId(sessionId), owner(), version)); }
    @GetMapping("/internal/{sessionId}/character-policy") CharacterPolicyView characterPolicy(@PathVariable UUID sessionId, @RequestHeader(value = "X-Internal-Service", required = false) String internalService, @RequestParam(required = false) UUID characterSheetId) {
        if (!"character-management".equals(internalService)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "internal service header required");
        AdventureSession session = service.readInternal(new SessionId(sessionId));
        AdventurePartyMember member = characterSheetId == null ? null : session.party().stream().filter(item -> item.characterSheetId().value().equals(characterSheetId)).findFirst().orElse(null);
        boolean mutable = session.status() != AdventureSession.Status.STARTED && session.status() != AdventureSession.Status.STARTING;
        return member == null && session.status() == AdventureSession.Status.DRAFT ? CharacterPolicyView.draft() : member == null ? CharacterPolicyView.terminated() : new CharacterPolicyView(
                mutable, mutable || member.nameMutableAfterStart(), mutable || member.levelMutableAfterStart(),
                mutable || member.raceMutableAfterStart(), mutable || member.characterClassMutableAfterStart(),
                mutable || member.backgroundMutableAfterStart(), mutable || member.startingAbilitiesMutableAfterStart());
    }
    private OwnerPlayerId owner() { return new OwnerPlayerId(playerResolver.playerId()); }
    public record CreateSessionRequest(UUID scenarioPackageId, UUID blueprintId, long blueprintRevision, AdventureSessionRuntimeConfiguration runtimeConfiguration) {}
    public record StartRequest(UUID adventureId) {}
    public record CharacterPolicyView(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
            boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable, boolean startingAbilitiesMutable) {
            static CharacterPolicyView draft() { return new CharacterPolicyView(true, true, true, true, true, true, true); }
        static CharacterPolicyView terminated() { return new CharacterPolicyView(false, false, false, false, false, false, false); }
    }
    public record PartyMemberRequest(UUID characterSheetId, ControlMode controlMode, boolean nameMutableAfterStart, boolean raceMutableAfterStart, boolean characterClassMutableAfterStart, boolean backgroundMutableAfterStart, boolean startingAbilitiesMutableAfterStart, boolean levelMutableAfterStart) {
        AdventurePartyMember toDomain() { return toDomain(characterSheetId); }
        AdventurePartyMember toDomain(UUID id) { return new AdventurePartyMember(new CharacterSheetId(id), controlMode, nameMutableAfterStart, raceMutableAfterStart, characterClassMutableAfterStart, backgroundMutableAfterStart, startingAbilitiesMutableAfterStart, levelMutableAfterStart); }
    }
    public record SessionView(UUID sessionId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, int characterLimit, long version, AdventureSession.Status status, UUID adventureId, AdventureSessionRuntimeConfiguration runtimeConfiguration, List<PartyMemberRequest> party) {
        static SessionView from(AdventureSession session) { return new SessionView(session.id().value(), session.scenarioPackageId(), session.scenarioPackageRevision(), session.blueprintId(), session.blueprintRevision(), session.characterLimit(), session.version(), session.status(), session.startedAdventureId() == null ? null : session.startedAdventureId().value(), session.runtimeConfiguration(), session.party().stream().map(m -> new PartyMemberRequest(m.characterSheetId().value(), m.controlMode(), m.nameMutableAfterStart(), m.raceMutableAfterStart(), m.characterClassMutableAfterStart(), m.backgroundMutableAfterStart(), m.startingAbilitiesMutableAfterStart(), m.levelMutableAfterStart())).toList()); }
    }
}
