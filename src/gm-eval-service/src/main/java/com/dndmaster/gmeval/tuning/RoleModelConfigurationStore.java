package com.dndmaster.gmeval.tuning;

import java.util.List;

public interface RoleModelConfigurationStore {
    List<RoleModelConfiguration> load();
    void save(List<RoleModelConfiguration> configurations);
}
