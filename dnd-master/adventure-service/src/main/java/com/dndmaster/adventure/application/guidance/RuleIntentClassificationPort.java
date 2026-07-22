package com.dndmaster.adventure.application.guidance;

public interface RuleIntentClassificationPort {
    RuleQueryIntent classify(String situation);
}
