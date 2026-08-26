package com.dndmaster.ruleknowledge.application.reset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Set;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class DevelopmentRagResetServiceTest {
    @Test
    void requiresExplicitConfirmation() {
        DataSource dataSource = mock(DataSource.class);
        DevelopmentRagResetService service = new DevelopmentRagResetService(dataSource, Set.of("development"));

        DevelopmentRagResetService.ResetException exception = assertThrows(
                DevelopmentRagResetService.ResetException.class,
                () -> service.reset(""));

        assertEquals("RESET_CONFIRMATION_REQUIRED", exception.code());
        verifyNoInteractions(dataSource);
    }

    @Test
    void rejectsProductionAndUndefinedProfilesBeforeOpeningDatabase() {
        DataSource dataSource = mock(DataSource.class);

        DevelopmentRagResetService production = new DevelopmentRagResetService(dataSource, Set.of("production"));
        assertEquals("RESET_FORBIDDEN_PRODUCTION", assertThrows(
                DevelopmentRagResetService.ResetException.class,
                () -> production.reset("RESET_RAG_DATA")).code());

        DevelopmentRagResetService undefined = new DevelopmentRagResetService(dataSource, Set.of());
        assertEquals("RESET_PROFILE_UNDEFINED", assertThrows(
                DevelopmentRagResetService.ResetException.class,
                () -> undefined.reset("RESET_RAG_DATA")).code());

        verifyNoInteractions(dataSource);
    }

    @Test
    void resetPlanContainsOnlyDerivedRagDataAndKeepsCatalogAndAssets() {
        assertEquals(List.of(
                "published_rag_chunk",
                "rag_extraction_page",
                "rag_extraction_retry",
                "rag_extraction_version",
                "rulebook_vector_chunk",
                "rulebook_vector_index",
                "game_system_definition_revision",
                "rulebook_registration"), DevelopmentRagResetService.ragTables());
        assertEquals(Set.of("rulebook_catalog_revision"),
                DevelopmentRagResetService.preservedTables());
    }

    @Test
    void confirmedDevelopmentResetExecutesOnlyTheRagDeletePlan() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        PreparedStatement statement = mock(PreparedStatement.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getAutoCommit()).thenReturn(true);
        when(connection.prepareStatement(anyString())).thenReturn(statement);

        DevelopmentRagResetService service = new DevelopmentRagResetService(dataSource, Set.of("development"));
        DevelopmentRagResetService.ResetResult result = service.reset("RESET_RAG_DATA");

        assertEquals(8, result.tables().size());
        verify(connection, times(9)).prepareStatement(anyString());
        verify(connection).commit();
    }
}
