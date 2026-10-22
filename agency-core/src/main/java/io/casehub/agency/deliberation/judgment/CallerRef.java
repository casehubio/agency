package io.casehub.agency.deliberation.judgment;

import io.casehub.agency.core.AgentRef;
import org.jspecify.annotations.Nullable;

import java.util.Map;

public record CallerRef(
        String id,
        @Nullable String description,
        @Nullable Map<String, Object> routingHints,
        @Nullable AgentRef agentRef) {

    public static CallerRef agent(String id, @Nullable AgentRef ref) {
        return new CallerRef(id, null, null, ref);
    }

    public static CallerRef human(String id, Map<String, Object> routingHints) {
        return new CallerRef(id, null, routingHints, null);
    }
}
