package io.casehub.agency.agentic.coalition;

@FunctionalInterface
public interface CoalitionEvaluator {
    CoalitionScore evaluate(CoalitionProposal proposal, CoalitionContext context);
}
