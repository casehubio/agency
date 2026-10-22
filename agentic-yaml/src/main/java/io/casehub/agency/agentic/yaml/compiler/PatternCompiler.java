package io.casehub.agency.agentic.yaml.compiler;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.AgentResult;
import io.casehub.agency.agentic.FailurePolicy;
import io.casehub.agency.agentic.RoutingCandidate;
import io.casehub.agency.agentic.activation.ActivationRule;
import io.casehub.agency.agentic.activation.OnExplicitDispatch;
import io.casehub.agency.agentic.aggregation.AggregationStrategy;
import io.casehub.agency.agentic.aggregation.CollectAll;
import io.casehub.agency.agentic.aggregation.MajorityVote;
import io.casehub.agency.agentic.aggregation.PassThrough;
import io.casehub.agency.agentic.decomposition.IdentityDecomposition;
import io.casehub.agency.agentic.judgment.DirectJudgmentDispatcher;
import io.casehub.agency.agentic.judgment.JudgmentPhase;
import io.casehub.agency.agentic.judgment.JudgmentPolicy;
import io.casehub.agency.agentic.judgment.NoOpVerifier;
import io.casehub.agency.agentic.judgment.RetryPolicy;
import io.casehub.agency.agentic.model.ExecutionModel;
import io.casehub.agency.agentic.model.PatternType;
import io.casehub.agency.agentic.routing.FirstMatchRouting;
import io.casehub.agency.agentic.routing.RoundRobinRouting;
import io.casehub.agency.agentic.routing.RoutingStrategy;
import io.casehub.agency.agentic.routing.SelectAllRouting;
import io.casehub.agency.agentic.routing.SequentialRouting;
import io.casehub.agency.agentic.termination.MaxIterationsTermination;
import io.casehub.agency.agentic.termination.TerminationCondition;
import io.casehub.agency.agentic.yaml.registry.ActivationRuleRegistry;
import io.casehub.agency.agentic.yaml.registry.AggregationStrategyRegistry;
import io.casehub.agency.agentic.yaml.registry.CallerStrategyRegistry;
import io.casehub.agency.agentic.yaml.registry.DecompositionStrategyRegistry;
import io.casehub.agency.agentic.yaml.registry.JudgmentTriggerRegistry;
import io.casehub.agency.agentic.yaml.registry.RoutingStrategyRegistry;
import io.casehub.agency.agentic.yaml.registry.TerminationConditionRegistry;
import io.casehub.agency.agentic.yaml.spec.AgentRefSpec;
import io.casehub.agency.agentic.yaml.spec.JudgmentSpec;
import io.casehub.agency.agentic.yaml.spec.PatternSpec;
import io.casehub.agency.agentic.yaml.spec.TerminationSpec;
import io.casehub.eidos.api.AgentCapability;
import io.casehub.eidos.api.AgentDescriptor;
import io.casehub.engine.plan.DecompositionStrategy;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PatternCompiler {

    private final RoutingStrategyRegistry routingRegistry = new RoutingStrategyRegistry();
    private final TerminationConditionRegistry terminationRegistry = new TerminationConditionRegistry();
    private final AggregationStrategyRegistry aggregationRegistry = new AggregationStrategyRegistry();
    private final ActivationRuleRegistry activationRegistry = new ActivationRuleRegistry();
    private final DecompositionStrategyRegistry decompositionRegistry = new DecompositionStrategyRegistry();
    private final JudgmentTriggerRegistry judgmentTriggerRegistry = new JudgmentTriggerRegistry();
    private final CallerStrategyRegistry callerStrategyRegistry = new CallerStrategyRegistry();

    private final @Nullable ExpressionEngine expressionEngine;

    public PatternCompiler(@Nullable ExpressionEngine expressionEngine) {
        this.expressionEngine = expressionEngine;
    }

    @SuppressWarnings("unchecked")
    public <T> ExecutionModel<T> compile(PatternSpec spec) {
        var agents = buildCandidates(spec.agents());
        var patternType = resolvePatternType(spec);

        var routing = resolveRouting(spec);
        var termination = resolveTermination(spec, agents);
        var aggregation = resolveAggregation(spec, patternType);
        var activation = resolveActivation(spec);
        var decomposition = resolveDecomposition(spec);
        var failurePolicy = spec.failurePolicy() != null ? spec.failurePolicy()
                : FailurePolicy.defaults();
        var task = spec.task() != null ? spec.task() : "execution";
        var judgment = spec.judgment() != null ? resolveJudgment(spec.judgment()) : null;

        var candidateList = List.copyOf(agents);
        return new ExecutionModel<>(
                (RoutingStrategy<T>) routing,
                (DecompositionStrategy<T>) decomposition,
                (ActivationRule<T>) activation,
                (AggregationStrategy<T>) aggregation,
                (TerminationCondition<T>) termination,
                () -> candidateList,
                failurePolicy,
                List.of(),
                task,
                patternType,
                null,
                (JudgmentPhase<T>) judgment
        );
    }

    @SuppressWarnings("unchecked")
    private <T> JudgmentPhase<T> resolveJudgment(JudgmentSpec spec) {
        var trigger = judgmentTriggerRegistry.resolve(spec.trigger(), expressionEngine);
        var caller = callerStrategyRegistry.resolve(spec.caller());
        return (JudgmentPhase<T>) JudgmentPolicy.builder()
                .trigger(trigger)
                .caller(caller)
                .verifier(new NoOpVerifier())
                .retryPolicy(RetryPolicy.defaults())
                .dispatcher(new DirectJudgmentDispatcher())
                .build();
    }

    private PatternType resolvePatternType(PatternSpec spec) {
        return switch (spec) {
            case PatternSpec.Supervisor s -> PatternType.SUPERVISOR;
            case PatternSpec.Debate d -> PatternType.DEBATE;
            case PatternSpec.Loop l -> PatternType.LOOP;
            case PatternSpec.Parallel p -> PatternType.PARALLEL;
            case PatternSpec.Voting v -> PatternType.VOTING;
            case PatternSpec.Conditional c -> PatternType.CONDITIONAL;
            case PatternSpec.Sequence s -> PatternType.SEQUENCE;
            case PatternSpec.Htn h -> PatternType.HTN;
        };
    }

    private RoutingStrategy<?> resolveRouting(PatternSpec spec) {
        if (spec.routing() != null) {
            return routingRegistry.resolve(spec.routing(), expressionEngine);
        }
        return switch (spec) {
            case PatternSpec.Parallel p -> new SelectAllRouting<>();
            case PatternSpec.Voting v -> new SelectAllRouting<>();
            case PatternSpec.Sequence s -> new SequentialRouting<>();
            case PatternSpec.Conditional c -> new FirstMatchRouting<>(candidate -> true);
            case PatternSpec.Supervisor s -> new FirstMatchRouting<>(candidate -> true);
            case PatternSpec.Debate d -> new RoundRobinRouting<>();
            case PatternSpec.Loop l -> new RoundRobinRouting<>();
            case PatternSpec.Htn h -> new SequentialRouting<>();
        };
    }

    private TerminationCondition<?> resolveTermination(PatternSpec spec,
                                                        List<RoutingCandidate> agents) {
        if (spec.termination() != null && !spec.termination().isEmpty()) {
            if (spec.termination().size() == 1) {
                return resolveSingleTermination(spec.termination().get(0), agents);
            }
            @SuppressWarnings({"unchecked", "rawtypes"})
            TerminationCondition composite = resolveSingleTermination(
                    spec.termination().get(0), agents);
            for (int i = 1; i < spec.termination().size(); i++) {
                composite = composite.or(resolveSingleTermination(
                        spec.termination().get(i), agents));
            }
            return composite;
        }
        return switch (spec) {
            case PatternSpec.Debate d -> new MaxIterationsTermination<>(d.maxRounds());
            case PatternSpec.Loop l -> new MaxIterationsTermination<>(l.maxIterations());
            case PatternSpec.Supervisor s -> new MaxIterationsTermination<>(10);
            case PatternSpec.Parallel p -> new MaxIterationsTermination<>(1);
            case PatternSpec.Voting v -> new MaxIterationsTermination<>(1);
            case PatternSpec.Conditional c -> new MaxIterationsTermination<>(1);
            case PatternSpec.Sequence s -> new MaxIterationsTermination<>(agents.size());
            case PatternSpec.Htn h -> new MaxIterationsTermination<>(1);
        };
    }

    private TerminationCondition<?> resolveSingleTermination(TerminationSpec ts,
                                                              List<RoutingCandidate> agents) {
        if (ts instanceof TerminationSpec.AgentCount) {
            return new MaxIterationsTermination<>(agents.size());
        }
        return terminationRegistry.resolve(ts, expressionEngine);
    }

    private AggregationStrategy<?> resolveAggregation(PatternSpec spec, PatternType type) {
        if (spec.aggregation() != null) {
            return aggregationRegistry.resolve(spec.aggregation());
        }
        return switch (type) {
            case PARALLEL -> new CollectAll<>();
            case VOTING -> new MajorityVote<>();
            case DEBATE -> new CollectAll<>();
            default -> new PassThrough<>();
        };
    }

    private ActivationRule<?> resolveActivation(PatternSpec spec) {
        if (spec.activation() != null) {
            return activationRegistry.resolve(spec.activation());
        }
        return new OnExplicitDispatch<>();
    }

    private DecompositionStrategy<?> resolveDecomposition(PatternSpec spec) {
        if (spec.decomposition() != null) {
            return decompositionRegistry.resolve(spec.decomposition(), expressionEngine);
        }
        return new IdentityDecomposition<>();
    }

    private List<RoutingCandidate> buildCandidates(List<AgentRefSpec> agentSpecs) {
        return agentSpecs.stream()
                .map(this::buildCandidate)
                .toList();
    }

    private RoutingCandidate buildCandidate(AgentRefSpec agentSpec) {
        var ref = buildAgentRef(agentSpec);
        var descriptor = buildDescriptor(agentSpec);
        return new RoutingCandidate(ref, descriptor);
    }

    private @Nullable AgentDescriptor buildDescriptor(AgentRefSpec spec) {
        if (spec instanceof AgentRefSpec.Composed) {
            return null;
        }
        var builder = AgentDescriptor.builder()
                .agentId(spec.name())
                .name(spec.name())
                .slot("yaml")
                .tenancyId("yaml");
        if (spec.description() != null) {
            builder.briefing(spec.description());
        }
        if (spec.capabilities() != null && !spec.capabilities().isEmpty()) {
            builder.capabilities(spec.capabilities().stream()
                    .map(name -> AgentCapability.builder().name(name).build())
                    .toList());
        }
        if (spec.model() != null) {
            builder.modelFamily(spec.model());
        }
        return builder.build();
    }

    private AgentRef buildAgentRef(AgentRefSpec spec) {
        return switch (spec) {
            case AgentRefSpec.Composed c -> {
                var nestedModel = compile(c.pattern());
                yield new AgentRef.ComposedAgent(nestedModel);
            }
            default -> {
                var ref = AgentRef.external(spec.name(),
                        (Object ctx) -> CompletableFuture.completedFuture(
                                AgentResult.success(null, "YAML-declared agent placeholder")));
                yield ref;
            }
        };
    }
}
