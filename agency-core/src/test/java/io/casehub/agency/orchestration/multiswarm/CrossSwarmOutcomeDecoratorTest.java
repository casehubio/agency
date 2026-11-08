package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.topology.CoordinationFeedback;
import io.casehub.agency.orchestration.topology.DecisionOutcomeExecutor;
import io.casehub.agency.orchestration.topology.FeedbackSignal;
import io.casehub.agency.orchestration.topology.TopologyContext;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.spi.observation.Observation;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrossSwarmOutcomeDecoratorTest {

    CoordinationBus<MultiSwarmSignal> bus;
    RecordingExecutor delegate;
    TestMultiSwarmRegistry registry;
    CrossSwarmOutcomeDecorator decorator;
    UUID swarmId;

    @BeforeEach
    void setUp() {
        bus = new CoordinationBus<>(10);
        delegate = new RecordingExecutor();
        registry = new TestMultiSwarmRegistry();
        swarmId = UUID.randomUUID();
        var caseId = UUID.randomUUID();
        registry.register(new SwarmDescriptor(swarmId, caseId,
                TopologyType.CENTRALISED, Set.of("analysis"), Set.of("a1"), null));
        decorator = new CrossSwarmOutcomeDecorator(delegate, bus, registry, swarmId);
    }

    @Test
    void delegates_to_original_executor() {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        decorator.execute("agent", outcome, null);
        assertThat(delegate.lastOutcome).isEqualTo(outcome);
    }

    @Test
    void writeback_with_observations_publishes_findings() {
        var obs = new Observation("cpu-spike", 0.85, Map.of(), Instant.now());
        var outcome = new DecisionOutcome.WriteBack(List.of(obs), List.of(), List.of());
        decorator.execute("agent", outcome, null);
        assertThat(bus.replayBuffer()).hasSize(1);
        var finding = (MultiSwarmSignal.Finding) bus.replayBuffer().get(0);
        assertThat(finding.domain()).isEqualTo("cpu-spike");
        assertThat(finding.conclusion()).isEqualTo("cpu-spike:detected");
    }

    @Test
    void empty_writeback_does_not_publish() {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        decorator.execute("agent", outcome, null);
        assertThat(bus.replayBuffer()).isEmpty();
    }

    static class RecordingExecutor implements DecisionOutcomeExecutor {
        DecisionOutcome lastOutcome;
        @Override
        public CompletionStage<CoordinationFeedback> execute(String agentId, DecisionOutcome outcome, TopologyContext ctx) {
            this.lastOutcome = outcome;
            return CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
        }
    }
}
