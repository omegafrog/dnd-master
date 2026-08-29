package com.dndmaster.gmeval.infrastructure;

import com.dndmaster.gmeval.domain.*;
import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Loads one self-contained, schema-versioned EvalCase per JSONL line. */
public final class JsonlEvalDatasetLoader {
 private final ObjectMapper mapper = new ObjectMapper();
 public List<EvalCase> loadResource(String resource) {
  try { var stream = JsonlEvalDatasetLoader.class.getClassLoader().getResourceAsStream(resource); if (stream == null) throw new IllegalArgumentException("dataset resource not found: " + resource); Path temp = Files.createTempFile("gm-eval-resource", ".jsonl"); Files.copy(stream, temp, StandardCopyOption.REPLACE_EXISTING); try { return load(temp); } finally { Files.deleteIfExists(temp); } }
  catch (IOException e) { throw new IllegalArgumentException("invalid eval dataset resource", e); }
 }
 public List<EvalCase> load(Path path) {
  try { List<EvalCase> result=new ArrayList<>(); Set<String> ids=new HashSet<>();
   for(String line:Files.readAllLines(path)) { if(line.isBlank()) continue; JsonNode n=mapper.readTree(line); if(n.path("schemaVersion").asInt(-1)!=1) throw new IllegalArgumentException("unsupported schemaVersion");
    String id=text(n,"caseId"), input=text(n,"playerInput"); JsonNode ctx=n.path("context"); if(!n.has("hardExpectations") || !n.has("rubrics")) throw new IllegalArgumentException("missing hardExpectations or rubrics");
    EvalContext context=new EvalContext(map(ctx,"worldState"),map(ctx,"playerKnowledgeFacts"),strings(ctx,"playerKnowledge"),text(ctx,"storyStage"),map(ctx,"turnPlan"),map(ctx,"resolvedContext"));
    List<HardExpectation> expectations=new ArrayList<>(); for(JsonNode e:n.path("hardExpectations")) expectations.add(expectation(e));
    List<QualityRubric> rubrics=new ArrayList<>(); for(JsonNode r:n.path("rubrics")) { Map<Integer,String> anchors=new HashMap<>(); r.path("anchors").fields().forEachRemaining(x->anchors.put(Integer.valueOf(x.getKey()),x.getValue().asText())); rubrics.add(new QualityRubric(text(r,"dimension"),anchors)); }
    EvalCase c=new EvalCase(id,1,input,context,expectations,rubrics); if(!ids.add(id)) throw new IllegalArgumentException("duplicate caseId: "+id); result.add(c);
   } return List.copyOf(result);
  } catch(IOException|RuntimeException e) { if(e instanceof IllegalArgumentException ia) throw ia; throw new IllegalArgumentException("invalid eval dataset",e); }
 }
 private HardExpectation expectation(JsonNode e) { String type=text(e,"type"), cat=text(e,"category"), id=text(e,"id"); return switch(type) {
  case "FORBIDDEN_FACT" -> new HardExpectation.ForbiddenFact(cat,id,text(e,"fact")); case "REQUIRED_FACT" -> new HardExpectation.RequiredFact(cat,id,text(e,"fact"));
  case "RULE_CONTRADICTION" -> new HardExpectation.RuleContradiction(cat,id,text(e,"authoritativeRule")); case "STATE_MUTATION" -> new HardExpectation.StateMutation(cat,id,text(e,"stateFact"),text(e,"expectedValue"));
  case "AGENCY_VIOLATION" -> new HardExpectation.AgencyViolation(cat,id,text(e,"voluntaryAction")); case "UNSUPPORTED" -> new HardExpectation.Unsupported(cat,id,text(e,"reason")); default -> throw new IllegalArgumentException("unknown expectation type: "+type); }; }
 private static String text(JsonNode n,String k) { String v=n.path(k).isTextual()?n.path(k).asText():""; if(v.isBlank()) throw new IllegalArgumentException("missing "+k); return v; }
 private static Map<String,Object> map(JsonNode n,String k) { if(!n.has(k)||n.path(k).isNull()) return Map.of(); return new ObjectMapper().convertValue(n.path(k),Map.class); }
 private static List<String> strings(JsonNode n,String k) { List<String> out=new ArrayList<>(); for(JsonNode x:n.path(k)) out.add(x.asText()); return out; }
}
