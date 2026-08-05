package com.dndmaster.adventure.application.runtime;

public class CommandInProgressException extends RuntimeException {
    public CommandInProgressException() { super("runtime command is already being processed"); }
}
