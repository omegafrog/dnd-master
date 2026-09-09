package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.CombatMap;
import com.dndmaster.combatmap.domain.MapId;
import com.dndmaster.combatmap.domain.MapLayer;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;

/** 정렬값만 조회·적용하며 기존 전체 격자 보정 경로를 호출하지 않는다. */
public final class MapGridAlignmentService {
    private final CombatMapViewStore maps;
    private final MapGridAlignmentStore alignments;

    public MapGridAlignmentService(CombatMapViewStore maps, MapGridAlignmentStore alignments) {
        this.maps = Objects.requireNonNull(maps);
        this.alignments = Objects.requireNonNull(alignments);
    }

    public MapGridAlignmentService(CombatMapViewStore maps, MapGridAlignmentStore alignments, CombatMapViewService mapViews) {
        this(maps, alignments);
    }

    public MapGridAlignment find(MapId mapId, MapOwnerId owner) {
        CombatMap map = owned(mapId, owner);
        return alignments.find(mapId).orElseGet(() -> legacy(map));
    }

    public MapGridAlignment apply(MapId mapId, MapOwnerId owner, MapGridAlignmentRequest request) {
        CombatMap map = owned(mapId, owner);
        if (!imageRevision(map).equals(request.imageRevision())) throw new MapGridAlignmentConflictException();
        requireGridFitsImage(map, request);
        Optional<MapGridAlignment> current = alignments.find(mapId);
        if (current.isPresent() && sameGeometry(current.get(), request)) {
            synchronizeGridBounds(mapId, owner, request);
            return current.get();
        }
        MapGridAlignment saved;
        try {
            saved = alignments.apply(owner, mapId, request);
        } catch (MapGridAlignmentConflictException conflict) {
            // A timed-out request may already have committed. Treat an identical
            // retry from a newly mounted editor as a successful replay.
            Optional<MapGridAlignment> committed = alignments.find(mapId);
            if (committed.isPresent() && sameGeometry(committed.get(), request)) return committed.get();
            throw conflict;
        }
        synchronizeGridBounds(mapId, owner, request);
        return saved;
    }

    private static boolean sameGeometry(MapGridAlignment current, MapGridAlignmentRequest request) {
        return current.imageRevision().equals(request.imageRevision())
                && Double.compare(current.originX(), request.originX()) == 0
                && Double.compare(current.originY(), request.originY()) == 0
                && Double.compare(current.cellSize(), request.cellSize()) == 0;
    }

    /** Keeps the player-visible map metadata on the same geometry as the saved alignment. */
    private void synchronizeGridBounds(MapId mapId, MapOwnerId owner, MapGridAlignmentRequest request) {
        VersionedOwnedCombatMap state = maps.find(mapId).orElseThrow(CombatMapAccessDeniedException::new);
        if (!state.owner().equals(owner)) throw new CombatMapAccessDeniedException();
        CombatMap current = state.map();
        int[] size = imageSize(mapImage(current));
        String bounds = request.originX() + "," + request.originY() + ","
                + current.grid().width() * request.cellSize() + "," + current.grid().height() * request.cellSize()
                + "," + size[0] + "," + size[1];
        String existing = current.layers().stream().filter(layer -> "GRID_BOUNDS".equals(layer.type())).map(MapLayer::value).findFirst().orElse("");
        if (bounds.equals(existing)) return;
        List<MapLayer> layers = new ArrayList<>(current.layers().stream()
                .filter(layer -> !"GRID_BOUNDS".equals(layer.type())).toList());
        layers.add(new MapLayer("GRID_BOUNDS", bounds, com.dndmaster.combatmap.domain.LayerVisibility.PLAYER_VISIBLE));
        CombatMap updated = new CombatMap(current.id(), current.adventureId(), current.ruleSetId(), current.grid(),
                current.ownerPlayerId(), current.tokens(), current.obstacles(), layers, state.version() + 1,
                request.commandId(), "ALIGNMENT|" + request);
        updated.replaceDoors(current.doors());
        updated.replaceRuntimeState(current.runtimeState());
        if (current.visibilitySnapshot() != null) updated.replaceVisibility(current.visibilitySnapshot());
        maps.update(owner, updated, state.version(), state.version() + 1, request.commandId(), updated.operationFingerprint());
    }

    private static int[] imageSize(String image) {
        try {
            int encodedStart = image.indexOf("base64,");
            var decoded = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(image.substring(encodedStart + "base64,".length()))));
            if (decoded == null) throw new MapGridAlignmentImageUnavailableException();
            return new int[] { decoded.getWidth(), decoded.getHeight() };
        } catch (RuntimeException | java.io.IOException exception) {
            if (exception instanceof MapGridAlignmentImageUnavailableException unavailable) throw unavailable;
            throw new MapGridAlignmentImageUnavailableException();
        }
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

    public static String imageRevision(CombatMap map) {
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
