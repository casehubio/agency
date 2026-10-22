package io.casehub.agency.agentic.intention;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface IntentionMonitor {
    @Nullable ReconsiderationSignal evaluate(JointIntention intention);
}
