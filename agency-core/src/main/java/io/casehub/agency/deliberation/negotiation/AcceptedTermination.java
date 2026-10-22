package io.casehub.agency.deliberation.negotiation;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;

public record AcceptedTermination() implements TerminationCondition<NegotiationState> {
    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        if (context.state().outcome() == NegotiationOutcome.AGREED) {
            return new TerminationDecision.Complete("Proposal accepted");
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
