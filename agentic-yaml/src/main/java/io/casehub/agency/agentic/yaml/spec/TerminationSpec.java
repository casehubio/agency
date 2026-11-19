package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Set;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = TerminationSpec.MaxIterations.class, name = "max-iterations"),
        @Type(value = TerminationSpec.GoalReached.class, name = "goal-reached"),
        @Type(value = TerminationSpec.JudgeConvergence.class, name = "judge-convergence"),
        @Type(value = TerminationSpec.AllAgreed.class, name = "all-agreed"),
        @Type(value = TerminationSpec.Supervisor.class, name = "supervisor"),
        @Type(value = TerminationSpec.Contested.class, name = "contested"),
        @Type(value = TerminationSpec.Convergence.class, name = "convergence"),
        @Type(value = TerminationSpec.SinglePass.class, name = "single-pass"),
        @Type(value = TerminationSpec.AgentCount.class, name = "agent-count"),
        @Type(value = TerminationSpec.Accepted.class, name = "accepted"),
        @Type(value = TerminationSpec.TerminalOutcome.class, name = "terminal-outcome"),
        @Type(value = TerminationSpec.Deadline.class, name = "deadline")
})
public sealed interface TerminationSpec {

    record MaxIterations(int iterations) implements TerminationSpec {}

    record GoalReached(String when) implements TerminationSpec {}

    record JudgeConvergence(AgentRefSpec judge, @JsonAlias("max-iterations") int maxIterations) implements TerminationSpec {}

    record AllAgreed(@JsonAlias("resolved-statuses") @Nullable Set<String> resolvedStatuses) implements TerminationSpec {}

    record Supervisor(String role, @JsonAlias("signal-type") @Nullable String signalType) implements TerminationSpec {}

    record Contested(@JsonAlias("max-dispute-rounds") int maxDisputeRounds) implements TerminationSpec {}

    record Convergence(double threshold) implements TerminationSpec {}

    record SinglePass() implements TerminationSpec {}

    record AgentCount() implements TerminationSpec {}

    record Accepted() implements TerminationSpec {}

    record TerminalOutcome() implements TerminationSpec {}

    record Deadline(Duration timeout) implements TerminationSpec {}
}
