package io.casehub.agency.deliberation.negotiation;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;

public record TerminalOutcomeTermination() implements TerminationCondition<NegotiationState> {
    @Override
    public TerminationDecision evaluate(TerminationContext<NegotiationState> context) {
        return switch (context.state().outcome()) {
            case AGREED -> new TerminationDecision.Complete("Proposal accepted");
            case DEADLOCKED -> new TerminationDecision.Failed("Negotiation deadlocked");
            case WITHDRAWN -> new TerminationDecision.Failed("Party withdrew");
            case PENDING -> TerminationDecision.Continue.INSTANCE;
        };
    }
}
