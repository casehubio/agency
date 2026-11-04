package io.casehub.agency.orchestration.swarm;

import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

class SwarmProvisioningAdvisorImplTokenRecorderTest {

    @Test
    void setTokenRecorder_accepts_null() {
        var advisor = new SwarmProvisioningAdvisorImpl(
                noOpProvider(), new SwarmContext());
        advisor.setTokenRecorder(null);
    }

    @Test
    void setTokenRecorder_accepts_consumer() {
        var advisor = new SwarmProvisioningAdvisorImpl(
                noOpProvider(), new SwarmContext());
        var recorded = new AtomicLong(0);
        advisor.setTokenRecorder(recorded::addAndGet);
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
