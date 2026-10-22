package io.casehub.agency.conversation.orchestration;

import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.agentic.termination.TerminationContext;
import io.casehub.agency.agentic.termination.TerminationDecision;
import io.casehub.agency.conversation.ConversationState;

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
