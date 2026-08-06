package com.dndmaster.adventure.application.runtime;

public class UnknownToolException extends RuntimeException { public UnknownToolException(String name) { super("unknown GM tool: " + name); } }
