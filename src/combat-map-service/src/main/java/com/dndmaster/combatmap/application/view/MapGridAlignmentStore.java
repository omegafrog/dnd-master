package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.MapId;
import java.util.Optional;

/** 게임 맵 및 자식 행을 갱신하지 않는 정렬 전용 저장 경계. */
public interface MapGridAlignmentStore {
    Optional<MapGridAlignment> find(MapId mapId);
    MapGridAlignment apply(MapOwnerId owner, MapId mapId, MapGridAlignmentRequest request);
}
