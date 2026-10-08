package com.dndmaster.adventure.domain.scenario;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.util.UUID;

public interface StageArtifactRepository {
    Optional<StageBackbone> findBackbone(AdventureId adventureId, UUID scenarioPackageId, long revision);
    Optional<DetailedStage> findDetailedStage(AdventureId adventureId, UUID scenarioPackageId, String stageId,
            long backboneRevision, long revision);
    void saveBackbone(AdventureId adventureId, StageBackbone backbone, long expectedRevision);
    void saveDetailedStage(AdventureId adventureId, DetailedStage detailedStage, long expectedRevision);

    void saveInitialArtifacts(AdventureId adventureId, StageBackbone backbone, List<DetailedStage> detailedStages);

    final class InMemory implements StageArtifactRepository {
        private final Map<String, StageBackbone> backbones = new HashMap<>();
        private final Map<String, DetailedStage> detailedStages = new HashMap<>();
        public Optional<StageBackbone> findBackbone(AdventureId adventureId, UUID id, long revision) { return Optional.ofNullable(backbones.get(adventureId + ":" + id + ":" + revision)); }
        public Optional<DetailedStage> findDetailedStage(AdventureId adventureId, UUID id, String stage, long backbone, long revision) { return Optional.ofNullable(detailedStages.get(key(adventureId, id, stage, backbone, revision))); }
        public void saveBackbone(AdventureId adventureId, StageBackbone value, long expected) {
            long current = backbones.keySet().stream().filter(key -> key.startsWith(adventureId + ":" + value.scenarioPackageId() + ":")).count();
            if (current != expected || value.revision() != expected + 1) throw new IllegalStateException("stage backbone revision is stale");
            backbones.put(adventureId + ":" + value.scenarioPackageId() + ":" + value.revision(), value);
        }
        public void saveDetailedStage(AdventureId adventureId, DetailedStage value, long expected) {
            String prefix = key(adventureId, value.scenarioPackageId(), value.stageId(), value.backboneRevision(), 0).replace(":0", ":");
            long current = detailedStages.keySet().stream().filter(key -> key.startsWith(prefix)).count();
            if (current != expected || value.revision() != expected + 1) throw new IllegalStateException("detailed stage revision is stale");
            detailedStages.put(key(adventureId, value.scenarioPackageId(), value.stageId(), value.backboneRevision(), value.revision()), value);
        }

        @Override
        public synchronized void saveInitialArtifacts(AdventureId adventureId, StageBackbone backbone, List<DetailedStage> stages) {
            if (backbone.revision() != 1 || stages.size() != backbone.stages().size()
                    || stages.stream().anyMatch(stage -> stage.revision() != 1
                            || !backbone.scenarioPackageId().equals(stage.scenarioPackageId())
                            || stage.backboneRevision() != backbone.revision()
                            || backbone.stages().stream().noneMatch(entry -> entry.stageId().equals(stage.stageId())))) {
                throw new IllegalArgumentException("initial artifacts must contain every stage at revision one");
            }
            if (backbones.containsKey(adventureId + ":" + backbone.scenarioPackageId() + ":" + backbone.revision())
                    || stages.stream().anyMatch(stage -> detailedStages.containsKey(
                            key(adventureId, stage.scenarioPackageId(), stage.stageId(), stage.backboneRevision(), stage.revision())))) {
                throw new IllegalStateException("initial stage artifacts already exist");
            }
            backbones.put(adventureId + ":" + backbone.scenarioPackageId() + ":" + backbone.revision(), backbone);
            stages.forEach(stage -> detailedStages.put(
                    key(adventureId, stage.scenarioPackageId(), stage.stageId(), stage.backboneRevision(), stage.revision()), stage));
        }
        private static String key(AdventureId adventureId, UUID id, String stage, long backbone, long revision) { return adventureId + ":" + id + ":" + stage + ":" + backbone + ":" + revision; }
    }
}
