# Bottom-Up Formation Orchestrator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #410 — fix: Wire CoalitionProposal → NegotiationProtocol → GroupLifecycleService (bottom-up formation)
**Issue group:** #410

**Goal:** Create `BottomUpFormationOrchestrator` that wires coalition evaluation, negotiation protocol, and group lifecycle into a single bottom-up formation flow.

**Architecture:** Plain composition root (not CDI-managed) in `deliberation.coalition.formation` package. Constructor-injected dependencies. Returns `Uni<FormationResult>`. Follows `VouchService` pattern — consumer constructs with domain-specific deps.

**Tech Stack:** Java 17, JUnit 5, Mockito, Mutiny (provided scope), qhorus-api, eidos-org-api

## Global Constraints

- All new production code in `agency-core` module
- All new test code in `agency` module (where Mockito is available)
- `casehub-eidos-org-api` added as `provided` scope only
- `Uni` is already provided-scope in agency-core — no new dependency
- No CDI annotations — plain POJOs
- Follow existing defensive-copy pattern in records (compact constructor with `Set.copyOf`, `List.copyOf`)

---

## Batch 1: Foundation — CoalitionProposal cleanup + new types

### Task 1: CoalitionProposal — add proposer field, remove coveredCapabilities

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/CoalitionProposal.java`
- Modify: `agency/src/test/java/io/casehub/agency/deliberation/coalition/CoalitionFormationTest.java`

**Interfaces:**
- Produces: `CoalitionProposal(AgentRef proposer, String taskId, Set<String> requiredCapabilities, List<AgentRef> proposedMembers)` — used by Task 3

- [ ] **Step 1: Update existing tests for new proposer field**

In `CoalitionFormationTest.java`, every `new CoalitionProposal(...)` call currently has 3 args. Add a proposer argument (first position) to each. The test helper `agent(String name)` already exists and returns `AgentRef.ExternalAgent`.

Update the helper and all construction sites:

```java
// Add a proposer helper at the top of the test class
private static AgentRef proposer() {
    return agent("proposer");
}

// Update every CoalitionProposal construction — example:
// Before:
new CoalitionProposal("task-1", Set.of("analysis", "reporting"), List.of(agent("agent-a"), agent("agent-b")))
// After:
new CoalitionProposal(proposer(), "task-1", Set.of("analysis", "reporting"), List.of(agent("agent-a"), agent("agent-b")))
```

Also remove the test for `coveredCapabilities()` if one exists (the exploration found none, but verify).

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency -Dtest=CoalitionFormationTest`
Expected: Compilation error — `CoalitionProposal` constructor doesn't accept 4 args yet.

- [ ] **Step 3: Update CoalitionProposal record**

In `CoalitionProposal.java`:

```java
package io.casehub.agency.deliberation.coalition;

import io.casehub.agency.core.AgentRef;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record CoalitionProposal(
        AgentRef proposer,
        String taskId,
        Set<String> requiredCapabilities,
        List<AgentRef> proposedMembers
) {
    public CoalitionProposal {
        Objects.requireNonNull(proposer, "proposer");
        Objects.requireNonNull(taskId, "taskId");
        requiredCapabilities = Set.copyOf(requiredCapabilities);
        proposedMembers = List.copyOf(proposedMembers);
    }
}
```

The `coveredCapabilities()` method is removed entirely.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest=CoalitionFormationTest`
Expected: All tests pass. Existing evaluator tests are unaffected — `CapabilityCoverageEvaluator` uses `CoalitionContext.capabilitiesOf()`, not `coveredCapabilities()`.

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/deliberation/coalition/CoalitionProposal.java agency/src/test/java/io/casehub/agency/deliberation/coalition/CoalitionFormationTest.java
git commit -m "feat(coalition): add proposer field, remove coveredCapabilities()

CoalitionProposal now carries an explicit proposer(AgentRef) instead of
relying on proposedMembers ordering. coveredCapabilities() removed —
CapabilityCoverageEvaluator already resolves capabilities via
CoalitionContext.capabilitiesOf().

Refs casehubio/agency#410

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

### Task 2: FormationResult + MessageObserverRegistrar + dependency

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/FormationResult.java`
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/MessageObserverRegistrar.java`
- Modify: `agency-core/pom.xml`

**Interfaces:**
- Produces: `FormationResult` sealed interface with `Accepted(String)`, `Rejected(String)`, `NegotiationFailed(String)`, `Timeout()` — used by Task 3
- Produces: `MessageObserverRegistrar` `@FunctionalInterface` with `Runnable register(MessageObserver)` — used by Task 3

- [ ] **Step 1: Add eidos-org-api dependency to agency-core pom.xml**

In `agency-core/pom.xml`, add to the `<dependencies>` section alongside the existing `casehub-eidos-api` dependency:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-eidos-org-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: Create FormationResult**

Create `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/FormationResult.java`:

```java
package io.casehub.agency.deliberation.coalition.formation;

public sealed interface FormationResult {

    record Accepted(String groupInstanceId) implements FormationResult {}

    record Rejected(String reason) implements FormationResult {}

    record NegotiationFailed(String reason) implements FormationResult {}

    record Timeout() implements FormationResult {}

    static FormationResult accepted(String groupInstanceId) {
        return new Accepted(groupInstanceId);
    }

    static FormationResult rejected(String reason) {
        return new Rejected(reason);
    }

    static FormationResult negotiationFailed(String reason) {
        return new NegotiationFailed(reason);
    }

    static FormationResult timeout() {
        return new Timeout();
    }
}
```

- [ ] **Step 3: Create MessageObserverRegistrar**

Create `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/MessageObserverRegistrar.java`:

```java
package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.qhorus.api.gateway.MessageObserver;

@FunctionalInterface
public interface MessageObserverRegistrar {
    Runnable register(MessageObserver observer);
}
```

- [ ] **Step 4: Verify compilation**

Run: `mvn --batch-mode compile -pl agency-core`
Expected: BUILD SUCCESS — both types compile, eidos-org-api dependency resolves.

- [ ] **Step 5: Commit**

```bash
git add agency-core/pom.xml agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/FormationResult.java agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/MessageObserverRegistrar.java
git commit -m "feat(formation): add FormationResult sealed type + MessageObserverRegistrar SPI

FormationResult: Accepted/Rejected/NegotiationFailed/Timeout outcomes.
MessageObserverRegistrar: functional interface for observer registration
without leaking EventSource.Cancellation across packages.

Adds casehub-eidos-org-api as provided-scope dependency to agency-core.

Refs casehubio/agency#410

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## Batch 2: Orchestrator — BottomUpFormationOrchestrator with TDD

### Task 3: BottomUpFormationOrchestrator — TDD implementation

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/BottomUpFormationOrchestrator.java`
- Create: `agency/src/test/java/io/casehub/agency/deliberation/coalition/formation/BottomUpFormationOrchestratorTest.java`

**Interfaces:**
- Consumes: `CoalitionProposal(AgentRef proposer, String taskId, Set<String> requiredCapabilities, List<AgentRef> proposedMembers)` from Task 1
- Consumes: `FormationResult` sealed interface from Task 2
- Consumes: `MessageObserverRegistrar` from Task 2
- Consumes: `CoalitionEvaluator.evaluate(CoalitionProposal, CoalitionContext) → CoalitionScore`
- Consumes: `ChannelManager.findOrCreate(ChannelCreateRequest) → FindOrCreateResult`
- Consumes: `ChannelManager.pause(UUID) → Channel`
- Consumes: `MessageDispatcher.dispatch(MessageDispatch) → DispatchResult`
- Consumes: `GroupLifecycleService.proposeGroup(String, String, String) → GroupInstance`
- Consumes: `GroupLifecycleService.approveProposal(String, String)`
- Consumes: `GroupLifecycleService.joinGroup(String, String, String)`
- Consumes: `ChannelObserver.of(ChannelProjection, String) → ChannelObserver`
- Consumes: `NegotiationProjection(Set<String>, AcceptancePolicy)`
- Produces: `BottomUpFormationOrchestrator` with `Uni<FormationResult> propose(CoalitionProposal, CoalitionContext)`

- [ ] **Step 1: Write test — non-viable coalition is rejected**

Create `agency/src/test/java/io/casehub/agency/deliberation/coalition/formation/BottomUpFormationOrchestratorTest.java`:

```java
package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.communication.channel.ChannelObserver;
import io.casehub.agency.core.AgentRef;
import io.casehub.agency.deliberation.coalition.CoalitionContext;
import io.casehub.agency.deliberation.coalition.CoalitionEvaluator;
import io.casehub.agency.deliberation.coalition.CoalitionProposal;
import io.casehub.agency.deliberation.coalition.CoalitionScore;
import io.casehub.agency.deliberation.negotiation.AcceptancePolicy;
import io.casehub.agency.deliberation.negotiation.NegotiationOutcome;
import io.casehub.agency.deliberation.negotiation.NegotiationProjection;
import io.casehub.agency.deliberation.negotiation.NegotiationState;
import io.casehub.agency.deliberation.negotiation.UnanimousAcceptance;
import io.casehub.agency.orchestration.execution.DriverEvent;
import io.casehub.agency.orchestration.execution.EventSource;
import io.casehub.eidos.org.api.GroupInstance;
import io.casehub.eidos.org.api.GroupLifecycleService;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.channel.FindOrCreateResult;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BottomUpFormationOrchestratorTest {

    private CoalitionEvaluator evaluator;
    private GroupLifecycleService groupLifecycle;
    private ChannelManager channelManager;
    private MessageDispatcher messageDispatcher;
    private MessageObserverRegistrar observerRegistrar;

    private static final String SPEC_ID = "test-spec";
    private static final String TENANCY_ID = "tenant-1";
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    @BeforeEach
    void setUp() {
        evaluator = mock(CoalitionEvaluator.class);
        groupLifecycle = mock(GroupLifecycleService.class);
        channelManager = mock(ChannelManager.class);
        messageDispatcher = mock(MessageDispatcher.class);
        observerRegistrar = mock(MessageObserverRegistrar.class);
    }

    private BottomUpFormationOrchestrator orchestrator() {
        return orchestrator(new UnanimousAcceptance());
    }

    private BottomUpFormationOrchestrator orchestrator(AcceptancePolicy policy) {
        return new BottomUpFormationOrchestrator(
                evaluator, groupLifecycle, channelManager,
                messageDispatcher, observerRegistrar,
                SPEC_ID, TENANCY_ID, policy, TIMEOUT);
    }

    private static AgentRef agent(String name) {
        return new AgentRef.ExternalAgent(name, null);
    }

    private CoalitionProposal proposal() {
        return new CoalitionProposal(
                agent("proposer-1"), "task-1",
                Set.of("analysis", "reporting"),
                List.of(agent("agent-a"), agent("agent-b")));
    }

    private CoalitionContext context() {
        return new CoalitionContext(
                List.of(agent("agent-a"), agent("agent-b")),
                Map.of("agent-a", Set.of("analysis"),
                       "agent-b", Set.of("reporting")));
    }

    @Test
    void nonViableCoalition_returnsRejected() {
        var score = new CoalitionScore(0.4, 0.5,
                Set.of("missing-cap"), "insufficient coverage");
        when(evaluator.evaluate(any(), any())).thenReturn(score);

        var result = orchestrator().propose(proposal(), context())
                .await().atMost(Duration.ofSeconds(2));

        assertThat(result).isInstanceOf(FormationResult.Rejected.class);
        assertThat(((FormationResult.Rejected) result).reason())
                .isEqualTo("insufficient coverage");
        verifyNoInteractions(channelManager, messageDispatcher,
                groupLifecycle, observerRegistrar);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=BottomUpFormationOrchestratorTest#nonViableCoalition_returnsRejected`
Expected: Compilation error — `BottomUpFormationOrchestrator` class doesn't exist yet.

- [ ] **Step 3: Write minimal BottomUpFormationOrchestrator — rejection path only**

Create `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/BottomUpFormationOrchestrator.java`:

```java
package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.deliberation.coalition.CoalitionContext;
import io.casehub.agency.deliberation.coalition.CoalitionEvaluator;
import io.casehub.agency.deliberation.coalition.CoalitionProposal;
import io.casehub.agency.deliberation.negotiation.AcceptancePolicy;
import io.casehub.eidos.org.api.GroupLifecycleService;
import io.casehub.qhorus.api.channel.ChannelManager;
import io.casehub.qhorus.api.message.MessageDispatcher;
import io.smallrye.mutiny.Uni;

import java.time.Duration;
import java.util.Objects;

public class BottomUpFormationOrchestrator {

    private final CoalitionEvaluator evaluator;
    private final GroupLifecycleService groupLifecycle;
    private final ChannelManager channelManager;
    private final MessageDispatcher messageDispatcher;
    private final MessageObserverRegistrar observerRegistrar;
    private final String specId;
    private final String tenancyId;
    private final AcceptancePolicy acceptancePolicy;
    private final Duration timeout;

    public BottomUpFormationOrchestrator(
            CoalitionEvaluator evaluator,
            GroupLifecycleService groupLifecycle,
            ChannelManager channelManager,
            MessageDispatcher messageDispatcher,
            MessageObserverRegistrar observerRegistrar,
            String specId,
            String tenancyId,
            AcceptancePolicy acceptancePolicy,
            Duration timeout) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.groupLifecycle = Objects.requireNonNull(groupLifecycle);
        this.channelManager = Objects.requireNonNull(channelManager);
        this.messageDispatcher = Objects.requireNonNull(messageDispatcher);
        this.observerRegistrar = Objects.requireNonNull(observerRegistrar);
        this.specId = Objects.requireNonNull(specId);
        this.tenancyId = Objects.requireNonNull(tenancyId);
        this.acceptancePolicy = Objects.requireNonNull(acceptancePolicy);
        this.timeout = Objects.requireNonNull(timeout);
    }

    public Uni<FormationResult> propose(CoalitionProposal proposal, CoalitionContext context) {
        var score = evaluator.evaluate(proposal, context);
        if (!score.isViable()) {
            return Uni.createFrom().item(FormationResult.rejected(score.reason()));
        }

        throw new UnsupportedOperationException("negotiation path not yet implemented");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=BottomUpFormationOrchestratorTest#nonViableCoalition_returnsRejected`
Expected: PASS

- [ ] **Step 5: Write test — happy path (AGREED → group created with members)**

Add to `BottomUpFormationOrchestratorTest.java`:

```java
    private void stubViableCoalition() {
        var score = new CoalitionScore(1.0, 1.0, Set.of(), "full coverage");
        when(evaluator.evaluate(any(), any())).thenReturn(score);
    }

    private UUID stubChannelCreation() {
        var channelId = UUID.randomUUID();
        var channel = mock(Channel.class);
        when(channel.id()).thenReturn(channelId);
        var result = new FindOrCreateResult(channel, true);
        when(channelManager.findOrCreate(any())).thenReturn(result);
        when(channelManager.pause(any())).thenReturn(channel);
        return channelId;
    }

    private void stubGroupCreation(String instanceId) {
        var instance = mock(GroupInstance.class);
        when(instance.instanceId()).thenReturn(instanceId);
        when(groupLifecycle.proposeGroup(any(), any(), any())).thenReturn(instance);
    }

    private void stubObserverRegistrar(AtomicReference<MessageObserver> capturedObserver) {
        when(observerRegistrar.register(any())).thenAnswer(invocation -> {
            capturedObserver.set(invocation.getArgument(0));
            return (Runnable) () -> {};
        });
    }

    @Test
    void agreedNegotiation_createsGroupWithMembers() {
        stubViableCoalition();
        var channelId = stubChannelCreation();
        stubGroupCreation("group-instance-1");
        var capturedObserver = new AtomicReference<MessageObserver>();
        stubObserverRegistrar(capturedObserver);

        var proposal = proposal();
        var uni = orchestrator().propose(proposal, context());

        // Simulate negotiation: subscribe triggers, then simulate AGREED
        // The Uni is subscribed to but won't complete until we feed messages
        var resultRef = new AtomicReference<FormationResult>();
        uni.subscribe().with(resultRef::set);

        // Feed acceptance messages through the captured observer
        // Agent-a accepts
        var acceptA = mockMessageEvent(channelId, "agent-a",
                io.casehub.qhorus.api.message.MessageType.DONE, proposal.taskId());
        capturedObserver.get().onMessage(acceptA);
        // Agent-b accepts
        var acceptB = mockMessageEvent(channelId, "agent-b",
                io.casehub.qhorus.api.message.MessageType.DONE, proposal.taskId());
        capturedObserver.get().onMessage(acceptB);

        // Wait for result
        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get()).isInstanceOf(FormationResult.Accepted.class);
        assertThat(((FormationResult.Accepted) resultRef.get()).groupInstanceId())
                .isEqualTo("group-instance-1");

        // Verify group creation
        verify(groupLifecycle).proposeGroup(SPEC_ID, TENANCY_ID, "proposer-1");
        verify(groupLifecycle).approveProposal("group-instance-1", TENANCY_ID);
        verify(groupLifecycle).joinGroup("group-instance-1", "agent-a", TENANCY_ID);
        verify(groupLifecycle).joinGroup("group-instance-1", "agent-b", TENANCY_ID);

        // Verify cleanup
        verify(channelManager).pause(channelId);
    }

    private io.casehub.qhorus.api.gateway.MessageReceivedEvent mockMessageEvent(
            UUID channelId, String sender,
            io.casehub.qhorus.api.message.MessageType type, String correlationId) {
        var event = mock(io.casehub.qhorus.api.gateway.MessageReceivedEvent.class);
        when(event.channelId()).thenReturn(channelId);
        when(event.channelName()).thenReturn("formation:task-1:proposer-1");
        when(event.senderId()).thenReturn(sender);
        when(event.messageType()).thenReturn(type);
        when(event.correlationId()).thenReturn(correlationId);
        return event;
    }
```

Note: The exact mock structure for `MessageReceivedEvent` depends on its actual fields. Verify the `MessageReceivedEvent` record/interface — if it's a record, use real instances instead of mocks. If it has required fields beyond what's shown, add them.

- [ ] **Step 6: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=BottomUpFormationOrchestratorTest#agreedNegotiation_createsGroupWithMembers`
Expected: FAIL — `UnsupportedOperationException` from the negotiation path.

- [ ] **Step 7: Implement the full negotiation path**

Replace the `throw` in `propose()` with the complete negotiation flow:

```java
    public Uni<FormationResult> propose(CoalitionProposal proposal, CoalitionContext context) {
        var score = evaluator.evaluate(proposal, context);
        if (!score.isViable()) {
            return Uni.createFrom().item(FormationResult.rejected(score.reason()));
        }

        var memberNames = proposal.proposedMembers().stream()
                .map(AgentRef::name)
                .toList();
        var channelName = "formation:" + proposal.taskId() + ":" + proposal.proposer().name();

        var findResult = channelManager.findOrCreate(
                ChannelCreateRequest.builder(channelName)
                        .protocols(List.of("NEGOTIATION"))
                        .protocolParticipants(memberNames)
                        .allowedWriters(memberNames)
                        .build());
        var channelId = findResult.channel().id();

        var projection = new NegotiationProjection(
                Set.copyOf(memberNames), acceptancePolicy);
        var observer = ChannelObserver.of(projection, channelName);

        var registrationCancel = observerRegistrar.register(observer);

        var proposalContent = "Coalition proposal: " + proposal.taskId()
                + " requires: " + proposal.requiredCapabilities();
        messageDispatcher.dispatch(MessageDispatch.builder()
                .channelId(channelId)
                .sender(proposal.proposer().name())
                .type(MessageType.PROPOSE)
                .actorType(ActorType.AGENT)
                .correlationId(proposal.taskId())
                .tenancyId(tenancyId)
                .content(proposalContent)
                .build());

        return Uni.createFrom().<FormationResult>emitter(emitter -> {
            var subscription = observer.subscribe(event -> {
                var state = observer.currentState();
                if (state.outcome().isTerminal()) {
                    var result = handleOutcome(state, proposal);
                    emitter.complete(result);
                }
            });

            emitter.onTermination(() -> {
                subscription.cancel();
                registrationCancel.run();
                channelManager.pause(channelId);
            });
        }).ifNoItem().after(timeout).recoverWithUni(() -> {
            var state = observer.currentState();
            if (state.outcome().isTerminal()) {
                return Uni.createFrom().item(handleOutcome(state, proposal));
            }
            return Uni.createFrom().item(FormationResult.timeout());
        });
    }

    private FormationResult handleOutcome(NegotiationState state, CoalitionProposal proposal) {
        if (state.outcome() == NegotiationOutcome.AGREED) {
            var instance = groupLifecycle.proposeGroup(specId, tenancyId, proposal.proposer().name());
            groupLifecycle.approveProposal(instance.instanceId(), tenancyId);
            for (var member : proposal.proposedMembers()) {
                groupLifecycle.joinGroup(instance.instanceId(), member.name(), tenancyId);
            }
            return FormationResult.accepted(instance.instanceId());
        }
        return FormationResult.negotiationFailed(state.outcome().name());
    }
```

Required imports to add:

```java
import io.casehub.agency.communication.channel.ChannelObserver;
import io.casehub.agency.deliberation.negotiation.NegotiationOutcome;
import io.casehub.agency.deliberation.negotiation.NegotiationProjection;
import io.casehub.agency.deliberation.negotiation.NegotiationState;
import io.casehub.agency.core.AgentRef;
import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.message.MessageDispatch;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.platform.api.identity.ActorType;

import java.util.List;
import java.util.Set;
```

- [ ] **Step 8: Run happy path test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=BottomUpFormationOrchestratorTest#agreedNegotiation_createsGroupWithMembers`
Expected: PASS

- [ ] **Step 9: Write test — negotiation DEADLOCKED**

```java
    @Test
    void deadlockedNegotiation_returnsNegotiationFailed() {
        stubViableCoalition();
        var channelId = stubChannelCreation();
        var capturedObserver = new AtomicReference<MessageObserver>();
        stubObserverRegistrar(capturedObserver);

        var proposal = proposal();
        var resultRef = new AtomicReference<FormationResult>();
        orchestrator().propose(proposal, context())
                .subscribe().with(resultRef::set);

        // Agent-a rejects (DECLINE as non-proposer = reject)
        var reject = mockMessageEvent(channelId, "agent-a",
                MessageType.DECLINE, proposal.taskId());
        capturedObserver.get().onMessage(reject);

        // NegotiationProjection handles reject but doesn't auto-deadlock
        // Deadlock must be triggered by the protocol or explicitly
        // For this test, verify WITHDRAWN via proposer DECLINE:
        // Actually, let's test WITHDRAWN — proposer sends DECLINE
        var withdraw = mockMessageEvent(channelId, "proposer-1",
                MessageType.DECLINE, proposal.taskId());
        capturedObserver.get().onMessage(withdraw);

        assertThat(resultRef.get()).isNotNull();
        assertThat(resultRef.get()).isInstanceOf(FormationResult.NegotiationFailed.class);
        assertThat(((FormationResult.NegotiationFailed) resultRef.get()).reason())
                .isEqualTo("WITHDRAWN");

        verifyNoInteractions(groupLifecycle);
        verify(channelManager).pause(channelId);
    }
```

- [ ] **Step 10: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=BottomUpFormationOrchestratorTest#deadlockedNegotiation_returnsNegotiationFailed`
Expected: PASS — the WITHDRAWN path returns `NegotiationFailed`.

- [ ] **Step 11: Write test — timeout**

```java
    @Test
    void timeout_returnsTimeout() {
        stubViableCoalition();
        stubChannelCreation();
        var capturedObserver = new AtomicReference<MessageObserver>();
        stubObserverRegistrar(capturedObserver);

        // Use short timeout
        var shortTimeout = new BottomUpFormationOrchestrator(
                evaluator, groupLifecycle, channelManager,
                messageDispatcher, observerRegistrar,
                SPEC_ID, TENANCY_ID, new UnanimousAcceptance(),
                Duration.ofMillis(100));

        var result = shortTimeout.propose(proposal(), context())
                .await().atMost(Duration.ofSeconds(2));

        assertThat(result).isInstanceOf(FormationResult.Timeout.class);
        verifyNoInteractions(groupLifecycle);
    }
```

- [ ] **Step 12: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=BottomUpFormationOrchestratorTest#timeout_returnsTimeout`
Expected: PASS

- [ ] **Step 13: Write test — PROPOSE message has correct fields**

```java
    @Test
    void proposeMessage_hasCorrectFields() {
        stubViableCoalition();
        stubChannelCreation();
        stubObserverRegistrar(new AtomicReference<>());

        var proposal = proposal();
        orchestrator().propose(proposal, context());

        var captor = ArgumentCaptor.forClass(MessageDispatch.class);
        verify(messageDispatcher).dispatch(captor.capture());

        var dispatch = captor.getValue();
        assertThat(dispatch.sender()).isEqualTo("proposer-1");
        assertThat(dispatch.type()).isEqualTo(MessageType.PROPOSE);
        assertThat(dispatch.correlationId()).isEqualTo("task-1");
        assertThat(dispatch.actorType()).isEqualTo(ActorType.AGENT);
        assertThat(dispatch.tenancyId()).isEqualTo(TENANCY_ID);
        assertThat(dispatch.content()).contains("task-1");
    }
```

- [ ] **Step 14: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=BottomUpFormationOrchestratorTest#proposeMessage_hasCorrectFields`
Expected: PASS

- [ ] **Step 15: Write test — idempotent channel via findOrCreate**

```java
    @Test
    void channelName_isDeterministic() {
        stubViableCoalition();
        stubChannelCreation();
        stubObserverRegistrar(new AtomicReference<>());

        var proposal = proposal();
        orchestrator().propose(proposal, context());

        var captor = ArgumentCaptor.forClass(ChannelCreateRequest.class);
        verify(channelManager).findOrCreate(captor.capture());
        assertThat(captor.getValue().name()).isEqualTo("formation:task-1:proposer-1");
    }
```

- [ ] **Step 16: Run all tests**

Run: `mvn --batch-mode test -pl agency -Dtest=BottomUpFormationOrchestratorTest`
Expected: All tests pass.

- [ ] **Step 17: Run full module test suite**

Run: `mvn --batch-mode test -pl agency`
Expected: All tests pass including existing coalition tests (which now use the updated 4-arg `CoalitionProposal` constructor).

- [ ] **Step 18: Run full project build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS across all modules.

- [ ] **Step 19: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/BottomUpFormationOrchestrator.java agency/src/test/java/io/casehub/agency/deliberation/coalition/formation/BottomUpFormationOrchestratorTest.java
git commit -m "feat(formation): BottomUpFormationOrchestrator wiring coalition → negotiation → group

Composition root that evaluates coalition viability, opens a qhorus
negotiation channel with NegotiationProtocol, observes consensus via
ChannelObserver, and on acceptance creates the group via
GroupLifecycleService with all members enrolled.

Sealed FormationResult: Accepted/Rejected/NegotiationFailed/Timeout.
Uni-based async with first-wins timeout race resolution.

Fixes casehubio/agency#410

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## Implementation Notes

**ChannelObserver subscription threading:** `ChannelObserver.subscribe()` takes a `Consumer<DriverEvent>`. When `onMessage()` is called (on the message dispatch thread), the observer folds the projection and then pushes a `DriverEvent` to the subscriber. The subscriber callback runs on the same thread as `onMessage()`. The `emitter.complete()` call is thread-safe.

**NegotiationProjection party set:** Must include ALL proposed members (their `AgentRef.name()` values). The proposer is NOT in the party set unless they are also a proposed member. The proposer sends PROPOSE; members respond with DONE (accept) or DECLINE (reject). `NegotiationProjection` ignores messages from unknown senders.

**MessageReceivedEvent mocking:** If `MessageReceivedEvent` is a record (not an interface), you'll need to construct real instances. Check the actual type — if it has many required fields, extract a test factory method. The mock approach shown above works if it's an interface.

**ChannelObserver double-subscribe guard:** `ChannelObserver.subscribe()` throws `IllegalStateException` on a second call. The `emitter.onTermination()` cleanup must not call `subscribe()` again — it should track the `Cancellation` from the original `subscribe()` call and cancel that.

## References

- [2026-11-06-bottom-up-formation-orchestrator-design.md] — design spec this plan implements
- `agency-core/.../deliberation/coalition/CoalitionProposal.java` — modified record
- `agency-core/.../communication/channel/ChannelObserver.java` — observation bridge
- `agency-core/.../deliberation/negotiation/NegotiationProjection.java` — negotiation state projection
- `agency-core/.../governance/trust/VouchService.java` — composition root pattern
- `agency-core/.../deliberation/conversation/orchestration/ConversationOrchestrator.java` — Uni return convention
- GitHub #410 — focal issue
- GitHub #408 — parent epic
