package com.dndmaster.combatmap.application.view;

/** 정렬 또는 이미지 버전, 혹은 작업 식별자의 입력이 충돌했다. */
public final class MapGridAlignmentConflictException extends IllegalStateException {
    public MapGridAlignmentConflictException() { super("map grid alignment conflict"); }
}
