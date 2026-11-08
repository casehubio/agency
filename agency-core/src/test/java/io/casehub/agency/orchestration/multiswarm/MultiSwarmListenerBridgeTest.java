package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MultiSwarmListenerBridgeTest {

    CoordinationBus<MultiSwarmSignal> bus;
    TestMultiSwarmRegistry registry;
    MultiSwarmListenerBridge bridge;
    UUID caseId;
    UUID swarmId;

    @BeforeEach
    void setUp() {
        bus = new CoordinationBus<>(10);
        registry = new TestMultiSwarmRegistry();
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
    void swarmInitialised_publishes_progress_signal() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        var latch = new CountDownLatch(1);
        bus.subscribe(s -> true, s -> { received.add(s); latch.countDown(); });
        bridge.onSwarmInitialised(caseId, swarmId, new SwarmProgress(0.0, 0.0, 0.0, Instant.now()));
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get(0)).isInstanceOf(MultiSwarmSignal.Progress.class);
    }

    @Test
    void roleChanged_publishes_progress_signal() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        var latch = new CountDownLatch(1);
        bus.subscribe(s -> true, s -> { received.add(s); latch.countDown(); });
        bridge.onRoleChanged(caseId, swarmId, List.of(), new SwarmProgress(0.3, 0.3, 0.3, Instant.now()));
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get(0)).isInstanceOf(MultiSwarmSignal.Progress.class);
    }

    @Test
    void teamChanged_publishes_progress_signal() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        var latch = new CountDownLatch(1);
        bus.subscribe(s -> true, s -> { received.add(s); latch.countDown(); });
        bridge.onTeamChanged(caseId, swarmId, List.of(), new SwarmProgress(0.4, 0.4, 0.4, Instant.now()));
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received.get(0)).isInstanceOf(MultiSwarmSignal.Progress.class);
    }

    @Test
    void unknown_swarm_silently_ignored() {
        bridge.onProgressUpdated(caseId, UUID.randomUUID(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
        assertThat(bus.replayBuffer()).isEmpty();
    }
}
