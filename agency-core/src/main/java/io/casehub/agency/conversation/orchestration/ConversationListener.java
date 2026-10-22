package io.casehub.agency.conversation.orchestration;

import io.casehub.agency.agentic.termination.TerminationDecision;
import io.casehub.agency.conversation.ConversationState;

import java.time.Duration;

@FunctionalInterface
public interface ConversationListener {
    void onDispatch(ConversationState state, TerminationDecision decision,
                    int dispatchCount, Duration elapsed);
}
