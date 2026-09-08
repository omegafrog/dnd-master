package com.dndmaster.combatmap.application.view;

/** 이미 성공한 정렬 저장 작업의 입력과 결과. */
public record MapGridAlignmentCommand(MapGridAlignmentRequest request, MapGridAlignment result) {}
