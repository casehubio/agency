package io.casehub.agency.orchestration.multiswarm;

import java.util.Objects;
import java.util.UUID;

public record CrossSwarmNotification(
        UUID sourceSwarmId,
        UUID targetSwarmId,
        NotificationType type,
        MultiSwarmSignal trigger,
        double confidence
) {
    public CrossSwarmNotification {
        Objects.requireNonNull(sourceSwarmId);
        Objects.requireNonNull(targetSwarmId);
        Objects.requireNonNull(type);
        Objects.requireNonNull(trigger);
    }
}
