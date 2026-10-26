package io.casehub.agency.orchestration.swarm;

import io.casehub.api.model.stigmergy.IntegrationPolicy;
import io.casehub.api.model.stigmergy.SwarmBootstrapContext;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor.ProvisioningContext;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SwarmProvisioningAdvisorImplTest {

    private AgentProvider agentProvider;
    private SwarmContext context;
    private SwarmInvocationTracker tracker;

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
        context = new SwarmContext();
        tracker = new SwarmInvocationTracker(SwarmInvocationPolicy.defaults());
    }

    @Test
    void advise_delegatesToLlm() {
        when(agentProvider.invoke(any())).thenAnswer(inv -> {
            var events = new java.util.ArrayList<AgentEvent>();
            events.add(new AgentEvent.TextDelta("""
                    {"shouldProvision": true, "reasoning": "Need more analysts"}
                    """));
            events.add(new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.01, 100L, 80L, "s1", 1, false, null, null));
            return io.smallrye.mutiny.Multi.createFrom().iterable(events);
        });

        var advisor = new SwarmProvisioningAdvisorImpl(agentProvider, context, tracker);
        var advice = advisor.advise(provisioningContext());
        assertThat(advice.shouldProvision()).isTrue();
        assertThat(advice.reasoning()).contains("analysts");
    }

    @Test
    void advise_fallsBackOnLlmFailure() {
        when(agentProvider.invoke(any())).thenThrow(new RuntimeException("LLM unavailable"));

        var advisor = new SwarmProvisioningAdvisorImpl(agentProvider, context, tracker);
        var advice = advisor.advise(provisioningContext());
        assertThat(advice).isNotNull();
        assertThat(advice.reasoning()).contains("heuristic");
    }

    private ProvisioningContext provisioningContext() {
        return new ProvisioningContext(
                UUID.randomUUID(),
                mock(SwarmBootstrapContext.class),
                Set.of("analysis"),
                mock(IntegrationPolicy.class));
    }
}
