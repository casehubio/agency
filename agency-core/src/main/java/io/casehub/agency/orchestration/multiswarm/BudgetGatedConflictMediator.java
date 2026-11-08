package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.orchestration.budget.AgentBudgetView;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetGrant;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class BudgetGatedConflictMediator implements ConflictMediator {

    private final ConflictMediator primary;
    private final ConflictMediator fallback;
    private volatile @Nullable AgentBudgetView budgetView;

    public BudgetGatedConflictMediator(ConflictMediator primary, ConflictMediator fallback) {
        this.primary = Objects.requireNonNull(primary);
        this.fallback = Objects.requireNonNull(fallback);
    }

    public void bindPool(AutonomyBudgetPool pool) {
        this.budgetView = pool.createView("l3-mediator", BudgetPriority.RESERVED);
    }

    @Override
    public MediationResult mediate(ConflictContext context) {
        var view = this.budgetView;
        if (view == null) {
            return fallback.mediate(context);
        }
        var grant = view.tryAcquire();
        if (grant instanceof BudgetGrant.Denied) {
            return fallback.mediate(context);
        }
        try {
            return primary.mediate(context);
        } catch (Exception e) {
            return fallback.mediate(context);
        }
    }
}
