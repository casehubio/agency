package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.qhorus.api.channel.ChannelProjector;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ProjectionResult;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationProtocolTest {

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

    private ConversationState emptyState() {
        return new ConversationState(new LinkedHashMap<>(), List.of(), List.of(), Map.of());
    }

    @Test
    void protocolName_returnsConversation() {
        var protocol = new ConversationProtocol(projector);
        assertThat(protocol.protocolName()).isEqualTo("CONVERSATION");
    }

    @Test
    void evaluate_nonParticipant_returnsWarning() {
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(new ProjectionResult<>(emptyState(), 1L));

        var protocol = new ConversationProtocol(projector);
        var advisories = protocol.evaluate(ctx(MessageType.QUERY, "charlie"));

        assertThat(advisories).anyMatch(a -> a.severity() == Severity.WARNING);
    }

    @Test
    void evaluate_validMessage_noAdvisories() {
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(new ProjectionResult<>(emptyState(), 1L));

        var protocol = new ConversationProtocol(projector);
        var advisories = protocol.evaluate(ctx(MessageType.QUERY, "alice"));

        assertThat(advisories).isEmpty();
    }

    @Test
    void evaluate_secondCall_usesIncrementalProjection() {
        var state = emptyState();
        var result = new ProjectionResult<>(state, 1L);

        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(result);
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class), eq(result)))
                .thenReturn(new ProjectionResult<>(state, 2L));

        var protocol = new ConversationProtocol(projector);
        protocol.evaluate(ctx(MessageType.QUERY, "alice"));
        protocol.evaluate(ctx(MessageType.RESPONSE, "bob"));

        verify(projector).project(eq(channelId),
                any(DefaultConversationProjection.class), eq(result));
    }

    @Test
    void evaluate_cachingDisabled_alwaysFullScan() {
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(new ProjectionResult<>(emptyState(), 1L));

        var protocol = new ConversationProtocol(projector, ConversationRules.defaults(), false);
        protocol.evaluate(ctx(MessageType.QUERY, "alice"));
        protocol.evaluate(ctx(MessageType.RESPONSE, "bob"));

        verify(projector, times(2)).project(eq(channelId),
                any(DefaultConversationProjection.class));
        verify(projector, never()).project(eq(channelId),
                any(DefaultConversationProjection.class), any(ProjectionResult.class));
    }

    @Test
    void evaluate_participantsChange_invalidatesCache() {
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(new ProjectionResult<>(emptyState(), 1L));

        var protocol = new ConversationProtocol(projector);
        protocol.evaluate(ctx(MessageType.QUERY, "alice",
                List.of("alice", "bob"), Map.of()));
        protocol.evaluate(ctx(MessageType.QUERY, "alice",
                List.of("alice", "bob", "charlie"), Map.of()));

        verify(projector, times(2)).project(eq(channelId),
                any(DefaultConversationProjection.class));
    }

    @Test
    void evaluate_policyOverrides_appliedPerCall() {
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(new ProjectionResult<>(emptyState(), 1L));

        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn("alice");

        var protocol = new ConversationProtocol(projector, ConversationRules.defaults(), false);

        var ctx1 = new ProtocolContext(channelId, "ch", MessageType.QUERY, "alice",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg, msg), List.of(), Map.of());
        var advisories1 = protocol.evaluate(ctx1);
        assertThat(advisories1).anyMatch(a -> a.message().contains("consecutive"));

        var ctx2 = new ProtocolContext(channelId, "ch", MessageType.QUERY, "alice",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg, msg), List.of(),
                Map.of("conversation.max-consecutive", "5"));
        var advisories2 = protocol.evaluate(ctx2);
        assertThat(advisories2).noneMatch(a -> a.message().contains("consecutive"));
    }
}
