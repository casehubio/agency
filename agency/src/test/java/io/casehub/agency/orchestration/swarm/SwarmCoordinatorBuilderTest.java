package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.agency.optimisation.prompt.VariantSelector;
import io.casehub.agency.orchestration.swarm.prompt.SwarmOutcomeRecorder;
import io.casehub.agency.orchestration.swarm.prompt.SwarmPromptOutcomeProvider;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwarmCoordinatorBuilderTest {

    @Test
    void build_passesCustomiserToRoutingStrategy() {
        var customiser = mock(SystemPromptCustomiser.class);
        var coordinator = new SwarmCoordinatorBuilder()
                .agentProvider(mock(AgentProvider.class))
                .promptCustomiser(customiser)
                .build();

        assertThat(coordinator.routingStrategy())
                .extracting("promptCustomiser")
                .isSameAs(customiser);
    }

    @Test
    void build_selectsVariantSlotAndPropagates() {
        var selector = mock(VariantSelector.class);
        when(selector.selectSlot(any(), eq("swarm-coordination"))).thenReturn("experiment");

        var coordinator = new SwarmCoordinatorBuilder()
                .agentProvider(mock(AgentProvider.class))
                .variantSelector(selector)
                .build();

        assertThat(coordinator.routingStrategy())
                .extracting("variantSlot")
                .isEqualTo("experiment");
    }

    @Test
    void build_wrapsTerminationWithRecorder_whenOutcomeProviderSet() {
        var provider = new SwarmPromptOutcomeProvider();
        var coordinator = new SwarmCoordinatorBuilder()
                .agentProvider(mock(AgentProvider.class))
                .outcomeProvider(provider)
                .build();

        assertThat(coordinator.terminationCondition())
                .isInstanceOf(SwarmOutcomeRecorder.class);
    }

    @Test
    void build_usesDefaultTermination_whenNoOutcomeProvider() {
        var coordinator = new SwarmCoordinatorBuilder()
                .agentProvider(mock(AgentProvider.class))
                .build();

        assertThat(coordinator.terminationCondition())
                .isInstanceOf(SwarmConvergenceTermination.class);
    }
}
