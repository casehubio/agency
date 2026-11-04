package io.casehub.agency.orchestration.topology;

import io.casehub.api.model.coordination.DecisionScope;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GraduationStateTest {

    @Test
    void initial_scope_defaults_to_stimulus_driven() {
        var state = new GraduationState(Map.of(), 5);
        assertThat(state.scopeFor("unknown")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }

    @Test
    void initial_scope_uses_provided_value() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.TASK_ADAPTATION), 5);
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.TASK_ADAPTATION);
    }

    @Test
    void upgrade_from_stimulus_to_task_adaptation() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.STIMULUS_DRIVEN), 5);
        state.upgrade("agent-1");
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.TASK_ADAPTATION);
    }

    @Test
    void upgrade_from_task_adaptation_to_full_autonomy() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.TASK_ADAPTATION), 5);
        state.upgrade("agent-1");
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.FULL_AUTONOMY);
    }

    @Test
    void upgrade_caps_at_full_autonomy() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.FULL_AUTONOMY), 5);
        state.upgrade("agent-1");
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.FULL_AUTONOMY);
    }

    @Test
    void downgrade_from_full_autonomy_to_task_adaptation() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.FULL_AUTONOMY), 5);
        state.downgrade("agent-1");
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.TASK_ADAPTATION);
    }

    @Test
    void downgrade_from_task_adaptation_to_stimulus() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.TASK_ADAPTATION), 5);
        state.downgrade("agent-1");
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }

    @Test
    void downgrade_floors_at_stimulus_driven() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.STIMULUS_DRIVEN), 5);
        state.downgrade("agent-1");
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
    }

    @Test
    void scope_violation_causes_hard_downgrade() {
        var state = new GraduationState(
            Map.of("agent-1", DecisionScope.FULL_AUTONOMY), 5);
        state.recordFeedback("agent-1", FeedbackSignal.SCOPE_VIOLATION);
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.TASK_ADAPTATION);
    }

    @Test
    void sustained_success_upgrades_scope() {
        var state = new GraduationState(
                Map.of("agent-1", DecisionScope.STIMULUS_DRIVEN), 5);
        for (int i = 0; i < 3; i++) {
            state.recordFeedback("agent-1", FeedbackSignal.SUCCESS);
        }
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.STIMULUS_DRIVEN);
        state.recordFeedback("agent-1", FeedbackSignal.SUCCESS);
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.TASK_ADAPTATION);
    }

    @Test
    void sustained_failure_downgrades_scope() {
        var state = new GraduationState(
                Map.of("agent-1", DecisionScope.FULL_AUTONOMY), 4);
        state.recordFeedback("agent-1", FeedbackSignal.SUCCESS);
        state.recordFeedback("agent-1", FeedbackSignal.FAILURE);
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.FULL_AUTONOMY);
        state.recordFeedback("agent-1", FeedbackSignal.FAILURE);
        assertThat(state.scopeFor("agent-1")).isEqualTo(DecisionScope.TASK_ADAPTATION);
    }
}
