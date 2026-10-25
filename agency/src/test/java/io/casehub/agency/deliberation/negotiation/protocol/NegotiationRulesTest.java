package io.casehub.agency.deliberation.negotiation.protocol;

import io.casehub.agency.deliberation.negotiation.*;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class NegotiationRulesTest {

    private static final String SOURCE = "NEGOTIATION";

    private final NegotiationRules rules = NegotiationRules.defaults();

    private ProtocolContext ctx(MessageType type, String sender) {
        return ctx(type, sender, List.of("alice", "bob"), Map.of());
    }

    private ProtocolContext ctx(MessageType type, String sender,
                                List<String> participants, Map<String, String> overrides) {
        return new ProtocolContext(UUID.randomUUID(), "test-channel", type, sender,
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

    // ── Structural rules ─────────────────────────────────────────────────

    @Test
    void terminalState_anyMessage_returnsCritical() {
        var state = terminalState();
        var advisories = rules.evaluate(state, ctx(MessageType.PROPOSE, "alice"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.CRITICAL && a.source().equals(SOURCE));
    }

    @Test
    void nonParticipant_returnsWarning() {
        var state = pendingState();
        var advisories = rules.evaluate(state, ctx(MessageType.PROPOSE, "charlie"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("not a negotiation participant"));
    }

    @Test
    void orphanAccept_returnsAdvisory() {
        var state = new NegotiationState(List.of(), Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);
        var advisories = rules.evaluate(state, ctx(MessageType.DONE, "alice"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.ADVISORY
                && a.message().contains("without an active proposal"));
    }

    @Test
    void validMessage_noAdvisories() {
        var state = pendingState();
        var advisories = rules.evaluate(state, ctx(MessageType.PROPOSE, "alice"));

        assertThat(advisories).isEmpty();
    }

    // ── Policy rules ─────────────────────────────────────────────────────

    @Test
    void maxRoundsExceeded_returnsWarning() {
        var rules = NegotiationRules.builder().maxRounds(2).build();
        var proposals = List.of(
                new Proposal("p1", "alice", "v1", 1, Instant.now(), ProposalStatus.SUPERSEDED),
                new Proposal("p2", "bob", "v2", 2, Instant.now(), ProposalStatus.ACTIVE));
        var state = new NegotiationState(proposals, Set.of("alice", "bob"),
                Map.of(), NegotiationOutcome.PENDING);

        var advisories = rules.evaluate(state, ctx(MessageType.PROPOSE, "alice"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("Max negotiation rounds"));
    }

    @Test
    void turnOrder_proposerAcceptingOwn_returnsWarning() {
        var rules = NegotiationRules.builder().enforceTurnOrder(true).build();
        var state = pendingState();

        var advisories = rules.evaluate(state, ctx(MessageType.DONE, "alice"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("cannot accept their own proposal"));
    }

    @Test
    void turnOrder_disabled_noAdvisoryForSelfAccept() {
        var state = pendingState();

        var advisories = rules.evaluate(state, ctx(MessageType.DONE, "alice"));

        assertThat(advisories).noneMatch(a ->
                a.message().contains("cannot accept their own proposal"));
    }

    // ── policyOverrides parsing ──────────────────────────────────────────

    @Test
    void withOverrides_maxRounds() {
        var overridden = rules.withOverrides(Map.of("negotiation.max-rounds", "5"));
        assertThat(overridden.maxRounds()).isEqualTo(5);
    }

    @Test
    void withOverrides_acceptanceType() {
        var overridden = rules.withOverrides(Map.of("negotiation.acceptance", "majority"));
        assertThat(overridden.acceptanceType()).isEqualTo("majority");
    }

    @Test
    void withOverrides_malformedValue_usesFallback() {
        var overridden = rules.withOverrides(Map.of("negotiation.max-rounds", "notanumber"));
        assertThat(overridden.maxRounds()).isEqualTo(10);
    }

    @Test
    void withOverrides_unknownKey_ignored() {
        var overridden = rules.withOverrides(Map.of("negotiation.unknown", "value"));
        assertThat(overridden.maxRounds()).isEqualTo(10);
    }

    @Test
    void withOverrides_emptyMap_returnsSame() {
        var overridden = rules.withOverrides(Map.of());
        assertThat(overridden).isSameAs(rules);
    }

    // ── AcceptancePolicy resolution ──────────────────────────────────────

    @Test
    void resolveAcceptancePolicy_unanimous() {
        assertThat(rules.resolveAcceptancePolicy(3))
                .isInstanceOf(UnanimousAcceptance.class);
    }

    @Test
    void resolveAcceptancePolicy_majority() {
        var r = NegotiationRules.builder().acceptanceType("majority").build();
        assertThat(r.resolveAcceptancePolicy(3))
                .isInstanceOf(MajorityAcceptance.class);
    }

    @Test
    void resolveAcceptancePolicy_threshold() {
        var r = NegotiationRules.builder().acceptanceType("threshold").threshold(0.5).build();
        var policy = r.resolveAcceptancePolicy(5);
        assertThat(policy).isInstanceOf(ThresholdAcceptance.class);
    }
}
