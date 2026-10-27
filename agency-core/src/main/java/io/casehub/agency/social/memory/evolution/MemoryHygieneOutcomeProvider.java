package io.casehub.agency.social.memory.evolution;

@FunctionalInterface
public interface MemoryHygieneOutcomeProvider {
    MemoryHygieneOutcome outcomes(String agentId);
}
