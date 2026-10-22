package io.casehub.agency.deliberation.conversation.orchestration;

import io.casehub.agency.orchestration.termination.TerminationDecision;
import io.casehub.agency.deliberation.conversation.ConversationState;

import java.time.Duration;

@FunctionalInterface
public interface ConversationListener {
    void onDispatch(ConversationState state, TerminationDecision decision,
                    int dispatchCount, Duration elapsed);
}
