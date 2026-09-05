package com.dndmaster.adventure.application.runtime;

/** Local writer for the already-grounded, player-visible Scenario Runtime narration. */
public final class ScenarioRuntimeWriterAdapter implements TurnWriterPort {
    @Override
    public WriterProse write(PlayerVisibleTurn turn) {
        String prose = turn.visibleFacts().stream()
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(turn.narrationSeed().isBlank() ? "The moment passes without further detail." : turn.narrationSeed());
        return new WriterProse(prose);
    }
}
