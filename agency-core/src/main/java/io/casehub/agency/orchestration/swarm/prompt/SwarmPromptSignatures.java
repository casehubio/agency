package io.casehub.agency.orchestration.swarm.prompt;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.agency.optimisation.prompt.PromptSignature;
import io.casehub.agency.orchestration.swarm.SwarmAggregationState;

public final class SwarmPromptSignatures {

    public static final PromptSignature TEAM_FORMATION = new PromptSignature(
            "swarm-team-formation",
            "Swarm coordinator team formation — selects agents and assigns roles for a swarm team",
            "You are a swarm coordinator. Form a team and assign work.",
            String.class,
            JsonNode.class);

    public static final PromptSignature COLLECTIVE_REASONING = new PromptSignature(
            "swarm-collective-reasoning",
            "Swarm collective reasoning — synthesises multi-agent observations into agreements, disagreements, and complementary insights",
            "You are synthesising observations from multiple swarm members.\n" +
            "Your goal is multi-perspective analysis, NOT summarisation.",
            String.class,
            SwarmAggregationState.class);

    public static final PromptSignature PROVISIONING = new PromptSignature(
            "swarm-provisioning",
            "Swarm provisioning advisory — decides whether to provision additional agents based on swarm state",
            "You are advising on whether to provision additional agents for a swarm.",
            String.class,
            JsonNode.class);

    private SwarmPromptSignatures() {}
}
