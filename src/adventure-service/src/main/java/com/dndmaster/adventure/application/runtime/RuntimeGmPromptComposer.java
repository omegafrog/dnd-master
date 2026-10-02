package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Map;

/** Keeps the provider instructions ahead of all turn-specific material. */
public final class RuntimeGmPromptComposer {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final String RULE_MARKER = "\nLOOKUP_ORDER_RULE=";
    private static final String SUMMARY_PREFIX = "압축된 이전 대화: ";

    private RuntimeGmPromptComposer() { }

    public static String compose(GmContextEnvelope envelope, int contextLimit) {
        java.util.Map<String, Object> runtimeContext = new java.util.LinkedHashMap<>();
        runtimeContext.put("currentContext", envelope.currentContext());
        runtimeContext.put("scenarioContext", envelope.scenarioContext());
        runtimeContext.put("currentSituation", envelope.currentSituation());
        runtimeContext.put("runtimeFacts", envelope.runtimeFacts());
        runtimeContext.put("recentTurns", envelope.recentTurns());
        runtimeContext.put("characterSnapshots", envelope.characterSnapshots());
        runtimeContext.put("narrativeContext", envelope.narrativeContext());
        java.util.List<java.util.Map<String, Object>> factLookupResults = new java.util.ArrayList<>();
        envelope.factLookupResults().forEach(lookup -> {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("source", lookup.source().name());
            result.put("status", lookup.status().name());
            result.put("answer", lookup.answer());
            result.put("supportingElementIds", lookup.supportingElementIds());
            result.put("evidence", lookup.evidence());
            factLookupResults.add(result);
        });
        envelope.evidencePack().all().forEach(evidence -> {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("source", evidence.evidenceType().name() + "_RAG");
            result.put("answer", evidence.excerpt());
            result.put("locator", evidence.locator());
            result.put("citationKey", evidence.referenceKey());
            factLookupResults.add(result);
        });
        String action = envelope.action();
        String validationFeedback = envelope.validationFeedback().isBlank() ? ""
                : "\nVALIDATION_FEEDBACK=" + envelope.validationFeedback()
                    + "\nVALIDATION_RETRY_RULE=이 내용은 최종 계획 검증기의 내부 지적이며 플레이어 행동의 일부가 아닙니다. 같은 플레이어 행동에 대한 계획을 다시 생성하고, 열거된 문제를 모두 수정하세요. 이 지적을 플레이어에게 언급하지 마세요.";
        String existing =
                        "ROLE=RUNTIME_GM" + validationFeedback + "\nCOMPOSITE_FACT_LOOKUP_RESULTS=" + write(factLookupResults)
                        + "\nRUNTIME_CONTEXT=" + write(runtimeContext)
                        + "\nACTION=" + action
                        + "\nLOOKUP_ORDER_RULE=Use authoritative results in this order: Game State, established Runtime-added Facts, locked Scenario Model, then retrieved passages from either rules collection. RULEBOOK and STORYBOOK are both rule documents and use the same turn query. If all are NOT_FOUND, create only the minimum Runtime Fact needed to keep this turn playable. Do not use a lower-priority answer to contradict a higher-priority result."
                        + "\nGROUNDING_RULES=Use LOCKED_SCENARIO_MODEL for canonical adventure truth. RULEBOOK and STORYBOOK are both rules sources and cannot establish that a creature or event exists in this scene. RUNTIME_ADDED_FACTS contains facts already established in this adventure, including confirmed combat outcomes; do not reactivate a defeated enemy without a supported later event. A new NPC reaction, opinion, refusal, negotiation, or compatible offer may be improvised only when it does not contradict established truth."
                        + "\nMAP_GROUNDING_RULE=Use LOCKED_SCENARIO_AND_MAP_DATA tacticalMaps and mapSceneBindings to describe visible physical layout and decide whether a requested movement connects to the destination. A scene-name change alone does not require a Storybook citation. Do not invent passages or connections absent from the map data; if the map does not establish a route, keep the current position and ask for clarification or narrate only an observable attempt."
                        + "\nEVIDENCE_REQUIREMENT_RULE=RULEBOOK and STORYBOOK are both rule-document collections and are searched with the same turn query. Their passages have equal standing for rules claims; the source label records provenance only. Use LOCKED_SCENARIO_AND_MAP_DATA for established scenario truth. Cite a rules passage when it supports the claim. Each evidence item in COMPOSITE_FACT_LOOKUP_RESULTS has a citationKey; choose only keys that support this response."
                        + "\nDIALOGUE_RULE=ACTION is an executed dialogue action, not a suggestion. If ACTION directly addresses an NPC, generate the NPC reaction in the current response. Do not tell the player to ask the same question again, and do not turn a completed question into an instruction. If a canonical answer is unavailable, respond diegetically through the NPC's ignorance, refusal, evasion, or negotiation; never say that the scenario lacks the information."
                        + "\nANTI_LOOP_RULE=Do not repeat the same dialogue action as a next choice or recommendation. Use a follow-up action such as negotiating a different condition, accepting or refusing an offer, asking a new question, or observing the surroundings."
                        + "\nADVENTURE_COMPLETION_RULE=Distinguish ending a fight from completing the adventure. Complete only when every required objective and resolution condition in LOCKED_SCENARIO_MODEL is satisfied by confirmed prior runtime facts or this turn's result. A defeated enemy alone does not complete an objective that also requires investigation, preventing recurrence, or reporting to an NPC. List in resolvedObjectiveIds and satisfiedResolutionCriteriaIds only the compiled items already satisfied by confirmed play; unresolved items must be omitted. If any required item remains, complete=false and concludingScene is empty. When all are satisfied, set complete=true, identify every objective and condition by its exact elementId, and provide a concise Korean concludingScene that closes the current mission using established consequences. Never claim an objective is resolved from player intent alone. If no compiled objectives or resolution conditions exist, do not complete automatically. The conclusion is proposed for backend validation and is committed only with this turn. "
                        + "\nCHECK_DECISION_RULE=For every player action or requested move, judge check need from the situation, map, character sheets, and retrieved rules; do not use a word list. When needed, ground ability/skill, character, roller, dice, modifier, difficulty, and both outcomes in the supplied data and cited rules. Use PLAYER for player actions and SYSTEM only for events outside player control. If none is needed, return 판정제안 {필요:false}."
                        + "\nOUTPUT_CONTRACT=Return exactly one JSON object with scene, judgment, narration, situation, combatStart, combatEnemies, mapEntryRequested, citedEvidence, completion, 판정제안, and optional runtimeFacts. citedEvidence must be an array of exact citationKey strings copied from COMPOSITE_FACT_LOOKUP_RESULTS; include every selected rules item used to support a rule claim, otherwise return an empty array. 판정제안 must always exist. It must contain 필요 (boolean). When false, return no other check details. When true, include 이유, 판정능력또는기술, 대상캐릭터ID, 굴림주체 (플레이어 or 시스템), 굴림식, 보정치, 난이도, 근거키 (exact citationKey values), 성공시결과, and 실패시결과. 굴림식은 인용 근거가 요구하는 주사위식 그대로 적으세요 (예: 1d20, 1d4, 2d6). 1d20으로 고정하지 말고, 고정 보정치는 보정치 필드에 별도로 적으세요. Use complete compiled objective elementIds in completion, and Korean concludingScene only when complete. "
                        + "situation must contain kind (CONTINUE or TRANSITION), location, problem, threat, goal, basis (SCENARIO, RAG, or FALLBACK), reference, and required. "
                        + "Choose the situation basis in this order: an applicable ScenarioModel element; otherwise FALLBACK only when a new fact is necessary to keep play moving, with required=true. Rules passages do not establish canonical scenario truth. "
                        + "For SCENARIO, reference is a ScenarioModel element id. For RAG, reference is a citationKey or locator from COMPOSITE_FACT_LOOKUP_RESULTS. For FALLBACK, reference is empty. "
                        + "The next GM turn receives this saved situation, so make it concrete and playable. The player's action can express a choice to fight, but cannot prove that an enemy exists; confirm the enemy from the saved situation or ScenarioModel. Use either rules source only to retrieve its combat numbers. "
                        + "MANDATORY: if a hostile creature already supported by the saved situation is attacking, has cornered the party, or the player is exchanging attacks with it, return combatStart=true and a SITUATION enemy entry in the same response. "
                        + "MANDATORY: when the player explicitly chooses to start or join a fight against a hostile supported by the saved situation or a matching ScenarioModel combat-scenario, return combatStart=true and the matching structured enemy in the same response. Respect a clear refusal to fight. "
                        + "Do not narrate a supported hostile creature attacking, closing in to attack, or 'combat ready' while returning combatStart=false. This is an output validity rule, not a discretionary pacing choice. "
                        + "mapEntryRequested must be a boolean. Set it to true only when the committed situation places the party inside the prepared map area and the player should see that map now; set it to false while the party is still outside, approaching, or when no prepared map applies. Base this on the saved situation and scenario context, not on keyword matching. If ACTION together with the generated narration completes movement through an entrance or other transition into the destination area, set mapEntryRequested=true even when the scene label still contains the previous area; the completed transition and destination situation are the evidence. Do not decide this from a single word or a fixed list of words. "
                        + "runtimeFacts is optional. Include it only for a newly established playthrough fact created by this turn's compatible NPC reaction, refusal, offer, or negotiation after all authoritative lookup results are NOT_FOUND. Each item must contain subject and content. Never use runtimeFacts for a culprit, secret route, cause, hidden clue, puzzle answer, or other canonical scenario truth. "
                        + "When ACTION is SESSION_OPENING, LANGUAGE_CONTRACT requires all player-visible text in scene, judgment, narration, and situation to be written only in natural Korean. Do not output English or any other foreign-language words, labels, headings, or meta-commentary. Translate common nouns, class names, location names, action prompts, and proper names into Korean. Make the first player-facing narration establish the current location and why the party is here, state the immediate problem or pressure, identify a few observable things the party can respond to, and end with a Korean question inviting the player's action, such as '어떻게 하시겠어요?'. Use only LOCKED_SCENARIO_MODEL, RUNTIME_CONTEXT, and COMPOSITE_FACT_LOOKUP_RESULTS; never reveal a puzzle answer or hidden fact. "
                        + "A player action or rules passage is not evidence that an entity exists in the current scene. SCENARIO enemies must use an id present in the ScenarioModel in LOCKED_SCENARIO_MODEL; SITUATION and INSTANT enemies must be named by saved CURRENT_SITUATION. Never require a precompiled id when the situation itself supports the enemy. "
                        + "combatEnemies must always be an array of objects with mode (SCENARIO, SITUATION, or INSTANT), scenarioId, enemyKey, name, and positive count; "
                        + "SCENARIO requires a scenarioId from the current ScenarioModel. SITUATION leaves scenarioId empty and requires matching storybook RAG evidence for the current situation. INSTANT leaves scenarioId empty and is reserved for a GM-forced consequence such as noise or a critical failure. "
                        + "Use [] when combatStart is false. Never invent an enemy from the action alone. "
                        + "Do not use markdown, code fences, or any other text.";
        return compose(existing, envelope.recentTurns(), envelope.characterSnapshots(), runtimeContext, envelope.longTermFacts(), contextLimit);
    }

    private static String write(Object value) {
        try { return MAPPER.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("runtime GM context cannot be serialized", exception);
        }
    }

    public static String compose(String existingPrompt, List<String> recentTurns,
            List<String> characterSheets, Map<String, Object> runtimeContext) {
        return compose(existingPrompt, recentTurns, characterSheets, runtimeContext, 128000);
    }

    public static String compose(String existingPrompt, List<String> recentTurns,
            List<String> characterSheets, Map<String, Object> runtimeContext, int contextLimit) {
        return compose(existingPrompt, recentTurns, characterSheets, runtimeContext, List.of(), contextLimit);
    }

    public static String compose(String existingPrompt, List<String> recentTurns,
            List<String> characterSheets, Map<String, Object> runtimeContext,
            List<LongTermAdventureFact> longTermFacts, int contextLimit) {
        int rules = existingPrompt.indexOf(RULE_MARKER);
        if (rules < 0) throw new IllegalArgumentException("runtime GM rules are missing");
        int action = existingPrompt.indexOf("\nACTION=");
        if (action < 0 || action > rules) throw new IllegalArgumentException("runtime GM action is missing");
        int evidence = existingPrompt.indexOf("\nCOMPOSITE_FACT_LOOKUP_RESULTS=");
        if (evidence < 0 || evidence > action) throw new IllegalArgumentException("runtime GM evidence is missing");
        int runtime = existingPrompt.indexOf("\nRUNTIME_CONTEXT=", evidence);
        if (runtime < 0 || runtime > action) throw new IllegalArgumentException("runtime GM context is missing");
        String fixed = existingPrompt.substring(0, evidence) + existingPrompt.substring(rules);
        String actionText = existingPrompt.substring(action, rules);
        String evidenceText = existingPrompt.substring(evidence, runtime);
        Map<String, Object> current = new java.util.LinkedHashMap<>(runtimeContext);
        current.remove("recentTurns");
        current.remove("characterSnapshots");
        current.remove("scenarioContext");
        String currentJson;
        try {
            currentJson = MAPPER.writeValueAsString(current);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("runtime GM context cannot be serialized", exception);
        }
        String lockedScenario = String.valueOf(runtimeContext.getOrDefault("scenarioContext", ""));
        String fixedZone = "고정 지침·잠긴 자료\n" + fixed + "\nLOCKED_SCENARIO_AND_MAP_DATA=" + lockedScenario;
        String memoryZone = "\n\n현재 상황 관련 장기 기록 (GM이 문맥으로 판단)\n" + selectedLongTermFacts(longTermFacts,
                RuntimeGmInputBudget.inputLimit(contextLimit) * 10 / 100);
        List<String> compressedHistory = new java.util.ArrayList<>();
        List<String> uncompressedRecent = new java.util.ArrayList<>();
        for (String turn : recentTurns) {
            if (turn.startsWith(SUMMARY_PREFIX)) compressedHistory.add(turn.substring(SUMMARY_PREFIX.length()));
            else uncompressedRecent.add(turn);
        }
        String summaryZone = "\n\n압축된 이전 대화\n" + String.join("\n", compressedHistory);
        String recentHeading = "\n\n압축하지 않은 최근 대화\n";
        String currentZone = "\n\n최신 캐릭터 시트·Current Situation·이번 턴 근거·플레이어 입력\n"
                + "CHARACTER_SHEETS=" + characterSheets
                + "\nRUNTIME_CONTEXT=" + currentJson + evidenceText + actionText;
        List<String> selected = RuntimeGmInputBudget.selectRecent(contextLimit, fixedZone,
                memoryZone, summaryZone, uncompressedRecent, currentZone + recentHeading);
        String prompt = fixedZone + memoryZone + summaryZone + recentHeading + selected + currentZone;
        if (prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > RuntimeGmInputBudget.inputLimit(contextLimit)) {
            throw new RuntimeGmInputBudget.InputTooLargeException();
        }
        return prompt;
    }

    private static String selectedLongTermFacts(List<LongTermAdventureFact> facts, int byteLimit) {
        java.util.List<LongTermAdventureFact> candidates = facts.stream()
                .filter(LongTermAdventureFact::playerVisible)
                .sorted(java.util.Comparator.comparingLong(LongTermAdventureFact::version).reversed()
                        .thenComparing(LongTermAdventureFact::factId))
                .toList();
        java.util.List<String> selected = new java.util.ArrayList<>();
        int used = 0;
        for (LongTermAdventureFact fact : candidates) {
            String rendered = fact.kind() + ": " + fact.relevance();
            int size = rendered.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + (selected.isEmpty() ? 0 : 1);
            if (used + size > byteLimit) continue;
            selected.add(rendered);
            used += size;
        }
        if (!candidates.isEmpty() && selected.isEmpty()) throw new RuntimeGmInputBudget.InputTooLargeException();
        return String.join("\n", selected);
    }
}
