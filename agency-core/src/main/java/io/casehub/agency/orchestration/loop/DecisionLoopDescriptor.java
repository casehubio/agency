package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;

import java.util.List;
import java.util.Objects;

public record DecisionLoopDescriptor(
        String name,
        ObservationStrategy observationStrategy,
        DecisionScope scope,
        CommunicationMode communicationMode,
        BudgetPriority budgetPriority,
        List<String> promptSectionNames
) {
    public DecisionLoopDescriptor {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(observationStrategy, "observationStrategy must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        Objects.requireNonNull(communicationMode, "communicationMode must not be null");
        Objects.requireNonNull(budgetPriority, "budgetPriority must not be null");
        promptSectionNames = promptSectionNames != null
                             ? List.copyOf(promptSectionNames) : List.of();
    }
}
