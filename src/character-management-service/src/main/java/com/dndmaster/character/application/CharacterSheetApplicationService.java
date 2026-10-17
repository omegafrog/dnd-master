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
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger(CharacterSheetApplicationService.class.getName());
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

    public CharacterSheet updateAbilityResource(CharacterSheetId id, SessionId sessionId, UUID ownerPlayerId,
            String resourceId, List<String> abilityIds, int current, int maximum, String recoveryCondition,
            UUID commandId, long expectedVersion) {
        long started = CharacterResourceDiagnostics.begin(LOGGER, String.valueOf(commandId), safeResourceId(resourceId),
                abilityIds == null ? 0 : abilityIds.size(), expectedVersion);
        List<String> abilities;
        String idKey;
        if (resourceId == null || resourceId.isBlank() || current < 0 || maximum < current || recoveryCondition == null || recoveryCondition.isBlank()) {
            IllegalArgumentException failure = new IllegalArgumentException("유효한 자원 식별자와 현재량·최대량·회복 조건이 필요합니다.");
            CharacterResourceDiagnostics.fail(LOGGER, started, String.valueOf(commandId), expectedVersion, -1, failure);
            throw failure;
        }
        idKey = resourceId.trim();
        if (commandId == null || abilityIds == null) {
            IllegalArgumentException failure = new IllegalArgumentException("command id and ability IDs must not be null");
            CharacterResourceDiagnostics.fail(LOGGER, started, String.valueOf(commandId), expectedVersion, -1, failure);
            throw failure;
        }
        try {
            abilities = abilityIds.stream()
                .map(value -> { if (value == null || value.isBlank()) throw new IllegalArgumentException("능력 식별자가 비어 있습니다."); return value.trim(); })
                .distinct().toList();
        } catch (RuntimeException failure) {
            CharacterResourceDiagnostics.fail(LOGGER, started, String.valueOf(commandId), expectedVersion, -1, failure);
            throw failure;
        }
        String fingerprint = resourceCommandFingerprint(id, sessionId, ownerPlayerId,
                "SET|" + idKey + "|" + abilities + "|" + current + "|" + maximum + "|" + recoveryCondition);
        return applyResourceChange(id, sessionId, ownerPlayerId, commandId, expectedVersion, fingerprint, started, state -> {
            ObjectNode resource = JSON.createObjectNode();
            resource.put("current", current); resource.put("maximum", maximum); resource.put("recoveryCondition", recoveryCondition);
            resource.set("abilityIds", JSON.valueToTree(abilities));
            JsonNode existingResources = state.get("resources");
            ObjectNode resources;
            if (existingResources == null) resources = state.putObject("resources");
            else if (existingResources.isObject()) resources = (ObjectNode) existingResources;
            else throw new IllegalArgumentException("저장된 resources 값이 객체가 아닙니다.");
            resources.set(idKey, resource);
            java.util.LinkedHashSet<String> learned = new java.util.LinkedHashSet<>();
            JsonNode existing = state.get("learnedAbilityIds");
            if (existing != null && existing.isArray()) existing.forEach(value -> { if (value.isTextual()) learned.add(value.asText()); });
            learned.addAll(abilities);
            state.set("learnedAbilityIds", JSON.valueToTree(learned));
        });
    }

    public CharacterSheet changeAbilityResource(CharacterSheetId id, SessionId sessionId, UUID ownerPlayerId,
            String resourceId, int amount, String recoveryCondition, boolean recover, UUID commandId, long expectedVersion) {
        long started = CharacterResourceDiagnostics.begin(LOGGER, String.valueOf(commandId), safeResourceId(resourceId),
                amount, expectedVersion);
        if (resourceId == null || resourceId.isBlank() || amount < 1 || recover && (recoveryCondition == null || recoveryCondition.isBlank())) {
            IllegalArgumentException failure = new IllegalArgumentException("유효한 자원 식별자와 수량이 필요합니다.");
            CharacterResourceDiagnostics.fail(LOGGER, started, String.valueOf(commandId), expectedVersion, -1, failure);
            throw failure;
        }
        String idKey = resourceId.trim();
        if (commandId == null) {
            IllegalArgumentException failure = new IllegalArgumentException("command id must not be null");
            CharacterResourceDiagnostics.fail(LOGGER, started, "null", expectedVersion, -1, failure);
            throw failure;
        }
        String fingerprint = resourceCommandFingerprint(id, sessionId, ownerPlayerId,
                (recover ? "RECOVER|" + recoveryCondition : "CONSUME") + "|" + idKey + "|" + amount);
        return applyResourceChange(id, sessionId, ownerPlayerId, commandId, expectedVersion, fingerprint, started, state -> {
            JsonNode resources = state.get("resources");
            JsonNode found = resources == null ? null : resources.get(idKey);
            if (found == null || !found.isObject()) throw new IllegalArgumentException("자원을 찾을 수 없습니다.");
            ObjectNode resource = (ObjectNode) found;
            int current = resourceInt(resource, "current"), maximum = resourceInt(resource, "maximum");
            if (current > maximum) throw new IllegalArgumentException("현재 자원이 최대량을 초과합니다.");
            if (recover) {
                if (!recoveryCondition.equals(resource.path("recoveryCondition").asText())) throw new IllegalArgumentException("회복 조건이 저장된 규칙과 일치하지 않습니다.");
                resource.put("current", current + Math.min(amount, maximum - current));
            } else {
                if (current < amount) throw new IllegalArgumentException("자원이 부족합니다.");
                resource.put("current", current - amount);
            }
        });
    }

    private CharacterSheet applyResourceChange(CharacterSheetId id, SessionId sessionId, UUID ownerPlayerId,
            UUID commandId, long expectedVersion, String fingerprint, long started, java.util.function.Consumer<ObjectNode> update) {
        long actualVersion = -1;
        try {
            CharacterSheet sheet = load(id); actualVersion = sheet.version();
            if (!sheet.sessionId().equals(sessionId) || !Objects.equals(sheet.ownerPlayerId(), ownerPlayerId)) throw new IllegalStateException("character sheet belongs to another session or owner");
            if (!sessionPolicyPort.policyFor(sheet.adventureId(), sheet.id()).runtimeMutationsAllowed()) throw new IllegalStateException("runtime character mutations are not allowed");
            CharacterSheet replay = repository.findByCommandId(commandId).orElse(null);
            if (replay != null) {
                if (!fingerprint.equals(replay.operationFingerprint())) throw new IllegalStateException("character sheet command id reused with different payload");
                CharacterResourceDiagnostics.complete(LOGGER, started, commandId.toString(), "replay", expectedVersion, replay.version());
                return replay;
            }
            if (sheet.version() != expectedVersion) throw new IllegalStateException("character sheet version does not match");
            String storedState = sheet.data().characterState();
            ObjectNode state = storedState == null || storedState.isBlank() ? JSON.createObjectNode() : object(storedState, "characterState");
            update.accept(state);
            String nextState = JSON.writeValueAsString(state);
            CharacterSheetData data = sheet.data();
            CharacterSheetData proposed = data instanceof CharacterSheetData2014 d
                    ? new CharacterSheetData2014(d.characterName(), d.level(), d.inspiration(), d.race(), d.characterClass(), d.background(), d.startingAbilities(), d.derivedStatistics(), d.characterBuild(), nextState)
                    : new CharacterSheetData2024(data.characterName(), data.level(), ((CharacterSheetData2024) data).heroicInspiration(), data.race(), data.characterClass(), data.background(), data.startingAbilities(), data.derivedStatistics(), data.characterBuild(), nextState);
            sheet.applyUpdate(new CharacterSheetUpdate(sheet.edition(), proposed, InputMode.STRUCTURED_SHEET, commandId, expectedVersion), mutationRulesResolver.rulesFor(sheet.edition()));
            repository.save(sheet, expectedVersion + 1, commandId, fingerprint);
            sheet.markPersisted(expectedVersion + 1, commandId, fingerprint);
            CharacterResourceDiagnostics.complete(LOGGER, started, commandId.toString(), "updated", expectedVersion, sheet.version());
            return sheet;
        } catch (RuntimeException e) {
            CharacterResourceDiagnostics.fail(LOGGER, started, commandId.toString(), expectedVersion, actualVersion, e);
            throw e;
        } catch (Exception e) {
            CharacterResourceDiagnostics.fail(LOGGER, started, commandId.toString(), expectedVersion, actualVersion, e);
            throw new IllegalArgumentException("invalid character runtime state", e);
        }
    }

    private static int resourceInt(ObjectNode resource, String key) {
        JsonNode value = resource.get(key);
        if (value == null || !value.isIntegralNumber() || value.intValue() < 0) throw new IllegalArgumentException("저장된 자원 " + key + " 값이 올바르지 않습니다.");
        return value.intValue();
    }

    private static String resourceCommandFingerprint(CharacterSheetId id, SessionId sessionId, UUID ownerPlayerId,
            String operation) {
        return "RESOURCE|SHEET=" + id.value() + "|SESSION=" + sessionId.value() + "|OWNER=" + ownerPlayerId + "|" + operation;
    }

    private static String safeResourceId(String resourceId) { return resourceId == null ? "unknown" : resourceId.trim(); }

    private static void resetDeathSavingThrows(ObjectNode state) {
        state.put("deathSavingThrowSuccesses", 0);
        state.put("deathSavingThrowFailures", 0);
        state.put("stable", false);
    }

    private static CharacterSheetData mutateData(CharacterSheetData current, RuntimeCharacterMutation mutation) {
        try {
            ObjectNode state = object(current.characterState(), "characterState");
            ObjectNode build = object(current.characterBuild(), "characterBuild");
            if (mutation.hitPointDelta() != 0) {
                if (state.path("dead").asBoolean(false) && mutation.hitPointDelta() > 0) {
                    throw new IllegalArgumentException("dead characters require revival before healing");
                }
                Integer hitPointMaximum = mutation.hitPointDelta() > 0
                        ? derivedInteger(current.derivedStatistics(), "hitPointMaximum")
                        : null;
                Integer hitPointBaseline = state.has("currentHitPoints")
                        ? null
                        : hitPointMaximum != null
                                ? hitPointMaximum
                                : derivedInteger(current.derivedStatistics(), "hitPointMaximum");
                state.put("currentHitPoints", hitPointsAfterMutation(state, mutation.hitPointDelta(),
                        hitPointBaseline, hitPointMaximum));
                if (mutation.hitPointDelta() > 0) resetDeathSavingThrows(state);
            }
            if (mutation.deathSavingThrowRoll() != null) {
                if (state.path("currentHitPoints").asInt(-1) != 0) {
                    throw new IllegalArgumentException("death saving throw requires zero hit points");
                }
                if (state.path("dead").asBoolean(false) || state.path("stable").asBoolean(false)) {
                    throw new IllegalArgumentException("stable or dead characters cannot make death saving throws");
                }
                var result = DeathSavingThrowRules.resolve(mutation.deathSavingThrowRoll(),
                        state.path("deathSavingThrowSuccesses").asInt(0),
                        state.path("deathSavingThrowFailures").asInt(0));
                state.put("deathSavingThrowSuccesses", result.successes());
                state.put("deathSavingThrowFailures", result.failures());
                state.put("stable", result.stable());
                state.put("dead", result.dead());
                if (result.regainedHitPoint()) state.put("currentHitPoints", 1);
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
            if (mutation.consumeSpellSlotLevel() > 0) {
                ObjectNode slots;
                JsonNode existingSlots = state.get("spellSlots");
                if (existingSlots == null || existingSlots.isNull()) {
                    slots = state.putObject("spellSlots");
                    int initialSlots = current.characterClass().equals("위저드") && current.level() == 1 ? 2 : 0;
                    if (initialSlots > 0) slots.put("1", initialSlots);
                } else if (existingSlots.isObject()) {
                    slots = (ObjectNode) existingSlots;
                } else {
                    throw new IllegalArgumentException("spellSlots must be an object");
                }
                String levelKey = Integer.toString(mutation.consumeSpellSlotLevel());
                JsonNode available = slots.get(levelKey);
                if (available == null || !available.isIntegralNumber() || available.intValue() < 1) {
                    throw new IllegalArgumentException("주문 슬롯이 부족합니다.");
                }
                slots.put(levelKey, available.intValue() - 1);
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

    private static int hitPointsAfterMutation(ObjectNode state, int delta, Integer fallback, Integer maximum) {
        JsonNode value = state.get("currentHitPoints");
        if (value == null) {
            if (fallback == null) throw new IllegalArgumentException("currentHitPoints is required");
            value = JSON.getNodeFactory().numberNode(fallback);
        }
        if (!value.isIntegralNumber()) throw new IllegalArgumentException("currentHitPoints must be an integer");
        long next = Math.max(0L, value.longValue() + (long) delta);
        if (next > Integer.MAX_VALUE) throw new IllegalArgumentException("currentHitPoints is out of range");
        if (maximum != null && next > maximum) next = maximum;
        return (int) next;
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
        if (!sessionPolicyPort.policyFor(sheet.adventureId(), sheet.id()).sessionActive()) {
            throw new IllegalStateException("character sheet belongs to a terminated adventure session");
        }
    }

    private CharacterSheet load(CharacterSheetId id) {
        return repository.findById(Objects.requireNonNull(id, "character sheet id must not be null"))
                .orElseThrow(CharacterSheetNotFoundException::new);
    }
}
