package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;

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
        requireGridFitsImage(map, request);
        return alignments.apply(owner, mapId, request);
    }

    /** 원본은 이 경계 안에서만 읽고, 공개된 칸만 포함한 새 이미지로 바꾼다. */
    private CombatMap owned(MapId mapId, MapOwnerId owner) {
        VersionedOwnedCombatMap state = maps.find(mapId).orElseThrow(CombatMapAccessDeniedException::new);
        if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException();
        return state.map();
    }

    static MapGridAlignment legacy(CombatMap map) {
        return new MapGridAlignment(map.id(), imageRevision(map), 0, 0, map.grid().cellSize(), 0);
    }

    static String imageRevision(CombatMap map) {
        String image = mapImage(map);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(image.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }

    private static void requireGridFitsImage(CombatMap map, MapGridAlignmentRequest request) {
        try {
            String image = mapImage(map);
            int encodedStart = image.indexOf("base64,");
            if (!image.startsWith("data:image/") || encodedStart < 0) throw new MapGridAlignmentImageUnavailableException();
            var decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(image.substring(encodedStart + "base64,".length()))));
            if (decoded == null || request.originX() < 0 || request.originY() < 0
                    || request.originX() + map.grid().width() * request.cellSize() > decoded.getWidth()
                    || request.originY() + map.grid().height() * request.cellSize() > decoded.getHeight()) {
                throw new MapGridAlignmentImageUnavailableException();
            }
        } catch (MapGridAlignmentImageUnavailableException exception) {
            throw exception;
        } catch (RuntimeException | java.io.IOException exception) {
            throw new MapGridAlignmentImageUnavailableException();
        }
    }

    static String mapImage(CombatMap map) {
        return map.layers().stream().filter(layer -> "MAP_IMAGE".equals(layer.type())).map(MapLayer::value).findFirst()
                .orElseThrow(MapGridAlignmentImageUnavailableException::new);
    }
}
