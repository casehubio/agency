package io.casehub.agency.negotiation;

import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.agentic.termination.TerminationContext;
import io.casehub.agency.agentic.termination.TerminationDecision;

import java.time.Instant;
import java.util.Objects;

public record DeadlineTermination(Instant deadline) implements TerminationCondition<NegotiationState> {
    public DeadlineTermination {
        Objects.requireNonNull(deadline);
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        var proposals = context.state().proposals();
        if (proposals.isEmpty()) return TerminationDecision.Continue.INSTANCE;
        Instant latestActivity = proposals.getLast().createdAt();
        if (latestActivity.isAfter(deadline)) {
            return new TerminationDecision.Complete("Deadline exceeded");
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
