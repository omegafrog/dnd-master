package com.dndmaster.combatmap.application.spatial;

import com.dndmaster.combatmap.domain.GridPosition;
import com.dndmaster.combatmap.domain.SpatialFeatureType;
import com.dndmaster.combatmap.domain.DetectionSpec;
import com.dndmaster.combatmap.domain.SpatialTrigger;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record SpatialFeaturePlacementProposal(List<Candidate> candidates) {
    public SpatialFeaturePlacementProposal {
        candidates = List.copyOf(Objects.requireNonNull(candidates, "placement candidates must not be null"));
    }

    public record Candidate(UUID featureId, SpatialFeatureType type, List<GridPosition> cells,
            boolean required, String evidenceReference, DetectionSpec detectionSpec,
            Set<SpatialTrigger> triggers) {
        public Candidate(UUID featureId, SpatialFeatureType type, List<GridPosition> cells,
                boolean required, String evidenceReference) {
            this(featureId, type, cells, required, evidenceReference, null, Set.of());
        }

        public Candidate {
            featureId = Objects.requireNonNull(featureId, "feature id must not be null");
            type = Objects.requireNonNull(type, "feature type must not be null");
            cells = List.copyOf(Objects.requireNonNull(cells, "feature cells must not be null"));
            evidenceReference = evidenceReference == null ? "" : evidenceReference.trim();
            triggers = Set.copyOf(Objects.requireNonNull(triggers, "feature triggers must not be null"));
        }
    }
}
