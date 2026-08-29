package com.dndmaster.gmeval.application;

import com.dndmaster.gmeval.domain.EvalCase;

@FunctionalInterface
public interface ResponseGeneratorPort {
    GeneratedResponse generate(EvalCase evalCase, EvalRunConfiguration configuration);
}
