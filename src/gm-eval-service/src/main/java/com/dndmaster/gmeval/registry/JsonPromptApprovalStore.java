package com.dndmaster.gmeval.registry;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Atomic durable store for approval evidence and audit history. */
public final class JsonPromptApprovalStore implements PromptApprovalStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonPromptApprovalStore(Path path) { this.path = path.toAbsolutePath(); }

    @Override public List<PromptApprovalRecord> load() {
        if (!Files.exists(path)) return List.of();
        try {
            if (Files.size(path) == 0) return List.of();
            return List.copyOf(mapper.readValue(path.toFile(), mapper.getTypeFactory()
                    .constructCollectionType(List.class, PromptApprovalRecord.class)));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("could not read prompt approvals", failure);
        }
    }

    @Override public void save(List<PromptApprovalRecord> records) {
        try {
            Path parent = path.getParent() == null ? Path.of(".").toAbsolutePath() : path.getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                mapper.writeValue(temp.toFile(), records);
                try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException failure) { throw new IllegalArgumentException("could not write prompt approvals", failure); }
    }
}
