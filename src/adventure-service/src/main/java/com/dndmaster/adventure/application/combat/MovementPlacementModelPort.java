package com.dndmaster.adventure.application.combat;

import java.util.List;
import java.util.Objects;

/** 저장 권한 없이 플레이어가 공개된 지도에서 말한 목적지만 해석하는 경계. */
public interface MovementPlacementModelPort {
    MovementPlacementProposal interpret(MovementPlacementContext context);

    record MovementPlacementContext(String sourceText, String publicMap, String currentPosition, String tacticalContext) {
        public MovementPlacementContext {
            if (sourceText == null || sourceText.isBlank()) throw new IllegalArgumentException("source text must not be blank");
            publicMap = clean(publicMap);
            currentPosition = clean(currentPosition);
            tacticalContext = clean(tacticalContext);
        }
        private static String clean(String value) { return value == null ? "" : value.trim(); }
    }

    record MovementPlacementProposal(String status, Position destination, List<Candidate> candidates, String playerMessage) {
        public MovementPlacementProposal {
            status = status == null || status.isBlank() ? "UNRESOLVED" : status.trim().toUpperCase(java.util.Locale.ROOT);
            if (!status.equals("RESOLVED") && !status.equals("AMBIGUOUS") && !status.equals("UNRESOLVED")) {
                throw new IllegalArgumentException("movement placement status is invalid");
            }
            if (status.equals("RESOLVED") && destination == null) throw new IllegalArgumentException("resolved movement needs destination");
            if (!status.equals("RESOLVED") && destination != null) throw new IllegalArgumentException("unresolved movement must not choose destination");
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            playerMessage = playerMessage == null ? "" : playerMessage.trim();
        }
    }

    record Candidate(Position destination, double confidence, String reason) {
        public Candidate {
            destination = Objects.requireNonNull(destination, "candidate destination must not be null");
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("candidate confidence is invalid");
            reason = reason == null ? "" : reason.trim();
        }
    }

    record Position(int x, int y) {
        public Position {
            if (x < 0 || y < 0) throw new IllegalArgumentException("movement position must not be negative");
        }
    }
}
