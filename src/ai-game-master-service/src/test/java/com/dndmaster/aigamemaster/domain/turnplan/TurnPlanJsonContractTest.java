package com.dndmaster.aigamemaster.domain.turnplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class TurnPlanJsonContractTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void canonicalObservationRoundTrips() throws Exception {
        for (String fixture : List.of("observation.json", "perception-check.json", "information-asymmetry.json",
                "state-effect.json", "story-progress.json")) {
            TurnPlan plan = read(fixture);
            assertEquals("1", plan.schemaVersion());
            new TurnPlanValidator().validate(plan);
            assertEquals(plan, mapper.readValue(mapper.writeValueAsString(plan), TurnPlan.class));
        }
    }

    @Test
    void resultBearingFieldsAreRejected() {
        assertThrows(Exception.class, () -> mapper.readValue("{\"schemaVersion\":\"1\",\"roll\":20}", TurnPlan.class));
    }

    private TurnPlan read(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/turnplan/" + name)) {
            return mapper.readValue(input, TurnPlan.class);
        }
    }
}
