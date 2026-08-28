package com.dndmaster.character.application;

import com.dndmaster.character.domain.*;
import java.util.Objects;
import java.util.List;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class CharacterSheetApplicationService {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final CharacterSheetRepository repository;
    private final AdventureEditionHttpPort adventureEditionHttpPort;
    private final SessionCharacterPolicyPort sessionPolicyPort;
    private final CharacterMutationRulesResolver mutationRulesResolver;

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort) {
        this(repository, adventureEditionHttpPort, ignored -> SessionCharacterPolicy.draft(),
                CharacterMutationRulesResolver.standard());
    }

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository, AdventureEditionHttpPort adventureEditionHttpPort,
            SessionCharacterPolicyPort sessionPolicyPort) {
        this(repository, adventureEditionHttpPort, sessionPolicyPort, CharacterMutationRulesResolver.standard());
    }

    public CharacterSheetApplicationService(
            CharacterSheetRepository repository,
            AdventureEditionHttpPort adventureEditionHttpPort,
            SessionCharacterPolicyPort sessionPolicyPort,
            CharacterMutationRulesResolver mutationRulesResolver) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.adventureEditionHttpPort = Objects.requireNonNull(adventureEditionHttpPort, "edition HTTP port must not be null");
        this.sessionPolicyPort = Objects.requireNonNull(sessionPolicyPort, "session policy port must not be null");
        this.mutationRulesResolver = Objects.requireNonNull(mutationRulesResolver, "mutation rules resolver must not be null");
    }

    public CharacterSheet createSheet(CreateCharacterSheetCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        SessionCharacterPolicy sessionPolicy = sessionPolicyPort.policyFor(command.sessionId());
        if (!sessionPolicy.acceptingCharacterSheets()) {
            throw new IllegalStateException("adventure session no longer accepts character sheets");
        }
        SheetEdition applied = sessionPolicy.characterEdition() == null
                ? adventureEditionHttpPort.getAppliedEdition(command.sessionId().asAdventureId())
                : SheetEdition.valueOf(sessionPolicy.characterEdition());
        var sheet = new CharacterSheet(
                CharacterSheetId.generate(), command.sessionId(), command.ownerPlayerId(), command.requestedEdition(), command.data());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(command.sessionId().asAdventureId(), applied, command.requestedEdition()));
        CharacterMutationDecision decision = mutationRulesResolver.rulesFor(command.requestedEdition())
                .evaluate(command.data(), command.data());
        if (!decision.accepted()) throw new CharacterMutationRejectedException(decision.violations());
        repository.save(sheet);
        return sheet;
    }

    public List<CharacterSheet> listSheetsOwnedBy(UUID ownerPlayerId) {
        return repository.findByOwnerPlayerId(Objects.requireNonNull(ownerPlayerId, "owner player id must not be null"));
    }

    public java.util.Optional<CharacterSheet> findByCommandId(UUID commandId) { return repository.findByCommandId(commandId); }

    public CharacterSheet copyOwnedSheet(CharacterSheetId sourceId, SessionId targetSessionId, UUID ownerPlayerId) {
        CharacterSheet source = load(sourceId);
        if (!Objects.equals(source.ownerPlayerId(), Objects.requireNonNull(ownerPlayerId, "owner player id must not be null"))) {
            throw new IllegalStateException("character sheet belongs to another owner");
        }
        return createSheet(new CreateCharacterSheetCommand(targetSessionId, ownerPlayerId, source.edition(), source.data()));
    }

    public CharacterSheet openSheet(CharacterSheetId id, SheetEdition requestedEdition) {
        CharacterSheet sheet = load(id);
        requireSessionActive(sheet);
        SheetEdition applied = adventureEditionHttpPort.getAppliedEdition(sheet.adventureId());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(sheet.adventureId(), applied, requestedEdition));
        return sheet;
    }

    /** Internal runtime reads need metadata only; edition negotiation belongs to user-facing reads. */
    public CharacterSheet readForRuntime(CharacterSheetId id) {
        // Start orchestration reads the sheet before the Adventure aggregate exists;
        // ownership and lifecycle checks happen at party binding/start validation.
        return load(id);
    }

    public CharacterSheet verifySessionOwnership(CharacterSheetId id, SessionId sessionId, java.util.UUID ownerPlayerId) {
        CharacterSheet sheet = load(id);
        if (!sheet.sessionId().equals(sessionId)) throw new IllegalStateException("character sheet belongs to another session");
        if (sheet.ownerPlayerId() == null || !sheet.ownerPlayerId().equals(ownerPlayerId)) throw new IllegalStateException("character sheet belongs to another owner");
        requireSessionActive(sheet);
        return sheet;
    }

    public CharacterSheet manageCharacter(CharacterSheetId id, CharacterSheetUpdate update) {
        CharacterSheet sheet = load(id);
        SessionCharacterPolicy policy = sessionPolicyPort.policyFor(sheet.adventureId(), sheet.id());
        if (!policy.acceptingCharacterSheets()) throw new IllegalStateException("character sheet belongs to a terminated adventure session");
        CharacterSheet replay = repository.findByCommandId(update.commandId()).orElse(null);
        if (replay != null) {
            if (!update.fingerprint().equals(replay.operationFingerprint())) {
                throw new IllegalStateException("character sheet command id reused with different payload");
            }
            return replay;
        }
        SheetEdition applied = adventureEditionHttpPort.getAppliedEdition(sheet.adventureId());
        sheet.authorizeOpen(new CharacterSheetOpenRequest(sheet.adventureId(), applied, update.edition()));
        if (sheet.version() != update.expectedVersion()) {
            throw new IllegalStateException("character sheet version does not match");
        }
        if (!policy.nameMutable() && !sheet.data().characterName().equals(update.data().characterName())) {
            throw new IllegalStateException("character name is fixed for this adventure session");
        }
        if (!policy.levelMutable() && sheet.data().level() != update.data().level()) {
            throw new IllegalStateException("character level is fixed for this adventure session");
        }
        if (!policy.raceMutable() && !sheet.data().race().equals(update.data().race())) throw new IllegalStateException("character race is fixed for this adventure session");
        if (!policy.characterClassMutable() && !sheet.data().characterClass().equals(update.data().characterClass())) throw new IllegalStateException("character class is fixed for this adventure session");
        if (!policy.backgroundMutable() && !sheet.data().background().equals(update.data().background())) throw new IllegalStateException("character background is fixed for this adventure session");
        if (!policy.startingAbilitiesMutable() && !sheet.data().startingAbilities().equals(update.data().startingAbilities())) throw new IllegalStateException("starting abilities are fixed for this adventure session");
        sheet.applyUpdate(update, mutationRulesResolver.rulesFor(sheet.edition()));
        repository.save(sheet, update.expectedVersion() + 1, update.commandId(), update.fingerprint());
        sheet.markPersisted(update.expectedVersion() + 1, update.commandId(), update.fingerprint());
        return sheet;
    }

    public CharacterSheet applyRuntimeMutation(CharacterSheetId id, SessionId sessionId, UUID ownerPlayerId,
                                               RuntimeCharacterMutation mutation, UUID commandId, long expectedVersion) {
        Objects.requireNonNull(mutation, "mutation must not be null");
        CharacterSheet sheet = load(id);
        if (!sheet.sessionId().equals(sessionId)) throw new IllegalStateException("character sheet belongs to another session");
        if (!Objects.equals(sheet.ownerPlayerId(), ownerPlayerId)) throw new IllegalStateException("character sheet belongs to another owner");
        SessionCharacterPolicy policy = sessionPolicyPort.policyFor(sheet.adventureId(), sheet.id());
        if (!policy.runtimeMutationsAllowed()) throw new IllegalStateException("runtime character mutations are not allowed");
        CharacterSheet replay = repository.findByCommandId(commandId).orElse(null);
        if (replay != null) {
            if (!runtimeFingerprint(mutation).equals(replay.operationFingerprint())) throw new IllegalStateException("character sheet command id reused with different payload");
            return replay;
        }
        if (sheet.version() != expectedVersion) throw new IllegalStateException("character sheet version does not match");
        CharacterSheetData proposed = mutateData(sheet.data(), mutation);
        CharacterMutationDecision decision = mutationRulesResolver.rulesFor(sheet.edition()).evaluate(sheet.data(), proposed);
        if (!decision.accepted()) throw new CharacterMutationRejectedException(decision.violations());
        CharacterSheetUpdate update = new CharacterSheetUpdate(sheet.edition(), proposed, InputMode.STRUCTURED_SHEET, commandId, expectedVersion);
        sheet.applyUpdate(update, mutationRulesResolver.rulesFor(sheet.edition()));
        repository.save(sheet, expectedVersion + 1, commandId, runtimeFingerprint(mutation));
        sheet.markPersisted(expectedVersion + 1, commandId, runtimeFingerprint(mutation));
        return sheet;
    }

    private static String runtimeFingerprint(RuntimeCharacterMutation mutation) { return "RUNTIME|" + mutation; }

    private static CharacterSheetData mutateData(CharacterSheetData current, RuntimeCharacterMutation mutation) {
        try {
            ObjectNode state = object(current.characterState(), "characterState");
            ObjectNode build = object(current.characterBuild(), "characterBuild");
            if (mutation.hitPointDelta() != 0) {
                Integer hitPointMaximum = mutation.hitPointDelta() > 0
                        ? derivedInteger(current.derivedStatistics(), "hitPointMaximum")
                        : null;
                Integer hitPointBaseline = state.has("currentHitPoints")
                        ? null
                        : hitPointMaximum != null
                                ? hitPointMaximum
                                : derivedInteger(current.derivedStatistics(), "hitPointMaximum");
                state.put("currentHitPoints", nonNegative(state, "currentHitPoints", mutation.hitPointDelta(),
                        hitPointBaseline, hitPointMaximum));
            }
            if (mutation.currencyDelta() != 0) {
                state.put("currency", nonNegative(state, "currency", mutation.currencyDelta(), 0));
            }
            if (!mutation.addItems().isEmpty() || !mutation.removeItems().isEmpty()) {
                JsonNode owned = build.get("ownedEquipment");
                if (owned == null || !owned.isArray()) throw new IllegalArgumentException("character build has no ownedEquipment");
                ArrayNode items = (ArrayNode) owned;
                for (String item : mutation.removeItems()) { boolean removed = false; for (int i = items.size() - 1; i >= 0; i--) if (item.equals(items.get(i).asText())) { items.remove(i); removed = true; break; } if (!removed) throw new IllegalArgumentException("character does not own item: " + item); }
                for (String item : mutation.addItems()) { boolean exists = false; for (JsonNode value : items) if (item.equals(value.asText())) exists = true; if (!exists) items.add(item); }
            }
            String nextBuild = JSON.writeValueAsString(build), nextState = JSON.writeValueAsString(state);
            return current instanceof CharacterSheetData2014 data
                    ? new CharacterSheetData2014(data.characterName(), data.level(), data.inspiration(), data.race(), data.characterClass(), data.background(), data.startingAbilities(), data.derivedStatistics(), nextBuild, nextState)
                    : new CharacterSheetData2024(current.characterName(), current.level(), ((CharacterSheetData2024) current).heroicInspiration(), current.race(), current.characterClass(), current.background(), current.startingAbilities(), current.derivedStatistics(), nextBuild, nextState);
        } catch (Exception e) { if (e instanceof IllegalArgumentException iae) throw iae; throw new IllegalArgumentException("invalid character runtime state", e); }
    }

    private static ObjectNode object(String value, String field) throws Exception { if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required"); JsonNode node = JSON.readTree(value); if (!node.isObject()) throw new IllegalArgumentException(field + " must be an object"); return (ObjectNode) node; }
    private static Integer derivedInteger(String derivedStatistics, String field) throws Exception {
        if (derivedStatistics == null || derivedStatistics.isBlank()) return null;
        JsonNode node = JSON.readTree(derivedStatistics);
        if (!node.isObject()) throw new IllegalArgumentException("derivedStatistics must be an object");
        JsonNode value = node.get(field);
        if (value == null) return null;
        if (!value.isIntegralNumber() || value.longValue() < 0 || value.longValue() > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(field + " must be a non-negative integer");
        }
        return value.intValue();
    }
    private static int nonNegative(ObjectNode state, String field, int delta, Integer fallback) {
        return nonNegative(state, field, delta, fallback, null);
    }
    private static int nonNegative(ObjectNode state, String field, int delta, Integer fallback, Integer maximum) {
        JsonNode value = state.get(field);
        if (value == null) {
            if (fallback == null) throw new IllegalArgumentException(field + " is required");
            value = JSON.getNodeFactory().numberNode(fallback);
        }
        if (!value.isIntegralNumber()) throw new IllegalArgumentException(field + " must be an integer");
        long next = value.longValue() + (long) delta;
        if (next < 0 || next > Integer.MAX_VALUE) throw new IllegalArgumentException(field + " is out of range");
        if (maximum != null && next > maximum) next = maximum;
        return (int) next;
    }

    private void requireSessionActive(CharacterSheet sheet) {
        if (!sessionPolicyPort.policyFor(sheet.adventureId(), sheet.id()).acceptingCharacterSheets()) {
            throw new IllegalStateException("character sheet belongs to a terminated adventure session");
        }
    }

    private CharacterSheet load(CharacterSheetId id) {
        return repository.findById(Objects.requireNonNull(id, "character sheet id must not be null"))
                .orElseThrow(CharacterSheetNotFoundException::new);
    }
}
