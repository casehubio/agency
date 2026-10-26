package io.casehub.agency.orchestration.swarm;

@FunctionalInterface
public interface SwarmPromptSection {
    String contribute(SwarmContext context);
}
