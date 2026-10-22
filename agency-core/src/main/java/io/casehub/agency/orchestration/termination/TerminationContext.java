package io.casehub.agency.orchestration.termination;

import io.casehub.agency.core.AgentResult;

import java.time.Duration;
import java.util.List;

public record TerminationContext<T>(
        T state,
        int iterationCount,
        Duration elapsed,
        List<AgentResult> results
) {
    public TerminationContext { results = List.copyOf(results); }
}
