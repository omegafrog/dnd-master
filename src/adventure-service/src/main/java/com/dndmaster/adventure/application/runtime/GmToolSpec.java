package com.dndmaster.adventure.application.runtime;

/** Model-facing description of a tool. It intentionally excludes trusted execution context. */
public record GmToolSpec(String name, String inputSchema) { }
