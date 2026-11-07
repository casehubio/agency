package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.deliberation.intention.JointIntention;
import io.casehub.agency.deliberation.intention.ReconsiderationSignal;

import java.util.Objects;

public sealed interface ActivationResult {

    record Activated(String groupInstanceId,
                     JointIntention intention) implements ActivationResult {
        public Activated {
            Objects.requireNonNull(groupInstanceId);
            Objects.requireNonNull(intention);
        }
    }

    record MonitorRejected(
            ReconsiderationSignal signal) implements ActivationResult {
        public MonitorRejected {
            Objects.requireNonNull(signal);
        }
    }

    record ActivationFailed(String reason) implements ActivationResult {
        public ActivationFailed {
            Objects.requireNonNull(reason);
        }
    }

    static ActivationResult activated(String groupInstanceId,
                                      JointIntention intention) {
        return new Activated(groupInstanceId, intention);
    }

    static ActivationResult monitorRejected(ReconsiderationSignal signal) {
        return new MonitorRejected(signal);
    }

    static ActivationResult activationFailed(String reason) {
        return new ActivationFailed(reason);
    }
}
