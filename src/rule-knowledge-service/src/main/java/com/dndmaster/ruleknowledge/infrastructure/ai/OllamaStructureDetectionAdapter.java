package com.dndmaster.ruleknowledge.infrastructure.ai;

import com.dndmaster.ruleknowledge.application.indexing.StructureDetectionPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class OllamaStructureDetectionAdapter implements StructureDetectionPort {

    private static final Logger log = Logger.getLogger(OllamaStructureDetectionAdapter.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int SAMPLE_SIZE = 1000;

    private static final Map<String, PatternMatch> PATTERN_REGISTRY = new HashMap<>();

    static {
        PATTERN_REGISTRY.put("korean_chapter",
                new PatternMatch("^제\\d+장\\s*:\\s*(.+)$", "CHAPTER", "Korean chapter heading (제N장: title)"));
        PATTERN_REGISTRY.put("korean_appendix",
                new PatternMatch("^부록\\s+[A-Z]\\s*:\\s*(.+)$", "APPENDIX", "Korean appendix (부록 A: title)"));
        PATTERN_REGISTRY.put("korean_part",
                new PatternMatch("^제\\d+부\\s*:\\s*(.+)$", "PART", "Korean part heading (제N부: title)"));
        PATTERN_REGISTRY.put("korean_section",
                new PatternMatch("^제\\d+절\\s*:\\s*(.+)$", "SECTION", "Korean section heading (제N절: title)"));
        PATTERN_REGISTRY.put("numbered_section",
                new PatternMatch("^\\d+\\.\\s+(.+)$", "SECTION", "Numbered section (N. title)"));
        PATTERN_REGISTRY.put("hash_heading_h1",
                new PatternMatch("^#\\s+(.+)$", "CHAPTER", "Markdown H1 heading"));
        PATTERN_REGISTRY.put("hash_heading_h2",
                new PatternMatch("^##\\s+(.+)$", "SECTION", "Markdown H2 heading"));
        PATTERN_REGISTRY.put("hash_heading_h3",
                new PatternMatch("^###\\s+(.+)$", "SUB_HEADING", "Markdown H3 heading"));
        PATTERN_REGISTRY.put("uppercase_heading",
                new PatternMatch("^([A-Z][A-Z\\s]{3,})$", "SECTION", "ALL-CAPS heading line"));
    }

    private final HttpClient httpClient;
    private final String ollamaBaseUrl;
    private final String chatModel;
    private final Duration requestTimeout;

    public OllamaStructureDetectionAdapter(
            String ollamaBaseUrl,
            String chatModel,
            Duration requestTimeout) {
        this.ollamaBaseUrl = Objects.requireNonNull(ollamaBaseUrl, "ollamaBaseUrl must not be null");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel must not be null");
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public DetectedStructure detect(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return DetectedStructure.none();
        }

        String beginning = extractSample(fullText, 0);
        String middle = extractSample(fullText, fullText.length() / 2);
        String end = extractSample(fullText, Math.max(0, fullText.length() - SAMPLE_SIZE));

        String prompt = buildPrompt(beginning, middle, end);

        try {
            String response = callOllama(prompt);
            return parseResponse(response);
        } catch (Exception e) {
            log.log(Level.WARNING, "structure detection failed, falling back to no structure", e);
            return DetectedStructure.none();
        }
    }

    private String extractSample(String text, int offset) {
        int start = Math.min(offset, text.length());
        int end = Math.min(start + SAMPLE_SIZE, text.length());
        return text.substring(start, end);
    }

    private String buildPrompt(String beginning, String middle, String end) {
        return """
                Analyze these three samples from a document and classify its heading structure.

                Choose from these known pattern types:
                - korean_chapter: lines like "제1장: 캐릭터 제작 순서" (제N장: title)
                - korean_appendix: lines like "부록 A: 플레이어용 정보" (부록 letter: title)
                - korean_part: lines like "제1부: 캐릭터 제작" (제N부: title)
                - korean_section: lines like "제1절: 시작" (제N절: title)
                - numbered_section: lines like "1. Initiative" or "1. 이니셔티브" (N. title at line start)
                - hash_heading_h1: lines like "# Title"
                - hash_heading_h2: lines like "## Subtitle"
                - hash_heading_h3: lines like "### Sub-subtitle"
                - uppercase_heading: lines in ALL CAPS like "COMBAT"

                Also identify the document language (ko, en, or mixed).

                --- BEGINNING ---
                """ + beginning + """

                --- MIDDLE ---
                """ + middle + """

                --- END ---
                """ + end + """

                Return ONLY JSON, nothing else:
                {"language": "ko|en|mixed", "patterns": ["type1", "type2"], "description": "brief explanation"}
                """;
    }

    private String callOllama(String prompt) throws Exception {
        String requestBody = mapper.writeValueAsString(new OllamaRequest(
                chatModel,
                prompt,
                false,
                false,
                new OllamaOptions(256)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                .header("Content-Type", "application/json")
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new StructureDetectionException(
                    "ollama returned status " + response.statusCode() + ": " + response.body());
        }

        JsonNode responseJson = mapper.readTree(response.body());
        String content = responseJson.path("response").asText("");
        if (content.isBlank()) {
            throw new StructureDetectionException("ollama returned empty response");
        }
        return content;
    }

    private DetectedStructure parseResponse(String response) {
        try {
            String json = extractJson(response);
            JsonNode root = mapper.readTree(json);

            String description = root.path("description").asText("detected structure");
            JsonNode patternsNode = root.path("patterns");

            List<PatternMatch> patterns = new ArrayList<>();
            if (patternsNode.isArray()) {
                for (JsonNode p : patternsNode) {
                    String type = p.asText("");
                    PatternMatch registered = PATTERN_REGISTRY.get(type);
                    if (registered != null) {
                        patterns.add(registered);
                    } else {
                        log.info("unknown pattern type from LLM: " + type);
                    }
                }
            }

            return new DetectedStructure(patterns, description);
        } catch (Exception e) {
            log.log(Level.WARNING, "failed to parse structure detection response", e);
            return DetectedStructure.none();
        }
    }

    private String extractJson(String text) {
        String trimmed = text.strip();
        int jsonStart = trimmed.indexOf('{');
        int jsonEnd = trimmed.lastIndexOf('}');
        if (jsonStart >= 0 && jsonEnd > jsonStart) {
            return trimmed.substring(jsonStart, jsonEnd + 1);
        }
        return trimmed;
    }

    private record OllamaOptions(int num_predict) {}
    private record OllamaRequest(String model, String prompt, boolean stream, boolean think, OllamaOptions options) {}
}
