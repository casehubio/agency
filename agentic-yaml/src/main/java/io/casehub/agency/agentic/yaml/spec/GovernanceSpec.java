package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public record GovernanceSpec(
        @Nullable OversightSpec oversight,
        @JsonAlias("trust-routing") @Nullable TrustRoutingSpec trustRouting,
        @JsonAlias("cbr-routing") @Nullable CbrRoutingSpec cbrRouting,
        @Nullable AttestationSpec attestation) {

    public record OversightSpec(
            String classifier,
            boolean reversible,
            @Nullable @JsonProperty("candidateGroups") @JsonAlias("candidate-groups") List<String> candidateGroups) {

        public OversightSpec {
            Objects.requireNonNull(classifier, "classifier");
        }
    }

    public record TrustRoutingSpec(
            double threshold,
            @JsonAlias("minimum-observations") int minimumObservations,
            @JsonAlias("borderline-margin") double borderlineMargin,
            @JsonAlias("blend-factor") double blendFactor) {

        public TrustRoutingSpec() {
            this(0.7, 10, 0.1, 0.6);
        }
    }

    public record CbrRoutingSpec(
            @JsonAlias("success-weight") double successWeight,
            @JsonAlias("gate-expired-weight") double gateExpiredWeight,
            @JsonAlias("gate-rejected-weight") double gateRejectedWeight,
            @JsonAlias("failure-weight") double failureWeight) {

        public CbrRoutingSpec() {
            this(1.0, 0.5, 0.25, 0.0);
        }
    }

    public record AttestationSpec(
            String observer,
            @JsonAlias("capability-tag") @Nullable String capabilityTag) {

        public AttestationSpec {
            Objects.requireNonNull(observer, "observer");
        }
    }
}
