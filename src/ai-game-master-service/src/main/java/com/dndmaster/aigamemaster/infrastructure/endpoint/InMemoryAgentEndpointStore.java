package com.dndmaster.aigamemaster.infrastructure.endpoint;

import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class InMemoryAgentEndpointStore implements AgentEndpointStore {
    private final List<AgentEndpoint> endpoints = new ArrayList<>();
    @Override public synchronized List<AgentEndpoint> list() { return endpoints.stream().sorted(Comparator.comparing(AgentEndpoint::name)).toList(); }
    @Override public synchronized Optional<AgentEndpoint> active() { return endpoints.stream().filter(AgentEndpoint::active).findFirst(); }
    @Override public synchronized void save(AgentEndpoint endpoint) {
        endpoints.removeIf(value -> value.id().equals(endpoint.id()) || endpoint.active() && value.active());
        endpoints.add(endpoint);
    }
}
