package com.dndmaster.aigamemaster.application.endpoint;

import java.util.List;
import java.util.Optional;

public interface AgentEndpointStore {
    List<AgentEndpoint> list();
    Optional<AgentEndpoint> active();
    void save(AgentEndpoint endpoint);
}
