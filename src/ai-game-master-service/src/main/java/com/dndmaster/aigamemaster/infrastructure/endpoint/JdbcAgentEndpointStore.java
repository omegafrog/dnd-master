package com.dndmaster.aigamemaster.infrastructure.endpoint;

import com.dndmaster.aigamemaster.application.endpoint.AgentEndpoint;
import com.dndmaster.aigamemaster.application.endpoint.AgentEndpointStore;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

public final class JdbcAgentEndpointStore implements AgentEndpointStore {
    private final DataSource dataSource;
    public JdbcAgentEndpointStore(DataSource dataSource) { this.dataSource = dataSource; }
    @Override public List<AgentEndpoint> list() { return find(" ORDER BY name"); }
    @Override public Optional<AgentEndpoint> active() { return find(" WHERE active = TRUE").stream().findFirst(); }
    @Override public void save(AgentEndpoint value) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            if (value.active()) try (var clear = connection.prepareStatement("UPDATE agent_endpoint SET active = FALSE WHERE active = TRUE")) { clear.executeUpdate(); }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO agent_endpoint (endpoint_id, name, provider, base_url, model, secret_environment_variable, active, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (endpoint_id) DO UPDATE SET name = EXCLUDED.name, provider = EXCLUDED.provider,
                      base_url = EXCLUDED.base_url, model = EXCLUDED.model, secret_environment_variable = EXCLUDED.secret_environment_variable,
                      active = EXCLUDED.active, updated_at = EXCLUDED.updated_at
                    """)) {
                statement.setObject(1, value.id()); statement.setString(2, value.name()); statement.setString(3, value.provider().name());
                statement.setString(4, value.baseUrl().toString()); statement.setString(5, value.model()); statement.setString(6, value.secretEnvironmentVariable());
                statement.setBoolean(7, value.active()); statement.setTimestamp(8, java.sql.Timestamp.from(value.updatedAt())); statement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException error) { throw new IllegalStateException("could not save agent endpoint", error); }
    }
    private List<AgentEndpoint> find(String suffix) {
        try (var connection = dataSource.getConnection(); var statement = connection.prepareStatement("SELECT endpoint_id, name, provider, base_url, model, secret_environment_variable, active, updated_at FROM agent_endpoint" + suffix); var rows = statement.executeQuery()) {
            List<AgentEndpoint> values = new ArrayList<>();
            while (rows.next()) values.add(new AgentEndpoint(rows.getObject(1, java.util.UUID.class), rows.getString(2), AgentEndpoint.Provider.valueOf(rows.getString(3)), URI.create(rows.getString(4)), rows.getString(5), rows.getString(6), rows.getBoolean(7), rows.getTimestamp(8).toInstant()));
            return List.copyOf(values);
        } catch (SQLException error) { throw new IllegalStateException("could not load agent endpoints", error); }
    }
}
