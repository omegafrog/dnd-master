package com.dndmaster.adventure.application.runtime;

public class CommandFingerprintConflictException extends RuntimeException { public CommandFingerprintConflictException() { super("command id reused with different payload"); } }
