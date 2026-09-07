package com.dndmaster.combatmap.application.view;

import java.util.Optional;
import java.util.UUID;

/** 지도 원본 문서에서 특정 이미지 자산을 읽는 경계. */
public interface MapImageEvidencePort {
    Optional<MapImageEvidence> load(UUID documentId, String locator);
}
