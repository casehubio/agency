package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.orchestration.termination.TerminationCondition;
import io.casehub.agency.orchestration.termination.TerminationContext;
import io.casehub.agency.orchestration.termination.TerminationDecision;

public class SwarmConvergenceTermination implements TerminationCondition<SwarmContext> {

    private final double threshold;

    public SwarmConvergenceTermination(double threshold) {
        if (threshold < 0.0 || threshold > 1.0)
            throw new IllegalArgumentException("threshold must be in [0.0, 1.0]");
        this.threshold = threshold;
    }

    @Override
    public TerminationDecision evaluate(TerminationContext<SwarmContext> context) {
        var progress = context.state().swarmProgress();
        if (progress == null) return TerminationDecision.Continue.INSTANCE;

        double consensus = progress.consensusScore();
        double stability = progress.stabilityScore();

        if (consensus >= threshold && stability >= threshold) {
            return new TerminationDecision.Complete("Swarm converged — consensus: %.2f, stability: %.2f".formatted(consensus, stability));
        }
        if (stability >= threshold && consensus < threshold) {
            return new TerminationDecision.Escalate("Deadlock — stable (%.2f) but low consensus (%.2f)".formatted(stability, consensus));
        }
        return TerminationDecision.Continue.INSTANCE;
    }

    public double threshold() { return threshold; }
}
