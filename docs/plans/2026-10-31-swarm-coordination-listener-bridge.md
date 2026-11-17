# SwarmCoordinationListener Bridge Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #375 — feat: SwarmCoordinationListener bridge — connect engine events to agency LLM coordination
**Issue group:** #375

**Goal:** Create a CDI-managed adapter that implements the engine's `SwarmCoordinationListener` SPI and delegates lifecycle events to agency's `SwarmEventSource`, bridging the engine ↔ agency event gap.

**Architecture:** Single `@ApplicationScoped` class with programmatic `bind(SwarmEventSource)`. Maps 6 engine callbacks to 6 `SwarmCoordinationEvent` sealed variants. Handles data mismatches at the bridge level (empty experiences for `SwarmInitialised`, null result for `MemberCompleted`). Exception-safe — never propagates failures back to the engine.

**Tech Stack:** Java 17, JUnit 5, Mockito, AssertJ. No CDI container in tests.

## Global Constraints

- Follow existing agency test conventions: plain JUnit 5 + Mockito, AssertJ assertions
- Logging: `java.util.logging.Logger` with `LOG` constant
- No `@DefaultBean` — engine SPI javadoc says multi-instance
- No CDI container needed — bridge is a plain class with `@ApplicationScoped` annotation

---

## Batch 1: SwarmCoordinationListenerBridge

### Task 1: Bridge implementation with tests

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmCoordinationListenerBridge.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmCoordinationListenerBridgeTest.java`

**Interfaces:**
- Consumes: `SwarmCoordinationListener` (engine SPI — 6 default methods), `SwarmEventSource.onSwarmEvent(SwarmCoordinationEvent)`, `SwarmCoordinationEvent` sealed variants
- Produces: CDI-discoverable `SwarmCoordinationListener` implementation, `bind(SwarmEventSource)` / `isBound()` API

- [ ] **Step 1: Write the test class with pre-bind no-op safety tests**

```java
package io.casehub.agency.orchestration.swarm;

import io.casehub.api.model.stigmergy.BehavioralFingerprint;
import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.DetectedTeam;
import io.casehub.api.model.stigmergy.SwarmProgress;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

class SwarmCoordinationListenerBridgeTest {

    private static final UUID CASE_ID = UUID.randomUUID();
    private static final SwarmProgress PROGRESS =
            new SwarmProgress(0.5, 0.3, 0.4, Instant.now());

    @Test
    void isNotBoundBeforeBind() {
        var bridge = new SwarmCoordinationListenerBridge();
        assertThat(bridge.isBound()).isFalse();
    }

    @Test
    void callbacksAreNoOpsBeforeBind() {
        var bridge = new SwarmCoordinationListenerBridge();
        assertThatNoException().isThrownBy(() -> {
            bridge.onSwarmInitialised(CASE_ID, PROGRESS);
            bridge.onRoleChanged(CASE_ID, List.of(), PROGRESS);
            bridge.onTeamChanged(CASE_ID, List.of(), PROGRESS);
            bridge.onProgressUpdated(CASE_ID, PROGRESS);
            bridge.onMemberCompleted(CASE_ID, "agent-1");
            bridge.onProvisioningChanged(CASE_ID, 3, 10);
        });
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmCoordinationListenerBridgeTest`
Expected: Compilation failure — `SwarmCoordinationListenerBridge` does not exist yet.

- [ ] **Step 3: Write the bridge class (skeleton with bind/isBound and no-op callbacks)**

```java
package io.casehub.agency.orchestration.swarm;

import io.casehub.api.model.stigmergy.DetectedRole;
import io.casehub.api.model.stigmergy.DetectedTeam;
import io.casehub.api.model.stigmergy.SwarmProgress;
import io.casehub.api.spi.stigmergy.SwarmCoordinationListener;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

@ApplicationScoped
public class SwarmCoordinationListenerBridge implements SwarmCoordinationListener {

    private static final Logger LOG = Logger.getLogger(SwarmCoordinationListenerBridge.class.getName());

    private volatile SwarmEventSource eventSource;

    public void bind(SwarmEventSource eventSource) {
        this.eventSource = eventSource;
    }

    public boolean isBound() {
        return eventSource != null;
    }

    @Override
    public void onSwarmInitialised(UUID caseId, SwarmProgress progress) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.SwarmInitialised(List.of(), progress));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge SwarmInitialised event", e);
        }
    }

    @Override
    public void onRoleChanged(UUID caseId, List<DetectedRole> roles, SwarmProgress progress) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.RoleChanged(roles, progress));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge RoleChanged event", e);
        }
    }

    @Override
    public void onTeamChanged(UUID caseId, List<DetectedTeam> teams, SwarmProgress progress) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.TeamChanged(teams, progress));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge TeamChanged event", e);
        }
    }

    @Override
    public void onProgressUpdated(UUID caseId, SwarmProgress progress) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.ProgressUpdated(progress));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge ProgressUpdated event", e);
        }
    }

    @Override
    public void onMemberCompleted(UUID caseId, String agentId) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.MemberCompleted(agentId, null));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge MemberCompleted event", e);
        }
    }

    @Override
    public void onProvisioningChanged(UUID caseId, int currentSize, int maxSize) {
        var source = this.eventSource;
        if (source == null) return;
        try {
            source.onSwarmEvent(new SwarmCoordinationEvent.ProvisioningChanged(currentSize, maxSize));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to bridge ProvisioningChanged event", e);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmCoordinationListenerBridgeTest`
Expected: 2 tests PASS.

- [ ] **Step 5: Add callback mapping tests**

Add these tests to `SwarmCoordinationListenerBridgeTest`:

```java
@Test
void delegatesSwarmInitialised() {
    var context = new SwarmContext();
    var source = new SwarmEventSource(context);
    var received = new ArrayList<DriverEvent>();
    source.subscribe(received::add);

    var bridge = new SwarmCoordinationListenerBridge();
    bridge.bind(source);
    assertThat(bridge.isBound()).isTrue();

    bridge.onSwarmInitialised(CASE_ID, PROGRESS);

    assertThat(received).hasSize(1);
    var event = (SwarmCoordinationEvent.SwarmInitialised) received.get(0).payload();
    assertThat(event.experiences()).isEmpty();
    assertThat(event.progress()).isSameAs(PROGRESS);
}

@Test
void delegatesRoleChanged() {
    var context = new SwarmContext();
    var source = new SwarmEventSource(context);
    var received = new ArrayList<DriverEvent>();
    source.subscribe(received::add);

    var bridge = new SwarmCoordinationListenerBridge();
    bridge.bind(source);

    var role = new DetectedRole("role-1", Set.of("a1"), BehavioralFingerprint.EMPTY, List.of("f1"), 3, 0.1);
    bridge.onRoleChanged(CASE_ID, List.of(role), PROGRESS);

    assertThat(received).hasSize(1);
    var event = (SwarmCoordinationEvent.RoleChanged) received.get(0).payload();
    assertThat(event.roles()).containsExactly(role);
    assertThat(event.progress()).isSameAs(PROGRESS);
}

@Test
void delegatesTeamChanged() {
    var context = new SwarmContext();
    var source = new SwarmEventSource(context);
    var received = new ArrayList<DriverEvent>();
    source.subscribe(received::add);

    var bridge = new SwarmCoordinationListenerBridge();
    bridge.bind(source);

    var team = new DetectedTeam("team-1", Set.of("a1", "a2"), Set.of(), 0.8, 2);
    bridge.onTeamChanged(CASE_ID, List.of(team), PROGRESS);

    assertThat(received).hasSize(1);
    var event = (SwarmCoordinationEvent.TeamChanged) received.get(0).payload();
    assertThat(event.teams()).containsExactly(team);
    assertThat(event.progress()).isSameAs(PROGRESS);
}

@Test
void delegatesProgressUpdated() {
    var context = new SwarmContext();
    var source = new SwarmEventSource(context);
    var received = new ArrayList<DriverEvent>();
    source.subscribe(received::add);

    var bridge = new SwarmCoordinationListenerBridge();
    bridge.bind(source);

    bridge.onProgressUpdated(CASE_ID, PROGRESS);

    assertThat(received).hasSize(1);
    var event = (SwarmCoordinationEvent.ProgressUpdated) received.get(0).payload();
    assertThat(event.progress()).isSameAs(PROGRESS);
}

@Test
void delegatesMemberCompleted() {
    var context = new SwarmContext();
    var source = new SwarmEventSource(context);
    var received = new ArrayList<DriverEvent>();
    source.subscribe(received::add);

    var bridge = new SwarmCoordinationListenerBridge();
    bridge.bind(source);

    bridge.onMemberCompleted(CASE_ID, "agent-42");

    assertThat(received).hasSize(1);
    var event = (SwarmCoordinationEvent.MemberCompleted) received.get(0).payload();
    assertThat(event.agentId()).isEqualTo("agent-42");
    assertThat(event.result()).isNull();
}

@Test
void delegatesProvisioningChanged() {
    var context = new SwarmContext();
    var source = new SwarmEventSource(context);
    var received = new ArrayList<DriverEvent>();
    source.subscribe(received::add);

    var bridge = new SwarmCoordinationListenerBridge();
    bridge.bind(source);

    bridge.onProvisioningChanged(CASE_ID, 5, 10);

    assertThat(received).hasSize(1);
    var event = (SwarmCoordinationEvent.ProvisioningChanged) received.get(0).payload();
    assertThat(event.currentSize()).isEqualTo(5);
    assertThat(event.maxSize()).isEqualTo(10);
}
```

Additional imports needed at the top:

```java
import io.casehub.agency.orchestration.execution.DriverEvent;
import java.util.ArrayList;
```

- [ ] **Step 6: Run all tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmCoordinationListenerBridgeTest`
Expected: 8 tests PASS.

- [ ] **Step 7: Add exception isolation test**

Add to `SwarmCoordinationListenerBridgeTest`:

```java
@Test
void doesNotPropagateExceptionsFromEventSource() {
    var context = new SwarmContext();
    var source = new SwarmEventSource(context);
    source.subscribe(e -> { throw new RuntimeException("subscriber failure"); });

    var bridge = new SwarmCoordinationListenerBridge();
    bridge.bind(source);

    assertThatNoException().isThrownBy(() ->
            bridge.onProgressUpdated(CASE_ID, PROGRESS));
}
```

- [ ] **Step 8: Run all tests to verify they pass**

Run: `mvn --batch-mode test -pl agency -Dtest=SwarmCoordinationListenerBridgeTest`
Expected: 9 tests PASS.

- [ ] **Step 9: Run the full test suite to check for regressions**

Run: `mvn --batch-mode test`
Expected: All tests PASS.

- [ ] **Step 10: Update CLAUDE.md swarm package description**

In `CLAUDE.md`, find the swarm package row in the Key Directories table and append `SwarmCoordinationListenerBridge` to the description. Add after `SwarmDegradedEvent`:

`, SwarmCoordinationListenerBridge (`@ApplicationScoped` `SwarmCoordinationListener` bridge — maps engine lifecycle callbacks to `SwarmCoordinationEvent` sealed variants via bound `SwarmEventSource`; no-op before `bind()`)`

- [ ] **Step 11: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/swarm/SwarmCoordinationListenerBridge.java agency/src/test/java/io/casehub/agency/orchestration/swarm/SwarmCoordinationListenerBridgeTest.java CLAUDE.md
git commit -m "feat(#375): add SwarmCoordinationListenerBridge — engine event adapter

Implements SwarmCoordinationListener to bridge engine swarm lifecycle
events to agency's SwarmEventSource. Maps all 6 callbacks to
SwarmCoordinationEvent sealed variants. Exception-safe: never
propagates failures back to the engine's SwarmProvisioner.

Refs #375

Co-Authored-By: Claude Opus 4.6 (1M context) <noreply@anthropic.com>"
```

## References

- [2026-10-31-swarm-coordination-listener-bridge-design.md] — design spec this plan implements
- [engine/api/.../SwarmCoordinationListener.java:30] — engine SPI with 6 default methods
- [agency-core/.../SwarmCoordinationEvent.java:10] — agency sealed event hierarchy
- [agency-core/.../SwarmEventSource.java:9] — event source target for delegation
- [agency-core/.../SwarmContext.java:11] — mutable state updated by events
- [agency/test/.../SwarmEventSourceTest.java] — test conventions reference
- [GitHub #375] — focal issue
- [casehubio/engine#1274] — parent epic
