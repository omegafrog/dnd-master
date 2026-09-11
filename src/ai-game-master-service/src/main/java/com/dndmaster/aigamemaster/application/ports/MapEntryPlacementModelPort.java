package com.dndmaster.aigamemaster.application.ports;

import java.util.List;
import java.util.Objects;

/** 지도 자체가 아니라, 진입 서술을 지도 안의 시작 칸으로 연결하는 전용 계약. */
public interface MapEntryPlacementModelPort {
    EntryPlacementOutput propose(EntryPlacementInput input);

    record EntryPlacementInput(String targetScene, String action, String judgment, String narration,
                               String mapData, String imageDataUri) {
        public EntryPlacementInput {
            targetScene = required(targetScene, "target scene");
            action = clean(action);
            judgment = clean(judgment);
            narration = clean(narration);
            mapData = clean(mapData);
            imageDataUri = clean(imageDataUri);
        }

        private static String clean(String value) { return value == null ? "" : value.trim(); }
        private static String required(String value, String name) {
            if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
            return value.trim();
        }
    }

    record EntryInterpretation(String transition, String targetScene, String anchor,
                               String placementRelation, String evidence) {
        public EntryInterpretation {
            transition = clean(transition);
            targetScene = clean(targetScene);
            anchor = clean(anchor);
            placementRelation = clean(placementRelation);
            evidence = clean(evidence);
        }
        private static String clean(String value) { return value == null ? "" : value.trim(); }
    }

    record Candidate(int x, int y, double confidence, String source, String anchor,
                     String reason, List<String> evidence) {
        public Candidate {
            if (x < 0 || y < 0) throw new IllegalArgumentException("entry candidate coordinates must not be negative");
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("entry candidate confidence must be between 0 and 1");
            }
            source = source == null || source.isBlank() ? "MAP_IMAGE" : source.trim();
            anchor = anchor == null ? "" : anchor.trim();
            reason = reason == null ? "" : reason.trim();
            evidence = evidence == null ? List.of() : List.copyOf(evidence);
        }
    }

    record EntryPlacementOutput(String status, EntryInterpretation interpretation,
                                List<Candidate> candidates, String reason) {
        public EntryPlacementOutput {
            status = status == null || status.isBlank() ? "UNRESOLVED" : status.trim().toUpperCase(java.util.Locale.ROOT);
            if (!status.equals("RESOLVED") && !status.equals("AMBIGUOUS") && !status.equals("UNRESOLVED")) {
                throw new IllegalArgumentException("entry placement status is invalid");
            }
            interpretation = Objects.requireNonNull(interpretation, "entry interpretation must not be null");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            reason = reason == null ? "" : reason.trim();
        }
    }
}
