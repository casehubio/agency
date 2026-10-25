package io.casehub.agency.deliberation.negotiation.protocol;

import io.casehub.agency.deliberation.negotiation.AcceptancePolicy;
import io.casehub.agency.deliberation.negotiation.MajorityAcceptance;
import io.casehub.agency.deliberation.negotiation.NegotiationState;
import io.casehub.agency.deliberation.negotiation.ThresholdAcceptance;
import io.casehub.agency.deliberation.negotiation.UnanimousAcceptance;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NegotiationRules {

    private static final String SOURCE = "NEGOTIATION";

    private final int maxRounds;
    private final boolean enforceDeadline;
    private final Duration deadline;
    private final boolean enforceTurnOrder;
    private final String acceptanceType;
    private final double threshold;

    private NegotiationRules(int maxRounds, boolean enforceDeadline, Duration deadline,
                             boolean enforceTurnOrder, String acceptanceType, double threshold) {
        this.maxRounds = maxRounds;
        this.enforceDeadline = enforceDeadline;
        this.deadline = deadline;
        this.enforceTurnOrder = enforceTurnOrder;
        this.acceptanceType = acceptanceType;
        this.threshold = threshold;
    }

    public static NegotiationRules defaults() {
        return new NegotiationRules(10, false, null, false, "unanimous", 0.5);
    }

    public static Builder builder() {
        return new Builder();
    }

    public NegotiationRules withOverrides(Map<String, String> policyOverrides) {
        if (policyOverrides == null || policyOverrides.isEmpty()) return this;
        int mr = parseInt(policyOverrides, "negotiation.max-rounds", maxRounds);
        boolean ed = parseBoolean(policyOverrides, "negotiation.enforce-deadline", enforceDeadline);
        Duration dl = parseDuration(policyOverrides, "negotiation.deadline", deadline);
        boolean eto = parseBoolean(policyOverrides, "negotiation.enforce-turn-order", enforceTurnOrder);
        String at = policyOverrides.getOrDefault("negotiation.acceptance", acceptanceType);
        double th = parseDouble(policyOverrides, "negotiation.threshold", threshold);
        return new NegotiationRules(mr, ed, dl, eto, at, th);
    }

    public List<DispatchAdvisory> evaluate(NegotiationState state, ProtocolContext ctx) {
        List<DispatchAdvisory> advisories = new ArrayList<>();
        evaluateStructuralRules(state, ctx, advisories);
        evaluatePolicyRules(state, ctx, advisories);
        return List.copyOf(advisories);
    }

    public int maxRounds() { return maxRounds; }
    public boolean enforceDeadline() { return enforceDeadline; }
    public Duration deadline() { return deadline; }
    public boolean enforceTurnOrder() { return enforceTurnOrder; }
    public String acceptanceType() { return acceptanceType; }
    public double threshold() { return threshold; }

    public AcceptancePolicy resolveAcceptancePolicy(int partyCount) {
        return switch (acceptanceType) {
            case "majority" -> new MajorityAcceptance();
            case "threshold" -> new ThresholdAcceptance(
                    Math.max(1, (int) Math.ceil(threshold * (partyCount - 1))));
            default -> new UnanimousAcceptance();
        };
    }

    // ── Structural rules (always on) ─────────────────────────────────────

    private void evaluateStructuralRules(NegotiationState state, ProtocolContext ctx,
                                         List<DispatchAdvisory> advisories) {
        if (state.outcome().isTerminal()) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.CRITICAL,
                    "Negotiation is " + state.outcome() + " — no further messages expected",
                    Map.of("outcome", state.outcome().name()),
                    SuggestedAction.ESCALATE));
            return;
        }

        if (!state.parties().isEmpty() && !state.parties().contains(ctx.sender())) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Sender '" + ctx.sender() + "' is not a negotiation participant",
                    Map.of("sender", ctx.sender(), "parties", state.parties().toString()),
                    SuggestedAction.LOG));
        }

        if ((ctx.incomingType() == MessageType.DONE || ctx.incomingType() == MessageType.DECLINE)
                && state.activeProposal() == null) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.ADVISORY,
                    "Accept/reject without an active proposal",
                    Map.of("incomingType", ctx.incomingType().name()),
                    SuggestedAction.LOG));
        }
    }

    // ── Policy rules (configurable) ──────────────────────────────────────

    private void evaluatePolicyRules(NegotiationState state, ProtocolContext ctx,
                                     List<DispatchAdvisory> advisories) {
        if (state.outcome().isTerminal()) return;

        if (state.round() >= maxRounds) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Max negotiation rounds reached (" + maxRounds + ")",
                    Map.of("round", state.round(), "maxRounds", maxRounds),
                    SuggestedAction.INVESTIGATE));
        }

        if (enforceDeadline && deadline != null && state.activeProposal() != null) {
            Instant deadlineInstant = state.proposals().getFirst().createdAt().plus(deadline);
            if (Instant.now().isAfter(deadlineInstant)) {
                advisories.add(new DispatchAdvisory(SOURCE, Severity.CRITICAL,
                        "Negotiation deadline exceeded",
                        Map.of("deadline", deadline.toString()),
                        SuggestedAction.ESCALATE));
            }
        }

        if (enforceTurnOrder && ctx.incomingType() == MessageType.DONE
                && state.activeProposal() != null
                && state.activeProposal().proposer().equals(ctx.sender())) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Proposer '" + ctx.sender() + "' cannot accept their own proposal",
                    Map.of("sender", ctx.sender()),
                    SuggestedAction.LOG));
        }
    }

    // ── Parsing utilities ────────────────────────────────────────────────

    private static int parseInt(Map<String, String> m, String key, int fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static double parseDouble(Map<String, String> m, String key, double fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static boolean parseBoolean(Map<String, String> m, String key, boolean fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        return Boolean.parseBoolean(v.trim());
    }

    private static Duration parseDuration(Map<String, String> m, String key, Duration fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Duration.parse(v.trim()); } catch (Exception e) { return fallback; }
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static final class Builder {
        private int maxRounds = 10;
        private boolean enforceDeadline = false;
        private Duration deadline = null;
        private boolean enforceTurnOrder = false;
        private String acceptanceType = "unanimous";
        private double threshold = 0.5;

        private Builder() {}

        public Builder maxRounds(int v) { this.maxRounds = v; return this; }
        public Builder enforceDeadline(boolean v) { this.enforceDeadline = v; return this; }
        public Builder deadline(Duration v) { this.deadline = v; return this; }
        public Builder enforceTurnOrder(boolean v) { this.enforceTurnOrder = v; return this; }
        public Builder acceptanceType(String v) { this.acceptanceType = Objects.requireNonNull(v); return this; }
        public Builder threshold(double v) { this.threshold = v; return this; }

        public NegotiationRules build() {
            return new NegotiationRules(maxRounds, enforceDeadline, deadline,
                    enforceTurnOrder, acceptanceType, threshold);
        }
    }
}
