package io.casehub.agency.deliberation.conversation;

public record ConvergenceSignal(
        ConvergenceState state,
        double confidence,
        String reason) {}
