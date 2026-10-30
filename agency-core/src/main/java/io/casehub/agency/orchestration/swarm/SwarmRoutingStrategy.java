package io.casehub.agency.orchestration.swarm;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.agency.orchestration.routing.RoutingContext;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.agency.orchestration.routing.RoutingStrategy;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SwarmRoutingStrategy implements RoutingStrategy<SwarmContext> {

    private static final System.Logger LOG =
            System.getLogger(SwarmRoutingStrategy.class.getName());

    private final AgentProvider agentProvider;
    private final SwarmExperienceSection experienceSection;
    private final int preFilterThreshold;
    private final @Nullable SwarmInvocationTracker invocationTracker;
    private final @Nullable SystemPromptCustomiser promptCustomiser;
    private final           List<SwarmPromptSection> additionalSections;
    private volatile @Nullable String                variantSlot;


    public SwarmRoutingStrategy(AgentProvider agentProvider,
                                SwarmExperienceSection experienceSection,
                                int preFilterThreshold,
                                @Nullable SwarmInvocationTracker invocationTracker) {
        this(agentProvider, experienceSection, preFilterThreshold, invocationTracker, null, List.of());
    }

    public SwarmRoutingStrategy(AgentProvider agentProvider,
                                SwarmExperienceSection experienceSection,
                                int preFilterThreshold,
                                @Nullable SwarmInvocationTracker invocationTracker,
                                @Nullable SystemPromptCustomiser promptCustomiser,
                                List<SwarmPromptSection> additionalSections) {
        this.agentProvider      = agentProvider;
        this.experienceSection  = experienceSection;
        this.preFilterThreshold = preFilterThreshold;
        this.invocationTracker  = invocationTracker;
        this.promptCustomiser   = promptCustomiser;
        this.additionalSections = List.copyOf(additionalSections);
    }


    public SwarmRoutingStrategy(AgentProvider agentProvider,
                                SwarmExperienceSection experienceSection,
                                int preFilterThreshold) {
        this(agentProvider, experienceSection, preFilterThreshold, null);
    }

    public void setVariantSlot(@Nullable String variantSlot) {
        this.variantSlot = variantSlot;
    }


    @Override
    public RoutingDecision route(RoutingContext<SwarmContext> context) {
        var candidates = context.candidates();
        if (candidates.isEmpty()) {
            return new RoutingDecision.Unresolvable("No candidates available");
        }

        var candidatesToReason = candidates;
        if (candidates.size() > preFilterThreshold) {
            candidatesToReason = preFilter(candidates, context.task());
        }

        if (invocationTracker != null && !invocationTracker.tryAcquire("routing")) {
            LOG.log(System.Logger.Level.WARNING, "LLM budget exhausted for routing — using heuristic fallback");
            return heuristicFallback(candidates);
        }

        var prompt = buildPrompt(candidatesToReason, context.state());

        for (var section : additionalSections) {
            var contribution = section.contribute(context.state());
            if (contribution != null && !contribution.isEmpty()) {
                prompt = prompt + "\n" + contribution;
            }
        }

        if (promptCustomiser != null) {
            prompt = promptCustomiser.customise(prompt,
                                                "swarm-team-formation", variantSlot);
        }

        var userMessage = "Select agents for the swarm team and assign roles. " +
                          "Respond in JSON: {\"agents\": [names], \"teams\": {\"teamName\": [names]}, " +
                          "\"strategy\": \"description\"}";
        var config = AgentSessionConfig.of(prompt, userMessage);

        var result = StructuredAgentInvoker.invokeRaw(agentProvider, config);
        if (result instanceof StructuredAgentInvoker.InvocationResult.Success<?> s) {
            if (invocationTracker != null) {
                invocationTracker.recordTokens(s.metadata().inputTokens() + s.metadata().outputTokens());
            }
            return parseAssignment(s, candidatesToReason);
        }
        if (result instanceof StructuredAgentInvoker.InvocationResult.ParseError<?> e) {
            LOG.log(System.Logger.Level.WARNING, "LLM response parse error: {0}", e.parseError());
        } else if (result instanceof StructuredAgentInvoker.InvocationResult.AgentError<?> e) {
            LOG.log(System.Logger.Level.WARNING, "LLM invocation failed: {0}", e.reason());
        }
        return heuristicFallback(candidates);
    }

    private List<RoutingCandidate> preFilter(List<RoutingCandidate> candidates, String task) {
        var allCaps = new java.util.LinkedHashSet<String>();
        candidates.forEach(c -> allCaps.addAll(capabilityNames(c)));

        var bestCandidates = new ArrayList<RoutingCandidate>();
        var uncovered = new java.util.LinkedHashSet<>(allCaps);

        for (var candidate : candidates) {
            if (bestCandidates.size() >= preFilterThreshold) break;
            var caps = capabilityNames(candidate);
            if (caps.stream().anyMatch(uncovered::contains)) {
                bestCandidates.add(candidate);
                uncovered.removeAll(caps);
            }
        }

        for (var candidate : candidates) {
            if (bestCandidates.size() >= preFilterThreshold) break;
            if (!bestCandidates.contains(candidate)) {
                bestCandidates.add(candidate);
            }
        }

        return bestCandidates;
    }

    private static Set<String> capabilityNames(RoutingCandidate c) {
        if (c.descriptor() == null || c.descriptor().capabilities() == null) return Set.of();
        return c.descriptor().capabilities().stream()
                .map(cap -> cap.name())
                .collect(Collectors.toSet());
    }

    private String buildPrompt(List<RoutingCandidate> candidates, SwarmContext state) {
        var sb = new StringBuilder("You are a swarm coordinator. Form a team and assign work.\n\n");

        sb.append("## Available Agents\n");
        for (var c : candidates) {
            var caps = capabilityNames(c);
            sb.append("- %s: %s%s\n".formatted(
                    c.ref().name(), c.ref().description(),
                    caps.isEmpty() ? "" : " (capabilities: " + String.join(", ", caps) + ")"));
        }

        var experienceContext = experienceSection.contribute(state);
        if (!experienceContext.isEmpty()) {
            sb.append("\n").append(experienceContext);
        }

        var progress = state.swarmProgress();
        if (progress != null) {
            sb.append("\n## Swarm Progress\n");
            sb.append("- Exploration pace: %.2f\n".formatted(progress.explorationPace()));
            sb.append("- Consensus: %.2f\n".formatted(progress.consensusScore()));
            sb.append("- Stability: %.2f\n".formatted(progress.stabilityScore()));
        }

        if (!state.detectedRoles().isEmpty()) {
            sb.append("\n## Detected Roles\n");
            state.detectedRoles().forEach(r ->
                    sb.append("- %s: %s\n".formatted(r.roleId(), r.memberAgents())));
        }

        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private RoutingDecision parseAssignment(StructuredAgentInvoker.InvocationResult.Success<?> success,
                                            List<RoutingCandidate> allCandidates) {
        try {
            var json = (com.fasterxml.jackson.databind.JsonNode) success.value();
            var agentNames = new ArrayList<String>();
            if (json.has("agents")) {
                json.get("agents").forEach(n -> agentNames.add(n.asText()));
            }

            var agentMap = allCandidates.stream()
                    .collect(Collectors.toMap(c -> c.ref().name(), c -> c.ref(), (a, b) -> a));

            var agents = agentNames.stream()
                    .filter(agentMap::containsKey)
                    .map(agentMap::get)
                    .map(AgentRef.class::cast)
                    .toList();

            if (agents.isEmpty()) {
                return heuristicFallback(allCandidates);
            }

            var teams = new HashMap<String, List<AgentRef>>();
            if (json.has("teams")) {
                json.get("teams").fields().forEachRemaining(entry -> {
                    var members = new ArrayList<AgentRef>();
                    entry.getValue().forEach(n -> {
                        var ref = agentMap.get(n.asText());
                        if (ref != null) members.add(ref);
                    });
                    if (!members.isEmpty()) teams.put(entry.getKey(), members);
                });
            }

            var strategy = json.has("strategy") ? json.get("strategy").asText() : null;
            return new RoutingDecision.SwarmAssignment(agents, teams, strategy, null);
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to parse swarm assignment", e);
            return heuristicFallback(allCandidates);
        }
    }

    private RoutingDecision heuristicFallback(List<RoutingCandidate> candidates) {
        var agents = candidates.stream()
                .map(c -> (AgentRef) c.ref())
                .toList();
        return new RoutingDecision.SwarmAssignment(agents, Map.of(), "heuristic-all", "LLM unavailable — all agents selected");
    }
}
