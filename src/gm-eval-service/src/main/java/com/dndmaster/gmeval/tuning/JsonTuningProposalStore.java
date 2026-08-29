package com.dndmaster.gmeval.tuning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Atomic JSON adapter for operator/audit persistence. */
public final class JsonTuningProposalStore implements TuningProposalStore {
    private final Path path;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    public JsonTuningProposalStore(Path path) { this.path = path.toAbsolutePath(); }

    @Override public List<TuningProposalRecord> load() {
        if (!Files.exists(path)) return List.of();
        try {
            if (Files.size(path) == 0) return List.of();
            return List.copyOf(mapper.readValue(path.toFile(), mapper.getTypeFactory()
                    .constructCollectionType(List.class, TuningProposalRecord.class)));
        } catch (IOException | RuntimeException failure) {
            throw new IllegalArgumentException("could not read tuning proposals", failure);
        }
    }

    @Override public void save(List<TuningProposalRecord> records) {
        try {
            Path parent = path.getParent() == null ? Path.of(".").toAbsolutePath() : path.getParent();
            Files.createDirectories(parent);
            Path temp = Files.createTempFile(parent, path.getFileName().toString(), ".tmp");
            try {
                mapper.writeValue(temp.toFile(), records);
                try { Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException unsupported) { Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING); }
            } finally { Files.deleteIfExists(temp); }
        } catch (IOException failure) {
            throw new IllegalArgumentException("could not write tuning proposals", failure);
        }
    }
}
