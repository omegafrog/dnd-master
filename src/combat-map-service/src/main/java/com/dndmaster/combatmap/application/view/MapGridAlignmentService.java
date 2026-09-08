package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/** 정렬값만 조회·적용하며 기존 전체 격자 보정 경로를 호출하지 않는다. */
public final class MapGridAlignmentService {
    private final CombatMapViewStore maps;
    private final MapGridAlignmentStore alignments;

    public MapGridAlignmentService(CombatMapViewStore maps, MapGridAlignmentStore alignments) {
        this.maps = Objects.requireNonNull(maps); this.alignments = Objects.requireNonNull(alignments);
    }

    public MapGridAlignment find(MapId mapId, MapOwnerId owner) {
        CombatMap map = owned(mapId, owner);
        return alignments.find(mapId).orElseGet(() -> legacy(map));
    }

    public MapGridAlignment apply(MapId mapId, MapOwnerId owner, MapGridAlignmentRequest request) {
        CombatMap map = owned(mapId, owner);
        if (!imageRevision(map).equals(request.imageRevision())) throw new MapGridAlignmentConflictException();
        return alignments.apply(owner, mapId, request);
    }

    private CombatMap owned(MapId mapId, MapOwnerId owner) {
        VersionedOwnedCombatMap state = maps.find(mapId).orElseThrow(CombatMapAccessDeniedException::new);
        if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException();
        return state.map();
    }

    private static MapGridAlignment legacy(CombatMap map) {
        return new MapGridAlignment(map.id(), imageRevision(map), 0, 0, map.grid().cellSize(), 0);
    }

    private static String imageRevision(CombatMap map) {
        String image = map.layers().stream().filter(layer -> "MAP_IMAGE".equals(layer.type())).map(MapLayer::value).findFirst().orElse("");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
