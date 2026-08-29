package com.dndmaster.gmeval.infrastructure;
import com.dndmaster.gmeval.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
/** Provider-neutral parser for the canonical pairwise structured output. */
public final class AiGmPairwiseJudgeAdapter implements PairwiseJudgePort {
    private final StructuredOutputCompletionPort completion; private final ObjectMapper mapper;
    public AiGmPairwiseJudgeAdapter(StructuredOutputCompletionPort completion) { this(completion, new ObjectMapper()); }
    public AiGmPairwiseJudgeAdapter(StructuredOutputCompletionPort completion, ObjectMapper mapper) { if (completion == null || mapper == null) throw new IllegalArgumentException("completion and mapper required"); this.completion = completion; this.mapper = mapper; }
    @Override public PairwiseJudgeResponse compare(PairwiseJudgeRequest request) {
        try { JsonNode root = mapper.readTree(completion.complete(prompt(request))); if (root == null || !root.isObject()) throw new IllegalArgumentException("structured pairwise response required");
            PairwiseWinner winner; try { winner = PairwiseWinner.valueOf(root.path("winner").asText()); } catch (Exception e) { throw new IllegalArgumentException("invalid pairwise winner"); }
            JsonNode nodes = root.get("preferences"); if (nodes == null || !nodes.isArray()) throw new IllegalArgumentException("pairwise preferences required");
            List<PairwiseDimensionPreference> prefs = new ArrayList<>(); for (JsonNode n : nodes) { PairwisePreference p; try { p = PairwisePreference.valueOf(n.path("preference").asText()); } catch (Exception e) { throw new IllegalArgumentException("invalid pairwise preference"); } prefs.add(new PairwiseDimensionPreference(n.path("dimension").asText(null), p, n.path("reason").asText(null), n.path("evidence").asText(null))); }
            return PairwiseJudgeResponseValidator.validate(request.rubrics(), new PairwiseJudgeResponse(winner, prefs, root.path("reason").asText(null), root.path("evidence").asText(null)));
        } catch (Exception e) { if (e instanceof IllegalArgumentException x) throw x; throw new IllegalArgumentException("malformed structured pairwise response", e); }
    }
    private String prompt(PairwiseJudgeRequest r) { return "Return JSON only with winner A|B|TIE, reason, evidence, preferences [{dimension,preference,reason,evidence}].\nCase: " + r.evalCase().caseId() + "\nA:\n" + r.responseA() + "\nB:\n" + r.responseB() + "\nRubrics: " + r.rubrics(); }
}
