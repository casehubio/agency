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
        assertThat(topology.aggregationStrategy())
                .extracting("budgetPool")
                .isNull();
    }

    @Test
    void loopEventSink_propagated_to_topology() {
        var captured = new java.util.ArrayList<io.casehub.agency.orchestration.loop.DecisionLoopEvent>();
        var topology = new SwarmCoordinatorBuilder()
                               .agentProvider(mock(AgentProvider.class))
                               .loopEventSink(captured::add)
                               .buildTopology();

        var ctx = new io.casehub.agency.orchestration.topology.TopologyContext(
                java.util.UUID.randomUUID(),
                new io.casehub.api.model.coordination.TopologyDescriptor(
                        io.casehub.api.model.coordination.TopologyType.CENTRALISED, java.util.Map.of()),
                mock(AgentProvider.class),
                new io.casehub.agency.orchestration.loop.DecisionLoopFactory(
                        name -> c -> "", new io.casehub.agency.summarisation.EventStreamBus<>()),
                io.casehub.platform.agent.budget.AutonomyBudgetPolicy.defaults(),
                io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults());

        topology.configure(ctx);

        assertThat(captured)
                .filteredOn(e -> e instanceof io.casehub.agency.orchestration.loop.DecisionLoopEvent.LoopTriggered)
                .hasSize(3);
    }

}
