package com.dndmaster.adventure.application.runtime;

import java.util.List;
import java.util.Objects;

public final class GmQualityGateEvaluator {
    public GmQualityGateReport evaluate(List<GmQualityCaseResult> cases) {
        Objects.requireNonNull(cases);
        if (cases.isEmpty()) throw new IllegalArgumentException("quality cases required");
        int structured = 0, rules = 0, plans = 0, secrets = 0, tools = 0, invented = 0;
        double human = 0;
        for (GmQualityCaseResult result : cases) {
            if (result.structuredSuccess()) structured++;
            if (result.ruleEvidenceCorrect()) rules++;
            if (result.planFactConsistent()) plans++;
            if (result.secretLeak()) secrets++;
            if (result.forbiddenTool()) tools++;
            if (result.inventedState()) invented++;
            human += result.humanScore();
        }
        return new GmQualityGateReport(cases.size(), structured, rules, plans, secrets, tools, invented, human / cases.size());
    }
}
