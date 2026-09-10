package com.dndmaster.combatmap.application.view;

import com.dndmaster.combatmap.domain.MapId;
import java.util.Optional;

/** 사용자·맵·원본 이미지별로 고정된 공개 이미지 자료를 보관한다. */
public interface PublicMapImageArtifactStore {
    Optional<PublicMapImageArtifact> findByObservation(MapOwnerId owner, MapId mapId, String imageRevision, long observationVersion);
    Optional<PublicMapImageArtifact> findLatest(MapOwnerId owner, MapId mapId, String imageRevision);
    Optional<PublicMapImageArtifact> findByPublicAreaRevision(MapOwnerId owner, MapId mapId, String imageRevision, long publicAreaRevision);
    PublicMapImageArtifact save(PublicMapImageArtifact artifact);
}
