package com.dndmaster.adventure.application.runtime;

@FunctionalInterface
public interface OfficialToolPort { GmToolOutcome execute(GmToolInvocation invocation); }
