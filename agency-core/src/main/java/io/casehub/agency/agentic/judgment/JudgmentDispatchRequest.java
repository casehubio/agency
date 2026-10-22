package io.casehub.agency.agentic.judgment;

import org.jspecify.annotations.Nullable;

public record JudgmentDispatchRequest(
        JudgmentContext<?> context,
        CallerRef caller,
        @Nullable String feedback) {}
