package com.dndmaster.ruleknowledge.application.indexing;

import java.util.List;

public interface StructureDetectionPort {

    DetectedStructure detect(String fullText);

    record DetectedStructure(
            List<PatternMatch> patterns,
            String description) {

        public static DetectedStructure none() {
            return new DetectedStructure(List.of(), "no structure detected");
        }

        public boolean hasPatterns() {
            return !patterns.isEmpty();
        }
    }

    record PatternMatch(
            String regex,
            String groupName,
            String description) {}
}
