package com.dndmaster.adventure.application.runtime;

/** Presentation-only writer boundary. */
public interface TurnWriterPort {
    WriterProse write(PlayerVisibleTurn turn);
}
