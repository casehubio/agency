package io.casehub.agency.conversation.orchestration;

import io.casehub.agency.agentic.AgentResult;
import io.casehub.agency.agentic.termination.TerminationDecision;
import io.casehub.agency.conversation.ConversationState;

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
