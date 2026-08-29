package com.dndmaster.gmeval.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.*;

public final class JsonEvalReportWriter {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    public Path write(EvalRunReport report, Path destination) {
        try { if (destination.getParent() != null) Files.createDirectories(destination.getParent()); Path temp = Files.createTempFile(destination.getParent() == null ? Path.of(".") : destination.getParent(), destination.getFileName().toString(), ".tmp"); try { mapper.writeValue(temp.toFile(), report); try { return Files.move(temp, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); } catch (AtomicMoveNotSupportedException e) { return Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING); } } finally { Files.deleteIfExists(temp); } }
        catch (IOException e) { throw new IllegalArgumentException("could not write evaluation report", e); }
    }
}
