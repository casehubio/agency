package io.casehub.agency.conversation;

public record ConvergenceSignal(
        ConvergenceState state,
        double confidence,
        String reason) {}
