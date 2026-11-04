package io.casehub.agency.orchestration.budget;

public record BudgetPressure(double utilisation, BudgetPriority effectivePriority) {

    public BudgetPressure {
        if (utilisation < 0.0 || utilisation > 1.0)
            throw new IllegalArgumentException("utilisation must be in [0.0, 1.0]");
    }
}
