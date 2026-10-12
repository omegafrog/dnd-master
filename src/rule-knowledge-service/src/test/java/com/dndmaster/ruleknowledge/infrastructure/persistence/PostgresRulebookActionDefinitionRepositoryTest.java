package com.dndmaster.ruleknowledge.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class PostgresRulebookActionDefinitionRepositoryTest {
    @Test
    void readsStoredSharedRuleWithSourceConditionsAndFields() throws Exception {
        UUID documentId = UUID.fromString("89ef62fc-b2dd-4b1b-91c9-04810d880f3a");
        String sharedRules = "[{\"id\":\"shared:stable\",\"applicability\":\"AMBIGUOUS\","
                + "\"conditions\":\"When using a listed action\",\"sourceChunkId\":\"source-id\","
                + "\"sourceLocator\":\"page=2\",\"sourceEvidence\":\"A listed action costs one action.\","
                + "\"fields\":{\"cost\":\"one action\"}}]";
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        ResultSet rows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(org.mockito.ArgumentMatchers.anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(rows);
        when(rows.next()).thenReturn(true);
        when(rows.getString(1)).thenReturn("COMPLETED");
        when(rows.getInt(2)).thenReturn(1);
        when(rows.getInt(3)).thenReturn(1);
        when(rows.getInt(4)).thenReturn(1);
        when(rows.getInt(5)).thenReturn(1);
        when(rows.getString(6)).thenReturn("[]");
        when(rows.getString(7)).thenReturn(null);
        when(rows.getString(8)).thenReturn("rulebook-action-definition-v7-focused-rules");
        when(rows.getString(9)).thenReturn("[]");
        when(rows.getBoolean(10)).thenReturn(false);
        when(rows.getInt(11)).thenReturn(0);
        when(rows.getString(12)).thenReturn("[]");
        when(rows.getString(13)).thenReturn(sharedRules);

        var extraction = new PostgresRulebookActionDefinitionRepository(dataSource, new ObjectMapper())
                .find(documentId, "published-version").orElseThrow();

        var rule = extraction.sharedRules().getFirst();
        assertEquals("shared:stable", rule.id());
        assertEquals("AMBIGUOUS", rule.applicability());
        assertEquals("When using a listed action", rule.conditions());
        assertEquals("source-id", rule.sourceChunkId());
        assertEquals("A listed action costs one action.", rule.sourceEvidence());
        assertEquals("one action", rule.fields().get("cost"));
    }
}
