package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Map;

/** Keeps the provider instructions ahead of all turn-specific material. */
public final class RuntimeGmPromptComposer {
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final String RULE_MARKER = "\nLOOKUP_ORDER_RULE=";

    private RuntimeGmPromptComposer() { }

    public static String compose(GmContextEnvelope envelope, int contextLimit) {
        java.util.Map<String, Object> runtimeContext = new java.util.LinkedHashMap<>();
        runtimeContext.put("currentContext", envelope.currentContext());
        runtimeContext.put("scenarioContext", envelope.scenarioContext());
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
        envelope.evidencePack().storybook().forEach(evidence -> {
            java.util.Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("source", "STORYBOOK_RAG");
            result.put("answer", evidence.excerpt());
            result.put("locator", evidence.locator());
            if (evidence.citationKey() != null) result.put("citationKey", evidence.citationKey());
            factLookupResults.add(result);
        });
        String action = envelope.action();
        String existing =
                        "ROLE=RUNTIME_GM\nCOMPOSITE_FACT_LOOKUP_RESULTS=" + write(factLookupResults)
                        + "\nRUNTIME_CONTEXT=" + write(runtimeContext)
                        + "\nACTION=" + action
                        + "\nLOOKUP_ORDER_RULE=Use authoritative results in this order: Game State, established Runtime-added Facts, locked Scenario Model, then Storybook RAG. If all are NOT_FOUND, create only the minimum Runtime Fact needed to keep this turn playable. Do not use a lower-priority answer to contradict a higher-priority result."
                        + "\nGROUNDING_RULES=Distinguish canonical Storybook truth from established adventure facts. Create or reveal canonical truths such as a culprit, secret route, cause, hidden clue, or encounter structure only when the locked ScenarioModel or selected evidence supports them. RUNTIME_ADDED_FACTS contains durable facts already established in this adventure, including confirmed combat outcomes; treat them as authoritative and never narrate a defeated enemy as active again unless a later supported event explains it. A new play-created NPC reaction, opinion, refusal, negotiation, or compatible offer may be improvised only when it does not contradict canonical truth."
                        + "\nDIALOGUE_RULE=ACTION is an executed dialogue action, not a suggestion. If ACTION directly addresses an NPC, generate the NPC reaction in the current response. Do not tell the player to ask the same question again, and do not turn a completed question into an instruction. If a canonical answer is unavailable, respond diegetically through the NPC's ignorance, refusal, evasion, or negotiation; never say that the scenario lacks the information."
                        + "\nANTI_LOOP_RULE=Do not repeat the same dialogue action as a next choice or recommendation. Use a follow-up action such as negotiating a different condition, accepting or refusing an offer, asking a new question, or observing the surroundings."
                        + "\nOUTPUT_CONTRACT=Return exactly one JSON object with scene, judgment, narration, situation, combatStart, combatEnemies, mapEntryRequested, and optional runtimeFacts. "
                        + "situation must contain kind (CONTINUE or TRANSITION), location, problem, threat, goal, basis (SCENARIO, RAG, or FALLBACK), reference, and required. "
                        + "Choose the situation basis in this order: an applicable ScenarioModel element; otherwise a matching storybook RAG citation; otherwise FALLBACK only when a new fact is necessary to keep play moving, with required=true. "
                        + "For SCENARIO, reference is a ScenarioModel element id. For RAG, reference is a citationKey or locator from COMPOSITE_FACT_LOOKUP_RESULTS. For FALLBACK, reference is empty. "
                        + "The next GM turn receives this saved situation, so make it concrete and playable. The player's action can express a choice to fight, but cannot prove that an enemy exists; confirm the enemy from the saved situation, ScenarioModel, or Storybook evidence. "
                        + "MANDATORY: if a hostile creature already supported by the saved situation is attacking, has cornered the party, or the player is exchanging attacks with it, return combatStart=true and a SITUATION enemy entry in the same response. "
                        + "MANDATORY: when the player explicitly chooses to start or join a fight against a hostile supported by the saved situation or a matching ScenarioModel combat-scenario, return combatStart=true and the matching structured enemy in the same response. Respect a clear refusal to fight. "
                        + "Do not narrate a supported hostile creature attacking, closing in to attack, or 'combat ready' while returning combatStart=false. This is an output validity rule, not a discretionary pacing choice. "
                        + "mapEntryRequested must be a boolean. Set it to true only when the committed situation places the party inside the prepared map area and the player should see that map now; set it to false while the party is still outside, approaching, or when no prepared map applies. Base this on the saved situation and scenario context, not on keyword matching. If ACTION together with the generated narration completes movement through an entrance or other transition into the destination area, set mapEntryRequested=true even when the scene label still contains the previous area; the completed transition and destination situation are the evidence. Do not decide this from a single word or a fixed list of words. "
                        + "runtimeFacts is optional. Include it only for a newly established playthrough fact created by this turn's compatible NPC reaction, refusal, offer, or negotiation after all authoritative lookup results are NOT_FOUND. Each item must contain subject and content. Never use runtimeFacts for a culprit, secret route, cause, hidden clue, puzzle answer, or other canonical scenario truth. "
                        + "When ACTION is SESSION_OPENING, LANGUAGE_CONTRACT requires all player-visible text in scene, judgment, narration, and situation to be written only in natural Korean. Do not output English or any other foreign-language words, labels, headings, or meta-commentary. Translate common nouns, class names, location names, action prompts, and proper names into Korean. Make the first player-facing narration establish the current location and why the party is here, state the immediate problem or pressure, identify a few observable things the party can respond to, and end with a Korean question inviting the player's action, such as '어떻게 하시겠어요?'. Use only RUNTIME_CONTEXT and COMPOSITE_FACT_LOOKUP_RESULTS; never reveal a puzzle answer or hidden fact. "
                        + "A player action is not evidence that an entity exists. SCENARIO enemies must use an id present in the ScenarioModel in RUNTIME_CONTEXT; SITUATION enemies may be grounded by saved CURRENT_SITUATION or matching Storybook evidence in COMPOSITE_FACT_LOOKUP_RESULTS. Never require a precompiled id when the situation itself supports the enemy. "
                        + "combatEnemies must always be an array of objects with mode (SCENARIO, SITUATION, or INSTANT), scenarioId, enemyKey, name, and positive count; "
                        + "SCENARIO requires a scenarioId from the current ScenarioModel. SITUATION leaves scenarioId empty and requires matching storybook RAG evidence for the current situation. INSTANT leaves scenarioId empty and is reserved for a GM-forced consequence such as noise or a critical failure. "
                        + "Use [] when combatStart is false. Never invent an enemy from the action alone. "
                        + "Do not use markdown, code fences, or any other text.";
        return compose(existing, envelope.recentTurns(), envelope.characterSnapshots(), runtimeContext, contextLimit);
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
        String currentJson;
        try {
            currentJson = MAPPER.writeValueAsString(current);
        } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalArgumentException("runtime GM context cannot be serialized", exception);
        }
        String fixedZone = "고정 지침·잠긴 자료\n" + fixed;
        String memoryZone = "\n\n현재 상황 관련 장기 기록\n";
        String summaryZone = "\n\n압축된 이전 대화\n";
        String recentHeading = "\n\n압축하지 않은 최근 대화\n";
        String currentZone = "\n\n최신 캐릭터 시트·Current Situation·이번 턴 근거·플레이어 입력\n"
                + "CHARACTER_SHEETS=" + characterSheets
                + "\nRUNTIME_CONTEXT=" + currentJson + evidenceText + actionText;
        List<String> selected = RuntimeGmInputBudget.selectRecent(contextLimit, fixedZone,
                memoryZone, summaryZone, recentTurns, currentZone + recentHeading);
        String prompt = fixedZone + memoryZone + summaryZone + recentHeading + selected + currentZone;
        if (prompt.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > RuntimeGmInputBudget.inputLimit(contextLimit)) {
            throw new RuntimeGmInputBudget.InputTooLargeException();
        }
        return prompt;
    }
}
