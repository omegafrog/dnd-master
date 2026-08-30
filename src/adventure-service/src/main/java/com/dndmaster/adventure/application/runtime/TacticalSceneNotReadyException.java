package com.dndmaster.adventure.application.runtime;

public final class TacticalSceneNotReadyException extends RuntimeException {
    private final int stagePosition;
    private final String stageId;
    private final TacticalPreparationState state;
    public TacticalSceneNotReadyException(int stagePosition, String stageId, TacticalPreparationState state) {
        super("TACTICAL_SCENE_NOT_READY");
        this.stagePosition = stagePosition; this.stageId = stageId; this.state = state;
    }
    public int stagePosition() { return stagePosition; }
    public String stageId() { return stageId; }
    public TacticalPreparationState state() { return state; }
}
