package io.casehub.agency.orchestration.swarm.prompt;

import io.casehub.agency.optimisation.prompt.VariantOutcome;
import io.casehub.agency.orchestration.swarm.SwarmContext;
import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class SwarmOutcomeRecorder implements TerminationCondition<SwarmContext> {
    private static final SwarmOutcomeMapper TEAM_FORMATION_MAPPER = new SwarmOutcomeMapper(SwarmPromptSignatures.TEAM_FORMATION.id());
    private static final SwarmOutcomeMapper REASONING_MAPPER      = new SwarmOutcomeMapper(SwarmPromptSignatures.COLLECTIVE_REASONING.id());
    private static final SwarmOutcomeMapper PROVISIONING_MAPPER   = new SwarmOutcomeMapper(SwarmPromptSignatures.PROVISIONING.id());


    private final TerminationCondition<SwarmContext> delegate;
    private final String variantSlot;
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<VariantOutcome>> outcomes = new ConcurrentHashMap<>();
    private final AtomicBoolean recorded = new AtomicBoolean(false);

    public SwarmOutcomeRecorder(TerminationCondition<SwarmContext> delegate,
                                String variantSlot) {
        this.delegate = delegate;
        this.variantSlot = variantSlot;
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<SwarmContext> context) {
        var decision = delegate.evaluate(context);
        if (isTerminal(decision) && recorded.compareAndSet(false, true)) {
            recordOutcomes(context.state(), decision);
        }
        return decision;
    }

    public List<VariantOutcome> outcomes(String signatureId) {
        return List.copyOf(outcomes.getOrDefault(signatureId, new CopyOnWriteArrayList<>()));
    }

    private boolean isTerminal(TerminationDecision decision) {
        return decision instanceof TerminationDecision.Complete
                || decision instanceof TerminationDecision.Failed
                || decision instanceof TerminationDecision.Escalate;
    }

    private void recordOutcomes(SwarmContext state, TerminationDecision decision) {
        var    progress   = state.swarmProgress();
        double similarity = progress != null ? progress.consensusScore() : 0.0;
        String outcome    = extractOutcome(decision);

        for (var mapper : List.of(TEAM_FORMATION_MAPPER, REASONING_MAPPER, PROVISIONING_MAPPER)) {
            var mapped = mapper.map(variantSlot, outcome, similarity, null);
            outcomes.computeIfAbsent(mapped.signatureId(), k -> new CopyOnWriteArrayList<>()).add(mapped);
        }
    }

    private String extractOutcome(TerminationDecision decision) {
        if (decision instanceof TerminationDecision.Complete c) {
            String desc = c.result() != null ? c.result().toString().toLowerCase() : "";
            return desc.contains("converged") ? "CONVERGED" : "COMPLETED";
        }
        if (decision instanceof TerminationDecision.Escalate) {return "DEADLOCK";}
        return "FAILED";
    }
}
