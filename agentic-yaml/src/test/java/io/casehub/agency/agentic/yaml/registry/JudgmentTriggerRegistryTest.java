package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.deliberation.judgment.ConfidenceThreshold;
import io.casehub.agency.core.yaml.spec.JudgmentSpec.TriggerSpec;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgmentTriggerRegistryTest {

    private final JudgmentTriggerRegistry registry = new JudgmentTriggerRegistry();
    private final MvelExpressionEngine engine = new MvelExpressionEngine();

    @Test
    void confidenceThresholdCompilesExtractor() {
        var spec = new TriggerSpec.ConfidenceThreshold(0.8, "iteration");
        var trigger = registry.resolve(spec, engine);
        assertThat(trigger).isInstanceOf(ConfidenceThreshold.class);
    }

    @Test
    void confidenceThresholdThrowsWithoutEngine() {
        var spec = new TriggerSpec.ConfidenceThreshold(0.8, "iteration");
        assertThatThrownBy(() -> registry.resolve(spec, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ExpressionEngine");
    }
}
