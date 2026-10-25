package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.qhorus.api.channel.ChannelProjector;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.ProjectionResult;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JudgmentProtocolTest {

    private final ChannelProjector projector = mock(ChannelProjector.class);
    private final UUID channelId = UUID.randomUUID();

    private ProtocolContext ctx(MessageType type, String sender) {
        return ctx(type, sender, List.of("alice", "bob"), Map.of());
    }

    private ProtocolContext ctx(MessageType type, String sender,
                                List<String> participants, Map<String, String> overrides) {
        return new ProtocolContext(channelId, "test-channel", type, sender,
                UUID.randomUUID().toString(), participants, List.of(), List.of(), overrides);
    }

    private JudgmentState pendingState() {
        return new JudgmentState(Set.of("alice", "bob"), Map.of(),
                JudgmentOutcome.PENDING, "subject");
    }

    private JudgmentState terminalState() {
        return new JudgmentState(Set.of("alice", "bob"), Map.of(),
                JudgmentOutcome.APPROVED, "subject");
    }

    @Test
    void protocolName_returnsJudgment() {
        var protocol = new JudgmentProtocol(projector);
        assertThat(protocol.protocolName()).isEqualTo("JUDGMENT");
    }

    @Test
    void evaluate_terminalState_returnsCritical() {
        when(projector.project(eq(channelId), any(JudgmentProjection.class)))
                .thenReturn(new ProjectionResult<>(terminalState(), 1L));

        var protocol = new JudgmentProtocol(projector);
        var advisories = protocol.evaluate(ctx(MessageType.DONE, "alice"));

        assertThat(advisories).anyMatch(a -> a.severity() == Severity.CRITICAL);
    }

    @Test
    void evaluate_validMessage_noAdvisories() {
        when(projector.project(eq(channelId), any(JudgmentProjection.class)))
                .thenReturn(new ProjectionResult<>(pendingState(), 1L));

        var protocol = new JudgmentProtocol(projector);
        var advisories = protocol.evaluate(ctx(MessageType.DONE, "alice"));

        assertThat(advisories).isEmpty();
    }

    @Test
    void evaluate_secondCall_usesIncrementalProjection() {
        var state = pendingState();
        var result = new ProjectionResult<>(state, 1L);

        when(projector.project(eq(channelId), any(JudgmentProjection.class)))
                .thenReturn(result);
        when(projector.project(eq(channelId), any(JudgmentProjection.class), eq(result)))
                .thenReturn(new ProjectionResult<>(state, 2L));

        var protocol = new JudgmentProtocol(projector);
        protocol.evaluate(ctx(MessageType.DONE, "alice"));
        protocol.evaluate(ctx(MessageType.DONE, "bob"));

        verify(projector).project(eq(channelId),
                any(JudgmentProjection.class), eq(result));
    }

    @Test
    void evaluate_cachingDisabled_alwaysFullScan() {
        when(projector.project(eq(channelId), any(JudgmentProjection.class)))
                .thenReturn(new ProjectionResult<>(pendingState(), 1L));

        var protocol = new JudgmentProtocol(projector, JudgmentRules.defaults(), false);
        protocol.evaluate(ctx(MessageType.DONE, "alice"));
        protocol.evaluate(ctx(MessageType.DONE, "bob"));

        verify(projector, times(2)).project(eq(channelId),
                any(JudgmentProjection.class));
        verify(projector, never()).project(eq(channelId),
                any(JudgmentProjection.class), any(ProjectionResult.class));
    }

    @Test
    void evaluate_terminalState_evictsCache() {
        when(projector.project(eq(channelId), any(JudgmentProjection.class)))
                .thenReturn(new ProjectionResult<>(terminalState(), 1L))
                .thenReturn(new ProjectionResult<>(terminalState(), 2L));

        var protocol = new JudgmentProtocol(projector);
        protocol.evaluate(ctx(MessageType.DONE, "alice"));
        protocol.evaluate(ctx(MessageType.DONE, "alice"));

        verify(projector, times(2)).project(eq(channelId),
                any(JudgmentProjection.class));
    }

    @Test
    void evaluate_participantsChange_invalidatesCache() {
        when(projector.project(eq(channelId), any(JudgmentProjection.class)))
                .thenReturn(new ProjectionResult<>(pendingState(), 1L));

        var protocol = new JudgmentProtocol(projector);
        protocol.evaluate(ctx(MessageType.DONE, "alice",
                List.of("alice", "bob"), Map.of()));
        protocol.evaluate(ctx(MessageType.DONE, "alice",
                List.of("alice", "bob", "charlie"), Map.of()));

        verify(projector, times(2)).project(eq(channelId),
                any(JudgmentProjection.class));
    }

    @Test
    void evaluate_policyOverrides_appliedPerCall() {
        var state = new JudgmentState(Set.of("alice"),
                Map.of(), JudgmentOutcome.PENDING, "sub");
        when(projector.project(eq(channelId), any(JudgmentProjection.class)))
                .thenReturn(new ProjectionResult<>(state, 1L));

        var protocol = new JudgmentProtocol(projector, JudgmentRules.defaults(), false);

        var advisories1 = protocol.evaluate(ctx(MessageType.DONE, "alice",
                List.of("alice"), Map.of()));
        assertThat(advisories1).anyMatch(a -> a.message().contains("fewer than"));

        var advisories2 = protocol.evaluate(ctx(MessageType.DONE, "alice",
                List.of("alice"), Map.of("judgment.min-reviewers", "1")));
        assertThat(advisories2).noneMatch(a -> a.message().contains("fewer than"));
    }
}
