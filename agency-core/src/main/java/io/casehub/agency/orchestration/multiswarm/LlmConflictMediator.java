package io.casehub.agency.orchestration.multiswarm;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.core.invoke.StructuredAgentInvoker.InvocationResult;
import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.util.List;
import java.util.Objects;

public class LlmConflictMediator implements ConflictMediator {

    private static final String CONFLICT_SYSTEM_PROMPT = """
            You are mediating a conflict between two swarms that reached contradicting \
            conclusions about the same domain. Analyze both positions and determine which \
            conclusion should prevail based on confidence, evidence, and reasoning.

            Respond ONLY with a JSON object:
            {"resolution": "<description>", "updatedConclusion": "<entity:state>", \
            "confidence": <0.0-1.0>, "justification": "<reasoning>"}""";

    private static final String RESOURCE_SYSTEM_PROMPT = """
            You are mediating a resource contention between two swarms competing for \
            the same agents. Evaluate which swarm benefits more from the contested agents \
            based on capabilities, progress, and task requirements.

            Respond ONLY with a JSON object:
            {"resolution": "<description>", "winner": "<swarmId>", \
            "justification": "<reasoning>"}""";

    private static final String DEPENDENCY_SYSTEM_PROMPT = """
            You are evaluating a cross-swarm dependency. Determine whether the swarms \
            should coordinate and how, based on their capabilities and progress.

            Respond ONLY with a JSON object:
            {"resolution": "<description>", "coordinationAction": "<action>", \
            "justification": "<reasoning>"}""";

    private static final String HANDOFF_SYSTEM_PROMPT = """
            You are evaluating a work handoff between swarms. Determine whether the \
            target swarm is capable of handling the work based on capability overlap.

            Respond ONLY with a JSON object:
            {"resolution": "<description>", "proceed": <true/false>, \
            "justification": "<reasoning>"}""";

    private final AgentProvider agentProvider;

    public LlmConflictMediator(AgentProvider agentProvider) {
        this.agentProvider = Objects.requireNonNull(agentProvider);
    }

    @Override
    public MediationResult mediate(ConflictContext context) {
        var systemPrompt = selectSystemPrompt(context.notification().type());
        var userPrompt = buildUserPrompt(context);
        var config = AgentSessionConfig.of(systemPrompt, userPrompt);
        var result = StructuredAgentInvoker.invokeRaw(agentProvider, config);
        return toMediationResult(result, context);
    }

    private static String selectSystemPrompt(NotificationType type) {
        return switch (type) {
            case CONFLICT            -> CONFLICT_SYSTEM_PROMPT;
            case RESOURCE_CONTENTION -> RESOURCE_SYSTEM_PROMPT;
            case DEPENDENCY          -> DEPENDENCY_SYSTEM_PROMPT;
            case HANDOFF             -> HANDOFF_SYSTEM_PROMPT;
        };
    }

    private static String buildUserPrompt(ConflictContext context) {
        var sb = new StringBuilder();
        var notification = context.notification();

        sb.append("## Conflict Details\n\n");
        sb.append("Type: ").append(notification.type()).append('\n');
        sb.append("Confidence: ").append(notification.confidence()).append('\n');
        sb.append("Source Swarm: ").append(notification.sourceSwarmId()).append('\n');
        sb.append("Target Swarm: ").append(notification.targetSwarmId()).append('\n');

        for (var entry : context.swarmDescriptors().entrySet()) {
            sb.append("\n### Swarm ").append(entry.getKey()).append('\n');
            sb.append("Capabilities: ").append(entry.getValue().capabilities()).append('\n');
        }

        if (!context.currentBeliefs().isEmpty()) {
            sb.append("\n## Current Beliefs\n\n");
            for (var belief : context.currentBeliefs().all()) {
                sb.append("- [").append(belief.key()).append("] ")
                        .append(belief.value().conclusion())
                        .append(" (confidence: ").append(belief.value().confidence())
                        .append(", entrenchment: ").append(belief.entrenchment())
                        .append(")\n");
            }
        }
        if (!context.contractedBeliefs().isEmpty()) {
            sb.append("\n## Beliefs Removed by AGM Revision\n\n");
            for (var belief : context.contractedBeliefs()) {
                sb.append("- [").append(belief.key()).append("] ")
                        .append(belief.value().conclusion())
                        .append(" (was entrenchment: ").append(belief.entrenchment())
                        .append(")\n");
            }
        }

        if (!context.latestProgress().isEmpty()) {
            sb.append("\n## Swarm Progress\n\n");
            for (var entry : context.latestProgress().entrySet()) {
                var p = entry.getValue();
                sb.append("- Swarm ").append(entry.getKey())
                        .append(": consensus=").append(p.consensusScore())
                        .append(", stability=").append(p.stabilityScore())
                        .append('\n');
            }
        }

        return sb.toString();
    }

    private static MediationResult toMediationResult(InvocationResult<JsonNode> result,
                                                      ConflictContext context) {
        if (result instanceof InvocationResult.Success<JsonNode> success) {
            return parseResolution(success.value(), context);
        }
        if (result instanceof InvocationResult.ParseError<JsonNode> parseError) {
            throw new MediationException("LLM response parse error: " + parseError.parseError());
        }
        if (result instanceof InvocationResult.AgentError<JsonNode> agentError) {
            throw new MediationException("LLM invocation error: " + agentError.reason());
        }
        throw new MediationException("Unknown invocation result type");
    }

    private static MediationResult parseResolution(JsonNode node, ConflictContext context) {
        var resolution = node.path("resolution").asText("resolved");
        var justification = node.path("justification").asText("");
        var updatedConclusion = node.path("updatedConclusion").asText(null);
        var confidence = node.path("confidence").asDouble(0.5);

        Belief<SwarmConclusion> updatedBelief = null;
        if (updatedConclusion != null && !updatedConclusion.isEmpty()) {
            var domain = extractDomain(context);
            var conclusion = new SwarmConclusion(
                    context.notification().sourceSwarmId(),
                    domain, updatedConclusion, confidence);
            updatedBelief = Belief.of(
                    context.notification().sourceSwarmId() + ":" + domain,
                    conclusion, (int) (confidence * 100));
        }

        var notification = new CrossSwarmNotification(
                context.notification().sourceSwarmId(),
                context.notification().targetSwarmId(),
                context.notification().type(),
                context.notification().trigger(),
                confidence);

        return new MediationResult.Resolved(
                resolution + " — " + justification,
                updatedBelief,
                List.of(notification));
    }

    private static String extractDomain(ConflictContext context) {
        if (context.notification().trigger() instanceof MultiSwarmSignal.Finding f) {
            return f.domain();
        }
        return "unknown";
    }
}
