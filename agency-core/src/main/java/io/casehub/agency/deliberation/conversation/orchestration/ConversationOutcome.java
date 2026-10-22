package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.core.AgentResult;
import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.agency.deliberation.conversation.ConversationState;

import java.time.Duration;
import java.util.List;

public record ConversationOutcome(
        ConversationState finalState,
        TerminationDecision terminationDecision,
        List<AgentResult> agentResults,
        int dispatchCount,
        Duration elapsed
) {
    public ConversationOutcome { agentResults = List.copyOf(agentResults); }
}
