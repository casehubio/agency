package io.casehub.agency.deliberation.judgment;

import org.jspecify.annotations.Nullable;

public record JudgmentDispatchRequest(
        JudgmentContext<?> context,
        CallerRef caller,
        @Nullable String feedback) {}
