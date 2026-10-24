package io.casehub.agency.orchestration.execution;

public enum PatternType {
    SEQUENCE,
    PARALLEL,
    LOOP,
    CONDITIONAL,
    SUPERVISOR,
    DEBATE,
    VOTING,
    HTN;

    public boolean isWorkflowShaped() {
        return this == SEQUENCE || this == PARALLEL || this == LOOP || this == CONDITIONAL;
    }

    public CapabilityType toCapabilityType() {
        return switch (this) {
            case SUPERVISOR -> CapabilityType.SUPERVISOR;
            case VOTING -> CapabilityType.VOTING;
            case DEBATE -> CapabilityType.DEBATE;
            case HTN -> CapabilityType.DECOMPOSITION;
            case SEQUENCE -> CapabilityType.SEQUENCE;
            case PARALLEL -> CapabilityType.PARALLEL;
            case LOOP -> CapabilityType.LOOP;
            case CONDITIONAL -> CapabilityType.CONDITIONAL;
        };
    }

}
