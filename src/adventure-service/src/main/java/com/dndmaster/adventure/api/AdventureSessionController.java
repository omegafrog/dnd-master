package com.dndmaster.adventure.api;

import com.dndmaster.adventure.application.session.AdventureSessionApplicationService;
import com.dndmaster.adventure.application.runtime.GmProviderBindingService;
import com.dndmaster.adventure.application.runtime.GmProviderSelection;
import com.dndmaster.adventure.application.runtime.ProviderBinding;
import com.dndmaster.adventure.domain.adventure.*;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/adventure-sessions")
public final class AdventureSessionController {
    private final AdventureSessionApplicationService service;
    private final AuthenticatedPlayerResolver playerResolver;
    private final GmProviderBindingService providerBindings;

    @org.springframework.beans.factory.annotation.Autowired
    public AdventureSessionController(AdventureSessionApplicationService service, AuthenticatedPlayerResolver playerResolver,
            GmProviderBindingService providerBindings) {
        this.service = service;
        this.playerResolver = playerResolver;
        this.providerBindings = providerBindings;
    }

    @PostMapping SessionView create(@RequestBody CreateSessionRequest request) { return SessionView.from(service.create(owner(), request.scenarioPackageId(), request.blueprintId(), request.blueprintRevision(), request.runtimeConfiguration(), request.partySize())); }
    @GetMapping List<SessionView> list(@RequestParam UUID scenarioPackageId) { return service.listByScenarioPackageId(scenarioPackageId, owner()).stream().map(SessionView::from).toList(); }
    @GetMapping("/{sessionId}") SessionView read(@PathVariable UUID sessionId) { return SessionView.from(service.read(new SessionId(sessionId), owner())); }
    @GetMapping("/{sessionId}/gm-provider") GmProviderView provider(@PathVariable UUID sessionId) {
        service.read(new SessionId(sessionId), owner());
        return GmProviderView.from(providerBindings.currentOrInitialize(sessionId, defaultProvider()));
    }
    @PutMapping("/{sessionId}/gm-provider") GmProviderView switchProvider(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version, @RequestBody GmProviderRequest request) {
        service.read(new SessionId(sessionId), owner());
        return GmProviderView.from(providerBindings.switchProvider(sessionId, version, request.toSelection()));
    }
    @PostMapping("/{sessionId}/party") SessionView add(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version, @RequestBody PartyMemberRequest request) { return SessionView.from(service.addMember(new SessionId(sessionId), owner(), version, request.toDomain())); }
    @PostMapping("/{sessionId}/party/ai-candidates") AiCandidateView generateAiCandidate(@PathVariable UUID sessionId) {
        return AiCandidateView.from(service.generateAiCandidate(new SessionId(sessionId), owner()));
    }
    @PostMapping("/{sessionId}/party/ai-candidates/adopt") SessionView adopt(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version, @RequestBody AiCandidateAdoptionRequest request) {
        return SessionView.from(service.adoptAiCandidate(new SessionId(sessionId), owner(), version, request.toCandidate(), request.controlMode()));
    }
    @PutMapping("/{sessionId}/party/{characterSheetId}") SessionView replace(@PathVariable UUID sessionId, @PathVariable UUID characterSheetId, @RequestHeader("If-Match-Version") long version, @RequestBody PartyMemberRequest request) { return SessionView.from(service.replaceMember(new SessionId(sessionId), owner(), version, request.toDomain(characterSheetId))); }
    @DeleteMapping("/{sessionId}/party/{characterSheetId}") SessionView remove(@PathVariable UUID sessionId, @PathVariable UUID characterSheetId, @RequestHeader("If-Match-Version") long version) { return SessionView.from(service.removeMember(new SessionId(sessionId), owner(), version, new CharacterSheetId(characterSheetId))); }
    @PostMapping("/{sessionId}/start") SessionView start(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version, @RequestHeader("Idempotency-Key") UUID requestId, @RequestBody StartRequest request) {
        return SessionView.from(service.start(new SessionId(sessionId), owner(), version, requestId, new AdventureId(request.adventureId())));
    }
    @PostMapping("/{sessionId}/complete") SessionView complete(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version) { return SessionView.from(service.complete(new SessionId(sessionId), owner(), version)); }
    @PostMapping("/{sessionId}/start/recover") SessionView recoverStart(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version) { return SessionView.from(service.recoverFailedStart(new SessionId(sessionId), owner(), version)); }
    @DeleteMapping("/{sessionId}") SessionView delete(@PathVariable UUID sessionId, @RequestHeader("If-Match-Version") long version) { return SessionView.from(service.delete(new SessionId(sessionId), owner(), version)); }
    @GetMapping("/internal/{sessionId}/character-policy") CharacterPolicyView characterPolicy(@PathVariable UUID sessionId, @RequestHeader(value = "X-Internal-Service", required = false) String internalService, @RequestParam(required = false) UUID characterSheetId) {
        if (!"character-management".equals(internalService)) throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN, "internal service header required");
        AdventureSession session = service.readInternal(new SessionId(sessionId));
        AdventurePartyMember member = characterSheetId == null ? null : session.party().stream().filter(item -> item.characterSheetId().value().equals(characterSheetId)).findFirst().orElse(null);
        boolean mutable = session.status() != AdventureSession.Status.STARTED && session.status() != AdventureSession.Status.STARTING;
        return member == null && session.status() == AdventureSession.Status.DRAFT ? CharacterPolicyView.draft(session.characterEdition()) : member == null ? CharacterPolicyView.terminated(session.characterEdition()) : new CharacterPolicyView(
                mutable, mutable || member.nameMutableAfterStart(), mutable || member.levelMutableAfterStart(),
                mutable || member.raceMutableAfterStart(), mutable || member.characterClassMutableAfterStart(),
                mutable || member.backgroundMutableAfterStart(), mutable || member.startingAbilitiesMutableAfterStart(), session.characterEdition(), session.status() == AdventureSession.Status.STARTED);
    }
    private OwnerPlayerId owner() { return new OwnerPlayerId(playerResolver.playerId()); }
    private static GmProviderSelection defaultProvider() { return new GmProviderSelection("codex-cli", "gpt-5.6-luna", "medium"); }
    public record CreateSessionRequest(UUID scenarioPackageId, UUID blueprintId, long blueprintRevision, AdventureSessionRuntimeConfiguration runtimeConfiguration, Integer partySize) {}
    public record StartRequest(UUID adventureId) {}
    public record GmProviderRequest(UUID endpointId, String provider, String model, String reasoning) {
        public GmProviderRequest(String provider, String model, String reasoning) { this(null, provider, model, reasoning); }
        GmProviderSelection toSelection() { return new GmProviderSelection(endpointId, provider, model, reasoning); }
    }
    public record GmProviderView(UUID sessionId, UUID endpointId, String provider, String model, String reasoning, long version, boolean turnInProgress) {
        public GmProviderView(UUID sessionId, String provider, String model, String reasoning, long version, boolean turnInProgress) { this(sessionId, null, provider, model, reasoning, version, turnInProgress); }
        static GmProviderView from(ProviderBinding binding) { return new GmProviderView(binding.sessionId(), binding.selection().endpointId(), binding.selection().provider(), binding.selection().model(), binding.selection().reasoning(), binding.stateVersion(), binding.turnInProgress()); }
    }
    public record CharacterPolicyView(boolean acceptingCharacterSheets, boolean nameMutable, boolean levelMutable,
            boolean raceMutable, boolean characterClassMutable, boolean backgroundMutable, boolean startingAbilitiesMutable,
            String characterEdition, boolean runtimeMutationsAllowed) {
        static CharacterPolicyView draft(String characterEdition) { return new CharacterPolicyView(true, true, true, true, true, true, true, characterEdition, true); }
        static CharacterPolicyView terminated(String characterEdition) { return new CharacterPolicyView(false, false, false, false, false, false, false, characterEdition, false); }
    }
    public record PartyMemberRequest(UUID characterSheetId, ControlMode controlMode, boolean nameMutableAfterStart, boolean raceMutableAfterStart, boolean characterClassMutableAfterStart, boolean backgroundMutableAfterStart, boolean startingAbilitiesMutableAfterStart, boolean levelMutableAfterStart) {
        AdventurePartyMember toDomain() { return toDomain(characterSheetId); }
        AdventurePartyMember toDomain(UUID id) { return new AdventurePartyMember(new CharacterSheetId(id), controlMode, nameMutableAfterStart, raceMutableAfterStart, characterClassMutableAfterStart, backgroundMutableAfterStart, startingAbilitiesMutableAfterStart, levelMutableAfterStart); }
    }
    public record AiCandidateAdoptionRequest(UUID candidateId, String name, String race, String characterClass, String sheetSummary, ControlMode controlMode) {
        com.dndmaster.adventure.domain.adventure.AiCompanionCandidate toCandidate() { return new com.dndmaster.adventure.domain.adventure.AiCompanionCandidate(candidateId, name, race, characterClass, sheetSummary); }
    }
    public record AiCandidateView(UUID candidateId, String name, String race, String characterClass, String sheetSummary) {
        static AiCandidateView from(com.dndmaster.adventure.domain.adventure.AiCompanionCandidate candidate) { return new AiCandidateView(candidate.candidateId(), candidate.name(), candidate.race(), candidate.characterClass(), candidate.sheetSummary()); }
    }
    public record SessionView(UUID sessionId, UUID scenarioPackageId, long scenarioPackageRevision, UUID blueprintId, long blueprintRevision, String characterEdition, int characterLimit, long version, AdventureSession.Status status, UUID adventureId, AdventureSessionRuntimeConfiguration runtimeConfiguration, List<PartyMemberRequest> party) {
        static SessionView from(AdventureSession session) { return new SessionView(session.id().value(), session.scenarioPackageId(), session.scenarioPackageRevision(), session.blueprintId(), session.blueprintRevision(), session.characterEdition(), session.characterLimit(), session.version(), session.status(), session.startedAdventureId() == null ? null : session.startedAdventureId().value(), session.runtimeConfiguration(), session.party().stream().map(m -> new PartyMemberRequest(m.characterSheetId().value(), m.controlMode(), m.nameMutableAfterStart(), m.raceMutableAfterStart(), m.characterClassMutableAfterStart(), m.backgroundMutableAfterStart(), m.startingAbilitiesMutableAfterStart(), m.levelMutableAfterStart())).toList()); }
    }
}
