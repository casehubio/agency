package io.casehub.agency.orchestration.loop;

import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.api.model.coordination.DecisionType;
import io.casehub.api.spi.observation.Observation;

import java.util.List;
import java.util.Objects;

public sealed interface DecisionOutcome {

    record WriteBack(
        List<Observation> observations,
        List<SignalDeposit> signals,
        List<ContextWrite> contextWrites
    ) implements DecisionOutcome {
        public WriteBack {
            observations = observations != null ? List.copyOf(observations) : List.of();
            signals = signals != null ? List.copyOf(signals) : List.of();
            contextWrites = contextWrites != null ? List.copyOf(contextWrites) : List.of();
        }
    }

    record Route(RoutingDecision decision) implements DecisionOutcome {
        public Route { Objects.requireNonNull(decision, "decision must not be null"); }
    }

    record Aggregate(AggregationResult result) implements DecisionOutcome {
        public Aggregate { Objects.requireNonNull(result, "result must not be null"); }
    }

    record Composite(List<DecisionOutcome> outcomes) implements DecisionOutcome {
        public Composite { outcomes = List.copyOf(outcomes); }
    }

    record ScopeGated(DecisionType attempted, DecisionOutcome rejected) implements DecisionOutcome {
        public ScopeGated {
            Objects.requireNonNull(attempted, "attempted must not be null");
            Objects.requireNonNull(rejected, "rejected must not be null");
        }
    }

    record Provision(
            io.casehub.api.spi.stigmergy.SwarmProvisioningAdvisor.ProvisioningAdvice advice
    ) implements DecisionOutcome {
        public Provision {
            Objects.requireNonNull(advice, "advice must not be null");
        }
    }

}
