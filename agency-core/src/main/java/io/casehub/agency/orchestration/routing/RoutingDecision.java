package io.casehub.agency.orchestration.routing;

import io.casehub.agency.core.AgentRef;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public sealed interface RoutingDecision
        permits RoutingDecision.Selected, RoutingDecision.Unresolvable,
                RoutingDecision.Escalate, RoutingDecision.SwarmAssignment {

    record Selected(List<AgentRef> agents, @Nullable String reason) implements RoutingDecision {
        public Selected { agents = List.copyOf(agents); }
        public Selected(List<AgentRef> agents) { this(agents, null); }
    }

    record Unresolvable(String reason) implements RoutingDecision {}

    record Escalate(String reason) implements RoutingDecision {}

    record SwarmAssignment(
            List<AgentRef> agents,
            Map<String, List<AgentRef>> teams,
            @Nullable String strategy,
            @Nullable String reason
    ) implements RoutingDecision {
        public SwarmAssignment {
            agents = List.copyOf(agents);
            teams = teams.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(java.util.Map.Entry::getKey, e -> List.copyOf(e.getValue())));
        }
    }

}
