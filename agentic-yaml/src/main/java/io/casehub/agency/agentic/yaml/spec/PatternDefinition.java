package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.casehub.agency.core.FailurePolicy;
import org.jspecify.annotations.Nullable;

import java.util.List;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = io.casehub.agency.core.yaml.spec.PatternDefinition.Supervisor.class, name = "supervisor"),
        @Type(value = io.casehub.agency.core.yaml.spec.PatternDefinition.Debate.class, name = "debate"),
        @Type(value = io.casehub.agency.core.yaml.spec.PatternDefinition.Loop.class, name = "loop"),
        @Type(value = io.casehub.agency.core.yaml.spec.PatternDefinition.Parallel.class, name = "parallel"),
        @Type(value = io.casehub.agency.core.yaml.spec.PatternDefinition.Voting.class, name = "voting"),
        @Type(value = io.casehub.agency.core.yaml.spec.PatternDefinition.Conditional.class, name = "conditional"),
        @Type(value = io.casehub.agency.core.yaml.spec.PatternDefinition.Sequence.class, name = "sequence"),
        @Type(value = io.casehub.agency.core.yaml.spec.PatternDefinition.Htn.class, name = "htn")
})
public sealed interface PatternDefinition {

    @Nullable String name();
    @Nullable String description();
    @Nullable RoutingSpec routing();
    @Nullable List<TerminationSpec> termination();
    @Nullable AggregationSpec aggregation();
    @Nullable ActivationSpec activation();
    @Nullable DecompositionSpec decomposition();
    @Nullable FailurePolicy failurePolicy();
    @Nullable String task();
    List<AgentRefSpec> agents();
    @Nullable JudgmentSpec judgment();
    @Nullable GovernanceSpec governance();

    record Supervisor(
            @Nullable String name,
            @Nullable String description,
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance
    ) implements io.casehub.agency.core.yaml.spec.PatternDefinition {}

    record Debate(
            @Nullable String name,
            @Nullable String description,
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance,
            @Nullable AgentRefSpec judge,
            int maxRounds
    ) implements io.casehub.agency.core.yaml.spec.PatternDefinition {}

    record Loop(
            @Nullable String name,
            @Nullable String description,
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance,
            int maxIterations,
            @Nullable String exitCondition
    ) implements io.casehub.agency.core.yaml.spec.PatternDefinition {}

    record Parallel(
            @Nullable String name,
            @Nullable String description,
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance
    ) implements io.casehub.agency.core.yaml.spec.PatternDefinition {}

    record Voting(
            @Nullable String name,
            @Nullable String description,
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance
    ) implements io.casehub.agency.core.yaml.spec.PatternDefinition {}

    record Conditional(
            @Nullable String name,
            @Nullable String description,
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance,
            List<BranchSpec> branches
    ) implements io.casehub.agency.core.yaml.spec.PatternDefinition {}

    record Sequence(
            @Nullable String name,
            @Nullable String description,
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance
    ) implements io.casehub.agency.core.yaml.spec.PatternDefinition {}

    record Htn(
            @Nullable String name,
            @Nullable String description,
            @Nullable RoutingSpec routing,
            @Nullable List<TerminationSpec> termination,
            @Nullable AggregationSpec aggregation,
            @Nullable ActivationSpec activation,
            @Nullable DecompositionSpec decomposition,
            @Nullable FailurePolicy failurePolicy,
            @Nullable String task,
            List<AgentRefSpec> agents,
            @Nullable JudgmentSpec judgment,
            @Nullable GovernanceSpec governance,
            TaskNodeSpec rootTask
    ) implements io.casehub.agency.core.yaml.spec.PatternDefinition {}
}
