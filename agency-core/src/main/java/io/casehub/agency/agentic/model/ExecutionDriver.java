package io.casehub.agency.agentic.model;

import io.smallrye.mutiny.Uni;

public interface ExecutionDriver<T> {
    Uni<ExecutionResult> execute(ExecutionModel<T> model, T initialContext);
    void cancel();
    ExecutionState state();
}
