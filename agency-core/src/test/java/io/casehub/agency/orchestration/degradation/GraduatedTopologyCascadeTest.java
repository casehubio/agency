package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.topology.GraduationState;
import io.casehub.api.model.coordination.DecisionScope;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraduatedTopologyCascadeTest {

    @Test
    void forceScope_setsToStimulusDriven() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.FULL_AUTONOMY), 5);
        state.forceScope("agent-1", DecisionScope.STIMULUS_DRIVEN);
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }

    @Test
    void upgrade_clampedByScopeFloor() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.FULL_AUTONOMY), 5);
        state.forceScope("agent-1", DecisionScope.STIMULUS_DRIVEN);
        state.upgrade("agent-1");
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }

    @Test
    void forceScope_immediatelyOverridesCurrentScope() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.TASK_ADAPTATION), 5);
        state.forceScope("agent-1", DecisionScope.STIMULUS_DRIVEN);
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }

    @Test
    void downgrade_stillWorksWithFloor() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.TASK_ADAPTATION), 5);
        state.forceScope("agent-1", DecisionScope.STIMULUS_DRIVEN);
        state.downgrade("agent-1");
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }
}
