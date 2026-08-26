package com.dndmaster.ruleknowledge.application.reset;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.sql.DataSource;

/** Destructive RAG-data reset guarded by an explicit development-only contract. */
public final class DevelopmentRagResetService {
    public static final String CONFIRMATION = "RESET_RAG_DATA";
    private static final List<String> RAG_TABLES = List.of(
            "published_rag_chunk", "rag_extraction_page", "rag_extraction_retry",
            "rag_extraction_version", "rulebook_vector_chunk", "rulebook_vector_index",
            "game_system_definition_revision", "rulebook_registration");
    private static final Set<String> PRESERVED_TABLES = Set.of("rulebook_catalog_revision");
    private static final List<String> DELETE_STATEMENTS = List.of(
            "DELETE FROM published_rag_chunk",
            "DELETE FROM rag_extraction_page",
            "DELETE FROM rag_extraction_retry",
            "DELETE FROM rag_extraction_version",
            "DELETE FROM rulebook_vector_chunk",
            "DELETE FROM rulebook_vector_index",
            "DELETE FROM game_system_definition_revision",
            "DELETE FROM rulebook_registration");
    private static final String DETACH_CATALOG = """
            UPDATE rulebook_catalog_revision
               SET rulebook_id = NULL, status = 'UNAVAILABLE', published = FALSE,
                   failure_reason = 'RAG_RESET', updated_at = now()
             WHERE rulebook_id IS NOT NULL
            """;

    private final DataSource dataSource;
    private final Set<String> activeProfiles;

    public DevelopmentRagResetService(DataSource dataSource, Set<String> activeProfiles) {
        this.dataSource = Objects.requireNonNull(dataSource, "data source must not be null");
        this.activeProfiles = activeProfiles == null ? Set.of() : Set.copyOf(activeProfiles);
    }

    public ResetResult reset(String confirmation) {
        verifyProfile();
        if (!CONFIRMATION.equals(confirmation)) {
            throw new ResetException("RESET_CONFIRMATION_REQUIRED");
        }
        try (Connection connection = dataSource.getConnection()) {
            boolean previousAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                int deleted = 0;
                try (PreparedStatement detach = connection.prepareStatement(DETACH_CATALOG)) {
                    detach.executeUpdate();
                }
                for (String statement : DELETE_STATEMENTS) {
                    try (PreparedStatement delete = connection.prepareStatement(statement)) {
                        deleted += delete.executeUpdate();
                    }
                }
                connection.commit();
                return new ResetResult(deleted, List.copyOf(RAG_TABLES));
            } catch (Exception exception) {
                try {
                    connection.rollback();
                } catch (SQLException rollback) {
                    exception.addSuppressed(rollback);
                }
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (ResetException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResetException("RESET_DATABASE_UNAVAILABLE", exception);
        }
    }

    private void verifyProfile() {
        if (activeProfiles.contains("production")) {
            throw new ResetException("RESET_FORBIDDEN_PRODUCTION");
        }
        if (activeProfiles.isEmpty()) {
            throw new ResetException("RESET_PROFILE_UNDEFINED");
        }
        if (!activeProfiles.contains("development")) {
            throw new ResetException("RESET_FORBIDDEN_PROFILE");
        }
    }

    public static List<String> ragTables() {
        return List.copyOf(RAG_TABLES);
    }

    public static Set<String> preservedTables() {
        return Set.copyOf(PRESERVED_TABLES);
    }

    public record ResetResult(int deletedRows, List<String> tables) {
    }

    public static final class ResetException extends RuntimeException {
        private final String code;

        public ResetException(String code) {
            super(code);
            this.code = code;
        }

        public ResetException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
