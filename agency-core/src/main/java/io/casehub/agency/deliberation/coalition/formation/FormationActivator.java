package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.deliberation.intention.IntentionMonitor;
import io.casehub.agency.deliberation.intention.JointIntention;
import io.casehub.eidos.org.api.GroupLifecycleService;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public class FormationActivator {

    private final GroupLifecycleService groupLifecycle;
    private final @Nullable IntentionMonitor monitor;

    public FormationActivator(GroupLifecycleService groupLifecycle,
                              @Nullable IntentionMonitor monitor) {
        this.groupLifecycle = Objects.requireNonNull(groupLifecycle);
        this.monitor = monitor;
    }

    public ActivationResult activate(String groupInstanceId,
                                     Set<String> memberNames,
                                     String planDescription,
                                     String tenancyId) {
        Objects.requireNonNull(groupInstanceId);
        Objects.requireNonNull(memberNames);
        Objects.requireNonNull(planDescription);
        Objects.requireNonNull(tenancyId);

        var intention = JointIntention.form(
                groupInstanceId, planDescription, memberNames, Instant.now());

        if (monitor != null) {
            var signal = monitor.evaluate(intention);
            if (signal != null) {
                return ActivationResult.monitorRejected(signal);
            }
        }

        var activated = intention.activate();

        try {
            groupLifecycle.activateGroup(groupInstanceId, tenancyId);
        } catch (Exception e) {
            return ActivationResult.activationFailed(e.getMessage());
        }

        return ActivationResult.activated(groupInstanceId, activated);
    }
}
