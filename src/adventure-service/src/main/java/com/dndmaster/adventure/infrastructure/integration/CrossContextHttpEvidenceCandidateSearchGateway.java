package com.dndmaster.adventure.infrastructure.integration;

import com.dndmaster.adventure.evidence.EvidenceAcquisitionContractException;
import com.dndmaster.adventure.evidence.EvidenceAcquisitionTransientException;
import com.dndmaster.adventure.evidence.EvidenceCandidate;
import com.dndmaster.adventure.evidence.EvidenceCandidateSearchPort;
import com.dndmaster.adventure.evidence.EvidenceCandidateSearchRequest;
import com.dndmaster.adventure.evidence.EvidenceSearchScope;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Calls the unified Document Knowledge candidate endpoint within the already-fixed Adventure session scope. */
public final class CrossContextHttpEvidenceCandidateSearchGateway implements EvidenceCandidateSearchPort {
    private final HttpClient client; private final URI baseUri; private final Duration timeout; private final ObjectMapper mapper;
    private final String internalToken;
    public CrossContextHttpEvidenceCandidateSearchGateway(HttpClient client, URI baseUri, Duration timeout, ObjectMapper mapper, String internalToken) {
        this.client=Objects.requireNonNull(client,"client must not be null"); this.baseUri=Objects.requireNonNull(baseUri,"base uri must not be null"); this.timeout=Objects.requireNonNull(timeout,"timeout must not be null"); this.mapper=Objects.requireNonNull(mapper,"mapper must not be null");
        if (internalToken == null || internalToken.isBlank()) throw new IllegalArgumentException("internal token must not be blank"); this.internalToken = internalToken;
    }
    @Override public List<EvidenceCandidate> search(EvidenceCandidateSearchRequest request) {
        Objects.requireNonNull(request,"request must not be null");
        EvidenceSearchScope scope=request.acquisitionRequest().searchScope();
        if(scope==null) throw new EvidenceAcquisitionContractException("evidence search requires a server-confirmed session document scope");
        try {
            Request body=new Request(scope.ownerId(),scope.sessionId(),scope.scenarioPackageId(),scope.stageKey(),scope.actionIntent(),scope.documents().stream().map(document->new Scope(document.id(),document.extractionVersion(),document.type())).toList(),scope.activeLocators(),request.query(),30,30);
            HttpResponse<String> response=client.send(HttpRequest.newBuilder(baseUri.resolve("internal/v1/evidence-candidates/search")).timeout(timeout).header("X-Internal-Token",internalToken).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==429||response.statusCode()>=500) throw new EvidenceAcquisitionTransientException("evidence candidate search is unavailable");
            if(response.statusCode()/100!=2) throw new EvidenceAcquisitionContractException("evidence candidate search failed with status "+response.statusCode());
            Response parsed=mapper.readValue(response.body(),Response.class);
            validateEnvelope(parsed,scope);
            return candidates(parsed.candidates(),scope);
        } catch(IOException exception) { throw new EvidenceAcquisitionTransientException("evidence candidate search transport failed");
        } catch(InterruptedException exception) { Thread.currentThread().interrupt(); throw new EvidenceAcquisitionTransientException("evidence candidate search interrupted"); }
    }
    private static void validateEnvelope(Response response,EvidenceSearchScope scope) {
        if(!scope.ownerId().equals(response.ownerId())||!scope.sessionId().equals(response.sessionId())||!scope.scenarioPackageId().equals(response.scenarioPackageId())||response.candidates()==null) throw new EvidenceAcquisitionContractException("evidence candidate response scope does not match its request");
    }
    private static List<EvidenceCandidate> candidates(List<Candidate> values,EvidenceSearchScope scope) {
        if(values.size()>60) throw new EvidenceAcquisitionContractException("evidence candidate response exceeds the limit");
        var authorized=new LinkedHashMap<UUID,EvidenceSearchScope.Document>(); scope.documents().forEach(document->authorized.put(document.id(),document));
        return values.stream().map(candidate -> {
            EvidenceSearchScope.Document document=authorized.get(candidate.documentId());
            if(document==null||document.extractionVersion()!=candidate.extractionVersion()||!document.type().equals(candidate.documentType())) throw new EvidenceAcquisitionContractException("evidence candidate is outside the session document scope");
            return new EvidenceCandidate(candidate.chunkId(),candidate.documentId().toString(),candidate.documentType(),candidate.locator(),candidate.excerpt());
        }).toList();
    }
    record Request(UUID ownerId,UUID sessionId,UUID scenarioPackageId,String stageKey,String actionIntent,List<Scope> scope,List<String> activeLocators,String query,int denseLimit,int bm25Limit) {}
    record Scope(UUID documentId,long extractionVersion,String documentType) {}
    @JsonIgnoreProperties(ignoreUnknown=true) record Response(UUID ownerId,UUID sessionId,UUID scenarioPackageId,List<Candidate> candidates) {}
    @JsonIgnoreProperties(ignoreUnknown=true) record Candidate(UUID chunkId,UUID documentId,long extractionVersion,String documentType,String locator,String excerpt) {}
}
