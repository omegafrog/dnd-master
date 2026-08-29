package com.dndmaster.gmeval.tuning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Atomic JSON persistence for role activation and rollback lineage. */
public final class JsonRoleModelConfigurationStore implements RoleModelConfigurationStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonRoleModelConfigurationStore(Path path) { this.path = path.toAbsolutePath(); }

    @Override public List<RoleModelConfiguration> load() {
        if (!Files.exists(path)) return List.of();
        try {
            if (Files.size(path) == 0) return List.of();
            return List.copyOf(mapper.readValue(path.toFile(), mapper.getTypeFactory()
                    .constructCollectionType(List.class, RoleModelConfiguration.class)));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("could not read role model configurations", failure);
        }
    }

    @Override public void save(List<RoleModelConfiguration> configurations) {
        try {
            Path parent = path.getParent() == null ? Path.of(".").toAbsolutePath() : path.getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                mapper.writeValue(temp.toFile(), configurations);
                try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException failure) {
            throw new IllegalArgumentException("could not write role model configurations", failure);
        }
    }
}
