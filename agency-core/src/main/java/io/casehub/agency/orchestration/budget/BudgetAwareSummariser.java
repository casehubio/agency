package io.casehub.agency.orchestration.budget;

import io.casehub.agency.summarisation.ContentSummariser;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BudgetAwareSummariser<T, R> implements ContentSummariser<T, R> {

    private final ContentSummariser<T, R> delegate;
    private final AgentBudgetView view;

    public BudgetAwareSummariser(ContentSummariser<T, R> delegate, AgentBudgetView view) {
        this.delegate = delegate;
        this.view = view;
    }

    @Override
    public CompletionStage<R> summarise(List<T> items, @Nullable R previous) {
        BudgetGrant grant = view.tryAcquire();
        if (grant instanceof BudgetGrant.Denied) {
            return CompletableFuture.completedFuture(previous);
        }
        return delegate.summarise(items, previous);
    }
}
