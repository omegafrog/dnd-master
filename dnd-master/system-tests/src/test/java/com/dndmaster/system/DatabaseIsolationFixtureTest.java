package com.dndmaster.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.RepeatedTest;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class DatabaseIsolationFixtureTest {
    private static final DockerImageName PGVECTOR = DockerImageName.parse("pgvector/pgvector:pg17")
            .asCompatibleSubstituteFor("postgres");
    private static final Set<String> DATABASES_CREATED_BY_TESTS = ConcurrentHashMap.newKeySet();

    @RepeatedTest(2)
    void createsFreshPgvectorDatabaseForEveryTest() throws Exception {
        String databaseName = "fixture_" + UUID.randomUUID().toString().replace("-", "");

        try (var postgres = new PostgreSQLContainer<>(PGVECTOR)
                .withDatabaseName(databaseName)
                .withUsername("fixture_owner")
                .withPassword("fixture-password")) {
            postgres.start();

            assertTrue(DATABASES_CREATED_BY_TESTS.add(databaseName), "Each test must receive a unique database");
            try (var connection = DriverManager.getConnection(
                            postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                    var statement = connection.createStatement()) {
                try (var database = statement.executeQuery("select current_database()")) {
                    assertTrue(database.next());
                    assertEquals(databaseName, database.getString(1));
                }
                statement.execute("create extension if not exists vector");
                try (var extension = statement.executeQuery(
                        "select count(*) from pg_extension where extname = 'vector'")) {
                    assertTrue(extension.next());
                    assertEquals(1, extension.getInt(1));
                }
            }
        }
    }
}
