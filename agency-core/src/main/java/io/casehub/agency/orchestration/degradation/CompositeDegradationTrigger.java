package io.casehub.agency.orchestration.degradation;

import java.util.List;
import java.util.Objects;

public class CompositeDegradationTrigger implements DegradationTrigger {

    private final List<DegradationTrigger> triggers;

    public CompositeDegradationTrigger(List<DegradationTrigger> triggers) {
        this.triggers = List.copyOf(Objects.requireNonNull(triggers));
    }

    @Override
    public boolean shouldDegrade() {
        return triggers.stream().anyMatch(DegradationTrigger::shouldDegrade);
    }
}
