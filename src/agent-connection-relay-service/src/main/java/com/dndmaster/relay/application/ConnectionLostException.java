package com.dndmaster.relay.application;

public final class ConnectionLostException extends RuntimeException {
    public ConnectionLostException() { super("user PC agent connection was lost"); }
}
