# FormationActivator Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #417 — FORMING → ACTIVE transition via JointIntention
**Issue group:** #416 (epic), #417 (this issue)

**Goal:** Create a `FormationActivator` that bridges FORMING → ACTIVE
by validating member commitment via `JointIntention`/`IntentionMonitor`
and calling `GroupLifecycleService.activateGroup()`.

**Architecture:** Standalone composition root in the formation package.
Takes `GroupLifecycleService` and optional `IntentionMonitor`. Returns
`ActivationResult` sealed type. Synchronous — all underlying calls are
synchronous.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ

## Global Constraints

- No CDI container in tests — plain JUnit 5 with Mockito
- `GroupLifecycleService` is from eidos-org-api (already provided-scope)
- `JointIntention`, `IntentionMonitor`, `ReconsiderationSignal` are in
  `deliberation.intention` (same module)
- Follow `VouchService` pattern: plain class, consumer constructs

---

## Batch 1: FormationActivator + ActivationResult

### Task 1: ActivationResult sealed type

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/ActivationResult.java`

**Interfaces:**
- Consumes: `JointIntention` from `deliberation.intention`, `ReconsiderationSignal` from `deliberation.intention`
- Produces: `ActivationResult.activated(String, JointIntention)`, `ActivationResult.monitorRejected(ReconsiderationSignal)`, `ActivationResult.activationFailed(String)` — used by Task 2

- [ ] **Step 1: Create ActivationResult**

```java
package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.deliberation.intention.JointIntention;
import io.casehub.agency.deliberation.intention.ReconsiderationSignal;

import java.util.Objects;

public sealed interface ActivationResult {

    record Activated(String groupInstanceId,
                     JointIntention intention) implements ActivationResult {
        public Activated {
            Objects.requireNonNull(groupInstanceId);
            Objects.requireNonNull(intention);
        }
    }

    record MonitorRejected(
            ReconsiderationSignal signal) implements ActivationResult {
        public MonitorRejected {
            Objects.requireNonNull(signal);
        }
    }

    record ActivationFailed(String reason) implements ActivationResult {
        public ActivationFailed {
            Objects.requireNonNull(reason);
        }
    }

    static ActivationResult activated(String groupInstanceId,
                                      JointIntention intention) {
        return new Activated(groupInstanceId, intention);
    }

    static ActivationResult monitorRejected(ReconsiderationSignal signal) {
        return new MonitorRejected(signal);
    }

    static ActivationResult activationFailed(String reason) {
        return new ActivationFailed(reason);
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn --batch-mode compile -pl agency-core`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/ActivationResult.java
git commit -m "feat(formation): add ActivationResult sealed type for activation outcomes

Refs casehubio/agency#417"
```

### Task 2: FormationActivator with tests

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/FormationActivator.java`
- Create: `agency/src/test/java/io/casehub/agency/deliberation/coalition/formation/FormationActivatorTest.java`

**Interfaces:**
- Consumes: `ActivationResult` from Task 1, `GroupLifecycleService.activateGroup(String, String)` from eidos-org-api, `IntentionMonitor.evaluate(JointIntention)` from `deliberation.intention`, `JointIntention.form(String, String, Set<String>, Instant)` and `JointIntention.activate()` from `deliberation.intention`
- Produces: `FormationActivator(GroupLifecycleService, @Nullable IntentionMonitor)`, `FormationActivator.activate(String, Set<String>, String, String) → ActivationResult`

- [ ] **Step 1: Write failing test — happy path without monitor**

```java
package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.deliberation.intention.IntentionStatus;
import io.casehub.eidos.org.api.GroupLifecycleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class FormationActivatorTest {

    private GroupLifecycleService groupLifecycle;

    @BeforeEach
    void setUp() {
        groupLifecycle = mock(GroupLifecycleService.class);
    }

    @Test
    void noMonitor_activatesUnconditionally() {
        var activator = new FormationActivator(groupLifecycle, null);

        var result = activator.activate("group-1", Set.of("agent-a", "agent-b"),
                "Execute task", "tenant-1");

        assertThat(result).isInstanceOf(ActivationResult.Activated.class);
        var activated = (ActivationResult.Activated) result;
        assertThat(activated.groupInstanceId()).isEqualTo("group-1");
        assertThat(activated.intention().status()).isEqualTo(IntentionStatus.ACTIVE);
        assertThat(activated.intention().committedParties()).containsExactlyInAnyOrder("agent-a", "agent-b");
        verify(groupLifecycle).activateGroup("group-1", "tenant-1");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=FormationActivatorTest#noMonitor_activatesUnconditionally`
Expected: FAIL — `FormationActivator` class does not exist

- [ ] **Step 3: Write minimal FormationActivator**

```java
package io.casehub.agency.deliberation.coalition.formation;

import io.casehub.agency.deliberation.intention.IntentionMonitor;
import io.casehub.agency.deliberation.intention.JointIntention;
import io.casehub.eidos.org.api.GroupLifecycleService;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public class FormationActivator {

    private final GroupLifecycleService groupLifecycle;
    private final @Nullable IntentionMonitor monitor;

    public FormationActivator(GroupLifecycleService groupLifecycle,
                              @Nullable IntentionMonitor monitor) {
        this.groupLifecycle = Objects.requireNonNull(groupLifecycle);
        this.monitor = monitor;
    }

    public ActivationResult activate(String groupInstanceId,
                                     Set<String> memberNames,
                                     String planDescription,
                                     String tenancyId) {
        Objects.requireNonNull(groupInstanceId);
        Objects.requireNonNull(memberNames);
        Objects.requireNonNull(planDescription);
        Objects.requireNonNull(tenancyId);

        var intention = JointIntention.form(
                groupInstanceId, planDescription, memberNames, Instant.now());

        if (monitor != null) {
            var signal = monitor.evaluate(intention);
            if (signal != null) {
                return ActivationResult.monitorRejected(signal);
            }
        }

        var activated = intention.activate();

        try {
            groupLifecycle.activateGroup(groupInstanceId, tenancyId);
        } catch (Exception e) {
            return ActivationResult.activationFailed(e.getMessage());
        }

        return ActivationResult.activated(groupInstanceId, activated);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=FormationActivatorTest#noMonitor_activatesUnconditionally`
Expected: PASS

- [ ] **Step 5: Add test — monitor approves (returns null)**

```java
@Test
void monitorApproves_activatesGroup() {
    var monitor = mock(IntentionMonitor.class);
    when(monitor.evaluate(any())).thenReturn(null);
    var activator = new FormationActivator(groupLifecycle, monitor);

    var result = activator.activate("group-1", Set.of("agent-a"),
            "Execute task", "tenant-1");

    assertThat(result).isInstanceOf(ActivationResult.Activated.class);
    verify(groupLifecycle).activateGroup("group-1", "tenant-1");
    verify(monitor).evaluate(any());
}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=FormationActivatorTest#monitorApproves_activatesGroup`
Expected: PASS (already implemented)

- [ ] **Step 7: Add test — monitor rejects with shouldDrop**

```java
@Test
void monitorRejects_withShouldDrop_returnsMonitorRejected() {
    var signal = ReconsiderationSignal.drop(
            ReconsiderationReason.PRECONDITION_VIOLATED, "capability lost");
    var monitor = mock(IntentionMonitor.class);
    when(monitor.evaluate(any())).thenReturn(signal);
    var activator = new FormationActivator(groupLifecycle, monitor);

    var result = activator.activate("group-1", Set.of("agent-a"),
            "Execute task", "tenant-1");

    assertThat(result).isInstanceOf(ActivationResult.MonitorRejected.class);
    var rejected = (ActivationResult.MonitorRejected) result;
    assertThat(rejected.signal().shouldDrop()).isTrue();
    assertThat(rejected.signal().reason()).isEqualTo(ReconsiderationReason.PRECONDITION_VIOLATED);
    verifyNoInteractions(groupLifecycle);
}
```

Add these imports to the test class header:

```java
import io.casehub.agency.deliberation.intention.IntentionMonitor;
import io.casehub.agency.deliberation.intention.ReconsiderationReason;
import io.casehub.agency.deliberation.intention.ReconsiderationSignal;
import static org.mockito.ArgumentMatchers.any;
```

- [ ] **Step 8: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=FormationActivatorTest#monitorRejects_withShouldDrop_returnsMonitorRejected`
Expected: PASS

- [ ] **Step 9: Add test — monitor reconsiders (shouldDrop=false) still blocks**

```java
@Test
void monitorReconsiders_withoutShouldDrop_stillRejects() {
    var signal = ReconsiderationSignal.reconsider(
            ReconsiderationReason.CONTEXT_CHANGED, "environment shifted");
    var monitor = mock(IntentionMonitor.class);
    when(monitor.evaluate(any())).thenReturn(signal);
    var activator = new FormationActivator(groupLifecycle, monitor);

    var result = activator.activate("group-1", Set.of("agent-a"),
            "Execute task", "tenant-1");

    assertThat(result).isInstanceOf(ActivationResult.MonitorRejected.class);
    assertThat(((ActivationResult.MonitorRejected) result).signal().shouldDrop()).isFalse();
    verifyNoInteractions(groupLifecycle);
}
```

- [ ] **Step 10: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=FormationActivatorTest#monitorReconsiders_withoutShouldDrop_stillRejects`
Expected: PASS

- [ ] **Step 11: Add test — activateGroup failure**

```java
@Test
void activateGroupFailure_returnsActivationFailed() {
    doThrow(new RuntimeException("group not found"))
            .when(groupLifecycle).activateGroup(any(), any());
    var activator = new FormationActivator(groupLifecycle, null);

    var result = activator.activate("group-1", Set.of("agent-a"),
            "Execute task", "tenant-1");

    assertThat(result).isInstanceOf(ActivationResult.ActivationFailed.class);
    assertThat(((ActivationResult.ActivationFailed) result).reason())
            .isEqualTo("group not found");
}
```

- [ ] **Step 12: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agency -Dtest=FormationActivatorTest#activateGroupFailure_returnsActivationFailed`
Expected: PASS

- [ ] **Step 13: Add test — intention fields are correct**

```java
@Test
void activatedIntention_hasCorrectFields() {
    var activator = new FormationActivator(groupLifecycle, null);
    var members = Set.of("agent-a", "agent-b", "agent-c");

    var result = activator.activate("group-42", members,
            "Coalition for analysis", "tenant-1");

    assertThat(result).isInstanceOf(ActivationResult.Activated.class);
    var activated = (ActivationResult.Activated) result;
    assertThat(activated.intention().intentionId()).isEqualTo("group-42");
    assertThat(activated.intention().planDescription()).isEqualTo("Coalition for analysis");
    assertThat(activated.intention().committedParties()).isEqualTo(members);
    assertThat(activated.intention().status()).isEqualTo(IntentionStatus.ACTIVE);
    assertThat(activated.intention().formedAt()).isNotNull();
    assertThat(activated.intention().dropReason()).isNull();
}
```

- [ ] **Step 14: Add test — null parameter validation**

```java
@Test
void nullGroupInstanceId_throwsNPE() {
    var activator = new FormationActivator(groupLifecycle, null);

    assertThatThrownBy(() -> activator.activate(null, Set.of("a"), "plan", "t"))
            .isInstanceOf(NullPointerException.class);
}

@Test
void nullMemberNames_throwsNPE() {
    var activator = new FormationActivator(groupLifecycle, null);

    assertThatThrownBy(() -> activator.activate("g", null, "plan", "t"))
            .isInstanceOf(NullPointerException.class);
}

@Test
void nullConstructorGroupLifecycle_throwsNPE() {
    assertThatThrownBy(() -> new FormationActivator(null, null))
            .isInstanceOf(NullPointerException.class);
}
```

Add this import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

- [ ] **Step 15: Run all tests**

Run: `mvn --batch-mode test -pl agency -Dtest=FormationActivatorTest`
Expected: All 8 tests PASS

- [ ] **Step 16: Run full module test suite**

Run: `mvn --batch-mode test -pl agency`
Expected: BUILD SUCCESS — no regressions

- [ ] **Step 17: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/deliberation/coalition/formation/FormationActivator.java
git add agency/src/test/java/io/casehub/agency/deliberation/coalition/formation/FormationActivatorTest.java
git commit -m "feat(formation): FormationActivator bridges FORMING → ACTIVE via JointIntention

Creates JointIntention, validates via optional IntentionMonitor, calls
GroupLifecycleService.activateGroup(). Returns ActivationResult sealed type.

Refs casehubio/agency#417"
```

## References

- [2026-11-07-formation-activator-design.md] — design spec this plan implements
- `BottomUpFormationOrchestrator.java` — predecessor, returns FORMING state
- `FormationResult.java` — formation outcome type (not modified)
- `JointIntention.java` — intention lifecycle (form → activate)
- `IntentionMonitor.java` — optional validation SPI
- `ReconsiderationSignal.java` — monitor output with shouldDrop flag
- `GroupLifecycleService.java` — eidos activateGroup() API
- `VouchService.java` — composition root pattern precedent
- `BottomUpFormationOrchestratorTest.java` — test pattern reference
- GitHub #417 — focal issue
- GitHub #416 — parent epic
