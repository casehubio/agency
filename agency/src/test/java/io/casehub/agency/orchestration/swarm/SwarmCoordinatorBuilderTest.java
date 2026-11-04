package io.casehub.agency.orchestration.swarm;

import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;

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
        assertThat(topology.aggregation())
                .extracting("budgetPool")
                .isNull();
    }

}
