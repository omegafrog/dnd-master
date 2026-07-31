package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.dndmaster.adventure.application.campaign.CampaignPlanRepository;
import com.dndmaster.adventure.application.session.AdventureSessionRepository;
import com.dndmaster.adventure.domain.adventure.AdventurePartyMember;
import com.dndmaster.adventure.domain.adventure.AdventureSession;
import com.dndmaster.adventure.domain.adventure.AdventureSessionRuntimeConfiguration;
import com.dndmaster.adventure.domain.adventure.CampaignDocumentRevision;
import com.dndmaster.adventure.domain.adventure.CampaignPlan;
import com.dndmaster.adventure.domain.adventure.CampaignPlanEvidence;
import com.dndmaster.adventure.domain.adventure.CampaignStage;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.ControlMode;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureSessionRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresCampaignPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class CampaignPlanPostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure")
            .withUsername("adventure")
            .withPassword("adventure");

    private static DataSource dataSource;
    private CampaignPlanRepository repository;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void resetDatabase() throws SQLException {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE adventure_campaign_plan CASCADE");
            statement.execute("TRUNCATE adventure_session CASCADE");
        }
        repository = new PostgresCampaignPlanRepository(dataSource, new ObjectMapper());
    }

    @Test
    void saves_and_resumes_campaign_plan_with_document_revisions_and_evidence() {
        OwnerPlayerId owner = new OwnerPlayerId(UUID.randomUUID());
        SessionId sessionId = SessionId.generate();
        CharacterSheetId sheetId = new CharacterSheetId(UUID.randomUUID());
        AdventureSession session = AdventureSession.create(
                sessionId,
                owner,
                UUID.randomUUID(),
                5L,
                UUID.randomUUID(),
                2L,
                2,
                new AdventureSessionRuntimeConfiguration(
                        new ScenarioId(UUID.randomUUID()),
                        new RuleSetId(UUID.randomUUID()),
                        List.of(),
                        "ollama",
                        List.of("search"),
                        "opening"));
        session.addPartyMember(new AdventurePartyMember(
                sheetId,
                ControlMode.DIRECT,
                true,
                true,
                true,
                true,
                true,
                true));
        AdventureSessionRepository sessions = new PostgresAdventureSessionRepository(dataSource);
        AdventureSession persistedDraft = AdventureSession.create(
                sessionId,
                owner,
                session.scenarioPackageId(),
                session.scenarioPackageRevision(),
                session.blueprintId(),
                session.blueprintRevision(),
                session.characterLimit(),
                session.runtimeConfiguration());
        sessions.save(persistedDraft, 0L);

        KnowledgeDocumentId documentId = new KnowledgeDocumentId(UUID.randomUUID());
        UUID evidenceId = UUID.randomUUID();
        CampaignPlan plan = new CampaignPlan(
                UUID.randomUUID(),
                sessionId,
                session.scenarioPackageId(),
                session.scenarioPackageRevision(),
                1L,
                "A source-grounded overview.",
                List.of(new CampaignDocumentRevision(documentId, 9L, "campaign.txt")),
                List.of(sheetId),
                List.of(new CampaignPlanEvidence(
                        evidenceId,
                        documentId,
                        9L,
                        "page:2:span:4",
                        "The keeper opens the eastern gate after the bell rings.")),
                List.of(new CampaignStage(
                        1,
                        "The keeper opens the eastern gate after the bell rings.",
                        "근거의 상황을 확인한다.",
                        "근거의 갈등만 사용한다.",
                        List.of("The keeper opens the eastern gate after the bell rings."),
                        "상황이 해결되면 전환한다.",
                        List.of(evidenceId))));

        repository.save(plan);

        assertEquals(plan, repository.findBySessionId(sessionId).orElseThrow());
    }

    private record DriverManagerDataSource(String url, String username, String password) implements DataSource {
        @Override public Connection getConnection() throws SQLException { return DriverManager.getConnection(url, username, password); }
        @Override public Connection getConnection(String user, String pass) throws SQLException { return DriverManager.getConnection(url, user, pass); }
        @Override public <T> T unwrap(Class<T> iface) throws SQLException { throw new SQLException("unwrap unsupported"); }
        @Override public boolean isWrapperFor(Class<?> iface) { return false; }
        @Override public java.io.PrintWriter getLogWriter() { return null; }
        @Override public void setLogWriter(java.io.PrintWriter out) {}
        @Override public void setLoginTimeout(int seconds) {}
        @Override public int getLoginTimeout() { return 0; }
        @Override public java.util.logging.Logger getParentLogger() { return java.util.logging.Logger.getGlobal(); }
    }
}
