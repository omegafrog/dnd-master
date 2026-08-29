package com.dndmaster.gmeval.tuning;

import java.util.ArrayList;
import java.util.List;

public final class InMemoryRoleModelConfigurationStore implements RoleModelConfigurationStore {
    private List<RoleModelConfiguration> configurations = List.of();

    @Override public List<RoleModelConfiguration> load() { return List.copyOf(configurations); }
    @Override public void save(List<RoleModelConfiguration> next) { configurations = List.copyOf(new ArrayList<>(next)); }
}
