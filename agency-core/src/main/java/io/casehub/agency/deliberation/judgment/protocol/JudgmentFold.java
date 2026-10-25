package io.casehub.agency.deliberation.judgment.protocol;

import java.util.LinkedHashMap;
import java.util.Map;

public final class JudgmentFold {
    private JudgmentFold() {}

    public static JudgmentState submitVerdict(JudgmentState state, JudgmentVerdict verdict) {
        var verdicts = new LinkedHashMap<>(state.verdicts());
        verdicts.put(verdict.reviewer(), verdict);
        return new JudgmentState(state.reviewers(), Map.copyOf(verdicts),
                state.outcome(), state.subject());
    }

    public static JudgmentState approve(JudgmentState state) {
        return new JudgmentState(state.reviewers(), state.verdicts(),
                JudgmentOutcome.APPROVED, state.subject());
    }

    public static JudgmentState reject(JudgmentState state) {
        return new JudgmentState(state.reviewers(), state.verdicts(),
                JudgmentOutcome.REJECTED, state.subject());
    }

    public static JudgmentState escalate(JudgmentState state) {
        return new JudgmentState(state.reviewers(), state.verdicts(),
                JudgmentOutcome.ESCALATED, state.subject());
    }
}
