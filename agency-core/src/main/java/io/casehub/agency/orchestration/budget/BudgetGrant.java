package io.casehub.agency.orchestration.budget;

public sealed interface BudgetGrant {

    record Granted(BudgetPressure pressure, String correlationId) implements BudgetGrant {}

    record Denied(String reason, BudgetPressure pressure) implements BudgetGrant {}
}
