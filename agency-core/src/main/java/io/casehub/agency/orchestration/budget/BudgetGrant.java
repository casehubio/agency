package io.casehub.agency.orchestration.budget;

public sealed interface BudgetGrant {

    record Granted(BudgetPressure pressure, String correlationId) implements BudgetGrant {
        public Granted {
            java.util.Objects.requireNonNull(pressure, "pressure must not be null");
            java.util.Objects.requireNonNull(correlationId, "correlationId must not be null");
        }
    }

    record Denied(String reason, BudgetPressure pressure) implements BudgetGrant {
        public Denied {
            java.util.Objects.requireNonNull(reason, "reason must not be null");
            java.util.Objects.requireNonNull(pressure, "pressure must not be null");
        }
    }
}
