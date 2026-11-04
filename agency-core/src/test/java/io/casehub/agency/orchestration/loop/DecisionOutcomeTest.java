package io.casehub.agency.orchestration.loop;

import com.fasterxml.jackson.databind.node.TextNode;
import io.casehub.api.model.coordination.DecisionType;
import io.casehub.api.spi.observation.Observation;
import io.casehub.agency.orchestration.aggregation.AggregationResult;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class DecisionOutcomeTest {

    @Test
    void writeBack_validConstruction() {
        var obs = new Observation("p1", 0.9, Map.of(), Instant.now());
        var signal = new SignalDeposit("quality", 0.8, Duration.ofMinutes(5));
        var write = new ContextWrite("key", new TextNode("value"));
        var wb = new DecisionOutcome.WriteBack(List.of(obs), List.of(signal), List.of(write));
        assertThat(wb.observations()).hasSize(1);
        assertThat(wb.signals()).hasSize(1);
        assertThat(wb.contextWrites()).hasSize(1);
    }

    @Test
    void writeBack_nullLists_defaultToEmpty() {
        var wb = new DecisionOutcome.WriteBack(null, null, null);
        assertThat(wb.observations()).isEmpty();
        assertThat(wb.signals()).isEmpty();
        assertThat(wb.contextWrites()).isEmpty();
    }

    @Test
    void writeBack_listsAreImmutable() {
        var mutable = new ArrayList<>(List.of(
            new SignalDeposit("s", 1.0, null)));
        var wb = new DecisionOutcome.WriteBack(List.of(), mutable, List.of());
        mutable.add(new SignalDeposit("s2", 0.5, null));
        assertThat(wb.signals()).hasSize(1);
    }

    @Test
    void route_nullDecision_throws() {
        assertThatNullPointerException()
            .isThrownBy(() -> new DecisionOutcome.Route(null));
    }

    @Test
    void route_wrapsDecision() {
        var decision = new RoutingDecision.Unresolvable("no agents");
        var route = new DecisionOutcome.Route(decision);
        assertThat(route.decision()).isEqualTo(decision);
    }

    @Test
    void aggregate_wrapsResult() {
        var result = new AggregationResult.Resolved("synthesised");
        var agg = new DecisionOutcome.Aggregate(result);
        assertThat(agg.result()).isEqualTo(result);
    }

    @Test
    void composite_immutable() {
        var inner = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var mutable = new ArrayList<DecisionOutcome>(List.of(inner));
        var composite = new DecisionOutcome.Composite(mutable);
        mutable.add(inner);
        assertThat(composite.outcomes()).hasSize(1);
    }

    @Test
    void scopeGated_carriesAttemptedAndRejected() {
        var rejected = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var gated = new DecisionOutcome.ScopeGated(DecisionType.PROVISIONING, rejected);
        assertThat(gated.attempted()).isEqualTo(DecisionType.PROVISIONING);
        assertThat(gated.rejected()).isEqualTo(rejected);
    }

    @Test
    void scopeGated_nullAttempted_throws() {
        var rejected = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        assertThatNullPointerException()
            .isThrownBy(() -> new DecisionOutcome.ScopeGated(null, rejected));
    }

    @Test
    void signalDeposit_nullHalfLife_allowed() {
        var signal = new SignalDeposit("name", 0.5, null);
        assertThat(signal.halfLife()).isNull();
    }
}
