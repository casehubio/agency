package io.casehub.agency.orchestration.topology;

import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;

final class TestAgentProviders {
    private TestAgentProviders() {}

    static AgentProvider noOp() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().empty();
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("no-op");
            }
        };
    }

    static AgentProvider returning(String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(response),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 100L, 80L, null, 0, false, null, null));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }

    static AgentProvider failing() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }

    static AgentProvider counting(java.util.concurrent.atomic.AtomicInteger counter, String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(AgentSessionConfig config) {
                counter.incrementAndGet();
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(response),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 100L, 80L, null, 0, false, null, null));
            }

            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }


}
