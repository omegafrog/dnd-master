package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.evidence.EvidenceAcquisitionContractException;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionTransientException;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceSufficiencyJudgePort;
import com.dndmaster.adventure.evidence.EvidenceSufficiencyRequest;
import com.dndmaster.adventure.evidence.SufficiencyDecision;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Internal adapter for the AI Game Master's fixed-policy evidence-sufficiency endpoint. */
public final class HttpEvidenceSufficiencyJudgePort implements EvidenceSufficiencyJudgePort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout; private final ObjectMapper mapper; private final String internalToken;
    public HttpEvidenceSufficiencyJudgePort(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this.client=Objects.requireNonNull(client,"client must not be null"); this.baseUri=Objects.requireNonNull(baseUri,"base uri must not be null"); this.timeout=Objects.requireNonNull(timeout,"timeout must not be null"); this.mapper=Objects.requireNonNull(mapper,"mapper must not be null");
        if(internalToken==null||internalToken.isBlank()) throw new IllegalArgumentException("internal token must not be blank"); this.internalToken=internalToken;
    }
    @Override public SufficiencyDecision judge(EvidenceSufficiencyRequest request) {
        Objects.requireNonNull(request,"request must not be null");
        try {
            String body=mapper.writeValueAsString(new Request(request.policyId(),request.query(),candidates(request.candidates()),request.pinnedEvidenceIds().stream().map(UUID::toString).toList()));
            var builder = HttpRequest.newBuilder(baseUri.resolve("internal/v1/gm/evidence-sufficiency"))
                    .timeout(timeout).header("Content-Type","application/json").header("X-Internal-Token",internalToken);
            if (request.soloPlayerId() != null) builder.header("X-Solo-Player-Id", request.soloPlayerId().toString());
            HttpResponse<String> response=client.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==422||response.statusCode()==429||response.statusCode()>=500) throw new EvidenceAcquisitionTransientException("evidence sufficiency is unavailable");
            if(response.statusCode()/100!=2) throw new EvidenceAcquisitionContractException("evidence sufficiency failed with status "+response.statusCode());
            Response parsed=mapper.readValue(response.body(),Response.class);
            List<UUID> selected=parsed.selectedEvidenceIds().stream().map(UUID::fromString).toList();
            Map<UUID,String> reasons=parsed.selectionReasons().entrySet().stream().collect(java.util.stream.Collectors.toMap(entry->UUID.fromString(entry.getKey()),Map.Entry::getValue,(a,b)->a,java.util.LinkedHashMap::new));
            validate(selected,reasons,request);
            return new SufficiencyDecision(parsed.sufficient(),selected,reasons,parsed.missing());
        } catch(IOException exception) { throw new EvidenceAcquisitionTransientException("evidence sufficiency transport failed");
        } catch(InterruptedException exception) { Thread.currentThread().interrupt(); throw new EvidenceAcquisitionTransientException("evidence sufficiency interrupted"); }
    }
    private static List<Candidate> candidates(List<EvidenceCandidate> values) { return values.stream().map(value->new Candidate(value.id().toString(),value.documentType(),value.locator(),value.excerpt())).toList(); }
    private static void validate(List<UUID> selected,Map<UUID,String> reasons,EvidenceSufficiencyRequest request) {
        var allowed=request.candidates().stream().map(EvidenceCandidate::id).collect(java.util.stream.Collectors.toSet());
        if(selected.size()!=new LinkedHashSet<>(selected).size()||!allowed.containsAll(selected)||!selected.containsAll(request.pinnedEvidenceIds())||!reasons.keySet().equals(new LinkedHashSet<>(selected))) throw new EvidenceAcquisitionContractException("judge returned invalid evidence identifiers");
    }
    record Request(String policy,String taskContext,List<Candidate> candidates,List<String> pinnedEvidenceIds) {}
    record Candidate(String evidenceId,String documentType,String locator,String excerpt) {}
    @JsonIgnoreProperties(ignoreUnknown=true) record Response(boolean sufficient,List<String> selectedEvidenceIds,Map<String,String> selectionReasons,String missing) {}
}
