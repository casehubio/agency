package io.casehub.agency.orchestration.degradation;

import io.casehub.agency.orchestration.budget.AgentBudgetView;
import java.util.Objects;

public class BudgetDegradationTrigger implements DegradationTrigger {

    private final AgentBudgetView view;

    public BudgetDegradationTrigger(AgentBudgetView view) {
        this.view = Objects.requireNonNull(view, "view must not be null");
    }

    @Override
    public boolean shouldDegrade() {
        return view.isBudgetExhausted();
    }
}
