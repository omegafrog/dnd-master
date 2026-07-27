package com.dndmaster.adventure;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dndmaster.adventure.application.knowledge.KnowledgeDocumentStatus;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetApplicationService;
import com.dndmaster.adventure.application.knowledge.SessionKnowledgeSetRepository;
import com.dndmaster.adventure.application.saved.SavedAdventureApplicationService;
import com.dndmaster.adventure.domain.adventure.Adventure;
import com.dndmaster.adventure.domain.adventure.AdventureContext;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import com.dndmaster.adventure.domain.adventure.CharacterSheetId;
import com.dndmaster.adventure.domain.adventure.OwnerPlayerId;
import com.dndmaster.adventure.domain.adventure.RuleSetId;
import com.dndmaster.adventure.domain.adventure.ScenarioId;
import com.dndmaster.adventure.domain.adventure.SessionId;
import com.dndmaster.adventure.domain.knowledge.KnowledgeDocumentId;
import com.dndmaster.adventure.infrastructure.integration.CrossContextHttpKnowledgeDocumentLookupGateway;
import com.dndmaster.adventure.infrastructure.persistence.PostgresAdventureRepository;
import com.dndmaster.adventure.infrastructure.persistence.PostgresSessionKnowledgeSetRepository;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class SessionKnowledgeSetPostgresIntegrationTest {
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("adventure")
            .withUsername("adventure")
            .withPassword("adventure");

    private static DataSource dataSource;

    private WireMockServer wireMock;
    private PostgresAdventureRepository adventureRepository;
    private SessionKnowledgeSetRepository repository;
    private SessionKnowledgeSetApplicationService service;

    @BeforeAll
    static void startDatabase() {
        POSTGRES.start();
        dataSource = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
    }

    @AfterAll
    static void stopDatabase() {
        POSTGRES.stop();
    }

    @BeforeEach
    void setUp() throws SQLException {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("TRUNCATE adventure_session_knowledge_document CASCADE");
            statement.execute("TRUNCATE adventure CASCADE");
        }
        wireMock = new WireMockServer(0);
        wireMock.start();
        adventureRepository = new PostgresAdventureRepository(dataSource);
        repository = new PostgresSessionKnowledgeSetRepository(dataSource);
        service = new SessionKnowledgeSetApplicationService(
                adventureRepository,
                repository,
                new CrossContextHttpKnowledgeDocumentLookupGateway(
                        HttpClient.newHttpClient(),
                        URI.create(wireMock.baseUrl() + "/"),
                        Duration.ofSeconds(2),
                        new ObjectMapper()));
    }

    @AfterEach
    void tearDown() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Test
    void persistsAndReloadsSessionKnowledgeSetAfterOwnershipAndIndexLookup() {
        OwnerPlayerId owner = owner();
        Adventure adventure = adventure(owner);
        adventureRepository.save(adventure);
        KnowledgeDocumentId rulebook = document();
        KnowledgeDocumentId storybook = document();
        wireMock.stubFor(get(urlEqualTo("/internal/v1/rulebooks?ownerId=" + owner.value()))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"ownerId":"%s","rulebooks":[
                          {"knowledgeDocumentId":"%s","status":"INDEXED","documentType":"RULEBOOK","originalFilename":"phb.pdf"},
                          {"knowledgeDocumentId":"%s","status":"INDEXED","documentType":"STORYBOOK","originalFilename":"campaign.md"}
                        ]}
                        """.formatted(owner.value(), rulebook.value(), storybook.value()))));

        service.updateSessionKnowledgeSet(adventure.id(), owner, List.of(rulebook, storybook));

        assertEquals(List.of(rulebook, storybook), repository.findBySessionId(adventure.sessionId()).orElseThrow().knowledgeDocumentIds());
        wireMock.verify(getRequestedFor(urlEqualTo("/internal/v1/rulebooks?ownerId=" + owner.value())));
    }

    @Test
    void rejectsKnowledgeDocumentsThatAreNotIndexedYet() {
        OwnerPlayerId owner = owner();
        Adventure adventure = adventure(owner);
        adventureRepository.save(adventure);
        KnowledgeDocumentId pending = document();
        wireMock.stubFor(get(urlEqualTo("/internal/v1/rulebooks?ownerId=" + owner.value()))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                        {"ownerId":"%s","rulebooks":[
                          {"knowledgeDocumentId":"%s","status":"UPLOADED","documentType":"RULEBOOK","originalFilename":"phb.pdf"}
                        ]}
                        """.formatted(owner.value(), pending.value()))));

        assertThrows(
                IllegalStateException.class,
                () -> service.updateSessionKnowledgeSet(adventure.id(), owner, List.of(pending)));
    }

    private static Adventure adventure(OwnerPlayerId owner) {
        return Adventure.create(
                AdventureId.generate(),
                SessionId.generate(),
                owner,
                new ScenarioId(UUID.randomUUID()),
                new RuleSetId(UUID.randomUUID()),
                new CharacterSheetId(UUID.randomUUID()),
                new AdventureContext("start", "npc", null, null));
    }

    private static OwnerPlayerId owner() {
        return new OwnerPlayerId(UUID.randomUUID());
    }

    private static KnowledgeDocumentId document() {
        return new KnowledgeDocumentId(UUID.randomUUID());
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
