package io.casehub.agency.orchestration.swarm;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class SwarmRoutingStrategyTokenRecorderTest {

    @Test
    void setTokenRecorder_accepts_null() {
        var strategy = new SwarmRoutingStrategy(noOpProvider(),
                new SwarmExperienceSection(ExperienceReplayConfig.defaults()), 12);
        strategy.setTokenRecorder(null);
    }

    @Test
    void setTokenRecorder_accepts_consumer() {
        var strategy = new SwarmRoutingStrategy(noOpProvider(),
                new SwarmExperienceSection(ExperienceReplayConfig.defaults()), 12);
        var recorded = new AtomicLong(0);
        strategy.setTokenRecorder(recorded::addAndGet);
    }

    private static AgentProvider noOpProvider() {
        return new AgentProvider() {
            @Override public Multi<io.casehub.platform.agent.AgentEvent> invoke(AgentSessionConfig c) {
                return Multi.createFrom().empty();
            }
            @Override public AgentSession openSession(AgentSessionInit i) {
                throw new UnsupportedOperationException();
            }
        };
    }
}
