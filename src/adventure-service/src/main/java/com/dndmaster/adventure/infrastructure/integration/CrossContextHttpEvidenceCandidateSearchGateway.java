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
    private static final String CANDIDATE_SEARCH_PATH = "internal/v1/evidence-candidates/search";
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
        int candidateLimit = "PLAYER_ACTION".equals(request.acquisitionRequest().policyId()) ? 40 : 30;
        return search(request, scope, scope.documents(), candidateLimit);
    }

    private List<EvidenceCandidate> search(EvidenceCandidateSearchRequest request, EvidenceSearchScope scope,
            List<EvidenceSearchScope.Document> documents, int perRetrieverLimit) {
        try {
            Request body=new Request(scope.ownerId(),scope.sessionId(),scope.scenarioPackageId(),scope.stageKey(),scope.actionIntent(),documents.stream().map(document->new Scope(document.id(),document.extractionVersion(),document.type())).toList(),scope.activeLocators(),scope.knownEvidenceIds(),request.query(),perRetrieverLimit);
            HttpResponse<String> response=client.send(HttpRequest.newBuilder(baseUri.resolve(CANDIDATE_SEARCH_PATH)).timeout(timeout).header("X-Internal-Token",internalToken).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build(),HttpResponse.BodyHandlers.ofString());
            if(response.statusCode()==429||response.statusCode()>=500) throw new EvidenceAcquisitionTransientException("evidence candidate search is unavailable");
            if(response.statusCode()/100!=2) throw new EvidenceAcquisitionContractException("evidence candidate search failed with status "+response.statusCode());
            Response parsed=mapper.readValue(response.body(),Response.class);
            validateEnvelope(parsed,scope);
            return candidates(parsed.candidates(),documents,perRetrieverLimit);
        } catch(IOException exception) { throw new EvidenceAcquisitionTransientException("evidence candidate search failed endpoint=/" + CANDIDATE_SEARCH_PATH
                + " causeClass=" + exception.getClass().getSimpleName());
        } catch(InterruptedException exception) { Thread.currentThread().interrupt(); throw new EvidenceAcquisitionTransientException("evidence candidate search interrupted"); }
    }
    private static void validateEnvelope(Response response,EvidenceSearchScope scope) {
        if(!scope.ownerId().equals(response.ownerId())||!scope.sessionId().equals(response.sessionId())||!scope.scenarioPackageId().equals(response.scenarioPackageId())||response.candidates()==null) throw new EvidenceAcquisitionContractException("evidence candidate response scope does not match its request");
    }
    private static List<EvidenceCandidate> candidates(List<Candidate> values,
            List<EvidenceSearchScope.Document> requestedDocuments, int candidateLimit) {
        if(values.size()>candidateLimit) throw new EvidenceAcquisitionContractException("evidence candidate response exceeds the requested limit");
        var authorized=new LinkedHashMap<UUID,EvidenceSearchScope.Document>(); requestedDocuments.forEach(document->authorized.put(document.id(),document));
        return values.stream().map(candidate -> {
            EvidenceSearchScope.Document document=authorized.get(candidate.documentId());
            if(document==null||document.extractionVersion()!=candidate.extractionVersion()||!document.type().equals(candidate.documentType())) throw new EvidenceAcquisitionContractException("evidence candidate is outside the session document scope");
            return new EvidenceCandidate(candidate.chunkId(),candidate.documentId().toString(),candidate.documentType(),candidate.locator(),candidate.excerpt());
        }).toList();
    }
    record Request(UUID ownerId,UUID sessionId,UUID scenarioPackageId,String stageKey,String actionIntent,List<Scope> scope,List<String> activeLocators,List<String> excludedEvidenceIds,String query,int denseLimit) {}
    record Scope(UUID documentId,long extractionVersion,String documentType) {}
    @JsonIgnoreProperties(ignoreUnknown=true) record Response(UUID ownerId,UUID sessionId,UUID scenarioPackageId,List<Candidate> candidates) {}
    @JsonIgnoreProperties(ignoreUnknown=true) record Candidate(UUID chunkId,UUID documentId,long extractionVersion,String documentType,String locator,String excerpt) {}
}
