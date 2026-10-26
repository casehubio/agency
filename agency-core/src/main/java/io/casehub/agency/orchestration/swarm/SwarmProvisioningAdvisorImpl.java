package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

public class SwarmProvisioningAdvisorImpl implements SwarmProvisioningAdvisor {

    private static final System.Logger LOG =
            System.getLogger(SwarmProvisioningAdvisorImpl.class.getName());

    private final           AgentProvider          agentProvider;
    private final           SwarmContext           context;
    private final           SwarmInvocationTracker invocationTracker;
    private final @Nullable SystemPromptCustomiser promptCustomiser;
    private @Nullable       String                 variantSlot;


    public SwarmProvisioningAdvisorImpl(AgentProvider agentProvider,
                                        SwarmContext context,
                                        SwarmInvocationTracker invocationTracker,
                                        @Nullable SystemPromptCustomiser promptCustomiser) {
        this.agentProvider     = agentProvider;
        this.context           = context;
        this.invocationTracker = invocationTracker;
        this.promptCustomiser  = promptCustomiser;
    }

    public SwarmProvisioningAdvisorImpl(AgentProvider agentProvider,
                                        SwarmContext context,
                                        SwarmInvocationTracker invocationTracker) {
        this(agentProvider, context, invocationTracker, null);
    }

    public void setVariantSlot(@Nullable String variantSlot) {
        this.variantSlot = variantSlot;
    }


    @Override
    public ProvisioningAdvice advise(ProvisioningContext provisioningContext) {
        if (!invocationTracker.tryAcquire("provisioning")) {
            LOG.log(System.Logger.Level.WARNING, "LLM budget exhausted for provisioning — using heuristic");
            return heuristicAdvice(provisioningContext);
        }

        try {
            var prompt = buildPrompt(provisioningContext);
            if (promptCustomiser != null) {
                prompt = promptCustomiser.customise(prompt,
                                                    io.casehub.agency.orchestration.swarm.prompt.SwarmPromptSignatures.PROVISIONING.id(),
                                                    variantSlot != null ? variantSlot : "control");
            }
            var config = AgentSessionConfig.of(prompt,
                                               "Should we provision an additional agent? Respond in JSON: " +
                                               "{\"shouldProvision\": true/false, \"reasoning\": \"explanation\"}");

            var result = StructuredAgentInvoker.invokeRaw(agentProvider, config);
            if (result instanceof StructuredAgentInvoker.InvocationResult.Success<?> s) {
                invocationTracker.recordTokens(s.metadata().inputTokens() + s.metadata().outputTokens());
                var     json            = (com.fasterxml.jackson.databind.JsonNode) s.value();
                boolean shouldProvision = json.has("shouldProvision") && json.get("shouldProvision").asBoolean();
                String  reasoning       = json.has("reasoning") ? json.get("reasoning").asText() : null;
                return new ProvisioningAdvice(shouldProvision,
                                              provisioningContext.requestedCapabilities(),
                                              provisioningContext.currentPolicy(), reasoning);
            }
            LOG.log(System.Logger.Level.WARNING, "Provisioning LLM call failed — using heuristic");
            return heuristicAdvice(provisioningContext);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Provisioning LLM call failed", e);
            return heuristicAdvice(provisioningContext);
        }
    }

    private String buildPrompt(ProvisioningContext provisioningContext) {
        var sb         = new StringBuilder("You are advising on whether to provision additional agents for a swarm.\n\n");
        var swarmState = provisioningContext.swarmState();
        if (swarmState != null) {
            var progress = swarmState.swarmProgress();
            if (progress != null) {
                sb.append("## Current Swarm State\n");
                sb.append("- Exploration pace: %.2f\n".formatted(progress.explorationPace()));
                sb.append("- Consensus: %.2f\n".formatted(progress.consensusScore()));
                sb.append("- Stability: %.2f\n".formatted(progress.stabilityScore()));
            }
        }
        sb.append("\n## Requested Capabilities\n");
        provisioningContext.requestedCapabilities().forEach(
                cap -> sb.append("- %s\n".formatted(cap)));

        var coordinatorPrompt = context.activeStrategy();
        if (coordinatorPrompt != null) {
            sb.append("\n## Active Coordination Strategy\n");
            sb.append(coordinatorPrompt).append("\n");
        }

        return sb.toString();
    }

    private ProvisioningAdvice heuristicAdvice(ProvisioningContext provisioningContext) {
        var swarmState = provisioningContext.swarmState();
        boolean shouldProvision = swarmState != null
                                  && swarmState.activeSignals() != null
                                  && !swarmState.activeSignals().isEmpty();
        return new ProvisioningAdvice(shouldProvision, null, null, "heuristic — based on active signals");
    }
}
