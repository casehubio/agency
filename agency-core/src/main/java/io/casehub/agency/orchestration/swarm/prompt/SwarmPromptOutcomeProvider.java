package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.VariantOutcome;
import io.casehub.agency.optimisation.prompt.evolution.PromptOutcomeProvider;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@ApplicationScoped
public class SwarmPromptOutcomeProvider implements PromptOutcomeProvider {

    private final CopyOnWriteArrayList<SwarmOutcomeRecorder> recorders = new CopyOnWriteArrayList<>();

    public void register(SwarmOutcomeRecorder recorder) {
        recorders.add(recorder);
    }

    @Override
    public List<VariantOutcome> outcomes(String signatureId) {
        return recorders.stream()
                .flatMap(r -> r.outcomes(signatureId).stream())
                .toList();
    }
}
