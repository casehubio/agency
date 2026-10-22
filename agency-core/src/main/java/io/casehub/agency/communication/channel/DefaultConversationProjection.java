package io.casehub.agency.communication.channel;

import io.casehub.agency.deliberation.conversation.ConversationProjection;

class DefaultConversationProjection extends ConversationProjection {

    private static final String SENTINEL = "CH:";

    @Override
    protected String sentinel() {
        return SENTINEL;
    }

    @Override
    protected boolean isPointInitiator(String entryType) {
        return "RAISE".equals(entryType) || "POINT".equals(entryType);
    }

    @Override
    protected String statusAfter(String entryType) {
        return switch (entryType) {
            case "AGREE" -> "AGREED";
            case "COUNTER" -> "ACTIVE";
            case "DISPUTE" -> "DISPUTED";
            case "RESOLVE" -> "RESOLVED";
            default -> null;
        };
    }
}
