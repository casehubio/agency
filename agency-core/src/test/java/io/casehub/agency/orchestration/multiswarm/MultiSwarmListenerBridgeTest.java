package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.coordination.MultiSwarmRegistry;
import io.casehub.api.spi.coordination.RegistryListener;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiSwarmListenerBridgeTest {

    CoordinationBus<MultiSwarmSignal> bus;
    StubRegistry registry;
    MultiSwarmListenerBridge bridge;
    UUID caseId;
    UUID swarmId;

    @BeforeEach
    void setUp() {
        bus = new CoordinationBus<>(10);
        registry = new StubRegistry();
        caseId = UUID.randomUUID();
        swarmId = UUID.randomUUID();
        registry.register(new SwarmDescriptor(swarmId, caseId,
                TopologyType.CENTRALISED, Set.of("analysis"), Set.of("a1"), null));
        bridge = new MultiSwarmListenerBridge(bus, registry);
    }

    @Test
    void progressUpdated_publishes_progress_signal() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        var latch = new CountDownLatch(1);
        bus.subscribe(s -> true, s -> { received.add(s); latch.countDown(); });
        bridge.onProgressUpdated(caseId, swarmId, new SwarmProgress(0.5, 0.6, 0.7, Instant.now()));
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get(0)).isInstanceOf(MultiSwarmSignal.Progress.class);
        assertThat(received.get(0).sourceSwarmId()).isEqualTo(swarmId);
        assertThat(received.get(0).domainTags()).containsExactly("analysis");
    }

    @Test
    void memberCompleted_publishes_completion_signal() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        var latch = new CountDownLatch(1);
        bus.subscribe(s -> true, s -> { received.add(s); latch.countDown(); });
        bridge.onMemberCompleted(caseId, swarmId, "agent-a");
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get(0)).isInstanceOf(MultiSwarmSignal.Completion.class);
    }

    @Test
    void unknown_swarm_silently_ignored() {
        bridge.onProgressUpdated(caseId, UUID.randomUUID(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
        assertThat(bus.replayBuffer()).isEmpty();
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
