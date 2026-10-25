package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.ConversationConstants;
import io.casehub.agency.deliberation.conversation.ConversationProjection;

import java.util.Set;

public class DefaultConversationProjection extends ConversationProjection {

    private static final Set<String> INFRASTRUCTURE_TYPES = Set.of(
            ConversationConstants.SUB_TASK_REQUEST,
            ConversationConstants.SUB_TASK_FINDING,
            ConversationConstants.SUB_TASK_ERROR,
            ConversationConstants.MEMO,
            ConversationConstants.FLAG_HUMAN,
            ConversationConstants.RESTART_CONTEXT);

    @Override
    protected String sentinel() {
        return "@@conv:";
    }

    @Override
    protected boolean isPointInitiator(String entryType) {
        return !INFRASTRUCTURE_TYPES.contains(entryType);
    }

    @Override
    protected String statusAfter(String entryType) {
        return "ACTIVE";
    }
}
