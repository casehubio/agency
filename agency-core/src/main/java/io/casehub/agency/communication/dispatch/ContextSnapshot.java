package io.casehub.agency.communication.dispatch;

public record ContextSnapshot(
        long serverContributionChars,
        long windowSizeChars,
        Double agentReportedPercent,
        int messageCount,
        double effectivePercent,
        boolean thresholdExceeded
) {}
