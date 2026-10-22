package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.agency.deliberation.conversation.ConversationState;

import java.util.Set;

public class AllAgreedTermination implements TerminationCondition<ConversationState> {

    private final Set<String> resolvedStatuses;

    public AllAgreedTermination(Set<String> resolvedStatuses) {
        this.resolvedStatuses = Set.copyOf(resolvedStatuses);
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<ConversationState> context) {
        var points = context.state().points();
        if (points.isEmpty()) {
            return TerminationDecision.Continue.INSTANCE;
        }
        boolean allResolved = points.values().stream()
                                    .allMatch(p -> resolvedStatuses.contains(p.status()));
        if (allResolved) {
            return new TerminationDecision.Complete("All points resolved");
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
