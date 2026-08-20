package com.dndmaster.adventure.application.runtime;

/** Durable provenance of a runtime turn; trigger evidence may only use PLAYER. */
public enum RuntimeTurnOrigin {
    PLAYER,
    GM,
    AGENT
}
