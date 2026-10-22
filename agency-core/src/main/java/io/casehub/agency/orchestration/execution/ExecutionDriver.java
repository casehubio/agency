package io.casehub.agency.orchestration.execution;

import io.smallrye.mutiny.Uni;

public interface ExecutionDriver<T> {
    Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext);
    void cancel();
    ExecutionState state();
}
