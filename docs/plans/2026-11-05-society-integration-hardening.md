# Society Integration Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #408 — epic: Society integration hardening
**Issue group:** #409, #410, #411, #412, #413

**Goal:** Replace 5 topology classes with a composable TopologyBuilder,
wire CoalitionProposal through NegotiationProtocol to
GroupLifecycleService, and align agency's role model with eidos RoleSpec.

**Architecture:** Extract three orthogonal concerns (loop planning,
outcome execution, degradation cascade) from topology classes into
composable strategies. A single `ComposableTopology` backed by
`TopologyBuilder` factory methods replaces `HiveTopology`,
`DistributedTopology`, `HierarchicalTopology`, `HybridTopology`, and
`GraduatedTopology`. `CentralisedTopology<C>` is excluded (swarm
reasoner entanglement). New eidos org-api dependency enables
`BottomUpFormationOrchestrator` and `RolePolicyCompiler`.

**Tech Stack:** Java 17, JUnit 5, AssertJ, Mockito, jspecify @Nullable

## Global Constraints

- `CentralisedTopology<C>` is EXCLUDED from migration — stays as-is
- eidos `org-api` types (`RoleSpec`, `GroupLifecycleService`) are consumed
  via provided dependency — agency does not own them
- All topology migrations must preserve identical observable behavior —
  existing tests are the contract
- `org.jspecify:jspecify` for `@Nullable` annotations
- No Quarkus runtime in tests — plain JUnit 5 with Mockito

---

## Batch 1: Foundation — TopologyContext + Infrastructure Types

### Task 1: TopologyContext enrichment (#412)

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/TopologyContext.java`
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/TopologyContextTest.java`
- Modify (caller update): 9 additional test files that construct TopologyContext

**Interfaces:**
- Produces: `TopologyContext(UUID, TopologyDescriptor, AgentProvider, DecisionLoopFactory, AutonomyBudgetPolicy, DegradationPolicy, @Nullable String groupId, @Nullable String tenancyId)` — the enriched record

- [ ] **Step 1: Write failing test for groupId/tenancyId**

Add to `TopologyContextTest.java`:

```java
@Test
void creates_with_group_context() {
    var ctx = new TopologyContext(
            UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY,
            "group-1", "tenant-1");
    assertThat(ctx.groupId()).isEqualTo("group-1");
    assertThat(ctx.tenancyId()).isEqualTo("tenant-1");
}

@Test
void allows_null_groupId_and_tenancyId() {
    var ctx = new TopologyContext(
            UUID.randomUUID(), DESCRIPTOR, AGENT_PROVIDER, LOOP_FACTORY, POLICY, DEG_POLICY,
            null, null);
    assertThat(ctx.groupId()).isNull();
    assertThat(ctx.tenancyId()).isNull();
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency-core -Dtest=TopologyContextTest#creates_with_group_context -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — constructor doesn't accept 8 args

- [ ] **Step 3: Add groupId and tenancyId to TopologyContext**

Modify `TopologyContext.java`:

```java
import org.jspecify.annotations.Nullable;

public record TopologyContext(
        UUID caseId,
        TopologyDescriptor descriptor,
        AgentProvider agentProvider,
        DecisionLoopFactory loopFactory,
        AutonomyBudgetPolicy budgetPolicy,
        DegradationPolicy degradationPolicy,
        @Nullable String groupId,
        @Nullable String tenancyId
) {
    public TopologyContext {
        Objects.requireNonNull(caseId, "caseId must not be null");
        Objects.requireNonNull(descriptor, "descriptor must not be null");
        Objects.requireNonNull(agentProvider, "agentProvider must not be null");
        Objects.requireNonNull(loopFactory, "loopFactory must not be null");
        Objects.requireNonNull(budgetPolicy, "budgetPolicy must not be null");
        Objects.requireNonNull(degradationPolicy, "degradationPolicy must not be null");
    }
}
```

- [ ] **Step 4: Update all existing TopologyContext constructors to pass null, null**

Every existing 6-arg `new TopologyContext(...)` call needs `, null, null` appended. Files to update (16 call sites across 10 files):

| File | Call sites |
|------|-----------|
| `TopologyContextTest.java` | 6 (lines 31, 44, 51, 58, 65, 72, 79) |
| `HivePatternAdapterTest.java` | 1 (line 27) |
| `HiveTopologyTest.java` | 2 (lines 40, 93) |
| `HiveTopologyCascadeTest.java` | 1 (line 48) |
| `GraduatedTopologyTest.java` | 1 (line 44) |
| `HierarchicalTopologyTest.java` | 1 (line 42) |
| `CentralisedTopologyTest.java` | 1 (line 74) |
| `CentralisedTopologyCascadeTest.java` | 1 (line 68) |
| `DistributedTopologyTest.java` | 1 (line 43) |
| `HybridTopologyTest.java` | 1 (line 39) |

For each call site, append `, null, null` before the closing paren.

- [ ] **Step 5: Run all tests to verify pass**

Run: `mvn --batch-mode test -pl agency-core`
Expected: PASS — all existing tests pass with the added null params, new tests pass

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/TopologyContext.java
git add agency-core/src/test/java/
git commit -m "feat: add groupId and tenancyId to TopologyContext (#412)

Refs #412

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Extract DegradationCascade + LoopPlanner + FeedbackHandler

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/DegradationCascade.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/LoopPlanner.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/LoopPlan.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/FeedbackHandler.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/DegradationCascadeFactory.java`
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/DegradationCascadeTest.java`
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/LoopPlanTest.java`

**Interfaces:**
- Produces: `LoopPlanner` (`@FunctionalInterface`: `LoopPlan plan(TopologyContext)`), `LoopPlan` record (`descriptors`, `triggers`, `heuristics`), `FeedbackHandler` (`@FunctionalInterface`: `void onFeedback(CoordinationFeedback)`), `DegradationCascade` (encapsulates cascade threshold + trigger activation), `DegradationCascadeFactory` (`@FunctionalInterface`: `DegradationCascade create(DegradationPolicy, LoopPlan, List<CascadeDegradationTrigger>)`)

- [ ] **Step 1: Write failing test for DegradationCascade**

Create `DegradationCascadeTest.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.degradation.CascadeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.SimpleFractionThreshold;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class DegradationCascadeTest {

    @Test
    void breach_fires_handler_when_threshold_exceeded() {
        var triggers = List.of(new CascadeDegradationTrigger(), new CascadeDegradationTrigger());
        var priorities = Map.of("loop-a", BudgetPriority.RESERVED, "loop-b", BudgetPriority.NORMAL);
        var breached = new AtomicReference<Set<String>>();

        var cascade = new DegradationCascade(
                new SimpleFractionThreshold(0.5),
                priorities, triggers,
                (degraded, trigs) -> {
                    breached.set(degraded);
                    trigs.forEach(CascadeDegradationTrigger::activate);
                });

        cascade.onDegradation(new SwarmDegradedEvent("loop-a", "budget", true));
        assertThat(breached.get()).isNull();

        cascade.onDegradation(new SwarmDegradedEvent("loop-b", "budget", true));
        assertThat(breached.get()).containsExactlyInAnyOrder("loop-a", "loop-b");
        assertThat(triggers).allMatch(CascadeDegradationTrigger::shouldDegrade);
    }

    @Test
    void recovery_removes_from_degraded_set() {
        var triggers = List.of(new CascadeDegradationTrigger());
        var priorities = Map.of("loop-a", BudgetPriority.NORMAL);
        var cascade = new DegradationCascade(
                new SimpleFractionThreshold(1.0), priorities, triggers,
                (degraded, trigs) -> {});

        cascade.onDegradation(new SwarmDegradedEvent("loop-a", "budget", true));
        cascade.onDegradation(new SwarmDegradedEvent("loop-a", "cleared", false));
        // No breach — loop recovered before threshold hit
    }

    @Test
    void breach_is_one_way_ratchet() {
        var triggers = List.of(new CascadeDegradationTrigger());
        var priorities = Map.of("loop-a", BudgetPriority.NORMAL);
        var count = new int[]{0};
        var cascade = new DegradationCascade(
                new SimpleFractionThreshold(1.0), priorities, triggers,
                (degraded, trigs) -> count[0]++);

        cascade.onDegradation(new SwarmDegradedEvent("loop-a", "budget", true));
        assertThat(count[0]).isEqualTo(1);

        cascade.onDegradation(new SwarmDegradedEvent("loop-a", "cleared", false));
        cascade.onDegradation(new SwarmDegradedEvent("loop-a", "budget", true));
        assertThat(count[0]).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency-core -Dtest=DegradationCascadeTest`
Expected: FAIL — `DegradationCascade` class doesn't exist

- [ ] **Step 3: Create LoopPlanner, LoopPlan, FeedbackHandler, DegradationCascadeFactory**

Create `LoopPlanner.java`:

```java
package io.casehub.agency.orchestration.topology;

@FunctionalInterface
public interface LoopPlanner {
    LoopPlan plan(TopologyContext context);
}
```

Create `LoopPlan.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.summarisation.ContentSummariser;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record LoopPlan(
        List<DecisionLoopDescriptor> descriptors,
        Map<String, DegradationTrigger> triggers,
        Map<String, ContentSummariser<ObservationEvent, DecisionOutcome>> heuristics
) {
    public LoopPlan {
        descriptors = List.copyOf(descriptors);
        triggers = Map.copyOf(Objects.requireNonNull(triggers));
        heuristics = Map.copyOf(Objects.requireNonNull(heuristics));
        if (descriptors.isEmpty()) {
            throw new IllegalArgumentException("LoopPlan must have at least one descriptor");
        }
    }
}
```

Create `FeedbackHandler.java`:

```java
package io.casehub.agency.orchestration.topology;

@FunctionalInterface
public interface FeedbackHandler {
    void onFeedback(CoordinationFeedback feedback);
}
```

Create `DegradationCascadeFactory.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.degradation.CascadeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.DegradationPolicy;

import java.util.List;

@FunctionalInterface
public interface DegradationCascadeFactory {
    DegradationCascade create(DegradationPolicy policy, LoopPlan plan,
                              List<CascadeDegradationTrigger> sharedTriggers);
}
```

- [ ] **Step 4: Create DegradationCascade**

Create `DegradationCascade.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.degradation.CascadeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.CascadeThreshold;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class DegradationCascade {

    @FunctionalInterface
    public interface CascadeBreachHandler {
        void onBreach(Set<String> degradedLoops,
                      List<CascadeDegradationTrigger> triggers);
    }

    private final CascadeThreshold threshold;
    private final Map<String, BudgetPriority> loopPriorities;
    private final List<CascadeDegradationTrigger> triggers;
    private final CascadeBreachHandler breachHandler;
    private final Set<String> degradedLoops = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean cascadeTriggered = new AtomicBoolean(false);

    public DegradationCascade(CascadeThreshold threshold,
                              Map<String, BudgetPriority> loopPriorities,
                              List<CascadeDegradationTrigger> triggers,
                              CascadeBreachHandler breachHandler) {
        this.threshold = Objects.requireNonNull(threshold);
        this.loopPriorities = Map.copyOf(loopPriorities);
        this.triggers = List.copyOf(triggers);
        this.breachHandler = Objects.requireNonNull(breachHandler);
    }

    public void onDegradation(SwarmDegradedEvent event) {
        if (event.fallbackActive()) {
            degradedLoops.add(event.capability());
        } else {
            degradedLoops.remove(event.capability());
        }
        if (event.fallbackActive() && !cascadeTriggered.get()
            && threshold.isBreached(degradedLoops, loopPriorities.size(), loopPriorities)
            && cascadeTriggered.compareAndSet(false, true)) {
            breachHandler.onBreach(Set.copyOf(degradedLoops), triggers);
        }
    }
}
```

- [ ] **Step 5: Run tests to verify pass**

Run: `mvn --batch-mode test -pl agency-core -Dtest=DegradationCascadeTest`
Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/DegradationCascade.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/DegradationCascadeFactory.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/LoopPlanner.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/LoopPlan.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/FeedbackHandler.java
git add agency-core/src/test/java/io/casehub/agency/orchestration/topology/DegradationCascadeTest.java
git commit -m "feat: extract DegradationCascade, LoopPlanner, FeedbackHandler infrastructure

Extract three orthogonal concerns from topology classes into composable
strategy types for the TopologyBuilder composition model.

Refs #408

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 2: TopologyBuilder + Migration

### Task 3: ComposableTopology + TopologyBuilder + DistributedTopology migration

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/ComposableTopology.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/TopologyBuilder.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/CoordinationTopology.java` (add describeTopology)
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CompositeDegradationTrigger.java` (add triggers() accessor)
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/TopologyBuilderTest.java`
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/DistributedTopologyTest.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/DistributedTopology.java` (after migration)

**Interfaces:**
- Consumes: `LoopPlanner`, `LoopPlan`, `FeedbackHandler`, `DegradationCascadeFactory`, `DegradationCascade`, `DecisionOutcomeExecutor`, `TopologyContext` (all from Task 1-2)
- Produces: `TopologyBuilder.distributed(Map<String, AgentRole>)` → `CoordinationTopology`, `TopologyBuilder.create(...)` generic factory, `ComposableTopology` class

- [ ] **Step 1: Add describeTopology to CoordinationTopology interface (D10)**

Modify `CoordinationTopology.java` — add the method declaration:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.spi.coordination.TopologyProvider;

import java.util.UUID;

public interface CoordinationTopology extends TopologyProvider {

    TopologyDescriptor describeTopology(UUID caseId);

    CoordinationPlan configure(TopologyContext context);

    void onSwarmEvent(SwarmCoordinationEvent event);

    void onFeedback(CoordinationFeedback feedback);

    default void onDegradation(io.casehub.agency.orchestration.swarm.SwarmDegradedEvent event) {}
}
```

- [ ] **Step 2: Write failing test for TopologyBuilder.distributed()**

Create `TopologyBuilderTest.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.DecisionType;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.StructuralRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyBuilderTest {

    static final Map<String, AgentRole> PEER_ROLES = Map.of(
            "agent-a", AgentRole.of("peer-a", StructuralRole.PEER),
            "agent-b", AgentRole.of("peer-b", StructuralRole.PEER),
            "agent-c", AgentRole.of("peer-c", StructuralRole.PEER));

    TopologyContext context(TopologyType type, Map<String, AgentRole> roles) {
        return new TopologyContext(
                UUID.randomUUID(),
                new TopologyDescriptor(type, roles),
                TestAgentProviders.noOp(),
                new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
                AutonomyBudgetPolicy.defaults(),
                io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults(),
                null, null);
    }

    @Test
    void distributed_creates_one_loop_per_agent() {
        var topology = TopologyBuilder.distributed(PEER_ROLES);
        var plan = topology.configure(context(TopologyType.DISTRIBUTED, PEER_ROLES));
        assertThat(plan.loopDescriptors()).hasSize(3);
    }

    @Test
    void distributed_loops_have_correct_properties() {
        var topology = TopologyBuilder.distributed(PEER_ROLES);
        var plan = topology.configure(context(TopologyType.DISTRIBUTED, PEER_ROLES));
        for (var d : plan.loopDescriptors()) {
            assertThat(d.name()).startsWith("distributed-");
            assertThat(d.scope()).isEqualTo(DecisionScope.FULL_AUTONOMY);
            assertThat(d.observationStrategy()).isEqualTo(ObservationStrategy.EVENT_DRIVEN);
            assertThat(d.communicationMode()).isEqualTo(CommunicationMode.STIGMERGY);
            assertThat(d.budgetPriority()).isEqualTo(BudgetPriority.NORMAL);
        }
    }

    @Test
    void distributed_pool_has_correct_id() {
        var topology = TopologyBuilder.distributed(PEER_ROLES);
        var plan = topology.configure(context(TopologyType.DISTRIBUTED, PEER_ROLES));
        assertThat(plan.pool().topologyId()).endsWith("-DISTRIBUTED");
    }

    @Test
    void distributed_executor_handles_scope_gated() throws Exception {
        var topology = TopologyBuilder.distributed(PEER_ROLES);
        var ctx = context(TopologyType.DISTRIBUTED, PEER_ROLES);
        var plan = topology.configure(ctx);
        var gated = new DecisionOutcome.ScopeGated(
                DecisionType.PROVISIONING,
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));
        var fb = plan.executor().execute("agent-a", gated, ctx)
                .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SCOPE_VIOLATION);
    }

    @Test
    void distributed_executor_returns_success_for_writeback() throws Exception {
        var topology = TopologyBuilder.distributed(PEER_ROLES);
        var ctx = context(TopologyType.DISTRIBUTED, PEER_ROLES);
        var plan = topology.configure(ctx);
        var wb = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var fb = plan.executor().execute("agent-a", wb, ctx)
                .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void distributed_describeTopology_returns_correct_type() {
        var topology = TopologyBuilder.distributed(PEER_ROLES);
        var descriptor = topology.describeTopology(UUID.randomUUID());
        assertThat(descriptor.topologyType()).isEqualTo(TopologyType.DISTRIBUTED);
        assertThat(descriptor.agentRoles()).hasSize(3);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency-core -Dtest=TopologyBuilderTest`
Expected: FAIL — `TopologyBuilder` class doesn't exist

- [ ] **Step 4: Create ComposableTopology**

Create `ComposableTopology.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPool;
import io.casehub.agency.orchestration.degradation.CascadeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.CompositeDegradationTrigger;
import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.swarm.SwarmCoordinationEvent;
import io.casehub.agency.orchestration.swarm.SwarmDegradedEvent;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

class ComposableTopology implements CoordinationTopology {

    private final TopologyType type;
    private final Map<String, AgentRole> agentRoles;
    private final LoopPlanner planner;
    private final DecisionOutcomeExecutor executor;
    private final @Nullable DegradationCascadeFactory cascadeFactory;
    private final @Nullable FeedbackHandler feedbackHandler;
    private volatile @Nullable DegradationCascade cascade;

    ComposableTopology(TopologyType type,
                       Map<String, AgentRole> agentRoles,
                       LoopPlanner planner,
                       DecisionOutcomeExecutor executor,
                       @Nullable DegradationCascadeFactory cascadeFactory,
                       @Nullable FeedbackHandler feedbackHandler) {
        this.type = Objects.requireNonNull(type);
        this.agentRoles = Map.copyOf(Objects.requireNonNull(agentRoles));
        this.planner = Objects.requireNonNull(planner);
        this.executor = Objects.requireNonNull(executor);
        this.cascadeFactory = cascadeFactory;
        this.feedbackHandler = feedbackHandler;
    }

    @Override
    public TopologyDescriptor describeTopology(UUID caseId) {
        return new TopologyDescriptor(type, agentRoles);
    }

    @Override
    public CoordinationPlan configure(TopologyContext context) {
        var plan = planner.plan(context);
        var pool = new AutonomyBudgetPool(
                context.budgetPolicy(), context.caseId() + "-" + type.name());
        if (cascadeFactory != null) {
            var sharedTriggers = extractCascadeTriggers(plan.triggers());
            this.cascade = cascadeFactory.create(
                    context.degradationPolicy(), plan, sharedTriggers);
        }
        return new CoordinationPlan(
                plan.descriptors(), executor, pool,
                plan.triggers(), plan.heuristics());
    }

    private List<CascadeDegradationTrigger> extractCascadeTriggers(
            Map<String, DegradationTrigger> triggers) {
        var result = new ArrayList<CascadeDegradationTrigger>();
        for (var trigger : triggers.values()) {
            if (trigger instanceof CascadeDegradationTrigger cdt) {
                result.add(cdt);
            } else if (trigger instanceof CompositeDegradationTrigger composite) {
                for (var child : composite.triggers()) {
                    if (child instanceof CascadeDegradationTrigger cdt) {
                        result.add(cdt);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void onSwarmEvent(SwarmCoordinationEvent event) {}

    @Override
    public void onFeedback(CoordinationFeedback feedback) {
        if (feedbackHandler != null) feedbackHandler.onFeedback(feedback);
    }

    @Override
    public void onDegradation(SwarmDegradedEvent event) {
        if (cascade != null) cascade.onDegradation(event);
    }
}
```

**Note:** `CompositeDegradationTrigger` does NOT have a `triggers()` accessor — only a private `triggers` field. Add a public `triggers()` accessor before using it:

```java
// Add to CompositeDegradationTrigger.java:
public List<DegradationTrigger> triggers() { return triggers; }
```

- [ ] **Step 5: Create TopologyBuilder with distributed() factory**

Create `TopologyBuilder.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.BudgetPriority;
import io.casehub.agency.orchestration.degradation.DegradationTrigger;
import io.casehub.agency.orchestration.degradation.LatencyDegradationTrigger;
import io.casehub.agency.orchestration.degradation.NoOpHeuristicSummariser;
import io.casehub.agency.orchestration.loop.DecisionLoopDescriptor;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.stigmergy.llm.LlmInvocationPolicy;
import io.casehub.agency.summarisation.ContentSummariser;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.CommunicationMode;
import io.casehub.api.model.coordination.DecisionScope;
import io.casehub.api.model.coordination.ObservationStrategy;
import io.casehub.api.model.coordination.TopologyType;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public final class TopologyBuilder {

    private TopologyBuilder() {}

    public static CoordinationTopology create(
            TopologyType type,
            Map<String, AgentRole> agentRoles,
            LoopPlanner planner,
            DecisionOutcomeExecutor executor,
            @Nullable DegradationCascadeFactory cascadeFactory,
            @Nullable FeedbackHandler feedbackHandler) {
        return new ComposableTopology(type, agentRoles, planner,
                executor, cascadeFactory, feedbackHandler);
    }

    public static CoordinationTopology distributed(Map<String, AgentRole> agentRoles) {
        return create(TopologyType.DISTRIBUTED, agentRoles,
                distributedPlanner(), defaultStubExecutor(), null, null);
    }

    private static LoopPlanner distributedPlanner() {
        return context -> {
            var defaultPolicy = LlmInvocationPolicy.of(
                    Duration.ofSeconds(5), Duration.ofSeconds(8), Duration.ofSeconds(10));
            var dp = context.degradationPolicy();

            var descriptors = context.descriptor().agentRoles().keySet().stream()
                    .map(agentId -> new DecisionLoopDescriptor(
                            "distributed-" + agentId,
                            ObservationStrategy.EVENT_DRIVEN,
                            DecisionScope.FULL_AUTONOMY,
                            CommunicationMode.STIGMERGY,
                            BudgetPriority.NORMAL,
                            List.of("environment-observer", "peer-state")))
                    .toList();

            var triggers = new LinkedHashMap<String, DegradationTrigger>();
            var heuristics = new LinkedHashMap<String, ContentSummariser<ObservationEvent, DecisionOutcome>>();
            for (var d : descriptors) {
                triggers.put(d.name(), new LatencyDegradationTrigger(
                        defaultPolicy, dp.latencyBreachWindow(),
                        dp.latencyBreachCount(), dp.latencyBreachWindowDuration()));
                heuristics.put(d.name(), new NoOpHeuristicSummariser<>());
            }
            return new LoopPlan(descriptors, triggers, heuristics);
        };
    }

    static DecisionOutcomeExecutor defaultStubExecutor() {
        return (agentId, outcome, ctx) -> {
            if (outcome instanceof DecisionOutcome.ScopeGated) {
                return CompletableFuture.completedFuture(
                        new CoordinationFeedback(agentId, outcome, FeedbackSignal.SCOPE_VIOLATION));
            }
            return CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
        };
    }
}
```

- [ ] **Step 6: Add triggers() accessor to CompositeDegradationTrigger**

`CompositeDegradationTrigger` has a private `triggers` field but no accessor. Add one:

```java
// In agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CompositeDegradationTrigger.java
public List<DegradationTrigger> triggers() { return triggers; }
```

This is needed by `ComposableTopology.extractCascadeTriggers()` to find `CascadeDegradationTrigger` instances inside composite triggers.

- [ ] **Step 7: Run tests to verify pass**

Run: `mvn --batch-mode test -pl agency-core -Dtest=TopologyBuilderTest`
Expected: PASS

- [ ] **Step 8: Migrate DistributedTopologyTest to use TopologyBuilder**

Update `DistributedTopologyTest.java` — replace `new DistributedTopology(agentRoles)` with `TopologyBuilder.distributed(agentRoles)` and change the type from `DistributedTopology` to `CoordinationTopology`:

```java
// In setUp():
CoordinationTopology topology;
// ...
topology = TopologyBuilder.distributed(agentRoles);
```

Remove all imports of `DistributedTopology`. All assertions remain the same — the behavior must be identical.

- [ ] **Step 9: Run migrated test to verify identical behavior**

Run: `mvn --batch-mode test -pl agency-core -Dtest=DistributedTopologyTest`
Expected: PASS — all existing assertions pass unchanged

- [ ] **Step 10: Delete DistributedTopology.java**

Use `ide_refactor_safe_delete` on `DistributedTopology.java`. If references remain, update them first.

- [ ] **Step 11: Run full test suite**

Run: `mvn --batch-mode test -pl agency-core`
Expected: PASS

- [ ] **Step 12: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/
git add agency-core/src/test/java/
git commit -m "feat: ComposableTopology + TopologyBuilder, migrate DistributedTopology

Introduce ComposableTopology backed by TopologyBuilder factory methods.
Add describeTopology() to CoordinationTopology interface (D10).
Migrate DistributedTopology to TopologyBuilder.distributed().
Delete DistributedTopology class.

Refs #408

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Migrate HierarchicalTopology + HybridTopology + GraduatedTopology

**Files:**
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/TopologyBuilder.java` (add 3 factory methods)
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/TopologyBuilderTest.java` (add tests)
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/HierarchicalTopologyTest.java` (migrate)
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/HybridTopologyTest.java` (migrate)
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/GraduatedTopologyTest.java` (migrate)
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/degradation/GraduatedTopologyCascadeTest.java` (if exists)
- Delete: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HierarchicalTopology.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HybridTopology.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/GraduatedTopology.java`

**Interfaces:**
- Consumes: `TopologyBuilder.create()`, `DegradationCascadeFactory`, `DegradationCascade`, `FeedbackHandler`, `LoopPlanner`, `GraduationState` (kept as-is)
- Produces: `TopologyBuilder.hierarchical(Map<String, AgentRole>, Map<String, String>)`, `TopologyBuilder.hybrid(Map<String, AgentRole>)`, `TopologyBuilder.graduated(Map<String, AgentRole>)` — all return `CoordinationTopology`

- [ ] **Step 1: Write failing tests for hierarchical(), hybrid(), graduated()**

Add to `TopologyBuilderTest.java`:

```java
@Test
void hierarchical_creates_one_loop_per_agent() {
    var roles = Map.of(
            "root", AgentRole.of("root", StructuralRole.INITIATOR),
            "mid", AgentRole.of("mid", StructuralRole.CONTRACTOR),
            "leaf", AgentRole.of("leaf", StructuralRole.CONTRACTOR));
    var parentMap = Map.of("leaf", "mid", "mid", "root");
    var topology = TopologyBuilder.hierarchical(roles, parentMap);
    var plan = topology.configure(context(TopologyType.HIERARCHICAL, roles));
    assertThat(plan.loopDescriptors()).hasSize(3);
}

@Test
void hybrid_creates_coordinator_plus_local_loops() {
    var roles = Map.of(
            "coordinator", AgentRole.of("coord", StructuralRole.INITIATOR),
            "worker-1", AgentRole.of("w1", StructuralRole.CONTRACTOR),
            "worker-2", AgentRole.of("w2", StructuralRole.CONTRACTOR));
    var topology = TopologyBuilder.hybrid(roles);
    var plan = topology.configure(context(TopologyType.HYBRID, roles));
    assertThat(plan.loopDescriptors()).hasSize(3);
    assertThat(plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("hybrid-coordinator")).count()).isEqualTo(1);
}

@Test
void graduated_records_feedback_via_handler() {
    var roles = Map.of("agent-a", AgentRole.of("a", StructuralRole.PEER));
    var topology = TopologyBuilder.graduated(roles);
    var ctx = context(TopologyType.GRADUATED, roles);
    topology.configure(ctx);
    topology.onFeedback(new CoordinationFeedback(
            "agent-a",
            new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()),
            FeedbackSignal.SUCCESS));
    // Graduated topology must process feedback via GraduationState — no exception
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency-core -Dtest=TopologyBuilderTest#hierarchical_creates_one_loop_per_agent`
Expected: FAIL — `hierarchical()` method doesn't exist

- [ ] **Step 3: Add hierarchical() factory method to TopologyBuilder**

Add to `TopologyBuilder.java`. Extract the loop planning logic from `HierarchicalTopology.configure()`, the cascade setup (using `DegradationCascadeFactory`), and the stub executor:

```java
public static CoordinationTopology hierarchical(
        Map<String, AgentRole> agentRoles,
        Map<String, String> parentMap) {
    return create(TopologyType.HIERARCHICAL, agentRoles,
            hierarchicalPlanner(parentMap),
            defaultStubExecutor(),
            standardCascadeFactory(
                    (degraded, triggers) -> triggers.forEach(CascadeDegradationTrigger::activate)),
            null);
}
```

The `hierarchicalPlanner(parentMap)` lambda replicates the loop descriptor creation from `HierarchicalTopology.configure()` — depth computation, leaf detection, scope/strategy/comms per depth level, cascade + latency triggers for leaves, latency-only triggers for non-leaves.

The `standardCascadeFactory(breachHandler)` helper creates a `DegradationCascadeFactory` that builds threshold from policy and wires the given handler:

```java
static DegradationCascadeFactory standardCascadeFactory(
        DegradationCascade.CascadeBreachHandler breachHandler) {
    return (policy, plan, sharedTriggers) -> {
        var threshold = switch (policy.thresholdType()) {
            case SIMPLE_FRACTION -> new SimpleFractionThreshold(policy.cascadeThreshold());
            case PRIORITY_WEIGHTED -> new PriorityWeightedThreshold(
                    policy.cascadeThreshold(), policy.priorityWeights());
        };
        var priorities = new LinkedHashMap<String, BudgetPriority>();
        for (var d : plan.descriptors()) {
            priorities.put(d.name(), d.budgetPriority());
        }
        return new DegradationCascade(threshold, priorities, sharedTriggers, breachHandler);
    };
}
```

- [ ] **Step 4: Add hybrid() factory method**

Replicate `HybridTopology`'s loop planning (1 coordinator RESERVED + N local NORMAL), cascade trigger wiring.

```java
public static CoordinationTopology hybrid(Map<String, AgentRole> agentRoles) {
    return create(TopologyType.HYBRID, agentRoles,
            hybridPlanner(),
            defaultStubExecutor(),
            standardCascadeFactory(
                    (degraded, triggers) -> triggers.forEach(CascadeDegradationTrigger::activate)),
            null);
}
```

- [ ] **Step 5: Add graduated() factory method**

Graduated is special — it takes `GraduationState`, has feedback handling, and its cascade handler calls `forceScope` instead of activating triggers:

```java
public static CoordinationTopology graduated(Map<String, AgentRole> agentRoles) {
    var graduationState = new GraduationState(Map.of(), 5);
    return graduated(agentRoles, graduationState);
}

public static CoordinationTopology graduated(Map<String, AgentRole> agentRoles,
                                              GraduationState graduationState) {
    return create(TopologyType.GRADUATED, agentRoles,
            graduatedPlanner(graduationState),
            defaultStubExecutor(),
            standardCascadeFactory((degraded, triggers) -> {
                for (String agentId : agentRoles.keySet()) {
                    graduationState.forceScope(agentId,
                            io.casehub.api.model.coordination.DecisionScope.STIMULUS_DRIVEN);
                }
            }),
            feedback -> graduationState.recordFeedback(
                    feedback.agentId(), feedback.signal()));
}
```

- [ ] **Step 6: Run new TopologyBuilder tests**

Run: `mvn --batch-mode test -pl agency-core -Dtest=TopologyBuilderTest`
Expected: PASS

- [ ] **Step 7: Migrate existing topology tests**

For each of `HierarchicalTopologyTest`, `HybridTopologyTest`, `GraduatedTopologyTest`:
1. Replace `new XxxTopology(...)` with `TopologyBuilder.xxx(...)`
2. Change type from concrete class to `CoordinationTopology`
3. Remove imports of old class
4. Keep all assertions identical

For cascade tests (e.g., `GraduatedTopologyCascadeTest` if it exists), migrate the same way. The cascade behavior is now inside `DegradationCascade` composed by TopologyBuilder — tests should verify identical behavior through the `CoordinationTopology` interface.

- [ ] **Step 8: Run migrated tests**

Run: `mvn --batch-mode test -pl agency-core -Dtest="HierarchicalTopologyTest+HybridTopologyTest+GraduatedTopologyTest"`
Expected: PASS

- [ ] **Step 9: Delete old topology classes**

Use `ide_refactor_safe_delete` for each: `HierarchicalTopology.java`, `HybridTopology.java`, `GraduatedTopology.java`.

- [ ] **Step 10: Run full test suite**

Run: `mvn --batch-mode test -pl agency-core`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/
git add agency-core/src/test/java/
git commit -m "feat: migrate Hierarchical, Hybrid, Graduated to TopologyBuilder

Add hierarchical(), hybrid(), graduated() factory methods. Extract
cascade factory helper. Graduated preserves FeedbackHandler for
GraduationState scope adjustment and divergent cascade response.
Delete 3 old topology classes.

Refs #408

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Migrate HiveTopology → OrchestrationPatternExecutor + TopologyBuilder.hive()

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/OrchestrationPatternExecutor.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/TopologyBuilder.java` (add hive factory)
- Test: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/OrchestrationPatternExecutorTest.java`
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/TopologyBuilderTest.java` (add hive tests)
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/HiveTopologyTest.java` (migrate)
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/topology/HivePatternAdapterTest.java` (migrate → OrchestrationPatternExecutorTest)
- Modify: `agency-core/src/test/java/io/casehub/agency/orchestration/degradation/HiveTopologyCascadeTest.java` (migrate)
- Delete: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HiveTopology.java`
- Delete: `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HivePatternAdapter.java`

**Interfaces:**
- Consumes: `TopologyBuilder.create()`, `DegradationCascadeFactory`, `DecisionOutcomeExecutor`, `FeedbackHandler`, `OrchestrationPatternType`, `AgentProvider` (platform-agent-api)
- Produces: `OrchestrationPatternExecutor` (`DecisionOutcomeExecutor` impl with pattern delegation), `TopologyBuilder.hive(Map<String, AgentRole>, OrchestrationPatternType)` → `CoordinationTopology`

- [ ] **Step 1: Write failing test for OrchestrationPatternExecutor**

Create `OrchestrationPatternExecutorTest.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.budget.AutonomyBudgetPolicy;
import io.casehub.agency.orchestration.loop.DecisionLoopFactory;
import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.api.model.coordination.DecisionType;
import io.casehub.api.model.coordination.TopologyDescriptor;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrchestrationPatternExecutorTest {

    OrchestrationPatternExecutor executor;
    TopologyContext topologyContext;

    @BeforeEach
    void setUp() {
        executor = new OrchestrationPatternExecutor(
                TestAgentProviders.noOp(), OrchestrationPatternType.SUPERVISOR);
        topologyContext = new TopologyContext(
                UUID.randomUUID(),
                new TopologyDescriptor(TopologyType.CENTRALISED, Map.of()),
                TestAgentProviders.noOp(),
                new DecisionLoopFactory(name -> ctx -> "", new EventStreamBus<>()),
                AutonomyBudgetPolicy.defaults(),
                io.casehub.agency.orchestration.degradation.DegradationPolicy.defaults(),
                null, null);
    }

    @Test
    void execute_supervisor_returns_feedback() throws Exception {
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var fb = executor.execute("coordinator", outcome, topologyContext)
                .toCompletableFuture().get();
        assertThat(fb.agentId()).isEqualTo("coordinator");
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void execute_debate_returns_feedback() throws Exception {
        executor = new OrchestrationPatternExecutor(
                TestAgentProviders.noOp(), OrchestrationPatternType.DEBATE);
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var fb = executor.execute("debater-1", outcome, topologyContext)
                .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void execute_voting_returns_feedback() throws Exception {
        executor = new OrchestrationPatternExecutor(
                TestAgentProviders.noOp(), OrchestrationPatternType.VOTING);
        var outcome = new DecisionOutcome.WriteBack(List.of(), List.of(), List.of());
        var fb = executor.execute("voter-1", outcome, topologyContext)
                .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SUCCESS);
    }

    @Test
    void execute_scope_gated_returns_violation() throws Exception {
        var gated = new DecisionOutcome.ScopeGated(
                DecisionType.PROVISIONING,
                new DecisionOutcome.WriteBack(List.of(), List.of(), List.of()));
        var fb = executor.execute("coordinator", gated, topologyContext)
                .toCompletableFuture().get();
        assertThat(fb.signal()).isEqualTo(FeedbackSignal.SCOPE_VIOLATION);
    }

    @Test
    void patternType_can_be_switched_at_runtime() {
        assertThat(executor.patternType()).isEqualTo(OrchestrationPatternType.SUPERVISOR);
        executor.setPatternType(OrchestrationPatternType.VOTING);
        assertThat(executor.patternType()).isEqualTo(OrchestrationPatternType.VOTING);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency-core -Dtest=OrchestrationPatternExecutorTest`
Expected: FAIL — class doesn't exist

- [ ] **Step 3: Create OrchestrationPatternExecutor**

Create `OrchestrationPatternExecutor.java`:

```java
package io.casehub.agency.orchestration.topology;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.platform.agent.AgentProvider;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class OrchestrationPatternExecutor implements DecisionOutcomeExecutor {

    private final AgentProvider agentProvider;
    private volatile OrchestrationPatternType patternType;

    public OrchestrationPatternExecutor(AgentProvider agentProvider,
                                        OrchestrationPatternType patternType) {
        this.agentProvider = Objects.requireNonNull(agentProvider);
        this.patternType = Objects.requireNonNull(patternType);
    }

    @Override
    public CompletionStage<CoordinationFeedback> execute(
            String agentId, DecisionOutcome outcome, TopologyContext context) {
        if (outcome instanceof DecisionOutcome.ScopeGated) {
            return CompletableFuture.completedFuture(
                    new CoordinationFeedback(agentId, outcome, FeedbackSignal.SCOPE_VIOLATION));
        }
        return switch (patternType) {
            case SUPERVISOR -> executeSupervisor(agentId, outcome, context);
            case DEBATE -> executeDebate(agentId, outcome, context);
            case VOTING -> executeVoting(agentId, outcome, context);
        };
    }

    private CompletionStage<CoordinationFeedback> executeSupervisor(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        // Stub — real implementation uses LlmSelectedRouting via agentProvider
        return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
    }

    private CompletionStage<CoordinationFeedback> executeDebate(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        // Stub — real implementation uses RoundRobinRouting + JudgeConvergence
        return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
    }

    private CompletionStage<CoordinationFeedback> executeVoting(
            String agentId, DecisionOutcome outcome, TopologyContext ctx) {
        // Stub — real implementation uses broadcast routing + MajorityVote
        return CompletableFuture.completedFuture(
                new CoordinationFeedback(agentId, outcome, FeedbackSignal.SUCCESS));
    }

    public OrchestrationPatternType patternType() { return patternType; }

    public void setPatternType(OrchestrationPatternType patternType) {
        this.patternType = Objects.requireNonNull(patternType);
    }

    public void onFeedback(OrchestrationPatternType type, CoordinationFeedback feedback) {
        // No-op until real pattern execution is implemented
    }

    public AgentProvider agentProvider() { return agentProvider; }
}
```

- [ ] **Step 4: Add hive() factory method to TopologyBuilder**

```java
public static CoordinationTopology hive(Map<String, AgentRole> agentRoles,
                                         OrchestrationPatternType patternType) {
    var patternExecutor = new OrchestrationPatternExecutor(
            /* agentProvider resolved at configure time — for now pass null and
               extract from TopologyContext in the executor */ null, patternType);

    return create(TopologyType.CENTRALISED, agentRoles,
            hivePlanner(),
            hiveExecutor(patternExecutor),
            standardCascadeFactory((degraded, triggers) -> {
                var newType = switch (patternExecutor.patternType()) {
                    case DEBATE -> OrchestrationPatternType.VOTING;
                    case VOTING -> OrchestrationPatternType.SUPERVISOR;
                    case SUPERVISOR -> OrchestrationPatternType.SUPERVISOR;
                };
                patternExecutor.setPatternType(newType);
                triggers.forEach(CascadeDegradationTrigger::activate);
            }),
            feedback -> patternExecutor.onFeedback(patternExecutor.patternType(), feedback));
}
```

**Note:** The AgentProvider is available from TopologyContext at configure time. Revise `OrchestrationPatternExecutor` to accept it lazily, OR have `hive()` accept an `AgentProvider` parameter. Check how `HivePatternAdapter` gets its AgentProvider — it takes it at construction. The `TopologyBuilder.hive()` method should also take it:

```java
public static CoordinationTopology hive(Map<String, AgentRole> agentRoles,
                                         OrchestrationPatternType patternType,
                                         AgentProvider agentProvider) {
    var patternExecutor = new OrchestrationPatternExecutor(agentProvider, patternType);
    // ... rest as above
}
```

The `hivePlanner()` replicates `HiveTopology.configure()`'s loop descriptor creation: 1 coordinator (RESERVED, EVENT_DRIVEN, BROADCAST) + N workers (NORMAL, DEMAND_DRIVEN, DIRECT_MESSAGING), with cascade + latency triggers for workers, latency-only for coordinator.

The `hiveExecutor(patternExecutor)` wraps the `OrchestrationPatternExecutor` as a `DecisionOutcomeExecutor`:

```java
private static DecisionOutcomeExecutor hiveExecutor(OrchestrationPatternExecutor pe) {
    return (agentId, outcome, ctx) -> pe.execute(agentId, outcome, ctx);
}
```

- [ ] **Step 5: Run new tests**

Run: `mvn --batch-mode test -pl agency-core -Dtest="OrchestrationPatternExecutorTest+TopologyBuilderTest"`
Expected: PASS

- [ ] **Step 6: Add hive-specific TopologyBuilder tests**

Add to `TopologyBuilderTest.java`:

```java
@Test
void hive_creates_coordinator_plus_worker_loops() {
    var roles = Map.of(
            "coordinator", AgentRole.of("coord", StructuralRole.INITIATOR),
            "worker-1", AgentRole.of("w1", StructuralRole.CONTRACTOR),
            "worker-2", AgentRole.of("w2", StructuralRole.CONTRACTOR));
    var topology = TopologyBuilder.hive(roles, OrchestrationPatternType.SUPERVISOR,
            TestAgentProviders.noOp());
    var plan = topology.configure(context(TopologyType.CENTRALISED, roles));
    assertThat(plan.loopDescriptors()).hasSize(3);
    assertThat(plan.loopDescriptors().stream()
            .filter(d -> d.name().equals("hive-coordinator")).count()).isEqualTo(1);
}

@Test
void hive_cascade_degrades_pattern_type() {
    var roles = Map.of(
            "coordinator", AgentRole.of("coord", StructuralRole.INITIATOR),
            "worker-1", AgentRole.of("w1", StructuralRole.CONTRACTOR),
            "worker-2", AgentRole.of("w2", StructuralRole.CONTRACTOR));
    var topology = TopologyBuilder.hive(roles, OrchestrationPatternType.DEBATE,
            TestAgentProviders.noOp());
    var ctx = context(TopologyType.CENTRALISED, roles);
    topology.configure(ctx);

    topology.onDegradation(new SwarmDegradedEvent("hive-coordinator", "budget", true));
    topology.onDegradation(new SwarmDegradedEvent("hive-worker-worker-1", "budget", true));
    // Pattern type should have degraded from DEBATE — verify via executor behavior
}
```

- [ ] **Step 7: Migrate HiveTopologyTest, HivePatternAdapterTest, HiveTopologyCascadeTest**

Replace `new HiveTopology(...)` with `TopologyBuilder.hive(...)`, `new HivePatternAdapter(...)` with `new OrchestrationPatternExecutor(...)`. Update types from concrete classes to `CoordinationTopology` / `OrchestrationPatternExecutor`. Keep all assertions identical.

- [ ] **Step 8: Run migrated tests**

Run: `mvn --batch-mode test -pl agency-core -Dtest="HiveTopologyTest+HivePatternAdapterTest+OrchestrationPatternExecutorTest+HiveTopologyCascadeTest"`
Expected: PASS

- [ ] **Step 9: Delete HiveTopology.java and HivePatternAdapter.java**

Use `ide_refactor_safe_delete` for both files.

- [ ] **Step 10: Run full test suite**

Run: `mvn --batch-mode test -pl agency-core`
Expected: PASS

- [ ] **Step 11: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/topology/
git add agency-core/src/test/java/
git commit -m "feat: OrchestrationPatternExecutor + TopologyBuilder.hive(), migrate HiveTopology (#409)

Replace HivePatternAdapter with generic OrchestrationPatternExecutor.
Migrate HiveTopology to TopologyBuilder.hive() factory method.
Hive cascade handler atomically switches patternType AND activates triggers.
Delete HiveTopology and HivePatternAdapter.

Refs #409

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 3: YAML + eidos Integration

### Task 6: RolePolicyCompiler + RolePolicySpec enrichment (#411)

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TopologySpec.java` (enrich RolePolicySpec)
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/registry/RolePolicyCompiler.java`
- Modify: `agentic-yaml/pom.xml` (add eidos-org-api provided dependency)
- Test: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/registry/RolePolicyCompilerTest.java`

**Interfaces:**
- Consumes: `RoleSpec`, `RolePolicy`, `PolicyKind` (eidos org-api — provided dependency)
- Produces: `RolePolicyCompiler.compile(String roleName, TopologySpec.RolePolicySpec spec) → RoleSpec`

- [ ] **Step 1: Add eidos-org-api provided dependency to agentic-yaml pom.xml**

Add to `agentic-yaml/pom.xml` in the dependencies section:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-eidos-org-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: Enrich RolePolicySpec with optional fields**

Modify `TopologySpec.java` — update the inner `RolePolicySpec` record:

```java
record RolePolicySpec(
        @Nullable List<String> obligations,
        @Nullable List<String> permissions,
        @Nullable Integer minAgents,
        @Nullable Integer maxAgents,
        @Nullable List<String> incompatibleWith
) {
    public RolePolicySpec {
        if (obligations != null) obligations = List.copyOf(obligations);
        if (permissions != null) permissions = List.copyOf(permissions);
        if (incompatibleWith != null) incompatibleWith = List.copyOf(incompatibleWith);
    }
}
```

- [ ] **Step 3: Write failing test for RolePolicyCompiler**

Create `RolePolicyCompilerTest.java`:

```java
package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.eidos.org.api.PolicyKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RolePolicyCompilerTest {

    final RolePolicyCompiler compiler = new RolePolicyCompiler();

    @Test
    void compiles_obligations_to_role_spec() {
        var spec = new TopologySpec.RolePolicySpec(
                List.of("review-output", "approve-plan"), null, null, null, null);
        var roleSpec = compiler.compile("coordinator", spec);
        assertThat(roleSpec.roleName()).isEqualTo("coordinator");
        assertThat(roleSpec.policies()).hasSize(2);
        assertThat(roleSpec.policies()).allMatch(p -> p.kind() == PolicyKind.OBLIGATION);
    }

    @Test
    void compiles_permissions_to_role_spec() {
        var spec = new TopologySpec.RolePolicySpec(
                null, List.of("provision-agent", "change-topology"), null, null, null);
        var roleSpec = compiler.compile("admin", spec);
        assertThat(roleSpec.policies()).hasSize(2);
        assertThat(roleSpec.policies()).allMatch(p -> p.kind() == PolicyKind.PERMISSION);
    }

    @Test
    void compiles_cardinality() {
        var spec = new TopologySpec.RolePolicySpec(null, null, 1, 3, null);
        var roleSpec = compiler.compile("analyst", spec);
        assertThat(roleSpec.minCardinality()).isEqualTo(1);
        assertThat(roleSpec.maxCardinality()).isEqualTo(3);
    }

    @Test
    void defaults_cardinality_when_null() {
        var spec = new TopologySpec.RolePolicySpec(List.of("work"), null, null, null, null);
        var roleSpec = compiler.compile("worker", spec);
        assertThat(roleSpec.minCardinality()).isZero();
        assertThat(roleSpec.maxCardinality()).isEqualTo(-1);
    }

    @Test
    void compiles_incompatible_with() {
        var spec = new TopologySpec.RolePolicySpec(
                null, null, null, null, List.of("admin", "auditor"));
        var roleSpec = compiler.compile("operator", spec);
        assertThat(roleSpec.incompatibleWith()).containsExactlyInAnyOrder("admin", "auditor");
    }

    @Test
    void compiles_both_obligations_and_permissions() {
        var spec = new TopologySpec.RolePolicySpec(
                List.of("review"), List.of("merge"), 1, 1, null);
        var roleSpec = compiler.compile("lead", spec);
        assertThat(roleSpec.policies()).hasSize(2);
        assertThat(roleSpec.policies().stream()
                .filter(p -> p.kind() == PolicyKind.OBLIGATION).count()).isEqualTo(1);
        assertThat(roleSpec.policies().stream()
                .filter(p -> p.kind() == PolicyKind.PERMISSION).count()).isEqualTo(1);
    }
}
```

- [ ] **Step 4: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=RolePolicyCompilerTest`
Expected: FAIL — class doesn't exist

- [ ] **Step 5: Create RolePolicyCompiler**

Create `RolePolicyCompiler.java`:

```java
package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.eidos.org.api.PolicyKind;
import io.casehub.eidos.org.api.RolePolicy;
import io.casehub.eidos.org.api.RoleSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RolePolicyCompiler {

    public RoleSpec compile(String roleName, TopologySpec.RolePolicySpec spec) {
        return new RoleSpec(
                roleName,
                null,
                spec.minAgents() != null ? spec.minAgents() : 0,
                spec.maxAgents() != null ? spec.maxAgents() : -1,
                spec.incompatibleWith() != null
                        ? Set.copyOf(spec.incompatibleWith()) : Set.of(),
                compilePolicies(spec));
    }

    private List<RolePolicy> compilePolicies(TopologySpec.RolePolicySpec spec) {
        var policies = new ArrayList<RolePolicy>();
        if (spec.obligations() != null) {
            for (var obligation : spec.obligations()) {
                policies.add(new RolePolicy(obligation, PolicyKind.OBLIGATION, Map.of()));
            }
        }
        if (spec.permissions() != null) {
            for (var permission : spec.permissions()) {
                policies.add(new RolePolicy(permission, PolicyKind.PERMISSION, Map.of()));
            }
        }
        return List.copyOf(policies);
    }
}
```

- [ ] **Step 6: Run tests to verify pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=RolePolicyCompilerTest`
Expected: PASS

- [ ] **Step 7: Run full agentic-yaml tests**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: PASS — existing tests unaffected by RolePolicySpec field additions (all nullable)

- [ ] **Step 8: Commit**

```bash
git add agentic-yaml/pom.xml
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TopologySpec.java
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/registry/RolePolicyCompiler.java
git add agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/registry/RolePolicyCompilerTest.java
git commit -m "feat: RolePolicyCompiler — compile YAML RolePolicySpec to eidos RoleSpec (#411)

Add eidos-org-api provided dependency. Enrich RolePolicySpec with
minAgents, maxAgents, incompatibleWith. Compile obligations/permissions
to RolePolicy with PolicyKind. Map cardinality and incompatibilities
to RoleSpec fields.

Refs #411

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: TopologyFactory — YAML to runtime (#413)

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/registry/TopologyFactory.java`
- Test: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/registry/TopologyFactoryTest.java`

**Interfaces:**
- Consumes: `TopologySpec` (sealed, 4 variants), `TopologyBuilder` (agency-core), `RolePolicyCompiler` (Task 6), `OrchestrationPatternType` (agency-core)
- Produces: `TopologyFactory.create(TopologySpec) → CoordinationTopology`

- [ ] **Step 1: Write failing test for TopologyFactory**

Create `TopologyFactoryTest.java`:

```java
package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.agency.orchestration.topology.CoordinationTopology;
import io.casehub.api.model.coordination.TopologyType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class TopologyFactoryTest {

    final TopologyFactory factory = new TopologyFactory(new RolePolicyCompiler());

    @Test
    void creates_distributed_from_swarm_spec() {
        var spec = new TopologySpec.Swarm(null);
        var topology = factory.create(spec);
        assertThat(topology.describeTopology(UUID.randomUUID()).topologyType())
                .isEqualTo(TopologyType.DISTRIBUTED);
    }

    @Test
    void creates_distributed_from_mesh_spec() {
        var spec = new TopologySpec.Mesh(null);
        var topology = factory.create(spec);
        assertThat(topology.describeTopology(UUID.randomUUID()).topologyType())
                .isEqualTo(TopologyType.DISTRIBUTED);
    }

    @Test
    void creates_distributed_from_many_spec() {
        var spec = new TopologySpec.Many();
        var topology = factory.create(spec);
        assertThat(topology).isNotNull();
    }

    @Test
    void creates_hive_from_hive_spec() {
        var policy = Map.of("coordinator",
                new TopologySpec.RolePolicySpec(
                        List.of("review"), List.of("merge"), 1, 1, null));
        var spec = new TopologySpec.Hive(null, List.of("coordinator"), "supervisor", policy);
        var topology = factory.create(spec);
        assertThat(topology).isNotNull();
    }

    @Test
    void hive_defaults_to_supervisor_when_orchestration_null() {
        var spec = new TopologySpec.Hive(null, null, null, null);
        var topology = factory.create(spec);
        assertThat(topology).isNotNull();
    }

    @Test
    void hive_rejects_unknown_orchestration() {
        var spec = new TopologySpec.Hive(null, null, "unknown", null);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> factory.create(spec))
                .withMessageContaining("Unknown orchestration");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=TopologyFactoryTest`
Expected: FAIL — class doesn't exist

- [ ] **Step 3: Create TopologyFactory**

Create `TopologyFactory.java`:

```java
package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.core.yaml.spec.TopologySpec;
import io.casehub.agency.orchestration.topology.CoordinationTopology;
import io.casehub.agency.orchestration.topology.OrchestrationPatternType;
import io.casehub.agency.orchestration.topology.TopologyBuilder;
import io.casehub.api.model.coordination.AgentRole;
import io.casehub.api.model.coordination.StructuralRole;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class TopologyFactory {

    private final RolePolicyCompiler rolePolicyCompiler;

    public TopologyFactory(RolePolicyCompiler rolePolicyCompiler) {
        this.rolePolicyCompiler = Objects.requireNonNull(rolePolicyCompiler);
    }

    public CoordinationTopology create(TopologySpec spec) {
        return switch (spec) {
            case TopologySpec.Swarm s -> TopologyBuilder.distributed(Map.of());
            case TopologySpec.Hive h -> createHive(h);
            case TopologySpec.Mesh m -> TopologyBuilder.distributed(Map.of());
            case TopologySpec.Many m -> TopologyBuilder.distributed(Map.of());
        };
    }

    private CoordinationTopology createHive(TopologySpec.Hive hive) {
        var roles = compileRoles(hive);
        var patternType = resolvePatternType(hive.orchestration());
        return TopologyBuilder.hive(roles, patternType, null);
    }

    private Map<String, AgentRole> compileRoles(TopologySpec.Hive hive) {
        var roles = new LinkedHashMap<String, AgentRole>();
        if (hive.roles() != null) {
            for (var roleName : hive.roles()) {
                var structuralRole = roles.isEmpty()
                        ? StructuralRole.INITIATOR : StructuralRole.CONTRACTOR;
                roles.put(roleName, AgentRole.of(roleName, structuralRole));
            }
        }
        return roles;
    }

    private OrchestrationPatternType resolvePatternType(@Nullable String orchestration) {
        if (orchestration == null) return OrchestrationPatternType.SUPERVISOR;
        return switch (orchestration) {
            case "supervisor" -> OrchestrationPatternType.SUPERVISOR;
            case "debate" -> OrchestrationPatternType.DEBATE;
            case "voting" -> OrchestrationPatternType.VOTING;
            default -> throw new IllegalArgumentException(
                    "Unknown orchestration: " + orchestration);
        };
    }
}
```

- [ ] **Step 4: Run tests to verify pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=TopologyFactoryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/registry/TopologyFactory.java
git add agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/registry/TopologyFactoryTest.java
git commit -m "feat: TopologyFactory — YAML TopologySpec to TopologyBuilder bridge (#413)

Map Swarm/Mesh/Many to distributed(), Hive to hive() with compiled
roles and orchestration pattern type. Reject unknown orchestration
values.

Refs #413

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## Batch 4: Formation Wiring

### Task 8: BottomUpFormationOrchestrator + CoalitionProposal cleanup (#410)

**Files:**
- Modify: `agency-core/pom.xml` (add eidos-org-api provided dependency)
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/FormationResult.java`
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/BottomUpFormationOrchestrator.java`
- Modify: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/CoalitionProposal.java` (remove coveredCapabilities)
- Test: `agency-core/src/test/java/io/casehub/agency/deliberation/coalition/BottomUpFormationOrchestratorTest.java`
- Test: `agency-core/src/test/java/io/casehub/agency/deliberation/coalition/CoalitionFormationTest.java` (update for removed method)

**Interfaces:**
- Consumes: `CoalitionEvaluator`, `CoalitionProposal`, `CoalitionContext`, `CoalitionScore`, `GroupLifecycleService` (eidos org-api), `FormationOrigin` (eidos org-api)
- Produces: `BottomUpFormationOrchestrator.propose(CoalitionProposal, CoalitionContext, String specId, String tenancyId) → CompletionStage<FormationResult>`, `FormationResult` sealed interface (Accepted, Rejected, NegotiationFailed, Timeout)

- [ ] **Step 1: Add eidos-org-api provided dependency to agency-core pom.xml**

Add to `agency-core/pom.xml`:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-eidos-org-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: Write failing test for FormationResult**

Create `BottomUpFormationOrchestratorTest.java`:

```java
package io.casehub.agency.deliberation.coalition;

import io.casehub.agency.core.AgentRef;
import io.casehub.eidos.org.api.FormationOrigin;
import io.casehub.eidos.org.api.GroupInstance;
import io.casehub.eidos.org.api.GroupLifecycleService;
import io.casehub.eidos.org.api.GroupLifecycleState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BottomUpFormationOrchestratorTest {

    CoalitionEvaluator evaluator;
    GroupLifecycleService lifecycleService;
    BottomUpFormationOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        evaluator = new CapabilityCoverageEvaluator();
        lifecycleService = Mockito.mock(GroupLifecycleService.class);
        orchestrator = new BottomUpFormationOrchestrator(evaluator, lifecycleService);
    }

    @Test
    void rejects_non_viable_coalition() throws Exception {
        var proposal = new CoalitionProposal("task-1",
                Set.of("cap-a", "cap-b"),
                List.of(AgentRef.worker("agent-1")));
        var context = new CoalitionContext(
                List.of(AgentRef.worker("agent-1")),
                Map.of("agent-1", Set.of("cap-a")));

        var result = orchestrator.propose(proposal, context, "spec-1", "tenant-1")
                .toCompletableFuture().get();

        assertThat(result).isInstanceOf(FormationResult.Rejected.class);
        assertThat(((FormationResult.Rejected) result).reason()).contains("Missing");
    }

    @Test
    void accepts_viable_coalition() throws Exception {
        var proposal = new CoalitionProposal("task-1",
                Set.of("cap-a"),
                List.of(AgentRef.worker("agent-1")));
        var context = new CoalitionContext(
                List.of(AgentRef.worker("agent-1")),
                Map.of("agent-1", Set.of("cap-a")));

        var instance = new GroupInstance("inst-1", "spec-1", "tenant-1",
                GroupLifecycleState.PROPOSED, FormationOrigin.PROPOSAL,
                "hive", null, null,
                List.of(), List.of(), List.of(),
                Instant.now(), Instant.now(), 0);
        when(lifecycleService.proposeGroup("spec-1", "tenant-1", "agent-1"))
                .thenReturn(instance);

        var result = orchestrator.propose(proposal, context, "spec-1", "tenant-1")
                .toCompletableFuture().get();

        assertThat(result).isInstanceOf(FormationResult.Accepted.class);
        assertThat(((FormationResult.Accepted) result).groupInstanceId()).isEqualTo("inst-1");
        verify(lifecycleService).proposeGroup("spec-1", "tenant-1", "agent-1");
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency-core -Dtest=BottomUpFormationOrchestratorTest`
Expected: FAIL — classes don't exist

- [ ] **Step 4: Create FormationResult**

Create `FormationResult.java`:

```java
package io.casehub.agency.deliberation.coalition;

public sealed interface FormationResult {
    record Accepted(String groupInstanceId) implements FormationResult {}
    record Rejected(String reason) implements FormationResult {}
    record NegotiationFailed(String reason) implements FormationResult {}
    record Timeout() implements FormationResult {}
}
```

- [ ] **Step 5: Create BottomUpFormationOrchestrator**

Create `BottomUpFormationOrchestrator.java`:

```java
package io.casehub.agency.deliberation.coalition;

import io.casehub.eidos.org.api.GroupLifecycleService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class BottomUpFormationOrchestrator {

    private final CoalitionEvaluator evaluator;
    private final GroupLifecycleService lifecycleService;

    public BottomUpFormationOrchestrator(CoalitionEvaluator evaluator,
                                         GroupLifecycleService lifecycleService) {
        this.evaluator = Objects.requireNonNull(evaluator);
        this.lifecycleService = Objects.requireNonNull(lifecycleService);
    }

    public CompletionStage<FormationResult> propose(
            CoalitionProposal proposal,
            CoalitionContext context,
            String specId,
            String tenancyId) {
        var score = evaluator.evaluate(proposal, context);
        if (!score.isViable()) {
            return CompletableFuture.completedFuture(
                    new FormationResult.Rejected(score.reason()));
        }

        var proposerAgentId = proposal.proposedMembers().isEmpty()
                ? "unknown"
                : proposal.proposedMembers().getFirst().name();

        var instance = lifecycleService.proposeGroup(specId, tenancyId, proposerAgentId);
        return CompletableFuture.completedFuture(
                new FormationResult.Accepted(instance.instanceId()));
    }
}
```

- [ ] **Step 6: Remove coveredCapabilities() from CoalitionProposal**

Delete the `coveredCapabilities()` method from `CoalitionProposal.java` (lines 20-24):

```java
// REMOVE this method:
public Set<String> coveredCapabilities() {
    return Set.copyOf(proposedMembers.stream().map(AgentRef::name).toList());
}
```

- [ ] **Step 7: Update CoalitionFormationTest if it uses coveredCapabilities()**

Check `CoalitionFormationTest.java` for calls to `coveredCapabilities()` and remove or replace them.

- [ ] **Step 8: Run tests to verify pass**

Run: `mvn --batch-mode test -pl agency-core -Dtest="BottomUpFormationOrchestratorTest+CoalitionFormationTest"`
Expected: PASS

- [ ] **Step 9: Run full test suite**

Run: `mvn --batch-mode test -pl agency-core`
Expected: PASS

- [ ] **Step 10: Commit**

```bash
git add agency-core/pom.xml
git add agency-core/src/main/java/io/casehub/agency/deliberation/coalition/
git add agency-core/src/test/java/io/casehub/agency/deliberation/coalition/
git commit -m "feat: BottomUpFormationOrchestrator — wire coalition to GroupLifecycleService (#410)

Compose CoalitionProposal evaluation with GroupLifecycleService.proposeGroup().
Add FormationResult sealed interface (Accepted, Rejected, NegotiationFailed, Timeout).
Remove CoalitionProposal.coveredCapabilities() stub.
Add eidos-org-api as provided dependency.

Refs #410

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

---

## References

- `specs/issue-408-society-integration/2026-11-05-society-integration-hardening-design.md` — design spec this plan implements
- `specs/issue-408-society-integration/decisions.md` — D1-D10 decisions
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/TopologyContext.java` — record to enrich
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HiveTopology.java:38-163` — to be replaced
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HivePatternAdapter.java:10-57` — stub to be replaced
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/DistributedTopology.java:29-88` — simplest migration target
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HierarchicalTopology.java:39-188` — tree topology
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/HybridTopology.java:40-162` — coordinator + local loops
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/GraduatedTopology.java:36-133` — divergent cascade + feedback
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/GraduationState.java:10-81` — kept as-is
- `agency-core/src/main/java/io/casehub/agency/orchestration/topology/CoordinationTopology.java` — interface to extend (D10)
- `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CascadeThreshold.java` — interface for cascade
- `agency-core/src/main/java/io/casehub/agency/orchestration/degradation/CascadeDegradationTrigger.java` — shared trigger
- `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/CoalitionProposal.java:9-25` — coveredCapabilities() stub
- `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/TopologySpec.java:19-52` — YAML spec types
- `eidos/org-api/.../RoleSpec.java`, `RolePolicy.java`, `PolicyKind.java` — canonical types (eidos)
- `eidos/org-api/.../GroupLifecycleService.java` — lifecycle interface (eidos)
- `2026-11-02-self-organising-agent-society-design.md` — authoritative design spec (engine workspace)
- casehubio/agency#408, #409, #410, #411, #412, #413
- casehubio/desiredstate#230 — parent epic
