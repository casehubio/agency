package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.CommonGroundAnalyser;
import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.agency.deliberation.conversation.ConvergenceAnalyser;
import io.casehub.agency.deliberation.conversation.ConvergencePolicies;
import io.casehub.agency.deliberation.conversation.EpistemicRules;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConversationRules {

    private static final String SOURCE = "CONVERSATION";

    private final String turnPolicy;
    private final int maxConsecutive;
    private final Double convergenceThreshold;

    private ConversationRules(String turnPolicy, int maxConsecutive,
                              Double convergenceThreshold) {
        this.turnPolicy = turnPolicy;
        this.maxConsecutive = maxConsecutive;
        this.convergenceThreshold = convergenceThreshold;
    }

    public static ConversationRules defaults() {
        return new ConversationRules("free-form", 2, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ConversationRules withOverrides(Map<String, String> policyOverrides) {
        if (policyOverrides == null || policyOverrides.isEmpty()) return this;
        String tp = policyOverrides.getOrDefault("conversation.turn-policy", turnPolicy);
        int mc = parseInt(policyOverrides, "conversation.max-consecutive", maxConsecutive);
        Double ct = parseNullableDouble(policyOverrides, "conversation.convergence-threshold",
                convergenceThreshold);
        return new ConversationRules(tp, mc, ct);
    }

    public List<DispatchAdvisory> evaluate(ConversationState state, ProtocolContext ctx) {
        List<DispatchAdvisory> advisories = new ArrayList<>();
        evaluateStructuralRules(ctx, advisories);
        evaluatePolicyRules(state, ctx, advisories);
        return List.copyOf(advisories);
    }

    public String turnPolicy() { return turnPolicy; }
    public int maxConsecutive() { return maxConsecutive; }
    public Double convergenceThreshold() { return convergenceThreshold; }

    // ── Structural rules (always on) ─────────────────────────────────────

    private void evaluateStructuralRules(ProtocolContext ctx,
                                         List<DispatchAdvisory> advisories) {
        var participants = new HashSet<>(ctx.protocolParticipants());
        if (!participants.isEmpty() && !participants.contains(ctx.sender())) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Sender '" + ctx.sender() + "' is not a conversation participant",
                    Map.of("sender", ctx.sender()),
                    SuggestedAction.LOG));
        }
    }

    // ── Policy rules (configurable) ──────────────────────────────────────

    private void evaluatePolicyRules(ConversationState state, ProtocolContext ctx,
                                     List<DispatchAdvisory> advisories) {
        evaluateTurnPolicy(ctx, advisories);
        evaluateMaxConsecutive(ctx, advisories);
        evaluateConvergence(state, advisories);
    }

    private void evaluateTurnPolicy(ProtocolContext ctx,
                                     List<DispatchAdvisory> advisories) {
        if ("round-robin".equals(turnPolicy)) {
            var participants = ctx.protocolParticipants();
            var recent = ctx.recentMessages();
            if (!recent.isEmpty() && participants.size() > 1) {
                String lastSender = recent.getLast().sender();
                int lastIdx = participants.indexOf(lastSender);
                if (lastIdx >= 0) {
                    int expectedIdx = (lastIdx + 1) % participants.size();
                    String expected = participants.get(expectedIdx);
                    if (!expected.equals(ctx.sender())) {
                        advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                                "Turn order violation — expected '" + expected
                                        + "' but got '" + ctx.sender() + "'",
                                Map.of("expected", expected, "actual", ctx.sender()),
                                SuggestedAction.LOG));
                    }
                }
            }
        } else if ("moderated".equals(turnPolicy)) {
            var participants = ctx.protocolParticipants();
            if (!participants.isEmpty()) {
                String moderator = participants.getFirst();
                if (!moderator.equals(ctx.sender())) {
                    var recent = ctx.recentMessages();
                    boolean addressed = !recent.isEmpty()
                            && recent.getLast().sender().equals(moderator);
                    if (!addressed) {
                        advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                                "Non-moderator '" + ctx.sender()
                                        + "' speaking without being addressed",
                                Map.of("sender", ctx.sender(), "moderator", moderator),
                                SuggestedAction.LOG));
                    }
                }
            }
        }
    }

    private void evaluateMaxConsecutive(ProtocolContext ctx,
                                         List<DispatchAdvisory> advisories) {
        var recent = ctx.recentMessages();
        int consecutive = 0;
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (recent.get(i).sender().equals(ctx.sender())) {
                consecutive++;
            } else {
                break;
            }
        }
        if (consecutive >= maxConsecutive) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Sender '" + ctx.sender() + "' has sent " + (consecutive + 1)
                            + " consecutive messages (max " + maxConsecutive + ")",
                    Map.of("sender", ctx.sender(), "consecutive", consecutive + 1,
                            "maxConsecutive", maxConsecutive),
                    SuggestedAction.INVESTIGATE));
        }
    }

    private void evaluateConvergence(ConversationState state,
                                      List<DispatchAdvisory> advisories) {
        if (convergenceThreshold == null) return;
        var commonGround = CommonGroundAnalyser.analyse(state,
                EpistemicRules.tacitAcceptance(2));
        var signal = ConvergenceAnalyser.analyse(state, commonGround,
                ConvergencePolicies.structural(0.8, 3), 5);
        if (signal.confidence() >= convergenceThreshold) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.ADVISORY,
                    "Conversation convergence detected (confidence: "
                            + String.format("%.2f", signal.confidence()) + ")",
                    Map.of("convergenceState", signal.state().name(),
                            "confidence", signal.confidence(),
                            "threshold", convergenceThreshold),
                    SuggestedAction.LOG));
        }
    }

    // ── Parsing utilities ────────────────────────────────────────────────

    private static int parseInt(Map<String, String> m, String key, int fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static Double parseNullableDouble(Map<String, String> m, String key, Double fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static final class Builder {
        private String turnPolicy = "free-form";
        private int maxConsecutive = 2;
        private Double convergenceThreshold = null;

        private Builder() {}

        public Builder turnPolicy(String v) { this.turnPolicy = Objects.requireNonNull(v); return this; }
        public Builder maxConsecutive(int v) { this.maxConsecutive = v; return this; }
        public Builder convergenceThreshold(Double v) { this.convergenceThreshold = v; return this; }

        public ConversationRules build() {
            return new ConversationRules(turnPolicy, maxConsecutive, convergenceThreshold);
        }
    }
}
