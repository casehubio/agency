package io.casehub.agency.agentic.yaml.spec;

import io.casehub.api.spi.judgment.EvidenceRequirement;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record PatternJudgmentConfigSpec(
        String prompt,
        CallerConfigSpec callerConfig,
        @Nullable VerifierStrategySpec verifier,
        List<EvidenceRequirement> evidenceRequirements,
        @Nullable String mode,
        boolean afterStep) {}
