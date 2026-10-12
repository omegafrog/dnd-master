package com.dndmaster.userpcagent;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class JsonSchemaRuntimeCompatibilityTest {
    @Test
    void validatesSchemasOnTheActualUserPcAgentRuntimeClasspath() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        var schema = mapper.readTree("{\"type\":\"object\",\"required\":[\"stageId\"],\"properties\":{\"stageId\":{\"const\":\"stage-1\"}},\"additionalProperties\":false}");
        Class<?> specificationVersion = Class.forName("com.networknt.schema.SpecificationVersion");
        @SuppressWarnings({"unchecked", "rawtypes"})
        Object draft202012 = Enum.valueOf((Class<? extends Enum>) specificationVersion, "DRAFT_2020_12");
        Class<?> schemaRegistry = Class.forName("com.networknt.schema.SchemaRegistry");
        Object registry = schemaRegistry.getMethod("withDefaultDialect", specificationVersion).invoke(null, draft202012);
        Object validator = schemaRegistry.getMethod("getSchema", com.fasterxml.jackson.databind.JsonNode.class).invoke(registry, schema);
        var validate = validator.getClass().getMethod("validate", com.fasterxml.jackson.databind.JsonNode.class);

        assertThat((java.util.List<?>) validate.invoke(validator, mapper.readTree("{\"stageId\":\"stage-1\"}"))).isEmpty();
        assertThat((java.util.List<?>) validate.invoke(validator, mapper.readTree("{\"stageId\":\"wrong\"}"))).isNotEmpty();
    }
}
