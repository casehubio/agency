package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.topology.CoordinationFeedback;
import io.casehub.agency.orchestration.topology.DecisionOutcomeExecutor;
import io.casehub.agency.orchestration.topology.FeedbackSignal;
import io.casehub.agency.orchestration.topology.TopologyContext;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.coordination.RegistryListener;
import io.casehub.api.spi.observation.Observation;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CrossSwarmOutcomeDecoratorTest {

    CoordinationBus<MultiSwarmSignal> bus;
    RecordingExecutor delegate;
    StubRegistry registry;
    CrossSwarmOutcomeDecorator decorator;
    UUID swarmId;

    @BeforeEach
    void setUp() {
        bus = new CoordinationBus<>(10);
        delegate = new RecordingExecutor();
        registry = new StubRegistry();
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
        assertThat(bus.replayBuffer().get(0)).isInstanceOf(MultiSwarmSignal.Finding.class);
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

    static class StubRegistry implements MultiSwarmRegistry {
        private final ConcurrentHashMap<UUID, SwarmDescriptor> swarms = new ConcurrentHashMap<>();
        @Override public void register(SwarmDescriptor d) { swarms.put(d.swarmId(), d); }
        @Override public void deregister(UUID id) { swarms.remove(id); }
        @Override public void updateInterests(UUID id, Set<String> caps) {}
        @Override public List<SwarmDescriptor> activeSwarms(UUID caseId) { return List.of(); }
        @Override public List<SwarmDescriptor> allActive() { return List.of(); }
        @Override public List<SwarmDescriptor> children(UUID parentId) { return List.of(); }
        @Override public @Nullable SwarmDescriptor get(UUID id) { return swarms.get(id); }
        @Override public void addListener(RegistryListener l) {}
    }
}
