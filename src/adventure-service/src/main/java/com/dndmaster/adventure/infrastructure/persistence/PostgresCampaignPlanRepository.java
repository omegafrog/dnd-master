package com.dndmaster.adventure.infrastructure.persistence;

import com.dndmaster.adventure.application.campaign.CampaignPlanRepository;
import com.dndmaster.adventure.domain.adventure.CampaignPlan;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;

public final class PostgresCampaignPlanRepository implements CampaignPlanRepository {
    private final DataSource dataSource;
    private final ObjectMapper objectMapper;

    public PostgresCampaignPlanRepository(DataSource dataSource, ObjectMapper objectMapper) {
        this.dataSource = Objects.requireNonNull(dataSource, "data source must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "object mapper must not be null");
    }

    @Override
    public Optional<CampaignPlan> findBySessionId(SessionId sessionId) {
        String sql = "SELECT campaign_plan_json FROM adventure_campaign_plan WHERE session_id = ?";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, sessionId.value());
            try (ResultSet row = statement.executeQuery()) {
                return row.next()
                        ? Optional.of(read(row.getString("campaign_plan_json")))
                        : Optional.empty();
            }
        } catch (SQLException exception) {
            throw new CampaignPlanPersistenceException("could not load campaign plan", exception);
        }
    }

    @Override
    public void save(CampaignPlan plan) {
        String sql = """
                INSERT INTO adventure_campaign_plan (
                    session_id, scenario_package_id, scenario_package_revision, plan_revision, campaign_plan_json
                ) VALUES (?, ?, ?, ?, CAST(? AS JSONB))
                ON CONFLICT (session_id) DO UPDATE SET
                    scenario_package_id = EXCLUDED.scenario_package_id,
                    scenario_package_revision = EXCLUDED.scenario_package_revision,
                    plan_revision = EXCLUDED.plan_revision,
                    campaign_plan_json = EXCLUDED.campaign_plan_json,
                    updated_at = CURRENT_TIMESTAMP
                WHERE adventure_campaign_plan.plan_revision <= EXCLUDED.plan_revision
                """;
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setObject(1, plan.sessionId().value());
            statement.setObject(2, plan.scenarioPackageId());
            statement.setLong(3, plan.scenarioPackageRevision());
            statement.setLong(4, plan.revision());
            statement.setString(5, write(plan));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new CampaignPlanPersistenceException("could not save campaign plan", exception);
        }
    }

    private CampaignPlan read(String json) throws SQLException {
        try {
            return objectMapper.readValue(json, CampaignPlan.class);
        } catch (Exception exception) {
            throw new SQLException("could not read campaign plan", exception);
        }
    }

    private String write(CampaignPlan plan) throws SQLException {
        try {
            return objectMapper.writeValueAsString(plan);
        } catch (Exception exception) {
            throw new SQLException("could not write campaign plan", exception);
        }
    }
}
