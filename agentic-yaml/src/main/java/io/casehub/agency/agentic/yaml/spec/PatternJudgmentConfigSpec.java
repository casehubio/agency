package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonAlias;
import io.casehub.api.spi.judgment.EvidenceRequirement;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record PatternJudgmentConfigSpec(
        String prompt,
        @JsonAlias("caller-config") CallerConfigSpec callerConfig,
        @Nullable VerifierStrategySpec verifier,
        @JsonAlias("evidence-requirements") List<EvidenceRequirement> evidenceRequirements,
        @Nullable String mode,
        @JsonAlias("after-step") boolean afterStep) {}
