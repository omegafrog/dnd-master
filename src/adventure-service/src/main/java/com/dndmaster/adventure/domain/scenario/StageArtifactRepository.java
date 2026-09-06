package com.dndmaster.adventure.domain.scenario;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface StageArtifactRepository {
    Optional<StageBackbone> findBackbone(UUID scenarioPackageId, long revision);
    Optional<DetailedStage> findDetailedStage(UUID scenarioPackageId, String stageId, long backboneRevision, long revision);
    void saveBackbone(StageBackbone backbone, long expectedRevision);
    void saveDetailedStage(DetailedStage detailedStage, long expectedRevision);

    void saveInitialArtifacts(StageBackbone backbone, DetailedStage detailedStage);

    final class InMemory implements StageArtifactRepository {
        private final Map<String, StageBackbone> backbones = new HashMap<>();
        private final Map<String, DetailedStage> detailedStages = new HashMap<>();
        public Optional<StageBackbone> findBackbone(UUID id, long revision) { return Optional.ofNullable(backbones.get(id + ":" + revision)); }
        public Optional<DetailedStage> findDetailedStage(UUID id, String stage, long backbone, long revision) { return Optional.ofNullable(detailedStages.get(key(id, stage, backbone, revision))); }
        public void saveBackbone(StageBackbone value, long expected) {
            long current = backbones.keySet().stream().filter(key -> key.startsWith(value.scenarioPackageId() + ":")).count();
            if (current != expected || value.revision() != expected + 1) throw new IllegalStateException("stage backbone revision is stale");
            backbones.put(value.scenarioPackageId() + ":" + value.revision(), value);
        }
        public void saveDetailedStage(DetailedStage value, long expected) {
            String prefix = key(value.scenarioPackageId(), value.stageId(), value.backboneRevision(), 0).replace(":0", ":");
            long current = detailedStages.keySet().stream().filter(key -> key.startsWith(prefix)).count();
            if (current != expected || value.revision() != expected + 1) throw new IllegalStateException("detailed stage revision is stale");
            detailedStages.put(key(value.scenarioPackageId(), value.stageId(), value.backboneRevision(), value.revision()), value);
        }

        @Override
        public synchronized void saveInitialArtifacts(StageBackbone backbone, DetailedStage detailedStage) {
            if (backbone.revision() != 1 || detailedStage.revision() != 1) throw new IllegalStateException("initial artifacts must start at revision one");
            if (!backbone.scenarioPackageId().equals(detailedStage.scenarioPackageId())
                    || detailedStage.backboneRevision() != backbone.revision()) {
                throw new IllegalArgumentException("initial artifacts must reference the same backbone");
            }
            if (backbones.containsKey(backbone.scenarioPackageId() + ":" + backbone.revision())
                    || detailedStages.containsKey(key(detailedStage.scenarioPackageId(), detailedStage.stageId(), detailedStage.backboneRevision(), detailedStage.revision()))) {
                throw new IllegalStateException("initial stage artifacts already exist");
            }
            backbones.put(backbone.scenarioPackageId() + ":" + backbone.revision(), backbone);
            detailedStages.put(key(detailedStage.scenarioPackageId(), detailedStage.stageId(), detailedStage.backboneRevision(), detailedStage.revision()), detailedStage);
        }
        private static String key(UUID id, String stage, long backbone, long revision) { return id + ":" + stage + ":" + backbone + ":" + revision; }
    }
}
