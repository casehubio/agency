package io.casehub.agency.deliberation.protocol;

import io.casehub.platform.coordination.CoordinationStateStore;
import io.casehub.platform.coordination.protocol.PlaybookProtocolEvaluator;
import io.casehub.platform.coordination.protocol.ProtocolAdvisory;
import io.casehub.platform.coordination.protocol.ProtocolInstanceKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationProtocolTemplateTest {

    private PlaybookProtocolEvaluator evaluator;
    private static final String PROTOCOL = "NEGOTIATION";
    private static final ProtocolInstanceKey KEY = new ProtocolInstanceKey(PROTOCOL, "channel-1");

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        evaluator = new PlaybookProtocolEvaluator(CoordinationStateStore.noOp());
        try (InputStream is = getClass().getClassLoader()
                                        .getResourceAsStream("protocol-templates/negotiation.protocol.yaml")) {
            assertThat(is).as("Template must exist on classpath").isNotNull();
            Map<String, Object> statesMap = new Yaml().load(is);
            evaluator.register(PROTOCOL, statesMap);
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void template_loads_and_registers_successfully() {
        List<ProtocolAdvisory> advisories = evaluator.evaluate(KEY, "INFORM", Map.of());
        assertThat(advisories).isEmpty();
    }

    @Test
    void propose_transitions_to_proposed() {
        evaluator.evaluate(KEY, "PROPOSE", Map.of());
        List<ProtocolAdvisory> advisories = evaluator.evaluate(KEY, "INFORM", Map.of());
        assertThat(advisories).hasSize(1);
        assertThat(advisories.getFirst().message()).contains("Active proposal");
    }

    @Test
    void done_after_propose_transitions_to_responding() {
        evaluator.evaluate(KEY, "PROPOSE", Map.of());
        evaluator.evaluate(KEY, "DONE", Map.of());
        List<ProtocolAdvisory> advisories = evaluator.evaluate(KEY, "INFORM", Map.of());
        assertThat(advisories).hasSize(1);
        assertThat(advisories.getFirst().message()).contains("Responses in progress");
    }

    @Test
    void counter_propose_returns_to_proposed() {
        evaluator.evaluate(KEY, "PROPOSE", Map.of());
        evaluator.evaluate(KEY, "DONE", Map.of());
        evaluator.evaluate(KEY, "PROPOSE", Map.of());
        List<ProtocolAdvisory> advisories = evaluator.evaluate(KEY, "INFORM", Map.of());
        assertThat(advisories).hasSize(1);
        assertThat(advisories.getFirst().message()).contains("Active proposal");
    }

    @Test
    void monitoring_state_has_no_advisory() {
        List<ProtocolAdvisory> advisories = evaluator.evaluate(KEY, "DONE", Map.of());
        assertThat(advisories).isEmpty();
    }
}
