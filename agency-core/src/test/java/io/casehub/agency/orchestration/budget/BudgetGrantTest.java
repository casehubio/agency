package io.casehub.agency.orchestration.budget;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BudgetGrantTest {

    private final BudgetPressure pressure = new BudgetPressure(0.5, BudgetPriority.NORMAL);

    @Test
    void granted_rejectsNullPressure() {
        assertThatThrownBy(() -> new BudgetGrant.Granted(null, "corr-1"))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void granted_rejectsNullCorrelationId() {
        assertThatThrownBy(() -> new BudgetGrant.Granted(pressure, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void denied_rejectsNullReason() {
        assertThatThrownBy(() -> new BudgetGrant.Denied(null, pressure))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void denied_rejectsNullPressure() {
        assertThatThrownBy(() -> new BudgetGrant.Denied("reason", null))
                .isInstanceOf(NullPointerException.class);
    }
}
