package io.casehub.agency.orchestration.execution;

import java.util.Optional;

public enum CapabilityType {
    SUPERVISOR(CapabilityCategory.ORCHESTRATION),
    CHOREOGRAPHY(CapabilityCategory.ORCHESTRATION),
    SAGA(CapabilityCategory.ORCHESTRATION),
    DECOMPOSITION(CapabilityCategory.ORCHESTRATION),

    VOTING(CapabilityCategory.DELIBERATION),
    DEBATE(CapabilityCategory.DELIBERATION),
    NEGOTIATION(CapabilityCategory.DELIBERATION),
    COALITION(CapabilityCategory.DELIBERATION),
    NORMATIVE(CapabilityCategory.DELIBERATION),

    SUMMARISATION(CapabilityCategory.PERCEPTION),
    NARRATIVE(CapabilityCategory.PERCEPTION),
    OBSERVATION(CapabilityCategory.PERCEPTION),

    CHANNEL_EXECUTION(CapabilityCategory.COMMUNICATION),
    CHANNEL_SUMMARY(CapabilityCategory.COMMUNICATION),

    JUDGMENT(CapabilityCategory.GOVERNANCE),
    OVERSIGHT(CapabilityCategory.GOVERNANCE),
    ATTESTATION(CapabilityCategory.GOVERNANCE),
    TRUST(CapabilityCategory.GOVERNANCE),

    PROMPT_OPTIMISATION(CapabilityCategory.OPTIMISATION),

    MEMORY_HYGIENE(CapabilityCategory.SOCIAL),
    COGNITION(CapabilityCategory.SOCIAL),
    REFLECTION(CapabilityCategory.SOCIAL),

    SEQUENCE(CapabilityCategory.PLAYBOOK),
    PARALLEL(CapabilityCategory.PLAYBOOK),
    LOOP(CapabilityCategory.PLAYBOOK),
    CONDITIONAL(CapabilityCategory.PLAYBOOK),
    MATCH(CapabilityCategory.PLAYBOOK),
    TRY_CATCH(CapabilityCategory.PLAYBOOK);

    private final CapabilityCategory category;

    CapabilityType(CapabilityCategory category) {
        this.category = category;
    }

    public CapabilityCategory category() {
        return category;
    }

    public Optional<PatternType> toPatternType() {
        return switch (this) {
            case SUPERVISOR -> Optional.of(PatternType.SUPERVISOR);
            case VOTING -> Optional.of(PatternType.VOTING);
            case DEBATE -> Optional.of(PatternType.DEBATE);
            case DECOMPOSITION -> Optional.of(PatternType.HTN);
            case SEQUENCE -> Optional.of(PatternType.SEQUENCE);
            case PARALLEL -> Optional.of(PatternType.PARALLEL);
            case LOOP -> Optional.of(PatternType.LOOP);
            case CONDITIONAL -> Optional.of(PatternType.CONDITIONAL);
            default -> Optional.empty();
        };
    }
}
