# ConversationProtocol & JudgmentProtocol Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #355 — ConversationProtocol as qhorus ChannelProtocol
**Issue group:** #355, #356

**Goal:** Implement ConversationProtocol and JudgmentProtocol as ChannelProtocol implementations following the NegotiationProtocol pattern.

**Architecture:** Both protocols follow the two-class pattern established by NegotiationProtocol (#354): an `@ApplicationScoped` protocol class with `ChannelProjector` injection and projection caching, and a pure immutable rules class with `Builder`, `defaults()`, and `withOverrides(Map)`. ConversationProtocol reuses the existing `ConversationProjection` via a concrete `DefaultConversationProjection`. JudgmentProtocol builds a new `JudgmentProjection` with fold types.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ, qhorus-api (ChannelProtocol SPI)

## Global Constraints

- Follow NegotiationProtocol pattern exactly — same caching, same structural/policy rule split, same parsing utilities
- All new classes in `.protocol` sub-packages under existing domain packages
- No CDI in Rules classes — pure immutable types
- policyOverrides keys follow `<protocol-name>.kebab-case` convention
- Tests use mocked `ChannelProjector`, same helper pattern as `NegotiationProtocolTest`

---

## Batch 1: Rename ConversationProtocol → ConversationConstants

### Task 1: Rename existing ConversationProtocol constants class

**Files:**
- Rename: `ConversationProtocol` → `ConversationConstants` (use `ide_refactor_rename`)
- Test: verify compilation via `mvn --batch-mode compile`

**Interfaces:**
- Produces: `ConversationConstants` class with identical constants (ENTRY_TYPE, ROLE, ROUND, etc.)

- [ ] **Step 1: Rename using IntelliJ refactoring**

Use `ide_refactor_rename` on `ConversationProtocol` to rename it to `ConversationConstants`. This updates all references across the project automatically.

```
ide_refactor_rename(
  file: "agency-core/src/main/java/io/casehub/agency/deliberation/conversation/ConversationProtocol.java",
  line: 3,
  column: 20,
  newName: "ConversationConstants"
)
```

- [ ] **Step 2: Verify compilation**

Run: `mvn --batch-mode compile -pl agency-core`
Expected: BUILD SUCCESS

- [ ] **Step 3: Verify tests still pass**

Run: `mvn --batch-mode test -pl agency`
Expected: BUILD SUCCESS, all existing conversation tests pass

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor(#355): rename ConversationProtocol → ConversationConstants

Frees the ConversationProtocol name for the new ChannelProtocol
implementation. Pure rename — no behavioral change.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: ConversationProtocol (#355)

### Task 2: DefaultConversationProjection and ConversationRules

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/conversation/protocol/DefaultConversationProjection.java`
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/conversation/protocol/ConversationRules.java`
- Test: `agency/src/test/java/io/casehub/agency/deliberation/conversation/protocol/ConversationRulesTest.java`

**Interfaces:**
- Consumes: `ConversationProjection` (abstract base), `ConversationConstants` (sentinel/entry types), `ConversationState`, `DispatchAdvisory`, `ProtocolContext`, `Severity`, `SuggestedAction`, `ConvergenceAnalyser`, `CommonGroundAnalyser`, `EpistemicRules`, `ConvergencePolicies`
- Produces: `DefaultConversationProjection` (concrete projection), `ConversationRules` with `defaults()`, `builder()`, `withOverrides(Map)`, `evaluate(ConversationState, ProtocolContext) → List<DispatchAdvisory>`

- [ ] **Step 1: Write test for ConversationRules structural rules**

```java
package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.*;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRulesTest {

    private static final String SOURCE = "CONVERSATION";

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
        return new ConversationState(new LinkedHashMap<>(), List.of(), List.of(), List.of());
    }

    @Test
    void nonParticipant_returnsWarning() {
        var advisories = rules.evaluate(emptyState(),
                ctx(MessageType.STANDARD, "charlie"));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.WARNING
                && a.message().contains("not a conversation participant"));
    }

    @Test
    void participant_noAdvisories() {
        var advisories = rules.evaluate(emptyState(),
                ctx(MessageType.STANDARD, "alice"));

        assertThat(advisories).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest="ConversationRulesTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement DefaultConversationProjection**

```java
package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.ConversationConstants;
import io.casehub.agency.deliberation.conversation.ConversationProjection;

import java.util.Set;

public class DefaultConversationProjection extends ConversationProjection {

    private static final Set<String> INFRASTRUCTURE_TYPES = Set.of(
            ConversationConstants.SUB_TASK_REQUEST,
            ConversationConstants.SUB_TASK_FINDING,
            ConversationConstants.SUB_TASK_ERROR,
            ConversationConstants.MEMO,
            ConversationConstants.FLAG_HUMAN,
            ConversationConstants.RESTART_CONTEXT);

    @Override
    protected String sentinel() {
        return "@@conv:";
    }

    @Override
    protected boolean isPointInitiator(String entryType) {
        return !INFRASTRUCTURE_TYPES.contains(entryType);
    }

    @Override
    protected String statusAfter(String entryType) {
        return "ACTIVE";
    }
}
```

- [ ] **Step 4: Implement ConversationRules**

```java
package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.ConversationState;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ConversationRules {

    private static final String SOURCE = "CONVERSATION";

    private final String turnPolicy;
    private final int maxConsecutive;
    private final Double convergenceThreshold;

    private ConversationRules(String turnPolicy, int maxConsecutive,
                              Double convergenceThreshold) {
        this.turnPolicy = turnPolicy;
        this.maxConsecutive = maxConsecutive;
        this.convergenceThreshold = convergenceThreshold;
    }

    public static ConversationRules defaults() {
        return new ConversationRules("free-form", 2, null);
    }

    public static Builder builder() {
        return new Builder();
    }

    public ConversationRules withOverrides(Map<String, String> policyOverrides) {
        if (policyOverrides == null || policyOverrides.isEmpty()) return this;
        String tp = policyOverrides.getOrDefault("conversation.turn-policy", turnPolicy);
        int mc = parseInt(policyOverrides, "conversation.max-consecutive", maxConsecutive);
        Double ct = parseNullableDouble(policyOverrides, "conversation.convergence-threshold",
                convergenceThreshold);
        return new ConversationRules(tp, mc, ct);
    }

    public List<DispatchAdvisory> evaluate(ConversationState state, ProtocolContext ctx) {
        List<DispatchAdvisory> advisories = new ArrayList<>();
        evaluateStructuralRules(state, ctx, advisories);
        evaluatePolicyRules(state, ctx, advisories);
        return List.copyOf(advisories);
    }

    public String turnPolicy() { return turnPolicy; }
    public int maxConsecutive() { return maxConsecutive; }
    public Double convergenceThreshold() { return convergenceThreshold; }

    // ── Structural rules (always on) ─────────────────────────────────────

    private void evaluateStructuralRules(ConversationState state, ProtocolContext ctx,
                                         List<DispatchAdvisory> advisories) {
        var participants = new HashSet<>(ctx.protocolParticipants());
        if (!participants.isEmpty() && !participants.contains(ctx.sender())) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Sender '" + ctx.sender() + "' is not a conversation participant",
                    Map.of("sender", ctx.sender()),
                    SuggestedAction.LOG));
        }
    }

    // ── Policy rules (configurable) ──────────────────────────────────────

    private void evaluatePolicyRules(ConversationState state, ProtocolContext ctx,
                                     List<DispatchAdvisory> advisories) {
        evaluateTurnPolicy(ctx, advisories);
        evaluateMaxConsecutive(ctx, advisories);
        evaluateConvergence(state, ctx, advisories);
    }

    private void evaluateTurnPolicy(ProtocolContext ctx,
                                     List<DispatchAdvisory> advisories) {
        if ("round-robin".equals(turnPolicy)) {
            var participants = ctx.protocolParticipants();
            var recent = ctx.recentMessages();
            if (!recent.isEmpty() && participants.size() > 1) {
                String lastSender = recent.getLast().sender();
                int lastIdx = participants.indexOf(lastSender);
                if (lastIdx >= 0) {
                    int expectedIdx = (lastIdx + 1) % participants.size();
                    String expected = participants.get(expectedIdx);
                    if (!expected.equals(ctx.sender())) {
                        advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                                "Turn order violation — expected '" + expected
                                        + "' but got '" + ctx.sender() + "'",
                                Map.of("expected", expected, "actual", ctx.sender()),
                                SuggestedAction.LOG));
                    }
                }
            }
        } else if ("moderated".equals(turnPolicy)) {
            var participants = ctx.protocolParticipants();
            if (!participants.isEmpty()) {
                String moderator = participants.getFirst();
                if (!moderator.equals(ctx.sender())) {
                    var recent = ctx.recentMessages();
                    boolean addressed = !recent.isEmpty()
                            && recent.getLast().sender().equals(moderator);
                    if (!addressed) {
                        advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                                "Non-moderator '" + ctx.sender()
                                        + "' speaking without being addressed",
                                Map.of("sender", ctx.sender(), "moderator", moderator),
                                SuggestedAction.LOG));
                    }
                }
            }
        }
    }

    private void evaluateMaxConsecutive(ProtocolContext ctx,
                                         List<DispatchAdvisory> advisories) {
        var recent = ctx.recentMessages();
        int consecutive = 0;
        for (int i = recent.size() - 1; i >= 0; i--) {
            if (recent.get(i).sender().equals(ctx.sender())) {
                consecutive++;
            } else {
                break;
            }
        }
        if (consecutive >= maxConsecutive) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.WARNING,
                    "Sender '" + ctx.sender() + "' has sent " + (consecutive + 1)
                            + " consecutive messages (max " + maxConsecutive + ")",
                    Map.of("sender", ctx.sender(), "consecutive", consecutive + 1,
                            "maxConsecutive", maxConsecutive),
                    SuggestedAction.INVESTIGATE));
        }
    }

    private void evaluateConvergence(ConversationState state, ProtocolContext ctx,
                                      List<DispatchAdvisory> advisories) {
        if (convergenceThreshold == null) return;
        // Convergence detection uses CommonGroundAnalyser + ConvergenceAnalyser
        // from the existing conversation infrastructure
        var commonGround = io.casehub.agency.deliberation.conversation.CommonGroundAnalyser
                .analyse(state, io.casehub.agency.deliberation.conversation.EpistemicRules
                        .tacitAcceptance(2));
        var signal = io.casehub.agency.deliberation.conversation.ConvergenceAnalyser
                .analyse(state, commonGround,
                        io.casehub.agency.deliberation.conversation.ConvergencePolicies
                                .structural(0.8, 3), 5);
        if (signal.confidence() >= convergenceThreshold) {
            advisories.add(new DispatchAdvisory(SOURCE, Severity.ADVISORY,
                    "Conversation convergence detected (confidence: "
                            + String.format("%.2f", signal.confidence()) + ")",
                    Map.of("convergenceState", signal.state().name(),
                            "confidence", signal.confidence(),
                            "threshold", convergenceThreshold),
                    SuggestedAction.LOG));
        }
    }

    // ── Parsing utilities ────────────────────────────────────────────────

    private static int parseInt(Map<String, String> m, String key, int fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    private static Double parseNullableDouble(Map<String, String> m, String key, Double fallback) {
        String v = m.get(key);
        if (v == null) return fallback;
        try { return Double.parseDouble(v.trim()); } catch (NumberFormatException e) { return fallback; }
    }

    // ── Builder ──────────────────────────────────────────────────────────

    public static final class Builder {
        private String turnPolicy = "free-form";
        private int maxConsecutive = 2;
        private Double convergenceThreshold = null;

        private Builder() {}

        public Builder turnPolicy(String v) { this.turnPolicy = Objects.requireNonNull(v); return this; }
        public Builder maxConsecutive(int v) { this.maxConsecutive = v; return this; }
        public Builder convergenceThreshold(Double v) { this.convergenceThreshold = v; return this; }

        public ConversationRules build() {
            return new ConversationRules(turnPolicy, maxConsecutive, convergenceThreshold);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest="ConversationRulesTest"`
Expected: PASS

- [ ] **Step 6: Write remaining ConversationRules tests**

Add to `ConversationRulesTest`:

```java
// ── Policy rules ─────────────────────────────────────────────────

@Test
void maxConsecutive_exceeded_returnsWarning() {
    var rules = ConversationRules.builder().maxConsecutive(1).build();
    var msg = mock(io.casehub.qhorus.api.message.MessageView.class);
    when(msg.sender()).thenReturn("alice");

    var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.STANDARD, "alice",
            UUID.randomUUID().toString(), List.of("alice", "bob"),
            List.of(msg), List.of(), Map.of());
    var advisories = rules.evaluate(emptyState(), ctx);

    assertThat(advisories).anyMatch(a ->
            a.severity() == Severity.WARNING
            && a.message().contains("consecutive messages"));
}

@Test
void roundRobin_outOfOrder_returnsWarning() {
    var rules = ConversationRules.builder().turnPolicy("round-robin").build();
    var msg = mock(io.casehub.qhorus.api.message.MessageView.class);
    when(msg.sender()).thenReturn("alice");

    var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.STANDARD, "alice",
            UUID.randomUUID().toString(), List.of("alice", "bob"),
            List.of(msg), List.of(), Map.of());
    var advisories = rules.evaluate(emptyState(), ctx);

    assertThat(advisories).anyMatch(a ->
            a.severity() == Severity.WARNING
            && a.message().contains("Turn order violation"));
}

@Test
void freeForm_noTurnAdvisories() {
    var msg = mock(io.casehub.qhorus.api.message.MessageView.class);
    when(msg.sender()).thenReturn("alice");

    var ctx = new ProtocolContext(UUID.randomUUID(), "ch", MessageType.STANDARD, "alice",
            UUID.randomUUID().toString(), List.of("alice", "bob"),
            List.of(msg), List.of(), Map.of());
    var advisories = rules.evaluate(emptyState(), ctx);

    assertThat(advisories).noneMatch(a ->
            a.message().contains("Turn order violation"));
}

// ── withOverrides ────────────────────────────────────────────────

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
```

- [ ] **Step 7: Run all ConversationRules tests**

Run: `mvn --batch-mode test -pl agency -Dtest="ConversationRulesTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(#355): add DefaultConversationProjection and ConversationRules

DefaultConversationProjection provides a concrete ConversationProjection
with general-purpose defaults. ConversationRules implements structural
and policy rules following the NegotiationRules pattern.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 3: ConversationProtocol ChannelProtocol implementation

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/conversation/protocol/ConversationProtocol.java`
- Test: `agency/src/test/java/io/casehub/agency/deliberation/conversation/protocol/ConversationProtocolTest.java`

**Interfaces:**
- Consumes: `DefaultConversationProjection`, `ConversationRules`, `ChannelProjector`, `ChannelProtocol`, `ConversationState`
- Produces: `ConversationProtocol` implementing `ChannelProtocol` with `protocolName() → "CONVERSATION"`, `evaluate(ProtocolContext) → List<DispatchAdvisory>`

- [ ] **Step 1: Write ConversationProtocolTest**

```java
package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.*;
import io.casehub.qhorus.api.channel.ChannelProjector;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.*;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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
        return new ConversationState(new LinkedHashMap<>(), List.of(), List.of(), List.of());
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
        var advisories = protocol.evaluate(ctx(MessageType.STANDARD, "charlie"));

        assertThat(advisories).anyMatch(a -> a.severity() == Severity.WARNING);
    }

    @Test
    void evaluate_validMessage_noAdvisories() {
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(new ProjectionResult<>(emptyState(), 1L));

        var protocol = new ConversationProtocol(projector);
        var advisories = protocol.evaluate(ctx(MessageType.STANDARD, "alice"));

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
        protocol.evaluate(ctx(MessageType.STANDARD, "alice"));
        protocol.evaluate(ctx(MessageType.STANDARD, "bob"));

        verify(projector).project(eq(channelId),
                any(DefaultConversationProjection.class), eq(result));
    }

    @Test
    void evaluate_cachingDisabled_alwaysFullScan() {
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(new ProjectionResult<>(emptyState(), 1L));

        var protocol = new ConversationProtocol(projector, ConversationRules.defaults(), false);
        protocol.evaluate(ctx(MessageType.STANDARD, "alice"));
        protocol.evaluate(ctx(MessageType.STANDARD, "bob"));

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
        protocol.evaluate(ctx(MessageType.STANDARD, "alice",
                List.of("alice", "bob"), Map.of()));
        protocol.evaluate(ctx(MessageType.STANDARD, "alice",
                List.of("alice", "bob", "charlie"), Map.of()));

        verify(projector, times(2)).project(eq(channelId),
                any(DefaultConversationProjection.class));
    }

    @Test
    void evaluate_policyOverrides_appliedPerCall() {
        when(projector.project(eq(channelId), any(DefaultConversationProjection.class)))
                .thenReturn(new ProjectionResult<>(emptyState(), 1L));

        var msg = mock(io.casehub.qhorus.api.message.MessageView.class);
        when(msg.sender()).thenReturn("alice");

        var protocol = new ConversationProtocol(projector, ConversationRules.defaults(), false);

        // Default max-consecutive=2, sending alice after alice in recent
        var ctx1 = new ProtocolContext(channelId, "ch", MessageType.STANDARD, "alice",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg, msg), List.of(), Map.of());
        var advisories1 = protocol.evaluate(ctx1);
        assertThat(advisories1).anyMatch(a -> a.message().contains("consecutive"));

        // Override max-consecutive=5
        var ctx2 = new ProtocolContext(channelId, "ch", MessageType.STANDARD, "alice",
                UUID.randomUUID().toString(), List.of("alice", "bob"),
                List.of(msg, msg), List.of(),
                Map.of("conversation.max-consecutive", "5"));
        var advisories2 = protocol.evaluate(ctx2);
        assertThat(advisories2).noneMatch(a -> a.message().contains("consecutive"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest="ConversationProtocolTest"`
Expected: FAIL — ConversationProtocol class not found

- [ ] **Step 3: Implement ConversationProtocol**

```java
package io.casehub.agency.deliberation.conversation.protocol;

import io.casehub.agency.deliberation.conversation.ConversationState;
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
public class ConversationProtocol implements ChannelProtocol {

    private final ChannelProjector projector;
    private final ConversationRules defaultRules;
    private final boolean cachingEnabled;
    private final ConcurrentHashMap<UUID, CachedProjection> cache;

    @Inject
    public ConversationProtocol(ChannelProjector projector) {
        this(projector, ConversationRules.defaults(), true);
    }

    public ConversationProtocol(ChannelProjector projector,
                                ConversationRules defaultRules,
                                boolean cachingEnabled) {
        this.projector = Objects.requireNonNull(projector);
        this.defaultRules = Objects.requireNonNull(defaultRules);
        this.cachingEnabled = cachingEnabled;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String protocolName() {
        return "CONVERSATION";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        ConversationRules rules = defaultRules.withOverrides(ctx.policyOverrides());
        Set<String> participants = new HashSet<>(ctx.protocolParticipants());

        ConversationState state = projectState(ctx.channelId(), participants);

        return rules.evaluate(state, ctx);
    }

    private ConversationState projectState(UUID channelId, Set<String> participants) {
        DefaultConversationProjection projection = new DefaultConversationProjection();

        if (!cachingEnabled) {
            return projector.project(channelId, projection).state();
        }

        CachedProjection cached = cache.get(channelId);
        ProjectionResult<ConversationState> result;

        if (cached != null && cached.matches(participants)) {
            result = projector.project(channelId, projection, cached.result());
        } else {
            result = projector.project(channelId, projection);
        }

        cache.put(channelId, new CachedProjection(result, participants));
        return result.state();
    }

    record CachedProjection(ProjectionResult<ConversationState> result,
                            Set<String> participants) {
        boolean matches(Set<String> currentParticipants) {
            return participants.equals(currentParticipants);
        }
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn --batch-mode test -pl agency -Dtest="ConversationProtocolTest,ConversationRulesTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "feat(#355): add ConversationProtocol ChannelProtocol implementation

Implements ChannelProtocol with projection caching, policyOverrides
support for turn-policy, max-consecutive, and convergence-threshold.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 3: JudgmentProtocol (#356)

### Task 4: Judgment fold types — JudgmentOutcome, JudgmentVerdict, JudgmentState, JudgmentFold

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentOutcome.java`
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentVerdict.java`
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentState.java`
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentFold.java`
- Test: `agency/src/test/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentFoldTest.java`

**Interfaces:**
- Produces:
  - `JudgmentOutcome` enum with `PENDING`, `APPROVED`, `REJECTED`, `ESCALATED`, `isTerminal()`
  - `JudgmentVerdict` record: `reviewer`, `decision` (Decision enum: APPROVE/REJECT), `reason` (@Nullable), `respondedAt`
  - `JudgmentState` record: `reviewers`, `verdicts`, `outcome`, `subject`. Methods: `pendingReviewers()`, `hasAllReviewed()`, `approvalCount()`, `rejectionCount()`
  - `JudgmentFold`: static `submitVerdict()`, `approve()`, `reject()`, `escalate()`

- [ ] **Step 1: Write JudgmentFoldTest**

```java
package io.casehub.agency.deliberation.judgment.protocol;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentFoldTest {

    private JudgmentState emptyState() {
        return new JudgmentState(Set.of("alice", "bob"), Map.of(),
                JudgmentOutcome.PENDING, null);
    }

    @Test
    void submitVerdict_addsVerdict() {
        var verdict = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), verdict);

        assertThat(state.verdicts()).containsKey("alice");
        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.APPROVE);
    }

    @Test
    void submitVerdict_duplicateReviewer_supersedes() {
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.REJECT,
                "changed mind", Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), v1);
        state = JudgmentFold.submitVerdict(state, v2);

        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.REJECT);
    }

    @Test
    void approve_setsOutcome() {
        var state = JudgmentFold.approve(emptyState());
        assertThat(state.outcome()).isEqualTo(JudgmentOutcome.APPROVED);
    }

    @Test
    void reject_setsOutcome() {
        var state = JudgmentFold.reject(emptyState());
        assertThat(state.outcome()).isEqualTo(JudgmentOutcome.REJECTED);
    }

    @Test
    void escalate_setsOutcome() {
        var state = JudgmentFold.escalate(emptyState());
        assertThat(state.outcome()).isEqualTo(JudgmentOutcome.ESCALATED);
    }

    @Test
    void pendingReviewers_returnsUnreviewed() {
        var verdict = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), verdict);

        assertThat(state.pendingReviewers()).containsExactly("bob");
    }

    @Test
    void hasAllReviewed_falseWhenPending() {
        assertThat(emptyState().hasAllReviewed()).isFalse();
    }

    @Test
    void hasAllReviewed_trueWhenComplete() {
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), v1);
        state = JudgmentFold.submitVerdict(state, v2);

        assertThat(state.hasAllReviewed()).isTrue();
    }

    @Test
    void approvalCount_countsCorrectly() {
        var v1 = new JudgmentVerdict("alice", JudgmentVerdict.Decision.APPROVE,
                null, Instant.now());
        var v2 = new JudgmentVerdict("bob", JudgmentVerdict.Decision.REJECT,
                null, Instant.now());
        var state = JudgmentFold.submitVerdict(emptyState(), v1);
        state = JudgmentFold.submitVerdict(state, v2);

        assertThat(state.approvalCount()).isEqualTo(1);
        assertThat(state.rejectionCount()).isEqualTo(1);
    }

    @Test
    void outcome_isTerminal() {
        assertThat(JudgmentOutcome.PENDING.isTerminal()).isFalse();
        assertThat(JudgmentOutcome.APPROVED.isTerminal()).isTrue();
        assertThat(JudgmentOutcome.REJECTED.isTerminal()).isTrue();
        assertThat(JudgmentOutcome.ESCALATED.isTerminal()).isTrue();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest="JudgmentFoldTest"`
Expected: FAIL — classes not found

- [ ] **Step 3: Implement JudgmentOutcome**

```java
package io.casehub.agency.deliberation.judgment.protocol;

public enum JudgmentOutcome {
    PENDING,
    APPROVED,
    REJECTED,
    ESCALATED;

    public boolean isTerminal() {
        return this != PENDING;
    }
}
```

- [ ] **Step 4: Implement JudgmentVerdict**

```java
package io.casehub.agency.deliberation.judgment.protocol;

import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

public record JudgmentVerdict(String reviewer, Decision decision,
                              @Nullable String reason, Instant respondedAt) {
    public enum Decision { APPROVE, REJECT }

    public JudgmentVerdict {
        Objects.requireNonNull(reviewer);
        Objects.requireNonNull(decision);
        Objects.requireNonNull(respondedAt);
    }
}
```

- [ ] **Step 5: Implement JudgmentState**

```java
package io.casehub.agency.deliberation.judgment.protocol;

import org.jspecify.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record JudgmentState(Set<String> reviewers,
                            Map<String, JudgmentVerdict> verdicts,
                            JudgmentOutcome outcome,
                            @Nullable String subject) {
    public JudgmentState {
        Objects.requireNonNull(reviewers);
        Objects.requireNonNull(verdicts);
        Objects.requireNonNull(outcome);
    }

    public Set<String> pendingReviewers() {
        var pending = new LinkedHashSet<>(reviewers);
        pending.removeAll(verdicts.keySet());
        return Set.copyOf(pending);
    }

    public boolean hasAllReviewed() {
        return verdicts.keySet().containsAll(reviewers);
    }

    public long approvalCount() {
        return verdicts.values().stream()
                .filter(v -> v.decision() == JudgmentVerdict.Decision.APPROVE)
                .count();
    }

    public long rejectionCount() {
        return verdicts.values().stream()
                .filter(v -> v.decision() == JudgmentVerdict.Decision.REJECT)
                .count();
    }
}
```

- [ ] **Step 6: Implement JudgmentFold**

```java
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
```

- [ ] **Step 7: Run tests**

Run: `mvn --batch-mode test -pl agency -Dtest="JudgmentFoldTest"`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat(#356): add judgment fold types — JudgmentOutcome, JudgmentVerdict, JudgmentState, JudgmentFold

Pure value types and state transitions for channel-based judgment
protocol. Follows NegotiationFold pattern.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 5: JudgmentProjection, JudgmentRules, JudgmentProtocol

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentProjection.java`
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentRules.java`
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentProtocol.java`
- Test: `agency/src/test/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentProjectionTest.java`
- Test: `agency/src/test/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentRulesTest.java`
- Test: `agency/src/test/java/io/casehub/agency/deliberation/judgment/protocol/JudgmentProtocolTest.java`

**Interfaces:**
- Consumes: `JudgmentOutcome`, `JudgmentVerdict`, `JudgmentState`, `JudgmentFold`, `ChannelProjection`, `ChannelProjector`, `ChannelProtocol`, `DispatchAdvisory`, `ProtocolContext`, `MessageType`
- Produces:
  - `JudgmentProjection` implementing `ChannelProjection<JudgmentState>` — DONE → approve, DECLINE → reject
  - `JudgmentRules` with agreement evaluation, min-reviewers, escalation config
  - `JudgmentProtocol` implementing `ChannelProtocol` with `protocolName() → "JUDGMENT"`

- [ ] **Step 1: Write JudgmentProjectionTest**

```java
package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JudgmentProjectionTest {

    private final JudgmentProjection projection = new JudgmentProjection(
            Set.of("alice", "bob"));

    private MessageView message(String sender, MessageType type) {
        var msg = mock(MessageView.class);
        when(msg.sender()).thenReturn(sender);
        when(msg.messageType()).thenReturn(type);
        when(msg.id()).thenReturn(UUID.randomUUID());
        when(msg.createdAt()).thenReturn(Instant.now());
        when(msg.content()).thenReturn("test");
        return msg;
    }

    @Test
    void identity_returnsPendingState() {
        var state = projection.identity();
        assertThat(state.outcome()).isEqualTo(JudgmentOutcome.PENDING);
        assertThat(state.reviewers()).containsExactlyInAnyOrder("alice", "bob");
        assertThat(state.verdicts()).isEmpty();
    }

    @Test
    void done_recordsApproval() {
        var state = projection.apply(projection.identity(),
                message("alice", MessageType.DONE));

        assertThat(state.verdicts()).containsKey("alice");
        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.APPROVE);
    }

    @Test
    void decline_recordsRejection() {
        var state = projection.apply(projection.identity(),
                message("alice", MessageType.DECLINE));

        assertThat(state.verdicts()).containsKey("alice");
        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.REJECT);
    }

    @Test
    void unknownSender_ignored() {
        var state = projection.apply(projection.identity(),
                message("charlie", MessageType.DONE));

        assertThat(state.verdicts()).isEmpty();
    }

    @Test
    void otherMessageType_passesThrough() {
        var state = projection.apply(projection.identity(),
                message("alice", MessageType.STANDARD));

        assertThat(state.verdicts()).isEmpty();
    }

    @Test
    void duplicateReviewer_supersedes() {
        var state = projection.identity();
        state = projection.apply(state, message("alice", MessageType.DONE));
        state = projection.apply(state, message("alice", MessageType.DECLINE));

        assertThat(state.verdicts().get("alice").decision())
                .isEqualTo(JudgmentVerdict.Decision.REJECT);
    }

    @Test
    void firstStandardMessage_capturedAsSubject() {
        var msg = message("someone", MessageType.STANDARD);
        when(msg.content()).thenReturn("review this please");

        var state = projection.apply(projection.identity(), msg);
        assertThat(state.subject()).isEqualTo("review this please");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest="JudgmentProjectionTest"`
Expected: FAIL — class not found

- [ ] **Step 3: Implement JudgmentProjection**

```java
package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.qhorus.api.channel.ChannelProjection;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.message.MessageView;

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

        MessageType type = message.messageType();
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

        if (type == MessageType.STANDARD && state.subject() == null) {
            return new JudgmentState(state.reviewers(), state.verdicts(),
                    state.outcome(), message.content());
        }

        return state;
    }
}
```

- [ ] **Step 4: Run projection tests**

Run: `mvn --batch-mode test -pl agency -Dtest="JudgmentProjectionTest"`
Expected: PASS

- [ ] **Step 5: Write JudgmentRulesTest**

```java
package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.*;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentRulesTest {

    private static final String SOURCE = "JUDGMENT";

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
                a.severity() == Severity.CRITICAL && a.source().equals(SOURCE));
    }

    @Test
    void nonReviewer_returnsWarning() {
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

    // ── Agreement evaluation ─────────────────────────────────────────────

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
                ctx(MessageType.DECLINE, "charlie", List.of("alice", "bob", "charlie"), Map.of()));

        assertThat(advisories).anyMatch(a ->
                a.severity() == Severity.ADVISORY
                && a.message().contains("approved"));
    }

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
```

- [ ] **Step 6: Implement JudgmentRules**

```java
package io.casehub.agency.deliberation.judgment.protocol;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.spi.DispatchAdvisory;
import io.casehub.qhorus.api.spi.ProtocolContext;
import io.casehub.qhorus.api.spi.Severity;
import io.casehub.qhorus.api.spi.SuggestedAction;

import java.util.ArrayList;
import java.util.HashSet;
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
                    approved = false;
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
                    approved = false;
                } else if (state.hasAllReviewed()) {
                    determined = true;
                    approved = approvals > rejections;
                }
            }
            case "consensus" -> {
                double threshold = 2.0 / 3.0;
                if (state.hasAllReviewed() || approvals >= Math.ceil(total * threshold)) {
                    if (total > 0 && (double) approvals / total >= threshold) {
                        determined = true;
                        approved = true;
                    } else if (state.hasAllReviewed()) {
                        determined = true;
                        approved = false;
                    }
                }
            }
            default -> {
                // Treat unknown as consensus
            }
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
```

- [ ] **Step 7: Run JudgmentRulesTest**

Run: `mvn --batch-mode test -pl agency -Dtest="JudgmentRulesTest"`
Expected: PASS

- [ ] **Step 8: Write JudgmentProtocolTest**

```java
package io.casehub.agency.deliberation.judgment.protocol;

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

        // Default min-reviewers=2, only 1 reviewer → warning
        var advisories1 = protocol.evaluate(ctx(MessageType.DONE, "alice",
                List.of("alice"), Map.of()));
        assertThat(advisories1).anyMatch(a -> a.message().contains("fewer than"));

        // Override min-reviewers=1 → no warning
        var advisories2 = protocol.evaluate(ctx(MessageType.DONE, "alice",
                List.of("alice"), Map.of("judgment.min-reviewers", "1")));
        assertThat(advisories2).noneMatch(a -> a.message().contains("fewer than"));
    }
}
```

- [ ] **Step 9: Implement JudgmentProtocol**

```java
package io.casehub.agency.deliberation.judgment.protocol;

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
public class JudgmentProtocol implements ChannelProtocol {

    private final ChannelProjector projector;
    private final JudgmentRules defaultRules;
    private final boolean cachingEnabled;
    private final ConcurrentHashMap<UUID, CachedProjection> cache;

    @Inject
    public JudgmentProtocol(ChannelProjector projector) {
        this(projector, JudgmentRules.defaults(), true);
    }

    public JudgmentProtocol(ChannelProjector projector,
                            JudgmentRules defaultRules,
                            boolean cachingEnabled) {
        this.projector = Objects.requireNonNull(projector);
        this.defaultRules = Objects.requireNonNull(defaultRules);
        this.cachingEnabled = cachingEnabled;
        this.cache = new ConcurrentHashMap<>();
    }

    @Override
    public String protocolName() {
        return "JUDGMENT";
    }

    @Override
    public List<DispatchAdvisory> evaluate(ProtocolContext ctx) {
        JudgmentRules rules = defaultRules.withOverrides(ctx.policyOverrides());
        Set<String> reviewers = new HashSet<>(ctx.protocolParticipants());

        JudgmentState state = projectState(ctx.channelId(), reviewers);

        if (state.outcome().isTerminal()) {
            evictCache(ctx.channelId());
        }

        return rules.evaluate(state, ctx);
    }

    private JudgmentState projectState(UUID channelId, Set<String> reviewers) {
        JudgmentProjection projection = new JudgmentProjection(reviewers);

        if (!cachingEnabled) {
            return projector.project(channelId, projection).state();
        }

        CachedProjection cached = cache.get(channelId);
        ProjectionResult<JudgmentState> result;

        if (cached != null && cached.matches(reviewers)) {
            result = projector.project(channelId, projection, cached.result());
        } else {
            result = projector.project(channelId, projection);
        }

        cache.put(channelId, new CachedProjection(result, reviewers));
        return result.state();
    }

    private void evictCache(UUID channelId) {
        cache.remove(channelId);
    }

    record CachedProjection(ProjectionResult<JudgmentState> result,
                            Set<String> reviewers) {
        boolean matches(Set<String> currentReviewers) {
            return reviewers.equals(currentReviewers);
        }
    }
}
```

- [ ] **Step 10: Run all judgment tests**

Run: `mvn --batch-mode test -pl agency -Dtest="JudgmentFoldTest,JudgmentProjectionTest,JudgmentRulesTest,JudgmentProtocolTest"`
Expected: PASS

- [ ] **Step 11: Run full test suite**

Run: `mvn --batch-mode test`
Expected: BUILD SUCCESS

- [ ] **Step 12: Commit**

```bash
git add -A
git commit -m "feat(#356): add JudgmentProtocol ChannelProtocol implementation

JudgmentProjection folds DONE/DECLINE messages into verdict state.
JudgmentRules evaluates agreement (unanimous/majority/consensus) with
optional escalation. JudgmentProtocol implements ChannelProtocol with
projection caching.

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- [2026-10-25-channel-protocols-design.md] — design spec this plan implements
- [NegotiationProtocol.java] — reference pattern for ChannelProtocol implementations
- [NegotiationRules.java] — reference pattern for rules classes
- [NegotiationProtocolTest.java, NegotiationRulesTest.java] — test structure reference
- [ConversationProjection.java] — abstract base reused by DefaultConversationProjection
- [ConversationProtocol.java] — constants class to be renamed
- [ConversationState.java] — state type reused by ConversationProtocol
- [ConvergenceAnalyser.java, CommonGroundAnalyser.java] — convergence detection
- [JudgmentPhase.java, AgreementPolicy.java] — existing judgment types (referenced, not reused)
- casehubio/agency#355, casehubio/agency#356
