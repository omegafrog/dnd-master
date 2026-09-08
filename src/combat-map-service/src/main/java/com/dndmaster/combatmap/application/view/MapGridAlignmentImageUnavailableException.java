package com.dndmaster.combatmap.application.view;

/** 정렬에 필요한 저장된 지도 이미지를 읽을 수 없다. */
public final class MapGridAlignmentImageUnavailableException extends IllegalArgumentException {
    public MapGridAlignmentImageUnavailableException() {
        super("map image is unavailable for alignment");
    }
}
