package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationRulesTest {

    private final ConversationRules rules = ConversationRules.defaults();

    private ProtocolContext ctx(MessageType type, String sender) {
        return ctx(type, sender, List.of("alice", "bob"), Map.of());
    }

    private ProtocolContext ctx(MessageType type, String sender,
                                List<String> participants, Map<String, String> overrides) {
        return new ProtocolContext(UUID.randomUUID(), "test-channel", type, sender,
                UUID.randomUUID().toString(), participants, List.of(), List.of(), overrides);
    }

    private ConversationState emptyState() {
        return new ConversationState(new LinkedHashMap<>(), List.of(), List.of(), Map.of());
    }

    // ── Structural rules ─────────────────────────────────────────────────

    @Test
    void nonParticipant_returnsWarning() {
        var advisories = rules.evaluate(emptyState(),
                ctx(MessageType.QUERY, "charlie"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("not a conversation participant"));
    }

    @Test
    void participant_noAdvisories() {
        var advisories = rules.evaluate(emptyState(),
                ctx(MessageType.QUERY, "alice"));

        assertThat(advisories).isEmpty();
    }

    // ── Policy rules: turn policy ────────────────────────────────────────

    @Test
    void roundRobin_outOfOrder_returnsWarning() {
        var rules = ConversationRules.builder().turnPolicy("round-robin").build();
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn("alice");

        var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.QUERY, "alice",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg), List.of(), Map.of());
        var advisories = rules.evaluate(emptyState(), ctx);

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("Turn order violation"));
    }

    @Test
    void roundRobin_correctOrder_noAdvisory() {
        var rules = ConversationRules.builder().turnPolicy("round-robin").build();
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn("alice");

        var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.QUERY, "bob",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg), List.of(), Map.of());
        var advisories = rules.evaluate(emptyState(), ctx);

        assertThat(advisories).isEmpty();
    }

    @Test
    void freeForm_noTurnAdvisories() {
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn("alice");

        var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.QUERY, "alice",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg), List.of(), Map.of());
        var advisories = rules.evaluate(emptyState(), ctx);

        assertThat(advisories).noneMatch(a ->
                a.message().contains("Turn order violation"));
    }

    @Test
    void moderated_nonModeratorWithoutAddress_returnsWarning() {
        var rules = ConversationRules.builder().turnPolicy("moderated").build();
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn("bob");

        var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.QUERY, "bob",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg), List.of(), Map.of());
        var advisories = rules.evaluate(emptyState(), ctx);

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("speaking without being addressed"));
    }

    // ── Policy rules: max consecutive ────────────────────────────────────

    @Test
    void maxConsecutive_exceeded_returnsWarning() {
        var rules = ConversationRules.builder().maxConsecutive(1).build();
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn("alice");

        var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.QUERY, "alice",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg), List.of(), Map.of());
        var advisories = rules.evaluate(emptyState(), ctx);

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("consecutive messages"));
    }

    @Test
    void maxConsecutive_withinLimit_noWarning() {
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn("bob");

        var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.QUERY, "alice",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg), List.of(), Map.of());
        var advisories = rules.evaluate(emptyState(), ctx);

        assertThat(advisories).noneMatch(a ->
                a.message().contains("consecutive messages"));
    }

    // ── withOverrides ────────────────────────────────────────────────────

    @Test
    void withOverrides_turnPolicy() {
        var overridden = rules.withOverrides(Map.of("conversation.turn-policy", "round-robin"));
        assertThat(overridden.turnPolicy()).isEqualTo("round-robin");
    }

    @Test
    void withOverrides_maxConsecutive() {
        var overridden = rules.withOverrides(Map.of("conversation.max-consecutive", "5"));
        assertThat(overridden.maxConsecutive()).isEqualTo(5);
    }

    @Test
    void withOverrides_convergenceThreshold() {
        var overridden = rules.withOverrides(
                Map.of("conversation.convergence-threshold", "0.8"));
        assertThat(overridden.convergenceThreshold()).isEqualTo(0.8);
    }

    @Test
    void withOverrides_malformed_usesFallback() {
        var overridden = rules.withOverrides(
                Map.of("conversation.max-consecutive", "bad"));
        assertThat(overridden.maxConsecutive()).isEqualTo(2);
    }

    @Test
    void withOverrides_emptyMap_returnsSame() {
        var overridden = rules.withOverrides(Map.of());
        assertThat(overridden).isSameAs(rules);
    }
}
