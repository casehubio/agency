package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.agency.deliberation.conversation.ConversationState;

public class SupervisorTermination implements TerminationCondition<ConversationState> {

    private final String supervisorRole;
    private final String signalEntryType;

    public SupervisorTermination(String supervisorRole, String signalEntryType) {
        this.supervisorRole = supervisorRole;
        this.signalEntryType = signalEntryType;
    }

    public SupervisorTermination(String supervisorRole) {
        this(supervisorRole, "TERMINATE");
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<ConversationState> context) {
        for (var point : context.state().points().values()) {
            for (var entry : point.thread()) {
                if (supervisorRole.equals(entry.role())
                    && signalEntryType.equals(entry.entryType())) {
                    return new TerminationDecision.Complete(entry.content());
                }
            }
        }
        return TerminationDecision.Continue.INSTANCE;
    }
}
