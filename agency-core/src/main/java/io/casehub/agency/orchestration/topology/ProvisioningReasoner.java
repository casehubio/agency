package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor.ProvisioningContext;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

class ProvisioningReasoner implements ContentSummariser<ObservationEvent, DecisionOutcome> {

    private final SwarmProvisioningAdvisor advisor;
    private final UUID caseId;

    ProvisioningReasoner(SwarmProvisioningAdvisor advisor, UUID caseId) {
        this.advisor = advisor;
        this.caseId = caseId;
    }

    @Override
    public CompletionStage<DecisionOutcome> summarise(
            List<ObservationEvent> items, @Nullable DecisionOutcome previous) {
        var ctx = new ProvisioningContext(caseId, null, Set.of(), null);
        var advice = advisor.advise(ctx);
        return CompletableFuture.completedFuture(new DecisionOutcome.Provision(advice));
    }
}
