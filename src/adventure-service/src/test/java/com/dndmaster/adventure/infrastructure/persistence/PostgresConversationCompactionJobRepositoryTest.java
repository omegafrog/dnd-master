package com.dndmaster.adventure.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dndmaster.adventure.application.runtime.ConversationCompactionJob;
import com.dndmaster.adventure.application.runtime.ConversationSummary;
import com.dndmaster.adventure.application.runtime.LongTermAdventureFact;
import com.dndmaster.adventure.domain.adventure.AdventureId;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class PostgresConversationCompactionJobRepositoryTest {
    @Test
    void lost_lease_rolls_back_before_archiving_or_removing_long_term_facts() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement lockAdventure = mock(PreparedStatement.class);
        PreparedStatement checkSummary = mock(PreparedStatement.class);
        PreparedStatement completeLease = mock(PreparedStatement.class);
        ResultSet adventureRows = mock(ResultSet.class);
        ResultSet summaryRows = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(startsWith("SELECT version FROM adventure"))).thenReturn(lockAdventure);
        when(connection.prepareStatement(startsWith("SELECT 1 FROM adventure_conversation_summary"))).thenReturn(checkSummary);
        when(connection.prepareStatement(startsWith("UPDATE adventure_conversation_compaction_job SET status='DONE'"))).thenReturn(completeLease);
        when(lockAdventure.executeQuery()).thenReturn(adventureRows);
        when(adventureRows.next()).thenReturn(true);
        when(adventureRows.getLong(1)).thenReturn(7L);
        when(checkSummary.executeQuery()).thenReturn(summaryRows);
        when(summaryRows.next()).thenReturn(false);
        when(completeLease.executeUpdate()).thenReturn(0);

        AdventureId adventureId = AdventureId.generate();
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        ConversationCompactionJob job = ConversationCompactionJob.ready(adventureId, 0, 1, 7, now).lease(now.plusSeconds(30));
        ConversationSummary summary = new ConversationSummary(adventureId, 1, 0, 1, 7, "summary");
        LongTermAdventureFact fact = new LongTermAdventureFact(adventureId, UUID.randomUUID(), UUID.randomUUID(),
                7, "EVENT", "confirmed event", true, 1);

        boolean published = new PostgresConversationCompactionJobRepository(dataSource)
                .publish(job, summary, List.of(fact), List.of(), 7);

        assertFalse(published);
        verify(connection).rollback();
        verify(connection, never()).prepareStatement(contains("adventure_long_term_fact"));
        verify(connection, never()).prepareStatement(startsWith("INSERT INTO adventure_conversation_summary"));
    }
}
