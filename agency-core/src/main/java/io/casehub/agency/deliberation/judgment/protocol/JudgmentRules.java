package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class JudgmentRules {

    private static final String SOURCE = "JUDGMENT";

    private final String agreementType;
    private final int minReviewers;
    private final String escalation;

    private JudgmentRules(String agreementType, int minReviewers, String escalation) {
        this.agreementType = agreementType;
        this.minReviewers = minReviewers;
        this.escalation = escalation;
    }

    public static JudgmentRules defaults() {
        return new JudgmentRules("consensus", 2, "none");
    }

    public static Builder builder() {
        return new Builder();
    }

    public JudgmentRules withOverrides(Map<String, String> policyOverrides) {
        if (policyOverrides == null || policyOverrides.isEmpty()) return this;
        String at = policyOverrides.getOrDefault("judgment.agreement", agreementType);
        int mr = parseInt(policyOverrides, "judgment.min-reviewers", minReviewers);
        String esc = policyOverrides.getOrDefault("judgment.escalation", escalation);
        return new JudgmentRules(at, mr, esc);
    }

    public List<DispatchAdvisory> evaluate(JudgmentState state, ProtocolContext ctx) {
        List<DispatchAdvisory> advisories = new ArrayList<>();
        evaluateStructuralRules(state, ctx, advisories);
        evaluatePolicyRules(state, ctx, advisories);
        return List.copyOf(advisories);
    }

    public String agreementType() { return agreementType; }
    public int minReviewers() { return minReviewers; }
    public String escalation() { return escalation; }

    // ── Structural rules (always on) ─────────────────────────────────────

    private void evaluateStructuralRules(JudgmentState state, ProtocolContext ctx,
                                         List<DispatchAdvisory> advisories) {
        if (state.outcome().isTerminal()) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.CRITICAL,
                    "Judgment is " + state.outcome() + " — no further verdicts expected",
                    Map.of("outcome", state.outcome().name()),
                    SuggestedAction.ESCALATE));
            return;
        }

        if ((ctx.incomingType() == MessageType.DONE || ctx.incomingType() == MessageType.DECLINE)
                && !state.reviewers().contains(ctx.sender())) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Sender '" + ctx.sender() + "' is not in reviewer set",
                    Map.of("sender", ctx.sender(),
                            "reviewers", state.reviewers().toString()),
                    SuggestedAction.LOG));
        }
    }

    // ── Policy rules (configurable) ──────────────────────────────────────

    private void evaluatePolicyRules(JudgmentState state, ProtocolContext ctx,
                                     List<DispatchAdvisory> advisories) {
        if (state.outcome().isTerminal()) return;

        if (state.reviewers().size() < minReviewers) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Reviewer set has fewer than " + minReviewers + " reviewers ("
                            + state.reviewers().size() + ")",
                    Map.of("actual", state.reviewers().size(),
                            "required", minReviewers),
                    SuggestedAction.INVESTIGATE));
        }

        evaluateAgreement(state, advisories);
    }

    private void evaluateAgreement(JudgmentState state,
                                    List<DispatchAdvisory> advisories) {
        if (state.verdicts().isEmpty()) return;

        long approvals = state.approvalCount();
        long rejections = state.rejectionCount();
        int total = state.reviewers().size();

        boolean determined = false;
        boolean approved = false;

        switch (agreementType) {
            case "unanimous" -> {
                if (rejections > 0) {
                    determined = true;
                } else if (state.hasAllReviewed()) {
                    determined = true;
                    approved = true;
                }
            }
            case "majority" -> {
                if (approvals > total / 2.0) {
                    determined = true;
                    approved = true;
                } else if (rejections > total / 2.0) {
                    determined = true;
                } else if (state.hasAllReviewed()) {
                    determined = true;
                    approved = approvals > rejections;
                }
            }
            case "consensus" -> {
                double threshold = 2.0 / 3.0;
                if (total > 0 && (double) approvals / total >= threshold) {
                    determined = true;
                    approved = true;
                } else if (state.hasAllReviewed()) {
                    determined = true;
                }
            }
            default -> { }
        }

        if (determined) {
            if (approved) {
                advisories.add(new DispatchAdvisory(SOURCE, Severity.ADVISORY,
                        "Judgment approved (" + approvals + "/" + total + " approvals, "
                                + agreementType + " agreement)",
                        Map.of("approvals", approvals, "rejections", rejections,
                                "agreement", agreementType),
                        SuggestedAction.LOG));
            } else if ("chain".equals(escalation)) {
                advisories.add(new DispatchAdvisory(SOURCE, Severity.CRITICAL,
                        "Judgment escalated — no agreement reached (" + approvals
                                + "/" + total + " approvals)",
                        Map.of("approvals", approvals, "rejections", rejections,
                                "agreement", agreementType),
                        SuggestedAction.ESCALATE));
            } else {
                advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                        "Judgment rejected (" + approvals + "/" + total + " approvals, "
                                + agreementType + " agreement required)",
                        Map.of("approvals", approvals, "rejections", rejections,
                                "agreement", agreementType),
                        SuggestedAction.INVESTIGATE));
            }
        }
    }

    // ── Parsing utilities ────────────────────────────────────────────────

    private static int parseInt(Map<String, String> m, String key, int fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static final class Builder {
        private String agreementType = "consensus";
        private int minReviewers = 2;
        private String escalation = "none";

        private Builder() {}

        public Builder agreementType(String v) { this.agreementType = Objects.requireNonNull(v); return this; }
        public Builder minReviewers(int v) { this.minReviewers = v; return this; }
        public Builder escalation(String v) { this.escalation = Objects.requireNonNull(v); return this; }

        public JudgmentRules build() {
            return new JudgmentRules(agreementType, minReviewers, escalation);
        }
    }
}
