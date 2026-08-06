package com.dndmaster.adventure;

import static org.junit.jupiter.api.Assertions.*;

import com.dndmaster.adventure.application.runtime.*;
import java.util.UUID;
import java.util.List;
import org.junit.jupiter.api.Test;

class GmProviderQualityGateTest {
    @Test
    void passes_only_at_exact_quality_gate_thresholds() {
        GmQualityGateReport report = new GmQualityGateReport(100, 99, 95, 95, 0, 0, 0, 4.0);

        assertTrue(report.passed());
        assertFalse(new GmQualityGateReport(100, 98, 95, 95, 0, 0, 0, 4.0).passed());
        assertFalse(new GmQualityGateReport(100, 99, 94, 95, 0, 0, 0, 4.0).passed());
        assertFalse(new GmQualityGateReport(100, 99, 95, 95, 1, 0, 0, 4.0).passed());
        assertFalse(new GmQualityGateReport(100, 99, 95, 95, 0, 0, 0, 3.99).passed());
    }

    @Test
    void provider_switch_preserves_session_state_and_rejects_mid_turn_mixing() {
        UUID session = UUID.randomUUID();
        InMemoryGmProviderBindingRepository repository = new InMemoryGmProviderBindingRepository();
        repository.save(new ProviderBinding(session, new GmProviderSelection("ollama", "qwen3:8b", "medium"), 17));
        GmProviderBindingService service = new GmProviderBindingService(repository);

        ProviderBinding switched = service.switchProvider(session, 17,
                new GmProviderSelection("openai", "gpt-5.6-luna", "medium"));

        assertEquals(session, switched.sessionId());
        assertEquals(18, switched.stateVersion());
        assertEquals("openai", switched.selection().provider());
        service.beginTurn(session, 18);
        assertThrows(IllegalStateException.class, () -> service.switchProvider(session, 19,
                new GmProviderSelection("ollama", "qwen3:8b", "medium")));
    }

    @Test
    void deployment_gate_is_production_enforceable() {
        GmProviderQualityGateService gate = new GmProviderQualityGateService();
        GmQualityGateReport report = new GmQualityGateReport(100, 99, 95, 95, 0, 0, 0, 4.0);

        assertTrue(gate.requireDeployable(report));
        assertThrows(IllegalStateException.class, () -> gate.requireDeployable(
                new GmQualityGateReport(100, 98, 95, 95, 0, 0, 0, 4.0)));
    }

    @Test
    void evaluator_builds_gate_report_from_case_results() {
        List<GmQualityCaseResult> cases = java.util.stream.IntStream.range(0, 100)
                .mapToObj(i -> new GmQualityCaseResult(true, true, true, false, false, false, 4.0)).toList();

        GmQualityGateReport report = new GmQualityGateEvaluator().evaluate(cases);

        assertTrue(report.passed());
        assertTrue(new GmProviderQualityGateService().requireDeployable(cases));
    }
}
