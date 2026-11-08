package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.orchestration.loop.DecisionLoopEvent;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SwarmCoordinatorBuilderTest {

    @Test
    void buildTopology_does_not_inject_budgetPool_on_strategies() {
        var topology = new SwarmCoordinatorBuilder()
                               .agentProvider(mock(AgentProvider.class))
                               .buildTopology();

        assertThat(topology.routingStrategy())
                .extracting("budgetPool")
                .isNull();
        assertThat(topology.aggregationStrategy())
                .extracting("budgetPool")
                .isNull();
    }


    @Test
    void buildTopology_customLoopEventSink_accepted() {
        var events = new ArrayList<DecisionLoopEvent>();
        var topology = new SwarmCoordinatorBuilder()
                               .agentProvider(mock(AgentProvider.class))
                               .loopEventSink(events::add)
                               .buildTopology();

        assertThat(topology).isNotNull();
    }

    @Test
    void buildTopology_defaultLoopEventSink_noOpDoesNotThrow() {
        var topology = new SwarmCoordinatorBuilder()
                               .agentProvider(mock(AgentProvider.class))
                               .buildTopology();
        assertThat(topology).isNotNull();
    }

    @Test
    void loopEventSink_rejectsNull() {
        org.junit.jupiter.api.Assertions.assertThrows(
                NullPointerException.class,
                () -> new SwarmCoordinatorBuilder().loopEventSink(null));
    }
}
