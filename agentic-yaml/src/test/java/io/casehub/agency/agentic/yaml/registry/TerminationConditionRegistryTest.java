package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.termination.GoalReached;
import io.casehub.agency.agentic.yaml.spec.TerminationSpec;
import io.casehub.agency.negotiation.AcceptedTermination;
import io.casehub.agency.negotiation.DeadlineTermination;
import io.casehub.agency.negotiation.TerminalOutcomeTermination;

import java.time.Duration;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminationConditionRegistryTest {

    private final TerminationConditionRegistry registry = new TerminationConditionRegistry();
    private final MvelExpressionEngine engine = new MvelExpressionEngine();

    @Test
    void goalReachedCompilesAndEvaluates() {
        var spec = new TerminationSpec.GoalReached("score > 0.9");
        var condition = registry.resolve(spec, engine);
        assertThat(condition).isInstanceOf(GoalReached.class);
    }

    @Test
    void goalReachedThrowsWithoutEngine() {
        var spec = new TerminationSpec.GoalReached("score > 0.9");
        assertThatThrownBy(() -> registry.resolve(spec, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ExpressionEngine");
    }

    @Test
    void resolvesAccepted() {
        var condition = registry.resolve(new TerminationSpec.Accepted(), null);
        assertThat(condition).isInstanceOf(AcceptedTermination.class);
    }

    @Test
    void resolvesTerminalOutcome() {
        var condition = registry.resolve(new TerminationSpec.TerminalOutcome(), null);
        assertThat(condition).isInstanceOf(TerminalOutcomeTermination.class);
    }

    @Test
    void resolvesDeadline() {
        var condition = registry.resolve(
                new TerminationSpec.Deadline(Duration.ofMinutes(30)), null);
        assertThat(condition).isInstanceOf(DeadlineTermination.class);
    }
}
