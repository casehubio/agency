package io.casehub.agency.deliberation.negotiation.protocol;

import io.casehub.agency.deliberation.negotiation.*;
import io.casehub.qhorus.api.channel.ChannelProjector;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NegotiationProtocolTest {

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

    private NegotiationState terminalState() {
        var proposal = new Proposal("p1", "alice", "offer", 1, Instant.now(), ProposalStatus.ACCEPTED);
        return new NegotiationState(List.of(proposal), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.AGREED);
    }

    private NegotiationState pendingState() {
        var proposal = new Proposal("p1", "alice", "offer", 1, Instant.now(), ProposalStatus.ACTIVE);
        return new NegotiationState(List.of(proposal), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
    }

    @Test
    void protocolName_returnsNegotiation() {
        var protocol = new NegotiationProtocol(projector);
        assertThat(protocol.protocolName()).isEqualTo("NEGOTIATION");
    }

    @Test
    void evaluate_terminalState_returnsCritical() {
        when(projector.project(eq(channelId), any(NegotiationProjection.class)))
                .thenReturn(new ProjectionResult<>(terminalState(), 1L));

        var protocol = new NegotiationProtocol(projector);
        var advisories = protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));

        assertThat(advisories).anyMatch(a -> a.severity() == Severity.CRITICAL);
    }

    @Test
    void evaluate_validMessage_noAdvisories() {
        when(projector.project(eq(channelId), any(NegotiationProjection.class)))
                .thenReturn(new ProjectionResult<>(pendingState(), 1L));

        var protocol = new NegotiationProtocol(projector);
        var advisories = protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));

        assertThat(advisories).isEmpty();
    }

    @Test
    void evaluate_secondCall_usesIncrementalProjection() {
        var state = pendingState();
        var result = new ProjectionResult<>(state, 1L);

        when(projector.project(eq(channelId), any(NegotiationProjection.class)))
                .thenReturn(result);
        when(projector.project(eq(channelId), any(NegotiationProjection.class), eq(result)))
                .thenReturn(new ProjectionResult<>(state, 2L));

        var protocol = new NegotiationProtocol(projector);
        protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));
        protocol.evaluate(ctx(MessageType.DONE, "bob"));

        verify(projector).project(eq(channelId), any(NegotiationProjection.class), eq(result));
    }

    @Test
    void evaluate_cachingDisabled_alwaysFullScan() {
        var state = pendingState();
        when(projector.project(eq(channelId), any(NegotiationProjection.class)))
                .thenReturn(new ProjectionResult<>(state, 1L));

        var protocol = new NegotiationProtocol(projector, NegotiationRules.defaults(), false);
        protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));
        protocol.evaluate(ctx(MessageType.DONE, "bob"));

        verify(projector, times(2)).project(eq(channelId), any(NegotiationProjection.class));
        verify(projector, never()).project(eq(channelId), any(NegotiationProjection.class),
                any(ProjectionResult.class));
    }

    @Test
    void evaluate_terminalState_evictsCache() {
        var terminal = terminalState();
        when(projector.project(eq(channelId), any(NegotiationProjection.class)))
                .thenReturn(new ProjectionResult<>(terminal, 1L))
                .thenReturn(new ProjectionResult<>(terminal, 2L));

        var protocol = new NegotiationProtocol(projector);
        protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));
        protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));

        verify(projector, times(2)).project(eq(channelId), any(NegotiationProjection.class));
    }

    @Test
    void evaluate_participantsChange_invalidatesCache() {
        var state = pendingState();
        when(projector.project(eq(channelId), any(NegotiationProjection.class)))
                .thenReturn(new ProjectionResult<>(state, 1L));

        var protocol = new NegotiationProtocol(projector);
        protocol.evaluate(ctx(MessageType.PROPOSE, "alice", List.of("alice", "bob"), Map.of()));
        protocol.evaluate(ctx(MessageType.PROPOSE, "alice", List.of("alice", "bob", "charlie"), Map.of()));

        verify(projector, times(2)).project(eq(channelId), any(NegotiationProjection.class));
    }

    @Test
    void evaluate_policyOverrides_appliedPerCall() {
        var proposals = new ArrayList<Proposal>();
        for (int i = 1; i <= 5; i++) {
            proposals.add(new Proposal("p" + i, i % 2 == 0 ? "bob" : "alice", "v" + i,
                    i, Instant.now(), i < 5 ? ProposalStatus.SUPERSEDED : ProposalStatus.ACTIVE));
        }
        var state = new NegotiationState(proposals, Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
        when(projector.project(eq(channelId), any(NegotiationProjection.class)))
                .thenReturn(new ProjectionResult<>(state, 1L));

        var protocol = new NegotiationProtocol(projector, NegotiationRules.defaults(), false);

        var advisories1 = protocol.evaluate(ctx(MessageType.PROPOSE, "alice",
                List.of("alice", "bob"), Map.of()));
        assertThat(advisories1).noneMatch(a -> a.message().contains("Max negotiation rounds"));

        var advisories2 = protocol.evaluate(ctx(MessageType.PROPOSE, "alice",
                List.of("alice", "bob"), Map.of("negotiation.max-rounds", "3")));
        assertThat(advisories2).anyMatch(a -> a.message().contains("Max negotiation rounds"));
    }
}
