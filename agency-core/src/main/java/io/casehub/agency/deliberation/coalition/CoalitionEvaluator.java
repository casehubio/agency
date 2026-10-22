package io.casehub.agency.deliberation.coalition;

@FunctionalInterface
public interface CoalitionEvaluator {
    CoalitionScore evaluate(CoalitionProposal proposal, CoalitionContext context);
}
