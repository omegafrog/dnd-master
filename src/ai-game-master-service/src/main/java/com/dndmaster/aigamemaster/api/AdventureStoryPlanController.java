package com.dndmaster.aigamemaster.api;

import com.dndmaster.aigamemaster.infrastructure.ai.GmCompletionAdapter;
import com.dndmaster.aigamemaster.infrastructure.ai.GmProviderRequest;
import com.dndmaster.aigamemaster.infrastructure.ai.DeadlineBudget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Generates a source-aware outline. JSON is validated before crossing the service boundary. */
@RestController("aiAdventureStoryPlanController")
public final class AdventureStoryPlanController {
    private final GmCompletionAdapter adapter; private final ObjectMapper mapper;
    public AdventureStoryPlanController(GmCompletionAdapter adapter, ObjectMapper mapper) { this.adapter = adapter; this.mapper = mapper; }
    @PostMapping("/internal/v1/gm/adventure-story-plan")
    Response generate(@RequestBody Request request) {
        String prompt = "Create a tabletop adventure outline grounded only in the supplied source documents and evidence. "
                + "Return JSON object only: {stages:[{position,title,goal,conflict,transitionCondition,npcOrClues:[string],endingIds:[string]}]}. "
                + "Create 4-6 stages. Create at least 2 distinct endingIds across the plan. Every ending must be reachable from a stage. "
                + "Do not invent named rules, DCs, monsters, or facts absent from evidence. Documents=" + request.sourceDocuments()
                + " Evidence=" + request.resolutionEvidence() + " partySize=" + request.partySize();
        if (request.provider() == null || request.provider().isBlank()) {
            return new Response(adapter.complete(request.operationId(), prompt, this::parse));
        }
            return new Response(adapter.complete(request.operationId(), prompt, this::parse,
                new GmProviderRequest(request.provider(), request.model(), request.reasoning()),
                DeadlineBudget.start(java.time.Duration.ofSeconds(1800), java.time.Duration.ofSeconds(30))));
    }
    private List<Stage> parse(String text) {
        try {
            JsonNode root = mapper.readTree(extractObject(text)); JsonNode stages = root.get("stages");
            if (stages == null || !stages.isArray()) throw new IllegalArgumentException("stages missing");
            List<Stage> result = new ArrayList<>();
            for (JsonNode n : stages) {
                List<String> endings = strings(n.get("endingIds")); if (endings.isEmpty()) throw new IllegalArgumentException("endingIds missing");
                result.add(new Stage(n.path("position").asInt(result.size() + 1), required(n,"title"), required(n,"goal"), required(n,"conflict"), required(n,"transitionCondition"), strings(n.get("npcOrClues")), endings));
            }
            if (result.size() < 2 || result.stream().flatMap(s -> s.endingIds().stream()).distinct().count() < 2) throw new IllegalArgumentException("multiple endings required");
            return List.copyOf(result);
        } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "AI story plan response malformed", e); }
    }
    private static String extractObject(String text) { int a = text.indexOf('{'), b = text.lastIndexOf('}'); if (a < 0 || b < a) throw new IllegalArgumentException("JSON object missing"); return text.substring(a,b+1); }
    private static String required(JsonNode n, String key) { String v = n.path(key).asText("").trim(); if (v.isBlank()) throw new IllegalArgumentException(key + " missing"); return v; }
    private static List<String> strings(JsonNode n) { if (n == null || !n.isArray()) return List.of(); List<String> r = new ArrayList<>(); n.forEach(v -> { if (v.isTextual() && !v.asText().isBlank()) r.add(v.asText()); }); return List.copyOf(r); }
    public record Request(String operationId, long packageRevision, int partySize, List<String> sourceDocuments,
                          List<String> resolutionEvidence, String provider, String model, String reasoning) {}
    public record Response(List<Stage> stages) {}
    public record Stage(int position, String title, String goal, String conflict, String transitionCondition, List<String> npcOrClues, List<String> endingIds) {}
}
