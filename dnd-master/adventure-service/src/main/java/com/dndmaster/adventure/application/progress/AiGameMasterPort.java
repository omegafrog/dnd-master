package com.dndmaster.adventure.application.progress;

import java.util.concurrent.TimeoutException;

public interface AiGameMasterPort {
    SceneProgress advanceScene(SceneProgressRequest request) throws TimeoutException;
    ActionJudgment adjudicate(ActionJudgmentRequest request) throws TimeoutException;
}
