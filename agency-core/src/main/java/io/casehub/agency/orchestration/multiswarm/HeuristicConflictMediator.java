package io.casehub.agency.orchestration.multiswarm;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HeuristicConflictMediator implements ConflictMediator {

    private static final int MAX_HANDOFF_RETRIES = 3;
    private static final double HANDOFF_CONFIDENCE_THRESHOLD = 0.5;

    private final ConcurrentHashMap<String, AtomicInteger> handoffAttempts = new ConcurrentHashMap<>();

    @Override
    public MediationResult mediate(ConflictContext context) {
        return switch (context.notification().type()) {
            case CONFLICT            -> resolveConflictHeuristic(context);
            case RESOURCE_CONTENTION -> resolveResourceHeuristic(context);
            case DEPENDENCY          -> resolveDependencyHeuristic(context);
            case HANDOFF             -> resolveHandoffHeuristic(context);
        };
    }

    private MediationResult resolveConflictHeuristic(ConflictContext context) {
        var notification = context.notification();
        var reverseNotification = new CrossSwarmNotification(
                notification.targetSwarmId(), notification.sourceSwarmId(),
                NotificationType.CONFLICT, notification.trigger(), notification.confidence());
        return new MediationResult.FallbackApplied(
                "disputed_tag",
                "Both conclusions retained — no LLM available for mediation",
                List.of(notification, reverseNotification));
    }

    private MediationResult resolveResourceHeuristic(ConflictContext context) {
        var notification = context.notification();
        var loserNotification = new CrossSwarmNotification(
                notification.sourceSwarmId(), notification.targetSwarmId(),
                NotificationType.RESOURCE_CONTENTION, notification.trigger(), notification.confidence());
        return new MediationResult.FallbackApplied(
                "first_claimer_wins",
                "Source swarm retains contested agents — first-claimer priority",
                List.of(loserNotification));
    }

    private MediationResult resolveDependencyHeuristic(ConflictContext context) {
        var notification = context.notification();
        var sourceProgress = context.latestProgress().get(notification.sourceSwarmId());
        var targetProgress = context.latestProgress().get(notification.targetSwarmId());

        double sourceScore = sourceProgress != null ? sourceProgress.consensusScore() : 0.0;
        double targetScore = targetProgress != null ? targetProgress.consensusScore() : 0.0;

        var winnerId = sourceScore >= targetScore
                ? notification.sourceSwarmId() : notification.targetSwarmId();
        var loserId = sourceScore >= targetScore
                ? notification.targetSwarmId() : notification.sourceSwarmId();

        var constraintNotification = new CrossSwarmNotification(
                winnerId, loserId,
                NotificationType.DEPENDENCY, notification.trigger(), notification.confidence());

        return new MediationResult.FallbackApplied(
                "consensus_delegation",
                "Deferred to swarm with higher consensus score",
                List.of(constraintNotification));
    }

    private MediationResult resolveHandoffHeuristic(ConflictContext context) {
        var notification = context.notification();
        if (notification.confidence() >= HANDOFF_CONFIDENCE_THRESHOLD) {
            return new MediationResult.FallbackApplied(
                    "handoff_accepted",
                    "Handoff confidence above threshold",
                    List.of(notification));
        }

        String key = notification.sourceSwarmId() + ":" + notification.targetSwarmId();
        int attempts = handoffAttempts
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (attempts >= MAX_HANDOFF_RETRIES) {
            handoffAttempts.remove(key);
            return new MediationResult.Escalated(
                    "handoff_blocked — " + attempts + " failed attempts");
        }

        return new MediationResult.FallbackApplied(
                "handoff_retry",
                "Low confidence — retry attempt " + attempts + " of " + MAX_HANDOFF_RETRIES,
                List.of());
    }
}
