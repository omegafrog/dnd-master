package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.*;
import java.util.Optional;

/** 저장된 게임 관찰에서만 플레이어용 이미지 자료를 만든다. */
public final class PublicMapImageArtifactService {
    private final CombatMapViewStore maps;
    private final MapGridAlignmentStore alignments;
    private final PublicMapImageArtifactStore artifacts;
    private final PublicMapImageRenderer renderer;

    public PublicMapImageArtifactService(CombatMapViewStore maps, MapGridAlignmentStore alignments,
            PublicMapImageArtifactStore artifacts) {
        this.maps = java.util.Objects.requireNonNull(maps); this.alignments = java.util.Objects.requireNonNull(alignments);
        this.artifacts = java.util.Objects.requireNonNull(artifacts);
        this.renderer = PlayerMapImageService::maskedPng;
    }
    public PublicMapImageArtifactService(CombatMapViewStore maps, MapGridAlignmentStore alignments,
            PublicMapImageArtifactStore artifacts, PublicMapImageRenderer renderer) {
        this.maps = java.util.Objects.requireNonNull(maps); this.alignments = java.util.Objects.requireNonNull(alignments);
        this.artifacts = java.util.Objects.requireNonNull(artifacts); this.renderer = java.util.Objects.requireNonNull(renderer);
    }

    public Optional<PublicMapImageArtifact> observe(MapId mapId, MapOwnerId owner, long observationVersion) {
        VersionedOwnedCombatMap state = maps.find(mapId).orElseThrow(CombatMapAccessDeniedException::new);
        if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException();
        CombatMap map = state.map();
        VisibilitySnapshot visibility = map.visibilitySnapshot();
        if (visibility == null) return Optional.empty();
        String sourceImageRevision;
        try { sourceImageRevision = MapGridAlignmentService.imageRevision(map); }
        catch (RuntimeException ignored) { return Optional.empty(); }
        MapGridAlignment alignment = alignments.find(mapId).orElseGet(() -> MapGridAlignmentService.legacy(map));
        if (!alignment.imageRevision().equals(sourceImageRevision)) return Optional.empty();
        // 정렬 버전이 바뀌면 같은 원본 이미지라도 마스크 좌표가 달라진다.
        // 원본 SHA만 캐시 키로 쓰면 이전 정렬의 검은색 마스크를 계속 재사용한다.
        String imageRevision = artifactRevision(sourceImageRevision, alignment.version());
        Optional<PublicMapImageArtifact> sameObservation = artifacts.findByObservation(owner, mapId, imageRevision, observationVersion);
        if (sameObservation.isPresent()) return sameObservation;
        Optional<PublicMapImageArtifact> lastSafe = artifacts.findLatest(owner, mapId, imageRevision);
        try {
            var explored = PlayerSafeFogProjection.filter(visibility.explored(), map.layers());
            if (explored.isEmpty()) return lastSafe;
            var newlyCovered = new java.util.HashSet<>(explored);
            // Re-entering a map can intentionally reset visibility at a new
            // situation-derived starting edge.  A previous public artifact is
            // usable only when this observation includes all of its cells;
            // otherwise it would reveal cells the player has not seen in the
            // new entry.
            boolean canExtend = lastSafe.map(previous -> explored.containsAll(previous.coveredCells())).orElse(false);
            if (canExtend) lastSafe.ifPresent(previous -> newlyCovered.removeAll(previous.coveredCells()));
            if (canExtend && lastSafe.isPresent() && newlyCovered.isEmpty()) return lastSafe;
            byte[] png = canExtend && lastSafe.isPresent()
                    ? PlayerMapImageService.extendPng(lastSafe.orElseThrow().png(), MapGridAlignmentService.mapImage(map),
                            alignment.originX(), alignment.originY(), alignment.cellSize(), newlyCovered)
                    : renderer.render(MapGridAlignmentService.mapImage(map), alignment.originX(), alignment.originY(), alignment.cellSize(), explored);
            long nextRevision = lastSafe.map(value -> value.publicAreaRevision() + 1).orElse(1L);
            return Optional.of(artifacts.save(new PublicMapImageArtifact(owner, mapId, imageRevision, nextRevision, observationVersion, png, explored)));
        } catch (RuntimeException exception) {
            return lastSafe;
        }
    }

    /** 서버가 발급한 참조에 정확히 대응하는, 이미 저장된 공개 이미지 자료만 읽는다. */
    public Optional<PublicMapImageArtifact> download(MapId mapId, MapOwnerId owner, String reference) {
        if (reference == null || reference.isBlank()) return Optional.empty();
        String[] values = reference.split(":", -1);
        if (values.length != 4) return Optional.empty();
        try {
            if (!owner.value().equals(java.util.UUID.fromString(values[0])) || !mapId.value().equals(java.util.UUID.fromString(values[1]))) return Optional.empty();
            long publicAreaRevision = Long.parseLong(values[3]);
            if (publicAreaRevision < 1) return Optional.empty();
            return artifacts.findByPublicAreaRevision(owner, mapId, values[2], publicAreaRevision)
                    .filter(artifact -> artifact.reference().equals(reference));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public Optional<String> latestReference(MapId mapId, MapOwnerId owner) {
        VersionedOwnedCombatMap state = maps.find(mapId).orElseThrow(CombatMapAccessDeniedException::new);
        if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException();
        try {
            String sourceRevision = MapGridAlignmentService.imageRevision(state.map());
            MapGridAlignment alignment = alignments.find(mapId).orElseGet(() -> MapGridAlignmentService.legacy(state.map()));
            if (!alignment.imageRevision().equals(sourceRevision)) return Optional.empty();
            return artifacts.findLatest(owner, mapId, artifactRevision(sourceRevision, alignment.version()))
                    .map(PublicMapImageArtifact::reference);
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    static String artifactRevision(String sourceImageRevision, long alignmentVersion) {
        return sourceImageRevision + "~alignment-" + alignmentVersion;
    }

    /** 맵 준비 단계에서 소유자에게만 원본을 전달한다. 플레이 중 공개 이미지 경로와 분리한다. */
    public Optional<byte[]> sourcePng(MapId mapId, MapOwnerId owner) {
        VersionedOwnedCombatMap state = maps.find(mapId).orElseThrow(CombatMapAccessDeniedException::new);
        if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException();
        try {
            return Optional.of(PlayerMapImageService.sourcePng(MapGridAlignmentService.mapImage(state.map())));
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }
}
