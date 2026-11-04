package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor.ProvisioningAdvice;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProvisioningReasonerTest {

    @Test
    void summarise_delegatesToAdvisor() throws Exception {
        var advice = new ProvisioningAdvice(true, null, null, "scale needed");
        SwarmProvisioningAdvisor advisor = ctx -> advice;

        var reasoner = new ProvisioningReasoner(advisor, new SwarmContext(), UUID.randomUUID());
        var event = ObservationEvent.of("provisioning-changed", "trigger");
        var result = reasoner.summarise(List.of(event), null)
                .toCompletableFuture().get();

        assertThat(result).isInstanceOf(DecisionOutcome.Provision.class);
        assertThat(((DecisionOutcome.Provision) result).advice()).isEqualTo(advice);
    }

    @Test
    void summarise_noProvision_returnsFalseAdvice() throws Exception {
        var advice = new ProvisioningAdvice(false, null, null, "stable");
        SwarmProvisioningAdvisor advisor = ctx -> advice;

        var reasoner = new ProvisioningReasoner(advisor, new SwarmContext(), UUID.randomUUID());
        var result = reasoner.summarise(
                List.of(ObservationEvent.of("tick", "poll")), null)
                .toCompletableFuture().get();

        assertThat(((DecisionOutcome.Provision) result).advice().shouldProvision()).isFalse();
    }
}
