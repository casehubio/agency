package io.casehub.agency.orchestration.budget;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AutonomyBudgetPolicyTest {

    @Test
    void defaults_returnsValidPolicy() {
        var policy = AutonomyBudgetPolicy.defaults();
        assertThat(policy.maxCalls()).isEqualTo(50);
        assertThat(policy.maxTokens()).isEqualTo(500_000L);
        assertThat(policy.reserveFraction()).isEqualTo(0.06);
        assertThat(policy.maxCallsPerAgent()).isEqualTo(10);
        assertThat(policy.maxTokensPerAgent()).isEqualTo(50_000L);
    }

    @Test
    void unlimited_returnsMaxValues() {
        var policy = AutonomyBudgetPolicy.unlimited();
        assertThat(policy.maxCalls()).isEqualTo(Integer.MAX_VALUE);
        assertThat(policy.maxTokens()).isEqualTo(Long.MAX_VALUE);
        assertThat(policy.reserveFraction()).isEqualTo(0.0);
        assertThat(policy.maxCallsPerAgent()).isEqualTo(Integer.MAX_VALUE);
        assertThat(policy.maxTokensPerAgent()).isEqualTo(Long.MAX_VALUE);
    }

    @Test
    void rejectsInvalidMaxCalls() {
        assertThatThrownBy(() -> new AutonomyBudgetPolicy(0, 500_000, 0.06, 10, 50_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxCalls");
    }

    @Test
    void rejectsInvalidMaxTokens() {
        assertThatThrownBy(() -> new AutonomyBudgetPolicy(50, 0, 0.06, 10, 50_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxTokens");
    }

    @Test
    void rejectsNegativeReserveFraction() {
        assertThatThrownBy(() -> new AutonomyBudgetPolicy(50, 500_000, -0.1, 10, 50_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserveFraction");
    }

    @Test
    void rejectsReserveFractionAboveOne() {
        assertThatThrownBy(() -> new AutonomyBudgetPolicy(50, 500_000, 1.1, 10, 50_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reserveFraction");
    }

    @Test
    void rejectsPerAgentCallsExceedingPool() {
        assertThatThrownBy(() -> new AutonomyBudgetPolicy(5, 500_000, 0.0, 10, 50_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxCallsPerAgent must be <= maxCalls");
    }

    @Test
    void rejectsPerAgentTokensExceedingPool() {
        assertThatThrownBy(() -> new AutonomyBudgetPolicy(50, 500, 0.0, 10, 50_000))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("maxTokensPerAgent must be <= maxTokens");
    }
}
