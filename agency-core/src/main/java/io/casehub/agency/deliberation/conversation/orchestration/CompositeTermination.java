package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.agency.deliberation.conversation.ConversationState;

import java.util.List;

public class CompositeTermination implements TerminationCondition<ConversationState> {

    private final List<TerminationCondition<ConversationState>> conditions;

    public CompositeTermination(
            List<TerminationCondition<ConversationState>> conditions) {
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public TerminationDecision evaluate(
            TerminationContext<ConversationState> context) {
        for (var condition : conditions) {
            var decision = condition.evaluate(context);
            if (!(decision instanceof TerminationDecision.Continue)) {
                return decision;
            }
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
