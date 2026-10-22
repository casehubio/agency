package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.casehub.api.spi.QuorumConfig;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = RiskDecisionSpec.Autonomous.class, name = "autonomous"),
        @Type(value = RiskDecisionSpec.GateRequired.class, name = "gate-required")
})
public sealed interface RiskDecisionSpec {

    record Autonomous() implements RiskDecisionSpec {}

    record GateRequired(
            String reason,
            boolean reversible,
            CandidateSetStrategySpec candidateGroups,
            Duration expiresIn,
            @Nullable String scope,
            @Nullable String resolutionType,
            @Nullable QuorumConfig quorum) implements RiskDecisionSpec {

        public GateRequired {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(candidateGroups, "candidateGroups");
            Objects.requireNonNull(expiresIn, "expiresIn");
        }
    }
}
