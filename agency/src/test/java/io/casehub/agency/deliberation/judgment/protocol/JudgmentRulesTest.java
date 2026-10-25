package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentRulesTest {

    private final JudgmentRules rules = JudgmentRules.defaults();

    private ProtocolContext ctx(MessageType type, String sender) {
        return ctx(type, sender, List.of("alice", "bob"), Map.of());
    }

    private ProtocolContext ctx(MessageType type, String sender,
                                List<String> participants, Map<String, String> overrides) {
        return new ProtocolContext(UUID.randomUUID(), "test-channel", type, sender,
                UUID.randomUUID().toString(), participants, List.of(), List.of(), overrides);
    }

    private JudgmentState pendingState() {
        return new JudgmentState(Set.of("alice", "bob"), Map.of(),
                JudgmentOutcome.PENDING, "subject");
    }

    private JudgmentState approvedState() {
        return new JudgmentState(Set.of("alice", "bob"), Map.of(),
                JudgmentOutcome.APPROVED, "subject");
    }

    // ── Structural rules ─────────────────────────────────────────────────

    @Test
    void terminalState_returnsCritical() {
        var advisories = rules.evaluate(approvedState(), ctx(MessageType.DONE, "alice"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.CRITICAL
                && a.source().equals("JUDGMENT"));
    }

    @Test
    void nonReviewer_verdict_returnsWarning() {
        var advisories = rules.evaluate(pendingState(),
                ctx(MessageType.DONE, "charlie"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("not in reviewer set"));
    }

    @Test
    void validMessage_noAdvisories() {
        var advisories = rules.evaluate(pendingState(),
                ctx(MessageType.DONE, "alice"));

        assertThat(advisories).isEmpty();
    }

    // ── Agreement: unanimous ─────────────────────────────────────────────

    @Test
    void unanimous_allApprove_advisory() {
        var rules = JudgmentRules.builder().agreementType("unanimous").build();
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var state = new JudgmentState(Set.of("alice", "bob"),
                Map.of("alice", v1, "bob", v2), JudgmentOutcome.PENDING, "sub");

        var advisories = rules.evaluate(state, ctx(MessageType.DONE, "bob"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.ADVISORY
                && a.message().contains("approved"));
    }

    @Test
    void unanimous_oneRejects_warning() {
        var rules = JudgmentRules.builder().agreementType("unanimous").build();
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.REJECT,
                null, Instant.now());
        var state = new JudgmentState(Set.of("alice", "bob"),
                Map.of("alice", v1, "bob", v2), JudgmentOutcome.PENDING, "sub");

        var advisories = rules.evaluate(state, ctx(MessageType.DECLINE, "bob"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("rejected"));
    }

    // ── Agreement: majority ──────────────────────────────────────────────

    @Test
    void majority_enoughApprovals_advisory() {
        var rules = JudgmentRules.builder().agreementType("majority").build();
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v3 = new JudgmentVerdict("charlie", JudgmentVerdict.Decision.REJECT,
                null, Instant.now());
        var state = new JudgmentState(Set.of("alice", "bob", "charlie"),
                Map.of("alice", v1, "bob", v2, "charlie", v3),
                JudgmentOutcome.PENDING, "sub");

        var advisories = rules.evaluate(state,
                ctx(MessageType.DECLINE, "charlie",
                        List.of("alice", "bob", "charlie"), Map.of()));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.ADVISORY
                && a.message().contains("approved"));
    }

    @Test
    void majority_earlyDetermination_approved() {
        var rules = JudgmentRules.builder().agreementType("majority").build();
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var state = new JudgmentState(Set.of("alice", "bob", "charlie"),
                Map.of("alice", v1, "bob", v2),
                JudgmentOutcome.PENDING, "sub");

        var advisories = rules.evaluate(state,
                ctx(MessageType.DONE, "bob",
                        List.of("alice", "bob", "charlie"), Map.of()));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.ADVISORY
                && a.message().contains("approved"));
    }

    // ── Escalation ───────────────────────────────────────────────────────

    @Test
    void escalation_chain_escalatesOnDisagreement() {
        var rules = JudgmentRules.builder()
                .agreementType("unanimous").escalation("chain").build();
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.REJECT,
                null, Instant.now());
        var state = new JudgmentState(Set.of("alice", "bob"),
                Map.of("alice", v1, "bob", v2), JudgmentOutcome.PENDING, "sub");

        var advisories = rules.evaluate(state, ctx(MessageType.DECLINE, "bob"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.CRITICAL
                && a.message().contains("escalated"));
    }

    // ── Min reviewers ────────────────────────────────────────────────────

    @Test
    void minReviewers_tooFew_warning() {
        var rules = JudgmentRules.builder().minReviewers(3).build();
        var advisories = rules.evaluate(pendingState(), ctx(MessageType.DONE, "alice"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("fewer than"));
    }

    // ── withOverrides ────────────────────────────────────────────────────

    @Test
    void withOverrides_agreementType() {
        var overridden = rules.withOverrides(Map.of("judgment.agreement", "majority"));
        assertThat(overridden.agreementType()).isEqualTo("majority");
    }

    @Test
    void withOverrides_minReviewers() {
        var overridden = rules.withOverrides(Map.of("judgment.min-reviewers", "5"));
        assertThat(overridden.minReviewers()).isEqualTo(5);
    }

    @Test
    void withOverrides_escalation() {
        var overridden = rules.withOverrides(Map.of("judgment.escalation", "chain"));
        assertThat(overridden.escalation()).isEqualTo("chain");
    }

    @Test
    void withOverrides_malformed_usesFallback() {
        var overridden = rules.withOverrides(Map.of("judgment.min-reviewers", "bad"));
        assertThat(overridden.minReviewers()).isEqualTo(2);
    }

    @Test
    void withOverrides_emptyMap_returnsSame() {
        var overridden = rules.withOverrides(Map.of());
        assertThat(overridden).isSameAs(rules);
    }
}
