package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.GridPosition;
import java.util.Set;
import java.util.Arrays;
import java.util.Objects;

/** 실제 관찰 시점에 고정한 플레이어용 지도 이미지 자료다. */
public record PublicMapImageArtifact(MapOwnerId owner, MapId mapId, String imageRevision,
        long publicAreaRevision, long observationVersion, byte[] png, Set<GridPosition> coveredCells) {
    public PublicMapImageArtifact(MapOwnerId owner, MapId mapId, String imageRevision,
            long publicAreaRevision, long observationVersion, byte[] png) {
        this(owner, mapId, imageRevision, publicAreaRevision, observationVersion, png, Set.of());
    }
    public PublicMapImageArtifact {
        Objects.requireNonNull(owner, "owner is required");
        Objects.requireNonNull(mapId, "map id is required");
        if (imageRevision == null || imageRevision.isBlank() || publicAreaRevision <= 0 || observationVersion < 0
                || png == null || png.length == 0) throw new IllegalArgumentException("invalid public map image artifact");
        png = Arrays.copyOf(png, png.length);
        coveredCells = Set.copyOf(coveredCells == null ? Set.of() : coveredCells);
    }
    @Override public byte[] png() { return Arrays.copyOf(png, png.length); }
    public String reference() { return owner.value() + ":" + mapId.value() + ":" + imageRevision + ":" + publicAreaRevision; }
}
