package com.dndmaster.aigamemaster.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;

public final class RetrievalEvaluationCorpusLoader{private RetrievalEvaluationCorpusLoader(){}public static RetrievalEvaluationCorpus load(InputStream input,ObjectMapper mapper)throws IOException{if(input==null)throw new IllegalArgumentException("retrieval corpus missing");var corpus=mapper.readValue(input,RetrievalEvaluationCorpus.class);if(!"retrieval-evaluation-v1".equals(corpus.version())||corpus.cases().size()!=100)throw new IllegalArgumentException("retrieval corpus must contain 100 v1 cases");return corpus;}}

