package io.casehub.agency.orchestration.loop;

import io.casehub.agency.summarisation.EventLevel;

public final class DecisionLoopLevels {
    public static final EventLevel OBSERVATION = new EventLevel("OBSERVATION", 50);
    public static final EventLevel DECISION = new EventLevel("DECISION", 60);
    private DecisionLoopLevels() {}
}
