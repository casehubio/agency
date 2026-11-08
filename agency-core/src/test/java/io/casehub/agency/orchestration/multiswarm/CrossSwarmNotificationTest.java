package io.casehub.agency.orchestration.multiswarm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import io.casehub.api.model.stigmergy.SwarmProgress;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CrossSwarmNotificationTest {

    @Test
    void creates_with_valid_fields() {
        var source = UUID.randomUUID();
        var target = UUID.randomUUID();
        var trigger = new MultiSwarmSignal.Finding(source, Set.of("analysis"),
                System.currentTimeMillis(), "health", "node:down", 0.9, Map.of());
        var n = new CrossSwarmNotification(source, target, NotificationType.CONFLICT, trigger, 0.8);
        assertThat(n.sourceSwarmId()).isEqualTo(source);
        assertThat(n.targetSwarmId()).isEqualTo(target);
        assertThat(n.type()).isEqualTo(NotificationType.CONFLICT);
        assertThat(n.trigger()).isEqualTo(trigger);
        assertThat(n.confidence()).isEqualTo(0.8);
    }

    @Test
    void rejects_null_sourceSwarmId() {
        var trigger = new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of(),
                System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
        assertThatNullPointerException().isThrownBy(() ->
                new CrossSwarmNotification(null, UUID.randomUUID(), NotificationType.CONFLICT, trigger, 0.5));
    }

    @Test
    void rejects_null_targetSwarmId() {
        var trigger = new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of(),
                System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
        assertThatNullPointerException().isThrownBy(() ->
                new CrossSwarmNotification(UUID.randomUUID(), null, NotificationType.CONFLICT, trigger, 0.5));
    }

    @Test
    void rejects_null_type() {
        var trigger = new MultiSwarmSignal.Progress(UUID.randomUUID(), Set.of(),
                System.currentTimeMillis(), new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
        assertThatNullPointerException().isThrownBy(() ->
                new CrossSwarmNotification(UUID.randomUUID(), UUID.randomUUID(), null, trigger, 0.5));
    }

    @Test
    void rejects_null_trigger() {
        assertThatNullPointerException().isThrownBy(() ->
                new CrossSwarmNotification(UUID.randomUUID(), UUID.randomUUID(), NotificationType.CONFLICT, null, 0.5));
    }
}
