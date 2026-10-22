package io.casehub.agency.negotiation;

import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.agentic.termination.TerminationContext;
import io.casehub.agency.agentic.termination.TerminationDecision;

public record AcceptedTermination() implements TerminationCondition<NegotiationState> {
    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        if (context.state().outcome() == NegotiationOutcome.AGREED) {
            return new TerminationDecision.Complete("Proposal accepted");
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
