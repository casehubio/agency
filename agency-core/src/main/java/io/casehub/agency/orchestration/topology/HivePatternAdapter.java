package io.casehub.agency.orchestration.topology;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.orchestration.execution.ExecutionResult;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.pattern.Patterns;
import io.casehub.agency.orchestration.termination.MaxIterationsTermination;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;
import io.casehub.api.model.coordination.StructuralRole;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class HivePatternAdapter {

    private static final System.Logger LOG =
            System.getLogger(HivePatternAdapter.class.getName());

    static final String SUPERVISOR_PROMPT =
            "You are a supervisor reviewing an agent's decision outcome in a multi-agent hive. "
            + "Evaluate the quality and correctness of the decision. "
            + "Approve if the reasoning is sound, or reject with specific feedback for improvement.";

    static final String DEBATE_PROMPT =
            "You are a debater in a multi-agent deliberation. "
            + "Argue your position on the decision outcome with evidence-based reasoning. "
            + "Challenge weak arguments and build on strong ones to reach a better conclusion.";

    static final String VOTING_PROMPT =
            "You are a voter evaluating a decision outcome. "
            + "Cast your vote: APPROVE if the outcome is sound, REJECT if it has material flaws. "
            + "Provide a brief rationale for your vote.";

    private final AgentProvider agentProvider;

    public HivePatternAdapter(AgentProvider agentProvider) {
        this.agentProvider = Objects.requireNonNull(agentProvider);
    }

    public CompletionStage<CoordinationFeedback> execute(
            OrchestrationPatternType patternType,
            String agentId,
            DecisionOutcome outcome,
            TopologyContext context) {
        if (outcome instanceof DecisionOutcome.ScopeGated) {
            return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, outcome, FeedbackSignal.SCOPE_VIOLATION));
        }
        return switch (patternType) {
            case SUPERVISOR -> executeSupervisor(agentId, outcome, context);
            case DEBATE -> executeDebate(agentId, outcome, context);
            case VOTING -> executeVoting(agentId, outcome, context);
        };
    }

    private CompletionStage<CoordinationFeedback> executeSupervisor(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        var coordinatorId = ctx.descriptor().coordinatorAgentId()
                .orElseThrow(() -> new IllegalStateException(
                        "Supervisor pattern requires a coordinator (INITIATOR role)"));
        var coordinator = createAgentRef(coordinatorId, SUPERVISOR_PROMPT,
                ctx.groupId(), ctx.tenancyId());

        return Patterns.<DecisionOutcome>supervisor()
                .agents(coordinator)
                .terminate(new MaxIterationsTermination<>(1))
                .task("hive-supervisor-review")
                .execute(outcome)
                .map(result -> mapToFeedback(agentId, outcome, result))
                .onFailure().recoverWithItem(
                        new CoordinationFeedback(agentId, outcome, FeedbackSignal.FAILURE))
                .subscribeAsCompletionStage();
    }

    private CompletionStage<CoordinationFeedback> executeDebate(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        var debaters = resolveParticipants(agentId, ctx, DEBATE_PROMPT,
                ctx.groupId(), ctx.tenancyId());
        if (debaters.length == 0) {
            LOG.log(System.Logger.Level.WARNING,
                    "Debate pattern has no participants (excluding {0}) — returning SUCCESS", agentId);
            return CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
        }

        return Patterns.<DecisionOutcome>debate()
                .debaters(debaters)
                .maxRounds(3)
                .task("hive-outcome-debate")
                .execute(outcome)
                .map(result -> mapToFeedback(agentId, outcome, result))
                .onFailure().recoverWithItem(
                        new CoordinationFeedback(agentId, outcome, FeedbackSignal.FAILURE))
                .subscribeAsCompletionStage();
    }

    private CompletionStage<CoordinationFeedback> executeVoting(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        var voters = resolveAllParticipants(ctx, VOTING_PROMPT,
                ctx.groupId(), ctx.tenancyId());
        if (voters.length == 0) {
            LOG.log(System.Logger.Level.WARNING,
                    "Voting pattern has no participants — returning SUCCESS");
            return CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
        }

        return Patterns.<DecisionOutcome>voting()
                .evaluators(voters)
                .task("hive-outcome-vote")
                .execute(outcome)
                .map(result -> mapToFeedback(agentId, outcome, result))
                .onFailure().recoverWithItem(
                        new CoordinationFeedback(agentId, outcome, FeedbackSignal.FAILURE))
                .subscribeAsCompletionStage();
    }

    private AgentRef[] resolveParticipants(String excludeId, TopologyContext ctx,
                                              String systemPrompt,
                                              @org.jspecify.annotations.Nullable String groupId,
                                              @org.jspecify.annotations.Nullable String tenancyId) {
        return ctx.descriptor().agentRoles().entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeId))
                .map(e -> createAgentRef(e.getKey(), systemPrompt, groupId, tenancyId))
                .toArray(AgentRef[]::new);
    }

    private AgentRef[] resolveAllParticipants(TopologyContext ctx, String systemPrompt,
                                                 @org.jspecify.annotations.Nullable String groupId,
                                                 @org.jspecify.annotations.Nullable String tenancyId) {
        return ctx.descriptor().agentRoles().entrySet().stream()
                .map(e -> createAgentRef(e.getKey(), systemPrompt, groupId, tenancyId))
                .toArray(AgentRef[]::new);
    }

    private AgentRef createAgentRef(String agentId, String systemPrompt) {
        return createAgentRef(agentId, systemPrompt, null, null);
    }

    private AgentRef createAgentRef(String agentId, String systemPrompt,
                                    @org.jspecify.annotations.Nullable String groupId,
                                    @org.jspecify.annotations.Nullable String tenancyId) {
        return AgentRef.external(agentId, (DecisionOutcome outcome) -> {
            var userPrompt = "## Decision Outcome\n\n" + renderOutcome(outcome, groupId, tenancyId);
            var config = AgentSessionConfig.of(systemPrompt, userPrompt);
            var result = StructuredAgentInvoker.invokeText(agentProvider, config);
            return switch (result) {
                case StructuredAgentInvoker.InvocationResult.Success<String> s ->
                        CompletableFuture.completedFuture(
                                AgentResult.success(null, s.value()));
                default ->
                        CompletableFuture.completedFuture(
                                AgentResult.failure(null, "Agent invocation failed"));
            };
        });
    }

    static String renderOutcome(DecisionOutcome outcome) {
        return renderOutcome(outcome, null, null);
    }

    static String renderOutcome(DecisionOutcome outcome,
                                @org.jspecify.annotations.Nullable String groupId,
                                @org.jspecify.annotations.Nullable String tenancyId) {
        var sb = new StringBuilder();
        if (groupId != null) sb.append("Group: ").append(groupId).append('\n');
        if (tenancyId != null) sb.append("Tenant: ").append(tenancyId).append('\n');
        sb.append(switch (outcome) {
            case DecisionOutcome.WriteBack wb ->
                    "Type: WriteBack\nObservations: " + wb.observations().size()
                            + "\nSignals: " + wb.signals().size()
                            + "\nContext writes: " + wb.contextWrites().size();
            case DecisionOutcome.Route r ->
                    "Type: Route\nDecision: " + r.decision();
            case DecisionOutcome.Aggregate a ->
                    "Type: Aggregate\nResult: " + a.result();
            case DecisionOutcome.Composite c ->
                    "Type: Composite\nSub-outcomes: " + c.outcomes().size();
            case DecisionOutcome.ScopeGated sg ->
                    "Type: ScopeGated\nAttempted: " + sg.attempted();
            case DecisionOutcome.Provision p ->
                    "Type: Provision\nAdvice: " + p.advice();
        });
        return sb.toString();
    }

    private static CoordinationFeedback mapToFeedback(
            String agentId, DecisionOutcome outcome, ExecutionResult result) {
        var signal = switch (result) {
            case ExecutionResult.Completed ignored -> FeedbackSignal.SUCCESS;
            case ExecutionResult.Failed ignored -> FeedbackSignal.FAILURE;
            case ExecutionResult.Escalated ignored -> FeedbackSignal.PARTIAL;
            case ExecutionResult.Cancelled ignored -> FeedbackSignal.FAILURE;
        };
        return new CoordinationFeedback(agentId, outcome, signal);
    }

    public void onFeedback(OrchestrationPatternType patternType,
                           CoordinationFeedback feedback) {
    }

    public AgentProvider agentProvider() { return agentProvider; }
}
