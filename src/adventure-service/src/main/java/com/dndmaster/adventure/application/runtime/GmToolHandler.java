package com.dndmaster.adventure.application.runtime;

@FunctionalInterface
public interface GmToolHandler { GmToolOutcome handle(GmToolInvocation invocation); }
