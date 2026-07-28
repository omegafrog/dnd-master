package com.dndmaster.ruleknowledge.infrastructure.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.ruleknowledge.application.registration.StoredRulebookRegistration;
import com.dndmaster.ruleknowledge.domain.rulebook.DocumentType;
import com.dndmaster.ruleknowledge.domain.rulebook.OwnerPlayerId;
import com.dndmaster.ruleknowledge.domain.rulebook.ProcessingStatus;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookFormat;
import com.dndmaster.ruleknowledge.domain.rulebook.RulebookId;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class PostgresRulebookRegistrationRepositoryTest {
    @Test
    void savesEmptyWarningAndLocationListsAsPostgresArrays() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        Array postgresArray = mock(Array.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.getConnection()).thenReturn(connection);
        when(connection.createArrayOf(eq("text"), any(String[].class))).thenReturn(postgresArray);

        PostgresRulebookRegistrationRepository repository =
                new PostgresRulebookRegistrationRepository(dataSource, new ObjectMapper());
        Instant now = Instant.now();
        repository.save(new StoredRulebookRegistration(
                RulebookId.generate(),
                new OwnerPlayerId(UUID.randomUUID()),
                "upload-1",
                "hash-1",
                RulebookFormat.TXT,
                1L,
                "storage-1",
                ProcessingStatus.QUEUED,
                null,
                null,
                List.of(),
                null,
                0L,
                now,
                now,
                DocumentType.RULEBOOK,
                "rules.txt"));

        verify(connection, times(2)).createArrayOf("text", new String[0]);
        verify(statement).setArray(11, postgresArray);
        verify(statement).setArray(19, postgresArray);
    }
}
