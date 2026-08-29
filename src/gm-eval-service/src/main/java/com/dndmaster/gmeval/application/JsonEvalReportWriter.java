package com.dndmaster.gmeval.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.file.*;

public final class JsonEvalReportWriter {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    public Path write(EvalRunReport report, Path destination) {
        try { if (destination.getParent() != null) Files.createDirectories(destination.getParent()); mapper.writeValue(destination.toFile(), report); return destination; }
        catch (IOException e) { throw new IllegalArgumentException("could not write evaluation report", e); }
    }
}
