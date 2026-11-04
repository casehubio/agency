package io.casehub.agency.orchestration.swarm;

import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SwarmCoordinatorBuilderTest {

    @Test
    void buildTopology_wiresBudgetPoolOnStrategies() {
        var topology = new SwarmCoordinatorBuilder()
                               .agentProvider(mock(AgentProvider.class))
                               .buildTopology();

        assertThat(topology.routingStrategy())
                .extracting("budgetPool")
                .isNotNull();
        assertThat(topology.aggregation())
                .extracting("budgetPool")
                .isNotNull();
    }

}
