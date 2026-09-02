package com.dndmaster.aigamemaster.infrastructure.ai;
import com.fasterxml.jackson.databind.JsonNode;
public record StructuredResponseContract<T>(JsonNode outputSchema, StructuredResponseParser<T> parser) { }
