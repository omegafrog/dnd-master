package com.dndmaster.gmeval.infrastructure;

import com.dndmaster.gmeval.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;

/** Maps an AI GM provider's JSON structured output to the domain judge contract. */
public final class AiGmRubricJudgeAdapter implements RubricJudgePort {
    private final StructuredOutputCompletionPort completion;
    private final ObjectMapper mapper;

    public AiGmRubricJudgeAdapter(StructuredOutputCompletionPort completion) {
        this(completion, new ObjectMapper());
    }

    public AiGmRubricJudgeAdapter(StructuredOutputCompletionPort completion, ObjectMapper mapper) {
        if (completion == null || mapper == null) throw new IllegalArgumentException("completion and mapper required");
        this.completion = completion;
        this.mapper = mapper;
    }

    @Override public RubricJudgeResponse judge(RubricJudgeRequest request) {
        try {
            JsonNode root = mapper.readTree(completion.complete(prompt(request)));
            JsonNode scores = root == null ? null : root.get("scores");
            if (scores == null || !scores.isArray()) throw new IllegalArgumentException("structured judge scores required");
            List<QualityScore> result = new ArrayList<>();
            for (JsonNode score : scores) {
                result.add(new QualityScore(score.path("dimension").asText(null), score.path("score").asInt(Integer.MIN_VALUE),
                        score.path("reason").asText(null), score.path("evidence").asText(null)));
            }
            return new RubricJudgeResponse(result);
        } catch (Exception failure) {
            if (failure instanceof IllegalArgumentException illegal) throw illegal;
            throw new IllegalArgumentException("malformed structured judge response", failure);
        }
    }

    private String prompt(RubricJudgeRequest request) {
        StringBuilder prompt = new StringBuilder("Return JSON only with scores [{dimension,score,reason,evidence}].\n");
        prompt.append("Case: ").append(request.evalCase().caseId()).append("\nResponse:\n").append(request.response()).append("\nRubrics:\n");
        for (QualityRubric rubric : request.rubrics()) prompt.append(rubric.dimension()).append(" anchors=").append(rubric.anchors()).append('\n');
        return prompt.toString();
    }
}
