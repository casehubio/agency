package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ChannelProjection;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

public class JudgmentProjection implements ChannelProjection<JudgmentState> {

    private final Set<String> reviewers;

    public JudgmentProjection(Set<String> reviewers) {
        this.reviewers = Set.copyOf(reviewers);
    }

    @Override
    public JudgmentState identity() {
        return new JudgmentState(reviewers, Map.of(), JudgmentOutcome.PENDING, null);
    }

    @Override
    public JudgmentState apply(JudgmentState state, MessageView message) {
        if (state.outcome().isTerminal()) return state;

        MessageType type = message.type();
        String sender = message.sender();

        if (type == MessageType.DONE && reviewers.contains(sender)) {
            return JudgmentFold.submitVerdict(state,
                    new JudgmentVerdict(sender, JudgmentVerdict.Decision.APPROVE,
                            null, Instant.now()));
        }

        if (type == MessageType.DECLINE && reviewers.contains(sender)) {
            return JudgmentFold.submitVerdict(state,
                    new JudgmentVerdict(sender, JudgmentVerdict.Decision.REJECT,
                            message.content(), Instant.now()));
        }

        if (state.subject() == null && message.content() != null) {
            return new JudgmentState(state.reviewers(), state.verdicts(),
                    state.outcome(), message.content());
        }

        return state;
    }
}
