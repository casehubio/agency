# L3 Conflict Resolution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #438 — L3 LLM-mediated conflict resolution for multi-swarm coordination
**Issue group:** #364 (swarm coordination epic)

**Goal:** Implement LLM-mediated conflict resolution (L3 tier) for
multi-swarm coordination, with heuristic fallbacks and budget gating.

**Architecture:** A `ConflictMediator` SPI with two implementations
(`LlmConflictMediator` using `StructuredAgentInvoker`, `HeuristicConflictMediator`
for deterministic fallbacks) composed via a `BudgetGatedConflictMediator`
decorator. Wired into `MultiSwarmTopology` via a pending conflicts queue
with sequential resolution.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ. Uses `StructuredAgentInvoker`,
`AutonomyBudgetPool`/`AgentBudgetView`, `BeliefSet` AGM revision.

## Global Constraints

- All new types in `io.casehub.agency.orchestration.multiswarm` package
- No new module dependencies — everything used is already in agency-core's dependency graph
- Follow existing `LlmTriageHandler` pattern for `AgentSessionConfig.of(system, user)` + `invokeRaw()`
- `@Nullable` annotations from `org.jspecify.annotations`
- No CDI — plain classes constructed via builder, same as rest of multiswarm package
- Thread safety: `AtomicReference` for BeliefSet, `ConcurrentLinkedQueue` for pending conflicts

---

## Batch 1: Foundation types and heuristic mediator

### Task 1: ConflictMediator SPI, ConflictContext, MediationResult, MediationException

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/ConflictMediator.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/ConflictContext.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/MediationResult.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/MediationException.java`

**Interfaces:**
- Produces: `ConflictMediator.mediate(ConflictContext) → MediationResult` — used by Tasks 2, 3, 4
- Produces: `ConflictContext(CrossSwarmNotification, BeliefSet<SwarmConclusion>, List<Belief<SwarmConclusion>>, Map<UUID, SwarmProgress>, Map<UUID, Set<String>>, Map<UUID, SwarmDescriptor>)` — used by Tasks 2, 3, 4
- Produces: `MediationResult` sealed: `Resolved`, `FallbackApplied`, `Escalated` — used by Tasks 2, 3, 4

- [ ] **Step 1: Create ConflictMediator SPI**

```java
package io.casehub.agency.orchestration.multiswarm;

@FunctionalInterface
public interface ConflictMediator {
    MediationResult mediate(ConflictContext context);
}
```

- [ ] **Step 2: Create ConflictContext record**

```java
package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.stigmergy.SwarmProgress;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record ConflictContext(
        CrossSwarmNotification notification,
        BeliefSet<SwarmConclusion> currentBeliefs,
        List<Belief<SwarmConclusion>> contractedBeliefs,
        Map<UUID, SwarmProgress> latestProgress,
        Map<UUID, Set<String>> resourceClaims,
        Map<UUID, SwarmDescriptor> swarmDescriptors
) {
    public ConflictContext {
        Objects.requireNonNull(notification);
        Objects.requireNonNull(currentBeliefs);
        Objects.requireNonNull(contractedBeliefs);
        Objects.requireNonNull(latestProgress);
        Objects.requireNonNull(resourceClaims);
        Objects.requireNonNull(swarmDescriptors);
        contractedBeliefs = List.copyOf(contractedBeliefs);
        latestProgress = Map.copyOf(latestProgress);
        resourceClaims = Map.copyOf(resourceClaims);
        swarmDescriptors = Map.copyOf(swarmDescriptors);
    }
}
```

- [ ] **Step 3: Create MediationResult sealed interface**

```java
package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public sealed interface MediationResult {

    record Resolved(
            String resolution,
            @Nullable Belief<SwarmConclusion> updatedBelief,
            List<CrossSwarmNotification> notifications
    ) implements MediationResult {
        public Resolved {
            Objects.requireNonNull(resolution);
            notifications = notifications == null ? List.of() : List.copyOf(notifications);
        }
    }

    record FallbackApplied(
            String fallbackAction,
            String reason,
            List<CrossSwarmNotification> notifications
    ) implements MediationResult {
        public FallbackApplied {
            Objects.requireNonNull(fallbackAction);
            Objects.requireNonNull(reason);
            notifications = notifications == null ? List.of() : List.copyOf(notifications);
        }
    }

    record Escalated(String reason) implements MediationResult {
        public Escalated {
            Objects.requireNonNull(reason);
        }
    }
}
```

- [ ] **Step 4: Create MediationException**

```java
package io.casehub.agency.orchestration.multiswarm;

public class MediationException extends RuntimeException {
    public MediationException(String message) {
        super(message);
    }

    public MediationException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

- [ ] **Step 5: Verify compilation**

Run: `mvn --batch-mode compile -pl agency-core`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/ConflictMediator.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/ConflictContext.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/MediationResult.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/MediationException.java
git commit -m "feat(#438): ConflictMediator SPI, ConflictContext, MediationResult, MediationException"
```

### Task 2: HeuristicConflictMediator with tests

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/HeuristicConflictMediator.java`
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/HeuristicConflictMediatorTest.java`

**Interfaces:**
- Consumes: `ConflictMediator`, `ConflictContext`, `MediationResult` from Task 1
- Produces: `HeuristicConflictMediator` — used by Tasks 3, 4

- [ ] **Step 1: Write failing tests for CONFLICT heuristic**

```java
package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class HeuristicConflictMediatorTest {

    HeuristicConflictMediator mediator;
    UUID swarmA = UUID.randomUUID();
    UUID swarmB = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        mediator = new HeuristicConflictMediator();
    }

    SwarmDescriptor descriptor(UUID swarmId, Set<String> caps) {
        return new SwarmDescriptor(swarmId, caseId, TopologyType.CENTRALISED,
                caps, Set.of("agent-1"), null);
    }

    ConflictContext contextFor(NotificationType type, MultiSwarmSignal trigger) {
        var notification = new CrossSwarmNotification(swarmA, swarmB, type, trigger, 0.8);
        var beliefs = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of("a:domain", new SwarmConclusion(swarmA, "domain", "node-7:healthy", 0.9), 90))
                .expand(Belief.of("b:domain", new SwarmConclusion(swarmB, "domain", "node-7:degraded", 0.8), 80));
        var contracted = List.of(
                Belief.of("b:domain", new SwarmConclusion(swarmB, "domain", "node-7:degraded", 0.8), 80));
        var progress = Map.of(
                swarmA, new SwarmProgress(0.9, 0.8, 0.9, Instant.now()),
                swarmB, new SwarmProgress(0.5, 0.6, 0.5, Instant.now()));
        var claims = Map.<UUID, Set<String>>of();
        var descriptors = Map.of(
                swarmA, descriptor(swarmA, Set.of("analysis")),
                swarmB, descriptor(swarmB, Set.of("remediation")));
        return new ConflictContext(notification, beliefs, contracted, progress, claims, descriptors);
    }

    @Nested
    class ConflictType {
        @Test
        void retains_both_conclusions_with_disputed_tag() {
            var finding = new MultiSwarmSignal.Finding(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), "domain", "node-7:healthy", 0.9, Map.of());
            var result = mediator.mediate(contextFor(NotificationType.CONFLICT, finding));

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
            var applied = (MediationResult.FallbackApplied) result;
            assertThat(applied.fallbackAction()).isEqualTo("disputed_tag");
        }
    }

    @Nested
    class DependencyType {
        @Test
        void defers_to_higher_consensus_score() {
            var progress = new MultiSwarmSignal.Progress(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(),
                    new SwarmProgress(0.5, 0.5, 0.5, Instant.now()));
            var result = mediator.mediate(contextFor(NotificationType.DEPENDENCY, progress));

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
            var applied = (MediationResult.FallbackApplied) result;
            assertThat(applied.fallbackAction()).isEqualTo("consensus_delegation");
        }
    }

    @Nested
    class ResourceContentionType {
        @Test
        void first_claimer_wins() {
            var claim = new MultiSwarmSignal.ResourceClaim(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), Set.of("agent-1"), Set.of("analysis"));
            var ctx = contextFor(NotificationType.RESOURCE_CONTENTION, claim);
            var result = mediator.mediate(ctx);

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
            var applied = (MediationResult.FallbackApplied) result;
            assertThat(applied.fallbackAction()).isEqualTo("first_claimer_wins");
        }
    }

    @Nested
    class HandoffType {
        @Test
        void accepts_high_confidence_handoff() {
            var handoff = new MultiSwarmSignal.Handoff(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), Set.of("remediation"), "task-context");
            var notification = new CrossSwarmNotification(swarmA, swarmB,
                    NotificationType.HANDOFF, handoff, 0.8);
            var ctx = new ConflictContext(notification, new BeliefSet<>(), List.of(),
                    Map.of(), Map.of(), Map.of(
                            swarmA, descriptor(swarmA, Set.of("analysis")),
                            swarmB, descriptor(swarmB, Set.of("remediation"))));
            var result = mediator.mediate(ctx);

            assertThat(result).isInstanceOf(MediationResult.FallbackApplied.class);
        }

        @Test
        void escalates_after_max_retries() {
            var handoff = new MultiSwarmSignal.Handoff(swarmA, Set.of("analysis"),
                    System.currentTimeMillis(), Set.of("remediation"), "task-context");
            var notification = new CrossSwarmNotification(swarmA, swarmB,
                    NotificationType.HANDOFF, handoff, 0.3);
            var ctx = new ConflictContext(notification, new BeliefSet<>(), List.of(),
                    Map.of(), Map.of(), Map.of(
                            swarmA, descriptor(swarmA, Set.of("analysis")),
                            swarmB, descriptor(swarmB, Set.of("remediation"))));

            // Three attempts at low confidence → escalate
            mediator.mediate(ctx);
            mediator.mediate(ctx);
            var result = mediator.mediate(ctx);

            assertThat(result).isInstanceOf(MediationResult.Escalated.class);
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency-core -Dtest=HeuristicConflictMediatorTest`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement HeuristicConflictMediator**

```java
package io.casehub.agency.orchestration.multiswarm;

import io.casehub.api.model.stigmergy.SwarmProgress;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class HeuristicConflictMediator implements ConflictMediator {

    private static final int MAX_HANDOFF_RETRIES = 3;
    private static final double HANDOFF_CONFIDENCE_THRESHOLD = 0.5;

    private final ConcurrentHashMap<String, AtomicInteger> handoffAttempts = new ConcurrentHashMap<>();

    @Override
    public MediationResult mediate(ConflictContext context) {
        return switch (context.notification().type()) {
            case CONFLICT            -> resolveConflictHeuristic(context);
            case RESOURCE_CONTENTION -> resolveResourceHeuristic(context);
            case DEPENDENCY          -> resolveDependencyHeuristic(context);
            case HANDOFF             -> resolveHandoffHeuristic(context);
        };
    }

    private MediationResult resolveConflictHeuristic(ConflictContext context) {
        var notification = context.notification();
        var reverseNotification = new CrossSwarmNotification(
                notification.targetSwarmId(), notification.sourceSwarmId(),
                NotificationType.CONFLICT, notification.trigger(), notification.confidence());
        return new MediationResult.FallbackApplied(
                "disputed_tag",
                "Both conclusions retained — no LLM available for mediation",
                List.of(notification, reverseNotification));
    }

    private MediationResult resolveResourceHeuristic(ConflictContext context) {
        var notification = context.notification();
        var loserNotification = new CrossSwarmNotification(
                notification.sourceSwarmId(), notification.targetSwarmId(),
                NotificationType.RESOURCE_CONTENTION, notification.trigger(), notification.confidence());
        return new MediationResult.FallbackApplied(
                "first_claimer_wins",
                "Source swarm retains contested agents — first-claimer priority",
                List.of(loserNotification));
    }

    private MediationResult resolveDependencyHeuristic(ConflictContext context) {
        var notification = context.notification();
        var sourceProgress = context.latestProgress().get(notification.sourceSwarmId());
        var targetProgress = context.latestProgress().get(notification.targetSwarmId());

        double sourceScore = sourceProgress != null ? sourceProgress.consensusScore() : 0.0;
        double targetScore = targetProgress != null ? targetProgress.consensusScore() : 0.0;

        var winnerId = sourceScore >= targetScore
                ? notification.sourceSwarmId() : notification.targetSwarmId();
        var loserId = sourceScore >= targetScore
                ? notification.targetSwarmId() : notification.sourceSwarmId();

        var constraintNotification = new CrossSwarmNotification(
                winnerId, loserId,
                NotificationType.DEPENDENCY, notification.trigger(), notification.confidence());

        return new MediationResult.FallbackApplied(
                "consensus_delegation",
                "Deferred to swarm with higher consensus score",
                List.of(constraintNotification));
    }

    private MediationResult resolveHandoffHeuristic(ConflictContext context) {
        var notification = context.notification();
        if (notification.confidence() >= HANDOFF_CONFIDENCE_THRESHOLD) {
            return new MediationResult.FallbackApplied(
                    "handoff_accepted",
                    "Handoff confidence above threshold",
                    List.of(notification));
        }

        String key = notification.sourceSwarmId() + ":" + notification.targetSwarmId();
        int attempts = handoffAttempts
                .computeIfAbsent(key, k -> new AtomicInteger(0))
                .incrementAndGet();

        if (attempts >= MAX_HANDOFF_RETRIES) {
            handoffAttempts.remove(key);
            return new MediationResult.Escalated(
                    "handoff_blocked — " + attempts + " failed attempts");
        }

        return new MediationResult.FallbackApplied(
                "handoff_retry",
                "Low confidence — retry attempt " + attempts + " of " + MAX_HANDOFF_RETRIES,
                List.of());
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency-core -Dtest=HeuristicConflictMediatorTest`
Expected: PASS — all tests green

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/HeuristicConflictMediator.java
git add agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/HeuristicConflictMediatorTest.java
git commit -m "feat(#438): HeuristicConflictMediator with per-type fallback logic"
```

## Batch 2: LLM mediator and budget-gated decorator

### Task 3: LlmConflictMediator with tests

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/LlmConflictMediator.java`
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/LlmConflictMediatorTest.java`

**Interfaces:**
- Consumes: `ConflictMediator`, `ConflictContext`, `MediationResult`, `MediationException` from Task 1
- Consumes: `StructuredAgentInvoker.invokeRaw(AgentProvider, AgentSessionConfig) → InvocationResult<JsonNode>`
- Consumes: `AgentSessionConfig.of(String systemPrompt, String userPrompt)`
- Produces: `LlmConflictMediator(AgentProvider)` — used by Task 3 (BudgetGated), Task 4

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.agency.orchestration.multiswarm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.agency.deliberation.belief.BeliefSet;
import io.casehub.api.model.coordination.SwarmDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LlmConflictMediatorTest {

    static final ObjectMapper MAPPER = new ObjectMapper();

    AgentProvider agentProvider;
    AgentSession session;
    LlmConflictMediator mediator;
    UUID swarmA = UUID.randomUUID();
    UUID swarmB = UUID.randomUUID();
    UUID caseId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        agentProvider = mock(AgentProvider.class);
        session = mock(AgentSession.class);
        when(agentProvider.createSession(any(AgentSessionConfig.class))).thenReturn(session);
        mediator = new LlmConflictMediator(agentProvider);
    }

    ConflictContext conflictContext() {
        var finding = new MultiSwarmSignal.Finding(swarmA, Set.of("analysis"),
                System.currentTimeMillis(), "domain", "node-7:healthy", 0.9, Map.of());
        var notification = new CrossSwarmNotification(swarmA, swarmB,
                NotificationType.CONFLICT, finding, 0.8);
        var beliefs = new BeliefSet<SwarmConclusion>()
                .expand(Belief.of("a:domain",
                        new SwarmConclusion(swarmA, "domain", "node-7:healthy", 0.9), 90));
        var contracted = List.of(
                Belief.of("b:domain",
                        new SwarmConclusion(swarmB, "domain", "node-7:degraded", 0.8), 80));
        return new ConflictContext(notification, beliefs, contracted,
                Map.of(swarmA, new SwarmProgress(0.9, 0.8, 0.9, Instant.now())),
                Map.of(),
                Map.of(swarmA, new SwarmDescriptor(swarmA, caseId, TopologyType.CENTRALISED,
                                Set.of("analysis"), Set.of("agent-1"), null),
                        swarmB, new SwarmDescriptor(swarmB, caseId, TopologyType.CENTRALISED,
                                Set.of("remediation"), Set.of("agent-2"), null)));
    }

    void stubLlmResponse(String json) {
        when(session.events()).thenReturn(List.of(
                new AgentEvent.TextDelta(json),
                new AgentEvent.InvocationComplete(10, 20, 0, 0, 0, 0.001, 100, 80)
        ));
    }

    @Test
    void resolves_conflict_from_valid_json() throws Exception {
        stubLlmResponse("""
                {"resolution": "node-7:healthy", "updatedConclusion": "node-7:healthy",
                 "confidence": 0.95, "justification": "Higher confidence from analysis swarm"}
                """);
        var result = mediator.mediate(conflictContext());

        assertThat(result).isInstanceOf(MediationResult.Resolved.class);
        var resolved = (MediationResult.Resolved) result;
        assertThat(resolved.resolution()).contains("node-7:healthy");
    }

    @Test
    void includes_conflict_type_in_prompt() {
        stubLlmResponse("""
                {"resolution": "resolved", "updatedConclusion": "node-7:healthy",
                 "confidence": 0.9, "justification": "test"}
                """);
        mediator.mediate(conflictContext());

        var captor = ArgumentCaptor.forClass(AgentSessionConfig.class);
        verify(agentProvider).createSession(captor.capture());
        var config = captor.getValue();
        assertThat(config.systemPrompt()).contains("contradicting conclusions");
    }

    @Test
    void throws_mediation_exception_on_agent_error() {
        when(session.events()).thenReturn(List.of());
        assertThatThrownBy(() -> mediator.mediate(conflictContext()))
                .isInstanceOf(MediationException.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency-core -Dtest=LlmConflictMediatorTest`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement LlmConflictMediator**

Follow the `LlmTriageHandler` pattern: system prompt per conflict type,
user prompt from context, `invokeRaw()`, pattern match on `InvocationResult`.

```java
package io.casehub.agency.orchestration.multiswarm;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.agency.core.invoke.StructuredAgentInvoker;
import io.casehub.agency.core.invoke.StructuredAgentInvoker.InvocationResult;
import io.casehub.agency.deliberation.belief.Belief;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSessionConfig;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class LlmConflictMediator implements ConflictMediator {

    private static final String CONFLICT_SYSTEM_PROMPT = """
            You are mediating a conflict between two swarms that reached contradicting \
            conclusions about the same domain. Analyze both positions and determine which \
            conclusion should prevail based on confidence, evidence, and reasoning.

            Respond ONLY with a JSON object:
            {"resolution": "<description>", "updatedConclusion": "<entity:state>", \
            "confidence": <0.0-1.0>, "justification": "<reasoning>"}""";

    private static final String RESOURCE_SYSTEM_PROMPT = """
            You are mediating a resource contention between two swarms competing for \
            the same agents. Evaluate which swarm benefits more from the contested agents \
            based on capabilities, progress, and task requirements.

            Respond ONLY with a JSON object:
            {"winner": "<swarmId>", "reassignments": [], "justification": "<reasoning>"}""";

    private static final String DEPENDENCY_SYSTEM_PROMPT = """
            You are evaluating a cross-swarm dependency. Determine whether the swarms \
            should coordinate and how, based on their capabilities and progress.

            Respond ONLY with a JSON object:
            {"coordinationAction": "<action>", "constraints": [], "justification": "<reasoning>"}""";

    private static final String HANDOFF_SYSTEM_PROMPT = """
            You are evaluating a work handoff between swarms. Determine whether the \
            target swarm is capable of handling the work based on capability overlap.

            Respond ONLY with a JSON object:
            {"proceed": <true/false>, "modifications": [], "justification": "<reasoning>"}""";

    private final AgentProvider agentProvider;

    public LlmConflictMediator(AgentProvider agentProvider) {
        this.agentProvider = Objects.requireNonNull(agentProvider);
    }

    @Override
    public MediationResult mediate(ConflictContext context) {
        var systemPrompt = selectSystemPrompt(context.notification().type());
        var userPrompt = buildUserPrompt(context);
        var config = AgentSessionConfig.of(systemPrompt, userPrompt);
        var result = StructuredAgentInvoker.invokeRaw(agentProvider, config);
        return toMediationResult(result, context);
    }

    private static String selectSystemPrompt(NotificationType type) {
        return switch (type) {
            case CONFLICT            -> CONFLICT_SYSTEM_PROMPT;
            case RESOURCE_CONTENTION -> RESOURCE_SYSTEM_PROMPT;
            case DEPENDENCY          -> DEPENDENCY_SYSTEM_PROMPT;
            case HANDOFF             -> HANDOFF_SYSTEM_PROMPT;
        };
    }

    private static String buildUserPrompt(ConflictContext context) {
        var sb = new StringBuilder();
        var notification = context.notification();

        sb.append("## Conflict Details\n\n");
        sb.append("Type: ").append(notification.type()).append('\n');
        sb.append("Confidence: ").append(notification.confidence()).append('\n');
        sb.append("Source Swarm: ").append(notification.sourceSwarmId()).append('\n');
        sb.append("Target Swarm: ").append(notification.targetSwarmId()).append('\n');

        appendSwarmDescriptors(sb, context);
        appendBeliefContext(sb, context);
        appendProgressContext(sb, context);

        return sb.toString();
    }

    private static void appendSwarmDescriptors(StringBuilder sb, ConflictContext context) {
        for (var entry : context.swarmDescriptors().entrySet()) {
            sb.append("\n### Swarm ").append(entry.getKey()).append('\n');
            sb.append("Capabilities: ").append(entry.getValue().capabilities()).append('\n');
        }
    }

    private static void appendBeliefContext(StringBuilder sb, ConflictContext context) {
        if (!context.currentBeliefs().isEmpty()) {
            sb.append("\n## Current Beliefs\n\n");
            for (var belief : context.currentBeliefs().all()) {
                sb.append("- [").append(belief.key()).append("] ")
                        .append(belief.value().conclusion())
                        .append(" (confidence: ").append(belief.value().confidence())
                        .append(", entrenchment: ").append(belief.entrenchment())
                        .append(")\n");
            }
        }
        if (!context.contractedBeliefs().isEmpty()) {
            sb.append("\n## Beliefs Removed by AGM Revision\n\n");
            for (var belief : context.contractedBeliefs()) {
                sb.append("- [").append(belief.key()).append("] ")
                        .append(belief.value().conclusion())
                        .append(" (was entrenchment: ").append(belief.entrenchment())
                        .append(")\n");
            }
        }
    }

    private static void appendProgressContext(StringBuilder sb, ConflictContext context) {
        if (!context.latestProgress().isEmpty()) {
            sb.append("\n## Swarm Progress\n\n");
            for (var entry : context.latestProgress().entrySet()) {
                var p = entry.getValue();
                sb.append("- Swarm ").append(entry.getKey())
                        .append(": consensus=").append(p.consensusScore())
                        .append(", stability=").append(p.stabilityScore())
                        .append('\n');
            }
        }
    }

    private static MediationResult toMediationResult(InvocationResult<JsonNode> result,
                                                      ConflictContext context) {
        if (result instanceof InvocationResult.Success<JsonNode> success) {
            return parseResolution(success.value(), context);
        }
        if (result instanceof InvocationResult.ParseError<JsonNode> parseError) {
            throw new MediationException("LLM response parse error: " + parseError.parseError());
        }
        if (result instanceof InvocationResult.AgentError<JsonNode> agentError) {
            throw new MediationException("LLM invocation error: " + agentError.reason());
        }
        throw new MediationException("Unknown invocation result type");
    }

    private static MediationResult parseResolution(JsonNode node, ConflictContext context) {
        var resolution = node.path("resolution").asText("resolved");
        var justification = node.path("justification").asText("");
        var updatedConclusion = node.path("updatedConclusion").asText(null);
        var confidence = node.path("confidence").asDouble(0.5);

        Belief<SwarmConclusion> updatedBelief = null;
        if (updatedConclusion != null && !updatedConclusion.isEmpty()) {
            var conclusion = new SwarmConclusion(
                    context.notification().sourceSwarmId(),
                    extractDomain(context),
                    updatedConclusion, confidence);
            updatedBelief = Belief.of(
                    context.notification().sourceSwarmId() + ":" + extractDomain(context),
                    conclusion, (int) (confidence * 100));
        }

        var notification = new CrossSwarmNotification(
                context.notification().sourceSwarmId(),
                context.notification().targetSwarmId(),
                context.notification().type(),
                context.notification().trigger(),
                confidence);

        return new MediationResult.Resolved(
                resolution + " — " + justification,
                updatedBelief,
                List.of(notification));
    }

    private static String extractDomain(ConflictContext context) {
        if (context.notification().trigger() instanceof MultiSwarmSignal.Finding f) {
            return f.domain();
        }
        return "unknown";
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency-core -Dtest=LlmConflictMediatorTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/LlmConflictMediator.java
git add agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/LlmConflictMediatorTest.java
git commit -m "feat(#438): LlmConflictMediator with type-specific prompts"
```

### Task 4: BudgetGatedConflictMediator with tests

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/BudgetGatedConflictMediator.java`
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/BudgetGatedConflictMediatorTest.java`

**Interfaces:**
- Consumes: `ConflictMediator`, `MediationResult`, `MediationException` from Task 1
- Consumes: `AgentBudgetView.tryAcquire() → BudgetGrant` (sealed: `Granted`, `Denied`)
- Consumes: `AutonomyBudgetPool.createView(String, BudgetPriority) → AgentBudgetView`
- Produces: `BudgetGatedConflictMediator(ConflictMediator primary, ConflictMediator fallback)` — used by Task 5

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.orchestration.budget.AgentBudgetView;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.BudgetGrant;
import io.casehub.agency.orchestration.budget.BudgetPressure;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class BudgetGatedConflictMediatorTest {

    ConflictMediator primary;
    ConflictMediator fallback;
    BudgetGatedConflictMediator gated;
    ConflictContext context;

    @BeforeEach
    void setUp() {
        primary = mock(ConflictMediator.class);
        fallback = mock(ConflictMediator.class);
        gated = new BudgetGatedConflictMediator(primary, fallback);
        context = mock(ConflictContext.class);
    }

    @Test
    void uses_fallback_when_pool_not_bound() {
        var expected = new MediationResult.FallbackApplied("test", "no pool", java.util.List.of());
        when(fallback.mediate(context)).thenReturn(expected);

        var result = gated.mediate(context);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(primary);
    }

    @Test
    void uses_primary_when_budget_granted() {
        var pool = new AutonomyBudgetPool(
                new AutonomyBudgetPolicy(100, 100_000, 10, 10_000, 0.5), "test");
        gated.bindPool(pool);

        var expected = new MediationResult.Resolved("ok", null, java.util.List.of());
        when(primary.mediate(context)).thenReturn(expected);

        var result = gated.mediate(context);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(fallback);
    }

    @Test
    void falls_back_on_primary_exception() {
        var pool = new AutonomyBudgetPool(
                new AutonomyBudgetPolicy(100, 100_000, 10, 10_000, 0.5), "test");
        gated.bindPool(pool);

        when(primary.mediate(context)).thenThrow(new MediationException("LLM failed"));
        var expected = new MediationResult.FallbackApplied("test", "fallback", java.util.List.of());
        when(fallback.mediate(context)).thenReturn(expected);

        var result = gated.mediate(context);

        assertThat(result).isSameAs(expected);
    }

    @Test
    void uses_fallback_when_budget_denied() {
        var pool = new AutonomyBudgetPool(
                new AutonomyBudgetPolicy(0, 0, 0, 0, 0.5), "test");
        gated.bindPool(pool);

        var expected = new MediationResult.FallbackApplied("test", "denied", java.util.List.of());
        when(fallback.mediate(context)).thenReturn(expected);

        var result = gated.mediate(context);

        assertThat(result).isSameAs(expected);
        verifyNoInteractions(primary);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency-core -Dtest=BudgetGatedConflictMediatorTest`
Expected: FAIL — class does not exist

- [ ] **Step 3: Implement BudgetGatedConflictMediator**

```java
package io.casehub.agency.orchestration.multiswarm;

import io.casehub.agency.orchestration.budget.AgentBudgetView;
import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.budget.BudgetGrant;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public class BudgetGatedConflictMediator implements ConflictMediator {

    private final ConflictMediator primary;
    private final ConflictMediator fallback;
    private volatile @Nullable AgentBudgetView budgetView;

    public BudgetGatedConflictMediator(ConflictMediator primary, ConflictMediator fallback) {
        this.primary = Objects.requireNonNull(primary);
        this.fallback = Objects.requireNonNull(fallback);
    }

    public void bindPool(AutonomyBudgetPool pool) {
        this.budgetView = pool.createView("l3-mediator", BudgetPriority.RESERVED);
    }

    @Override
    public MediationResult mediate(ConflictContext context) {
        var view = this.budgetView;
        if (view == null) {
            return fallback.mediate(context);
        }
        var grant = view.tryAcquire();
        if (grant instanceof BudgetGrant.Denied) {
            return fallback.mediate(context);
        }
        try {
            return primary.mediate(context);
        } catch (Exception e) {
            return fallback.mediate(context);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency-core -Dtest=BudgetGatedConflictMediatorTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/BudgetGatedConflictMediator.java
git add agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/BudgetGatedConflictMediatorTest.java
git commit -m "feat(#438): BudgetGatedConflictMediator decorator with LLM→heuristic fallback"
```

## Batch 3: MultiSwarmTopology integration

### Task 5: Wire ConflictMediator into MultiSwarmTopology and builder

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/MultiSwarmTopology.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/MultiSwarmTopologyBuilder.java`
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/MultiSwarmBehavioralTest.java` (new L3 nested class)

**Interfaces:**
- Consumes: `ConflictMediator`, `ConflictContext`, `MediationResult` from Task 1
- Consumes: `LlmConflictMediator` from Task 3
- Consumes: `HeuristicConflictMediator` from Task 2
- Consumes: `BudgetGatedConflictMediator` from Task 4
- Produces: `MultiSwarmTopology.resolveConflicts() → List<MediationResult>`
- Produces: `MultiSwarmTopologyBuilder.conflictMediator(ConflictMediator)`

- [ ] **Step 1: Write failing tests for L3 integration**

Add a new `@Nested class L3ConflictResolution` in `MultiSwarmBehavioralTest`:

```java
@Nested
class L3ConflictResolution {

    @Test
    void conflict_detection_enqueues_to_pending_conflicts_when_mediator_present() throws InterruptedException {
        var heuristicMediator = new HeuristicConflictMediator();
        var topology = MultiSwarmTopology.builder()
                .registry(registry)
                .consistencyChecker(new DomainConsistencyChecker())
                .conflictMediator(heuristicMediator)
                .build();

        registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
        registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

        publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                "domain", "node-7:healthy", 0.9));
        publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                "domain", "node-7:degraded", 0.8));

        var results = topology.resolveConflicts();
        assertThat(results).isNotEmpty();
        assertThat(results.get(0)).isInstanceOf(MediationResult.FallbackApplied.class);

        topology.close();
    }

    @Test
    void no_mediator_leaves_conflicts_as_notifications_only() throws InterruptedException {
        var topology = MultiSwarmTopology.builder()
                .registry(registry)
                .consistencyChecker(new DomainConsistencyChecker())
                .build();

        registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
        registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

        publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                "domain", "node-7:healthy", 0.9));
        publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                "domain", "node-7:degraded", 0.8));

        var results = topology.resolveConflicts();
        assertThat(results).isEmpty();

        // Notifications still present
        assertThat(topology.drainNotifications()).isNotEmpty();

        topology.close();
    }

    @Test
    void resolve_conflicts_updates_belief_set_on_resolved_result() throws InterruptedException {
        ConflictMediator resolvingMediator = ctx -> {
            var conclusion = new SwarmConclusion(
                    ctx.notification().sourceSwarmId(), "domain", "node-7:healthy", 0.95);
            var belief = io.casehub.agency.deliberation.belief.Belief.of(
                    ctx.notification().sourceSwarmId() + ":domain", conclusion, 95);
            return new MediationResult.Resolved("resolved", belief, java.util.List.of());
        };

        var topology = MultiSwarmTopology.builder()
                .registry(registry)
                .consistencyChecker(new DomainConsistencyChecker())
                .conflictMediator(resolvingMediator)
                .build();

        registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
        registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

        publishAndAwait(topology, finding(analysisSwarmId, ANALYSIS_CAPS,
                "domain", "node-7:healthy", 0.9));
        publishAndAwait(topology, finding(remediationSwarmId, REMEDIATION_CAPS,
                "domain", "node-7:degraded", 0.8));

        topology.resolveConflicts();

        // The resolved belief should be in the BeliefSet
        var beliefs = topology.currentBeliefs();
        assertThat(beliefs.all()).anyMatch(b ->
                b.value().conclusion().equals("node-7:healthy") && b.entrenchment() == 95);

        topology.close();
    }

    @Test
    void resource_contention_detected_on_overlapping_claims() throws InterruptedException {
        var heuristicMediator = new HeuristicConflictMediator();
        var topology = MultiSwarmTopology.builder()
                .registry(registry)
                .consistencyChecker(new DomainConsistencyChecker())
                .conflictMediator(heuristicMediator)
                .build();

        registry.register(descriptor(analysisSwarmId, ANALYSIS_CAPS));
        registry.register(descriptor(remediationSwarmId, REMEDIATION_CAPS));

        publishAndAwait(topology, resourceClaim(analysisSwarmId, ANALYSIS_CAPS,
                Set.of("agent-shared"), Set.of("analysis")));
        publishAndAwait(topology, resourceClaim(remediationSwarmId, REMEDIATION_CAPS,
                Set.of("agent-shared"), Set.of("remediation")));

        var results = topology.resolveConflicts();
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(r ->
                r instanceof MediationResult.FallbackApplied fa
                        && fa.fallbackAction().equals("first_claimer_wins"));

        topology.close();
    }

    @Test
    void builder_auto_creates_budget_gated_mediator_from_agent_provider() {
        var agentProvider = mock(AgentProvider.class);
        var topology = MultiSwarmTopology.builder()
                .registry(registry)
                .consistencyChecker(new DomainConsistencyChecker())
                .agentProvider(agentProvider)
                .build();

        // The builder should have auto-created a mediator
        var results = topology.resolveConflicts();
        assertThat(results).isEmpty(); // No conflicts yet — just verify no NPE

        topology.close();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agency-core -Dtest="MultiSwarmBehavioralTest$L3ConflictResolution"`
Expected: FAIL — `conflictMediator()` method and `resolveConflicts()` don't exist

- [ ] **Step 3: Modify MultiSwarmTopologyBuilder — add conflictMediator()**

Add field and method to `MultiSwarmTopologyBuilder`. In `build()`, auto-compose
from `agentProvider` when `conflictMediator` is not set:

Add field `private @Nullable ConflictMediator conflictMediator;`

Add method:
```java
public MultiSwarmTopologyBuilder conflictMediator(ConflictMediator mediator) {
    this.conflictMediator = mediator;
    return this;
}
```

In `build()`, after existing null checks, add:
```java
ConflictMediator mediator = this.conflictMediator;
if (mediator == null && agentProvider != null) {
    mediator = new BudgetGatedConflictMediator(
            new LlmConflictMediator(agentProvider),
            new HeuristicConflictMediator());
}
```

Pass `mediator` to the `MultiSwarmTopology` constructor.

- [ ] **Step 4: Modify MultiSwarmTopology — add conflict resolution infrastructure**

Add to the constructor parameters: `@Nullable ConflictMediator conflictMediator`

Add fields:
```java
private final @Nullable ConflictMediator conflictMediator;
private final ConcurrentLinkedQueue<ConflictContext> pendingConflicts = new ConcurrentLinkedQueue<>();
```

Modify `processL2Finding()` — after `pendingNotifications.add(notification)`,
add conflict enqueuing:
```java
if (conflictMediator != null) {
    var context = buildConflictContext(notification);
    pendingConflicts.add(context);
}
```

Modify `processResourceClaim()` — after storing claims, detect overlap:
```java
if (conflictMediator != null) {
    for (var entry : resourceClaims.entrySet()) {
        if (entry.getKey().equals(claim.sourceSwarmId())) continue;
        if (!java.util.Collections.disjoint(entry.getValue(), claim.agentIds())) {
            var notification = new CrossSwarmNotification(
                    claim.sourceSwarmId(), entry.getKey(),
                    NotificationType.RESOURCE_CONTENTION, claim, 0.8);
            pendingNotifications.add(notification);
            pendingConflicts.add(buildConflictContext(notification));
        }
    }
}
```

Modify `processL2Finding()` to use compareAndSet loop to capture contracted beliefs:
```java
BeliefSet<SwarmConclusion> before, after;
List<Belief<SwarmConclusion>> contracted;
do {
    before = beliefSet.get();
    after = before.revise(newBelief, consistencyChecker);
    contracted = before.all().stream()
            .filter(b -> !after.contains(b.key()))
            .toList();
} while (!beliefSet.compareAndSet(before, after));
boolean contradicted = !contracted.isEmpty();
```

Add `resolveConflicts()`:
```java
public List<MediationResult> resolveConflicts() {
    if (conflictMediator == null) return List.of();

    var results = new ArrayList<MediationResult>();
    ConflictContext ctx;
    while ((ctx = pendingConflicts.poll()) != null) {
        var result = conflictMediator.mediate(ctx);
        applyResult(result);
        results.add(result);
    }
    return List.copyOf(results);
}

private void applyResult(MediationResult result) {
    if (result instanceof MediationResult.Resolved resolved && resolved.updatedBelief() != null) {
        beliefSet.updateAndGet(current -> current.expand(resolved.updatedBelief()));
    }
}
```

Add helper:
```java
private ConflictContext buildConflictContext(CrossSwarmNotification notification) {
    var before = beliefSet.get();
    var descriptors = new java.util.HashMap<UUID, io.casehub.api.model.coordination.SwarmDescriptor>();
    var sourceDesc = registry.get(notification.sourceSwarmId());
    var targetDesc = registry.get(notification.targetSwarmId());
    if (sourceDesc != null) descriptors.put(notification.sourceSwarmId(), sourceDesc);
    if (targetDesc != null) descriptors.put(notification.targetSwarmId(), targetDesc);

    return new ConflictContext(
            notification, before, List.of(),
            Map.copyOf(latestProgress), Map.copyOf(resourceClaims),
            Map.copyOf(descriptors));
}
```

For the CONFLICT case in `processL2Finding`, pass the actual `contracted` list
to `ConflictContext` instead of `List.of()`.

- [ ] **Step 5: Modify MultiSwarmTopology constructor call in builder**

Update the `new MultiSwarmTopology(...)` call in `build()` to pass `mediator`
as the additional parameter. Update the constructor signature to accept it.

- [ ] **Step 6: Add budget pool binding in configure()**

In `configure()`, after creating the `AutonomyBudgetPool`, bind it to the
mediator if it's a `BudgetGatedConflictMediator`:

```java
if (conflictMediator instanceof BudgetGatedConflictMediator bgm) {
    bgm.bindPool(pool);
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency-core -Dtest="MultiSwarmBehavioralTest"`
Expected: PASS — all tests including new L3 nested class

- [ ] **Step 8: Run full test suite**

Run: `mvn --batch-mode test -pl agency-core`
Expected: PASS — no regressions

- [ ] **Step 9: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/MultiSwarmTopology.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/multiswarm/MultiSwarmTopologyBuilder.java
git add agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/MultiSwarmBehavioralTest.java
git commit -m "feat(#438): wire L3 conflict resolution into MultiSwarmTopology

ConflictMediator integration: pending conflicts queue, resolveConflicts(),
resource contention detection, compareAndSet belief revision delta,
budget pool binding in configure(), auto-compose from agentProvider in builder."
```

## References

- [2026-11-08-l3-conflict-resolution-design.md](../specs/issue-439-vocabulary-consistency-checker/2026-11-08-l3-conflict-resolution-design.md) — design spec this plan implements
- `MultiSwarmTopology.java:57` — existing agentProvider slot
- `MultiSwarmTopology.java:228-244` — existing processL2Finding (L2 conflict detection)
- `LlmTriageHandler.java` — reference pattern for StructuredAgentInvoker usage
- `AutonomyBudgetPool.java:40-47` — createView API
- `AgentBudgetView.java:25-42` — tryAcquire API
- `BudgetGrant.java` — sealed Granted/Denied hierarchy
- `StructuredAgentInvoker.java:83-101` — invokeRaw API
- GitHub #438 — focal issue
- GitHub #364 — swarm coordination epic
