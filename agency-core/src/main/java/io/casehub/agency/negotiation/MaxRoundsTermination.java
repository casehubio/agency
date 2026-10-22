package io.casehub.agency.negotiation;

import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.agentic.termination.TerminationContext;
import io.casehub.agency.agentic.termination.TerminationDecision;

public record MaxRoundsTermination(int maxRounds) implements TerminationCondition<NegotiationState> {
    public MaxRoundsTermination {
        if (maxRounds < 1) throw new IllegalArgumentException("maxRounds must be >= 1");
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        if (context.state().round() >= maxRounds) {
            return new TerminationDecision.Complete("Max rounds reached (" + maxRounds + ")");
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
