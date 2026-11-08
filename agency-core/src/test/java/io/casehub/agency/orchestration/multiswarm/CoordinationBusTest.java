package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;

import io.casehub.api.model.stigmergy.SwarmProgress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoordinationBusTest {

    CoordinationBus<MultiSwarmSignal> bus;

    @BeforeEach
    void setUp() {
        bus = new CoordinationBus<>(10);
    }

    @Test
    void publish_delivers_to_matching_subscribers() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        var latch = new CountDownLatch(1);
        bus.subscribe(s -> s.domainTags().contains("analysis"), s -> {
            received.add(s);
            latch.countDown();
        });
        var signal = new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of("analysis"),
                System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
        bus.publish(signal);
        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(received).hasSize(1);
    }

    @Test
    void non_matching_subscribers_not_notified() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        bus.subscribe(s -> s.domainTags().contains("remediation"), received::add);
        var signal = new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of("analysis"),
                System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
        bus.publish(signal);
        Thread.sleep(100);
        assertThat(received).isEmpty();
    }

    @Test
    void unsubscribe_stops_delivery() throws InterruptedException {
        var received = new ArrayList<MultiSwarmSignal>();
        java.util.function.Consumer<MultiSwarmSignal> callback = received::add;
        bus.subscribe(s -> true, callback);
        bus.unsubscribe(callback);
        bus.publish(new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of(),
                System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now())));
        Thread.sleep(100);
        assertThat(received).isEmpty();
    }

    @Test
    void replayBuffer_returns_recent_signals() {
        for (int i = 0; i < 5; i++) {
            bus.publish(new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of("d" + i),
                    System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now())));
        }
        assertThat(bus.replayBuffer()).hasSize(5);
    }

    @Test
    void replayBuffer_bounded_by_capacity() {
        var smallBus = new CoordinationBus<MultiSwarmSignal>(3);
        for (int i = 0; i < 5; i++) {
            smallBus.publish(new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of("d" + i),
                    System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now())));
        }
        assertThat(smallBus.replayBuffer()).hasSize(3);
    }
}
