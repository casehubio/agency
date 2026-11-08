# NegotiationProtocol Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #354 — feat: NegotiationProtocol — first agency pattern as qhorus ChannelProtocol
**Issue group:** #354

**Goal:** Implement `NegotiationProtocol` as a `ChannelProtocol` — the first agency interaction pattern that plugs directly into qhorus's enforcement pipeline.

**Architecture:** Agency's `NegotiationProtocol` implements qhorus's `ChannelProtocol` SPI. On each message dispatch, it projects negotiation state via `ChannelProjector` (with incremental caching), then evaluates configurable rules that return `DispatchAdvisory` results. Per-channel configuration flows through `policyOverrides` on `ProtocolContext` (upstream qhorus enhancement).

**Tech Stack:** Java 17, JUnit 5, Mockito, qhorus-api (`ChannelProtocol`, `ChannelProjector`, `ProtocolContext`, `DispatchAdvisory`), agency negotiation types (`NegotiationProjection`, `NegotiationFold`, `AcceptancePolicy`)

## Global Constraints

- No Quarkus runtime in tests — plain JUnit 5 with Mockito
- qhorus-api is a compile dependency of agency-core — no new dependencies
- `ChannelProjector` is a provided dependency (consumers supply the runtime impl)
- CDI discovery of `NegotiationProtocol` requires consumer to add the Jandex index config
- Cross-repo commit to qhorus on main is approved (consolidation pattern per CLAUDE.md)

---

## Batch 1: Foundation — upstream qhorus change + rename

### Task 1: Add policyOverrides to ProtocolContext

**Files:**
- Modify: `/Users/mdproctor/claude/casehub/qhorus/api/src/main/java/io/casehub/qhorus/api/spi/ProtocolContext.java`
- Modify: `/Users/mdproctor/claude/casehub/qhorus/runtime-core/src/main/java/io/casehub/qhorus/runtime/message/MessageService.java:354-358`
- Modify: `/Users/mdproctor/claude/casehub/qhorus/runtime/src/test/java/io/casehub/qhorus/runtime/message/protocol/RequestResponseProtocolTest.java:24-27`
- Modify: `/Users/mdproctor/claude/casehub/qhorus/runtime/src/test/java/io/casehub/qhorus/runtime/message/protocol/RoundRobinProtocolTest.java:22-24`
- Modify: `/Users/mdproctor/claude/casehub/qhorus/runtime/src/test/java/io/casehub/qhorus/runtime/message/protocol/ContributionRequiredProtocolTest.java:26-28`
- Modify: `/Users/mdproctor/claude/casehub/qhorus/runtime/src/test/java/io/casehub/qhorus/runtime/message/protocol/TaskCompletionProtocolTest.java:24-27`

**Interfaces:**
- Produces: `ProtocolContext.policyOverrides()` → `Map<String, String>` (never null, defaults to `Map.of()`)

- [ ] **Step 1: Add policyOverrides field to ProtocolContext record**

Add `Map<String, String> policyOverrides` as the 9th component. Defensively copy in compact constructor, default null to `Map.of()`.

```java
public record ProtocolContext(
        UUID channelId,
        String channelName,
        MessageType incomingType,
        String sender,
        String correlationId,
        List<String> protocolParticipants,
        List<MessageView> recentMessages,
        List<Commitment> activeCommitments,
        Map<String, String> policyOverrides) {

    public ProtocolContext {
        protocolParticipants = protocolParticipants != null ? List.copyOf(protocolParticipants) : List.of();
        recentMessages = recentMessages != null ? List.copyOf(recentMessages) : List.of();
        activeCommitments = activeCommitments != null ? List.copyOf(activeCommitments) : List.of();
        policyOverrides = policyOverrides != null ? Map.copyOf(policyOverrides) : Map.of();
    }
}
```

- [ ] **Step 2: Update MessageService to pass policyOverrides**

In `MessageService.dispatch()`, update the `ProtocolContext` construction (around line 355) to add `ch.policyOverrides()` as the 9th argument. Handle null channel overrides:

```java
ProtocolContext protocolCtx = new ProtocolContext(
        ch.id(), ch.name(), dispatch.type(), dispatch.sender(),
        dispatch.correlationId(), ch.protocolParticipants(),
        recent, activeCommitments,
        ch.policyOverrides() != null ? ch.policyOverrides() : Map.of());
```

- [ ] **Step 3: Update test helper methods in all 4 protocol test files**

Each test file has a `ctx(...)` helper. Add `Map.of()` as the 9th argument to each:

`RequestResponseProtocolTest.java`:
```java
private ProtocolContext ctx(MessageType incoming, String sender, List<Commitment> commitments) {
    return new ProtocolContext(UUID.randomUUID(), "test-channel", incoming, sender,
                               UUID.randomUUID().toString(), List.of(), List.of(), commitments, Map.of());
}
```

`RoundRobinProtocolTest.java`:
```java
return new ProtocolContext(CH, "test-channel", MessageType.STATUS, sender,
                           UUID.randomUUID().toString(), participants, List.of(), List.of(), Map.of());
```

`ContributionRequiredProtocolTest.java`:
```java
return new ProtocolContext(CH, "test-channel", MessageType.STATUS, sender,
                           UUID.randomUUID().toString(), participants, recent, List.of(), Map.of());
```

`TaskCompletionProtocolTest.java`:
```java
return new ProtocolContext(UUID.randomUUID(), "test-channel", incoming, sender,
                           UUID.randomUUID().toString(), List.of(), List.of(), commitments, Map.of());
```

- [ ] **Step 4: Build qhorus and verify all tests pass**

Run: `mvn --batch-mode test -f /Users/mdproctor/claude/casehub/qhorus/pom.xml`
Expected: BUILD SUCCESS, all existing protocol tests still pass.

- [ ] **Step 5: Install qhorus to local Maven repo**

Run: `mvn --batch-mode install -DskipTests -f /Users/mdproctor/claude/casehub/qhorus/pom.xml`

- [ ] **Step 6: Commit qhorus change**

```bash
git -C /Users/mdproctor/claude/casehub/qhorus add api/src/main/java/io/casehub/qhorus/api/spi/ProtocolContext.java runtime-core/src/main/java/io/casehub/qhorus/runtime/message/MessageService.java runtime/src/test/java/io/casehub/qhorus/runtime/message/protocol/
git -C /Users/mdproctor/claude/casehub/qhorus commit -m "feat: add policyOverrides to ProtocolContext Refs casehubio/agency#354"
```

### Task 2: Rename NegotiationProtocol → NegotiationConstants

**Files:**
- Rename: `NegotiationProtocol` → `NegotiationConstants` (use `ide_refactor_rename`)
- Modify: `CLAUDE.md` — update directory table reference
- Modify: `docs/research/2026-08-16-autonomous-agent-patterns-landscape.md` — update reference

**Interfaces:**
- Produces: `NegotiationConstants.OUTCOME_PENDING`, `.OUTCOME_AGREED`, `.OUTCOME_DEADLOCKED`, `.OUTCOME_WITHDRAWN` (same constants, new class name)

- [ ] **Step 1: Rename class via IntelliJ**

Use `ide_refactor_rename` on `NegotiationProtocol.java` to rename the class to `NegotiationConstants`. This updates the file name and all Java references automatically.

- [ ] **Step 2: Update CLAUDE.md reference**

In the negotiation package table entry, change `NegotiationProtocol` to `NegotiationConstants`:
```
| `NegotiationProtocol` | Constants for negotiation outcome strings |
```
→
```
| `NegotiationConstants` | Constants for negotiation outcome strings |
```

- [ ] **Step 3: Update research doc reference**

In `docs/research/2026-08-16-autonomous-agent-patterns-landscape.md`, update the reference to `NegotiationConstants`.

- [ ] **Step 4: Build agency to verify rename is clean**

Run: `mvn --batch-mode compile -f /Users/mdproctor/claude/casehub/agency/pom.xml`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit rename**

```bash
git add agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/NegotiationConstants.java CLAUDE.md docs/research/
git commit -m "refactor: rename NegotiationProtocol → NegotiationConstants Refs #354"
```

---

## Batch 2: NegotiationRules — pure function with builder

### Task 3: NegotiationRules with structural + policy rules

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/protocol/NegotiationRules.java`
- Test: `agency/src/test/java/io/casehub/agency/deliberation/negotiation/protocol/NegotiationRulesTest.java`

**Interfaces:**
- Consumes: `NegotiationState` (from existing negotiation package), `ProtocolContext` (from qhorus-api, now with `policyOverrides()`)
- Produces: `NegotiationRules.defaults()` → `NegotiationRules`, `NegotiationRules.builder()` → `Builder`, `NegotiationRules.withOverrides(Map<String,String>)` → `NegotiationRules`, `NegotiationRules.evaluate(NegotiationState, ProtocolContext)` → `List<DispatchAdvisory>`

- [ ] **Step 1: Write test for structural rule — terminal state violation**

```java
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

    @Test
    void terminalState_anyMessage_returnsCritical() {
        var state = terminalState();
        var advisories = rules.evaluate(state, ctx(MessageType.PROPOSE, "alice"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.CRITICAL && a.source().equals(SOURCE));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=NegotiationRulesTest#terminalState_anyMessage_returnsCritical`
Expected: FAIL — `NegotiationRules` class does not exist.

- [ ] **Step 3: Implement NegotiationRules skeleton with terminal state rule**

```java
package io.casehub.agency.deliberation.negotiation.protocol;

import io.casehub.agency.deliberation.negotiation.*;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NegotiationRules {

    private static final String SOURCE = "NEGOTIATION";

    private final int maxRounds;
    private final boolean enforceDeadline;
    private final Duration deadline;
    private final boolean enforceTurnOrder;
    private final String acceptanceType;
    private final double threshold;

    private NegotiationRules(int maxRounds, boolean enforceDeadline, Duration deadline,
                             boolean enforceTurnOrder, String acceptanceType, double threshold) {
        this.maxRounds = maxRounds;
        this.enforceDeadline = enforceDeadline;
        this.deadline = deadline;
        this.enforceTurnOrder = enforceTurnOrder;
        this.acceptanceType = acceptanceType;
        this.threshold = threshold;
    }

    public static NegotiationRules defaults() {
        return new NegotiationRules(10, false, null, false, "unanimous", 0.5);
    }

    public static Builder builder() {
        return new Builder();
    }

    public NegotiationRules withOverrides(Map<String, String> policyOverrides) {
        if (policyOverrides == null || policyOverrides.isEmpty()) return this;
        int mr = parseInt(policyOverrides, "negotiation.max-rounds", maxRounds);
        boolean ed = parseBoolean(policyOverrides, "negotiation.enforce-deadline", enforceDeadline);
        Duration dl = parseDuration(policyOverrides, "negotiation.deadline", deadline);
        boolean eto = parseBoolean(policyOverrides, "negotiation.enforce-turn-order", enforceTurnOrder);
        String at = policyOverrides.getOrDefault("negotiation.acceptance", acceptanceType);
        double th = parseDouble(policyOverrides, "negotiation.threshold", threshold);
        return new NegotiationRules(mr, ed, dl, eto, at, th);
    }

    public List<DispatchAdvisory> evaluate(NegotiationState state, ProtocolContext ctx) {
        List<DispatchAdvisory> advisories = new ArrayList<>();
        evaluateStructuralRules(state, ctx, advisories);
        evaluatePolicyRules(state, ctx, advisories);
        return List.copyOf(advisories);
    }

    public int maxRounds() { return maxRounds; }
    public boolean enforceDeadline() { return enforceDeadline; }
    public Duration deadline() { return deadline; }
    public boolean enforceTurnOrder() { return enforceTurnOrder; }
    public String acceptanceType() { return acceptanceType; }
    public double threshold() { return threshold; }

    public AcceptancePolicy resolveAcceptancePolicy(int partyCount) {
        return switch (acceptanceType) {
            case "majority" -> new MajorityAcceptance();
            case "threshold" -> new ThresholdAcceptance(
                    Math.max(1, (int) Math.ceil(threshold * (partyCount - 1))));
            default -> new UnanimousAcceptance();
        };
    }

    // ── Structural rules (always on) ─────────────────────────────────────

    private void evaluateStructuralRules(NegotiationState state, ProtocolContext ctx,
                                         List<DispatchAdvisory> advisories) {
        if (state.outcome().isTerminal()) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.CRITICAL,
                    "Negotiation is " + state.outcome() + " — no further messages expected",
                    Map.of("outcome", state.outcome().name()),
                    SuggestedAction.ESCALATE));
            return;
        }

        if (!state.parties().isEmpty() && !state.parties().contains(ctx.sender())) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Sender '" + ctx.sender() + "' is not a negotiation participant",
                    Map.of("sender", ctx.sender(), "parties", state.parties().toString()),
                    SuggestedAction.LOG));
        }

        if ((ctx.incomingType() == io.casehub.qhorus.api.message.MessageType.DONE
                || ctx.incomingType() == io.casehub.qhorus.api.message.MessageType.DECLINE)
                && state.activeProposal() == null) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.ADVISORY,
                    "Accept/reject without an active proposal",
                    Map.of("incomingType", ctx.incomingType().name()),
                    SuggestedAction.LOG));
        }
    }

    // ── Policy rules (configurable) ──────────────────────────────────────

    private void evaluatePolicyRules(NegotiationState state, ProtocolContext ctx,
                                     List<DispatchAdvisory> advisories) {
        if (state.outcome().isTerminal()) return;

        if (state.round() >= maxRounds) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Max negotiation rounds reached (" + maxRounds + ")",
                    Map.of("round", state.round(), "maxRounds", maxRounds),
                    SuggestedAction.INVESTIGATE));
        }

        if (enforceDeadline && deadline != null && state.activeProposal() != null) {
            Instant deadlineInstant = state.proposals().getFirst().createdAt().plus(deadline);
            if (java.time.Instant.now().isAfter(deadlineInstant)) {
                advisories.add(new DispatchAdvisory(SOURCE, Severity.CRITICAL,
                        "Negotiation deadline exceeded",
                        Map.of("deadline", deadline.toString()),
                        SuggestedAction.ESCALATE));
            }
        }

        if (enforceTurnOrder) {
            if (ctx.incomingType() == io.casehub.qhorus.api.message.MessageType.DONE
                    && state.activeProposal() != null
                    && state.activeProposal().proposer().equals(ctx.sender())) {
                advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                        "Proposer '" + ctx.sender() + "' cannot accept their own proposal",
                        Map.of("sender", ctx.sender()),
                        SuggestedAction.LOG));
            }
        }
    }

    // ── Parsing utilities ────────────────────────────────────────────────

    private static int parseInt(Map<String, String> m, String key, int fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static double parseDouble(Map<String, String> m, String key, double fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static boolean parseBoolean(Map<String, String> m, String key, boolean fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        return Boolean.parseBoolean(v.trim());
    }

    private static Duration parseDuration(Map<String, String> m, String key, Duration fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Duration.parse(v.trim()); } catch (Exception e) { return fallback; }
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static final class Builder {
        private int maxRounds = 10;
        private boolean enforceDeadline = false;
        private Duration deadline = null;
        private boolean enforceTurnOrder = false;
        private String acceptanceType = "unanimous";
        private double threshold = 0.5;

        private Builder() {}

        public Builder maxRounds(int v) { this.maxRounds = v; return this; }
        public Builder enforceDeadline(boolean v) { this.enforceDeadline = v; return this; }
        public Builder deadline(Duration v) { this.deadline = v; return this; }
        public Builder enforceTurnOrder(boolean v) { this.enforceTurnOrder = v; return this; }
        public Builder acceptanceType(String v) { this.acceptanceType = Objects.requireNonNull(v); return this; }
        public Builder threshold(double v) { this.threshold = v; return this; }

        public NegotiationRules build() {
            return new NegotiationRules(maxRounds, enforceDeadline, deadline,
                    enforceTurnOrder, acceptanceType, threshold);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=NegotiationRulesTest#terminalState_anyMessage_returnsCritical`
Expected: PASS

- [ ] **Step 5: Write remaining structural rule tests**

Add to `NegotiationRulesTest`:

```java
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

private NegotiationState pendingState() {
    var proposal = new Proposal("p1", "alice", "offer", 1, Instant.now(), ProposalStatus.ACTIVE);
    return new NegotiationState(List.of(proposal), Set.of("alice", "bob"),
            Map.of(), NegotiationOutcome.PENDING);
}
```

- [ ] **Step 6: Run all structural tests**

Run: `mvn --batch-mode test -pl agency -Dtest=NegotiationRulesTest`
Expected: PASS (all 4 tests)

- [ ] **Step 7: Write policy rule tests — max rounds + turn order**

```java
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
```

- [ ] **Step 8: Run all tests**

Run: `mvn --batch-mode test -pl agency -Dtest=NegotiationRulesTest`
Expected: PASS (all 7 tests)

- [ ] **Step 9: Write policyOverrides parsing tests**

```java
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
```

- [ ] **Step 10: Run all tests**

Run: `mvn --batch-mode test -pl agency -Dtest=NegotiationRulesTest`
Expected: PASS (all 15 tests)

- [ ] **Step 11: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/protocol/ agency/src/test/java/io/casehub/agency/deliberation/negotiation/protocol/
git commit -m "feat(#354): add NegotiationRules — builder-configurable rules with policyOverrides"
```

---

## Batch 3: NegotiationProtocol — ChannelProtocol implementation

### Task 4: NegotiationProtocol implements ChannelProtocol

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/protocol/NegotiationProtocol.java`
- Test: `agency/src/test/java/io/casehub/agency/deliberation/negotiation/protocol/NegotiationProtocolTest.java`
- Modify: `CLAUDE.md` — add protocol package to directory table

**Interfaces:**
- Consumes: `ChannelProjector.project(UUID, ChannelProjection)` and `ChannelProjector.project(UUID, ChannelProjection, ProjectionResult)` (from qhorus-api), `NegotiationRules.evaluate(NegotiationState, ProtocolContext)` (from Task 3), `NegotiationProjection` (existing)
- Produces: `NegotiationProtocol` implements `ChannelProtocol` — `protocolName()` → `"NEGOTIATION"`, `evaluate(ProtocolContext)` → `List<DispatchAdvisory>`

- [ ] **Step 1: Write test for basic evaluate — returns advisories from rules**

```java
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=NegotiationProtocolTest`
Expected: FAIL — `NegotiationProtocol` class does not exist (the old one was renamed).

- [ ] **Step 3: Implement NegotiationProtocol**

```java
package io.casehub.agency.deliberation.negotiation.protocol;

import io.casehub.agency.deliberation.negotiation.AcceptancePolicy;
import io.casehub.agency.deliberation.negotiation.NegotiationProjection;
import io.casehub.agency.deliberation.negotiation.NegotiationState;
import io.casehub.qhorus.api.channel.ChannelProjector;
import io.casehub.qhorus.api.spi.ChannelProtocol;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProjectionResult;
import io.casehub.qhorus.api.spi.ProtocolContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class NegotiationProtocol implements ChannelProtocol {

    private final ChannelProjector projector;
    private final NegotiationRules defaultRules;
    private final boolean cachingEnabled;
    private final ConcurrentHashMap<UUID, CachedProjection> cache;

    @Inject
    public NegotiationProtocol(ChannelProjector projector) {
        this(projector, NegotiationRules.defaults(), true);
    }

    public NegotiationProtocol(ChannelProjector projector,
                               NegotiationRules defaultRules,
                               boolean cachingEnabled) {
        this.projector = Objects.requireNonNull(projector);
        this.defaultRules = Objects.requireNonNull(defaultRules);
        this.cachingEnabled = cachingEnabled;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String protocolName() {
        return "NEGOTIATION";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        NegotiationRules rules = defaultRules.withOverrides(ctx.policyOverrides());
        Set<String> parties = new HashSet<>(ctx.protocolParticipants());
        AcceptancePolicy policy = rules.resolveAcceptancePolicy(parties.size());

        NegotiationState state = projectState(ctx.channelId(), parties, policy);

        if (state.outcome().isTerminal()) {
            evictCache(ctx.channelId());
        }

        return rules.evaluate(state, ctx);
    }

    private NegotiationState projectState(UUID channelId, Set<String> parties,
                                          AcceptancePolicy policy) {
        NegotiationProjection projection = new NegotiationProjection(parties, policy);

        if (!cachingEnabled) {
            return projector.project(channelId, projection).state();
        }

        CachedProjection cached = cache.get(channelId);
        ProjectionResult<NegotiationState> result;

        if (cached != null && cached.matches(parties)) {
            result = projector.project(channelId, projection, cached.result());
        } else {
            result = projector.project(channelId, projection);
        }

        cache.put(channelId, new CachedProjection(result, parties));
        return result.state();
    }

    private void evictCache(UUID channelId) {
        cache.remove(channelId);
    }

    record CachedProjection(ProjectionResult<NegotiationState> result, Set<String> parties) {
        boolean matches(Set<String> currentParties) {
            return parties.equals(currentParties);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=NegotiationProtocolTest`
Expected: PASS

- [ ] **Step 5: Write incremental caching tests**

```java
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

    org.mockito.Mockito.verify(projector).project(eq(channelId),
            any(NegotiationProjection.class), eq(result));
}

@Test
void evaluate_cachingDisabled_alwaysFullScan() {
    var state = pendingState();
    when(projector.project(eq(channelId), any(NegotiationProjection.class)))
            .thenReturn(new ProjectionResult<>(state, 1L));

    var protocol = new NegotiationProtocol(projector, NegotiationRules.defaults(), false);
    protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));
    protocol.evaluate(ctx(MessageType.DONE, "bob"));

    org.mockito.Mockito.verify(projector, org.mockito.Mockito.times(2))
            .project(eq(channelId), any(NegotiationProjection.class));
    org.mockito.Mockito.verify(projector, org.mockito.Mockito.never())
            .project(eq(channelId), any(NegotiationProjection.class),
                    any(ProjectionResult.class));
}

@Test
void evaluate_terminalState_evictsCache() {
    var terminal = terminalState();
    when(projector.project(eq(channelId), any(NegotiationProjection.class)))
            .thenReturn(new ProjectionResult<>(terminal, 1L));

    var protocol = new NegotiationProtocol(projector);
    protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));

    // Second call should do full scan (cache was evicted)
    when(projector.project(eq(channelId), any(NegotiationProjection.class)))
            .thenReturn(new ProjectionResult<>(terminal, 2L));
    protocol.evaluate(ctx(MessageType.PROPOSE, "alice"));

    org.mockito.Mockito.verify(projector, org.mockito.Mockito.times(2))
            .project(eq(channelId), any(NegotiationProjection.class));
}

@Test
void evaluate_participantsChange_invalidatesCache() {
    var state = pendingState();
    when(projector.project(eq(channelId), any(NegotiationProjection.class)))
            .thenReturn(new ProjectionResult<>(state, 1L));

    var protocol = new NegotiationProtocol(projector);
    protocol.evaluate(ctx(MessageType.PROPOSE, "alice", List.of("alice", "bob"), Map.of()));

    // Second call with different participants
    protocol.evaluate(ctx(MessageType.PROPOSE, "alice", List.of("alice", "bob", "charlie"), Map.of()));

    org.mockito.Mockito.verify(projector, org.mockito.Mockito.times(2))
            .project(eq(channelId), any(NegotiationProjection.class));
}

@Test
void evaluate_policyOverrides_appliedPerCall() {
    var proposals = new java.util.ArrayList<Proposal>();
    for (int i = 1; i <= 5; i++) {
        proposals.add(new Proposal("p" + i, i % 2 == 0 ? "bob" : "alice", "v" + i,
                i, Instant.now(), i < 5 ? ProposalStatus.SUPERSEDED : ProposalStatus.ACTIVE));
    }
    var state = new NegotiationState(proposals, Set.of("alice", "bob"),
            Map.of(), NegotiationOutcome.PENDING);
    when(projector.project(eq(channelId), any(NegotiationProjection.class)))
            .thenReturn(new ProjectionResult<>(state, 1L));

    var protocol = new NegotiationProtocol(projector, NegotiationRules.defaults(), false);

    // Default maxRounds=10 — should NOT warn at round 5
    var advisories1 = protocol.evaluate(ctx(MessageType.PROPOSE, "alice",
            List.of("alice", "bob"), Map.of()));
    assertThat(advisories1).noneMatch(a -> a.message().contains("Max negotiation rounds"));

    // Override maxRounds=3 — should warn at round 5
    var advisories2 = protocol.evaluate(ctx(MessageType.PROPOSE, "alice",
            List.of("alice", "bob"), Map.of("negotiation.max-rounds", "3")));
    assertThat(advisories2).anyMatch(a -> a.message().contains("Max negotiation rounds"));
}

private NegotiationState pendingState() {
    var proposal = new Proposal("p1", "alice", "offer", 1, Instant.now(), ProposalStatus.ACTIVE);
    return new NegotiationState(List.of(proposal), Set.of("alice", "bob"),
            Map.of(), NegotiationOutcome.PENDING);
}
```

- [ ] **Step 6: Run all protocol tests**

Run: `mvn --batch-mode test -pl agency -Dtest=NegotiationProtocolTest`
Expected: PASS (all 7 tests)

- [ ] **Step 7: Run full agency test suite**

Run: `mvn --batch-mode test`
Expected: BUILD SUCCESS, all tests pass including existing negotiation tests.

- [ ] **Step 8: Update CLAUDE.md — add protocol package to directory table**

Add after the negotiation package entry:
```
| `agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/protocol/` | Negotiation channel protocol — `NegotiationProtocol` (`ChannelProtocol` impl), `NegotiationRules` (builder-configurable pure rules function with `policyOverrides` mapping) |
| `agency/src/test/java/io/casehub/agency/deliberation/negotiation/protocol/` | Tests for negotiation channel protocol |
```

- [ ] **Step 9: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/deliberation/negotiation/protocol/ agency/src/test/java/io/casehub/agency/deliberation/negotiation/protocol/ CLAUDE.md
git commit -m "feat(#354): add NegotiationProtocol — first agency pattern as qhorus ChannelProtocol"
```

## References

- [2026-10-25-negotiation-protocol-design.md] — design spec this plan implements
- [decisions.md] — 5 captured decisions (D1-D5)
- `io.casehub.qhorus.api.spi.ChannelProtocol` — SPI being implemented
- `io.casehub.qhorus.api.channel.ChannelProjector` — projection facade
- `io.casehub.qhorus.runtime.message.protocol.RequestResponseProtocol` — reference impl pattern
- `io.casehub.agency.deliberation.negotiation.NegotiationProjection` — existing fold
- `io.casehub.agency.deliberation.negotiation.NegotiationFold` — pure state transitions
- GitHub casehubio/agency#354
- GitHub casehubio/casehub-ops#186 — governed interaction patterns epic
