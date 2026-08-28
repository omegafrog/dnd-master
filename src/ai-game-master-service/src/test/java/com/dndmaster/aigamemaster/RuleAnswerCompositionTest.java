package com.dndmaster.aigamemaster;
import static org.junit.jupiter.api.Assertions.*;
import com.dndmaster.aigamemaster.application.GroundingViolationException; import com.dndmaster.aigamemaster.application.rule.*; import java.util.*; import org.junit.jupiter.api.Test;
class RuleAnswerCompositionTest{
 final UUID book=UUID.randomUUID();final SourceEvidence evidence=new SourceEvidence(book,"p. 10","A grapple check is contested.");
 @Test void acceptsSufficientConclusionOnlyWithSelectedCitation(){Citation citation=evidence.citation();RuleAnswerOutput output=new RuleAnswerOutput(EvidenceStatus.SUFFICIENT,"Use a contested check",List.of(citation),List.of(),false);assertEquals(output,new GroundedRuleAnswerService(request->output).compose(request(EvidenceStatus.SUFFICIENT)));}
 @Test void rejectsUncitedConclusionAndCitationOutsideSelectedEvidence(){RuleAnswerRequest request=request(EvidenceStatus.SUFFICIENT);assertThrows(GroundingViolationException.class,()->new GroundedRuleAnswerService(r->new RuleAnswerOutput(EvidenceStatus.SUFFICIENT,"answer",List.of(),List.of(),false)).compose(request));assertThrows(GroundingViolationException.class,()->new GroundedRuleAnswerService(r->new RuleAnswerOutput(EvidenceStatus.SUFFICIENT,"answer",List.of(new Citation(UUID.randomUUID(),"p. 1")),List.of(),false)).compose(request));}
 @Test void uncertainOrConflictingEvidenceMustExposeCitedCandidatesAndNoConclusion(){RuleAnswerRequest request=request(EvidenceStatus.CONFLICTING);RuleCandidate candidate=new RuleCandidate("candidate A",List.of(evidence.citation()));RuleAnswerOutput valid=new RuleAnswerOutput(EvidenceStatus.CONFLICTING,null,List.of(),List.of(candidate),true);assertEquals(valid,new GroundedRuleAnswerService(r->valid).compose(request));assertThrows(GroundingViolationException.class,()->new GroundedRuleAnswerService(r->new RuleAnswerOutput(EvidenceStatus.CONFLICTING,"hidden choice",List.of(evidence.citation()),List.of(candidate),true)).compose(request));assertThrows(GroundingViolationException.class,()->new GroundedRuleAnswerService(r->new RuleAnswerOutput(EvidenceStatus.CONFLICTING,null,List.of(),List.of(candidate),false)).compose(request));}
 @Test void modelCannotHideEvidenceStatus(){RuleAnswerRequest request=request(EvidenceStatus.INSUFFICIENT);assertThrows(GroundingViolationException.class,()->new GroundedRuleAnswerService(r->new RuleAnswerOutput(EvidenceStatus.SUFFICIENT,"answer",List.of(evidence.citation()),List.of(),false)).compose(request));}
 @Test void preserves_server_owned_citation_key_and_requires_claim_support(){
  SourceEvidence keyed=new SourceEvidence(book,"p. 10","잡기 판정은 서로 겨루는 판정입니다.","rule-grapple");
  RuleAnswerRequest request=new RuleAnswerRequest(UUID.randomUUID(),"잡기",EvidenceStatus.SUFFICIENT,List.of(keyed));
  RuleAnswerOutput output=new RuleAnswerOutput(EvidenceStatus.SUFFICIENT,"잡기 판정을 사용합니다.",List.of(keyed.citation()),List.of(),false,
          List.of(new GmCitationBinding("잡기 판정을 사용합니다.","conclusion","rule-grapple")));
  assertEquals(output,new GroundedRuleAnswerService(r->output).compose(request));
  assertEquals("rule-grapple",output.conclusionCitations().getFirst().citationKey());
 }
 @Test void rejects_member_citation_when_bound_claim_is_unrelated(){
  SourceEvidence keyed=new SourceEvidence(book,"p. 10","잡기 판정은 서로 겨루는 판정입니다.","rule-grapple");
  RuleAnswerRequest request=new RuleAnswerRequest(UUID.randomUUID(),"잡기",EvidenceStatus.SUFFICIENT,List.of(keyed));
  RuleAnswerOutput output=new RuleAnswerOutput(EvidenceStatus.SUFFICIENT,"항구에 폭풍이 옵니다.",List.of(keyed.citation()),List.of(),false,
          List.of(new GmCitationBinding("항구에 폭풍이 옵니다.","conclusion","rule-grapple")));
  GroundingViolationException failure=assertThrows(GroundingViolationException.class,()->new GroundedRuleAnswerService(r->output).compose(request));
  assertTrue(failure.getMessage().contains("UNSUPPORTED_CLAIM_CITATION"));
 }
 private RuleAnswerRequest request(EvidenceStatus status){return new RuleAnswerRequest(UUID.randomUUID(),"grapple",status,List.of(evidence));}
}
