# Type-safe Expression Compilation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #242 — Type-safe expression compilation for YAML predicates
**Issue group:** #242

**Goal:** Wire per-site `Class<?>` context types into the agentic-yaml registries so expression-based spec variants compile and evaluate correctly instead of throwing `UnsupportedOperationException`.

**Architecture:** Each expression site in the sealed spec hierarchy maps to a known context type. The registry switch statements pass the concrete `Class<?>` to `ExpressionEngine.compile()`, producing `CompiledExpression<C, R>` handles that wire into the runtime SPI constructors (`GoalReached(Predicate)`, `ConfidenceThreshold.below(threshold, ToDoubleFunction)`, `FirstMatchRouting(Predicate)`). No new types or modules — uses existing platform-api infrastructure.

**Tech Stack:** Java 17, MVEL3 via `MvelExpressionEngine` (platform-expression), Jackson YAML for spec deserialization, JUnit 5 + AssertJ for tests.

## Global Constraints

- All expression compilation uses `ExpressionEngine.compile(expr, contextClass, resultClass)` from `casehub-platform-api`
- For generic `T` sites where the runtime type is unknown, compile against `Map<String, Object>` — MVEL duck-types property access
- `@Nullable ExpressionEngine` pattern continues — null engine + expression field = `IllegalStateException`
- Tests use `MvelExpressionEngine` from `casehub-platform-expression` (already a test dependency)

---

## Batch 1: Registry expression compilation

### Task 1: RoutingStrategyRegistry — FirstMatch guard compilation

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/RoutingStrategyRegistry.java`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/RoutingStrategyRegistryTest.java`

**Interfaces:**
- Consumes: `RoutingSpec.FirstMatch(guard)` spec record, `ExpressionEngine.compile()`, `RoutingCandidate(ref, descriptor)` record
- Produces: `RoutingStrategy<T>` with compiled guard predicate — `FirstMatchRouting<T>(Predicate<RoutingCandidate>)`

- [ ] **Step 1: Write the failing test — guard expression compiles and filters**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.AgentRef;
import io.casehub.blocks.agentic.RoutingCandidate;
import io.casehub.blocks.agentic.routing.FirstMatchRouting;
import io.casehub.blocks.agentic.routing.RoutingContext;
import io.casehub.blocks.agentic.yaml.spec.RoutingSpec;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingStrategyRegistryTest {

    private final RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
    private final MvelExpressionEngine engine = new MvelExpressionEngine();

    @Test
    void firstMatchWithGuardCompilesExpression() {
        var spec = new RoutingSpec.FirstMatch("ref.name() == \"agent-1\"");
        var strategy = registry.resolve(spec, engine);
        assertThat(strategy).isInstanceOf(FirstMatchRouting.class);
    }

    @Test
    void firstMatchWithoutGuardUsesDefaultPredicate() {
        var spec = new RoutingSpec.FirstMatch(null);
        var strategy = registry.resolve(spec, null);
        assertThat(strategy).isInstanceOf(FirstMatchRouting.class);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=RoutingStrategyRegistryTest`
Expected: first test FAILS (guard expression not compiled — hardcoded `c -> true`)

- [ ] **Step 3: Implement guard compilation in RoutingStrategyRegistry**

Replace the `FirstMatch` case in `RoutingStrategyRegistry.resolve()`:

```java
case RoutingSpec.FirstMatch fm -> {
    if (fm.guard() != null && engine != null) {
        var compiled = engine.compile(fm.guard(), RoutingCandidate.class, Boolean.class);
        yield new FirstMatchRouting<>(c -> compiled.eval((RoutingCandidate) c));
    }
    yield new FirstMatchRouting<>(c -> true);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=RoutingStrategyRegistryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/RoutingStrategyRegistry.java agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/RoutingStrategyRegistryTest.java
git commit -m "feat(#242): compile FirstMatch guard expression against RoutingCandidate

Refs casehubio/blocks#242"
```

### Task 2: TerminationConditionRegistry — GoalReached compilation

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/TerminationConditionRegistry.java`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/TerminationConditionRegistryTest.java`

**Interfaces:**
- Consumes: `TerminationSpec.GoalReached(when)` spec record, `ExpressionEngine.compile()`
- Produces: `TerminationCondition<T>` — `GoalReached<T>(Predicate<T>)` with compiled predicate

- [ ] **Step 1: Write the failing test — GoalReached compiles and evaluates**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.termination.GoalReached;
import io.casehub.blocks.agentic.termination.TerminationCondition;
import io.casehub.blocks.agentic.yaml.spec.TerminationSpec;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TerminationConditionRegistryTest {

    private final TerminationConditionRegistry registry = new TerminationConditionRegistry();
    private final MvelExpressionEngine engine = new MvelExpressionEngine();

    @Test
    void goalReachedCompilesWhenExpression() {
        var spec = new TerminationSpec.GoalReached("score > 0.9");
        var condition = registry.resolve(spec, engine);
        assertThat(condition).isInstanceOf(GoalReached.class);
    }

    @Test
    void goalReachedThrowsWithoutEngine() {
        var spec = new TerminationSpec.GoalReached("score > 0.9");
        assertThatThrownBy(() -> registry.resolve(spec, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ExpressionEngine");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=TerminationConditionRegistryTest`
Expected: FAIL — `GoalReached` case throws `UnsupportedOperationException`

- [ ] **Step 3: Implement GoalReached compilation**

Replace the `GoalReached` case in `TerminationConditionRegistry.resolve()`:

```java
case TerminationSpec.GoalReached gr -> {
    if (engine == null) {
        throw new IllegalStateException(
                "GoalReached requires ExpressionEngine for predicate compilation");
    }
    @SuppressWarnings("unchecked")
    var compiled = engine.compile(gr.when(),
            (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
    yield (TerminationCondition<T>) new GoalReached<>(
            state -> Boolean.TRUE.equals(compiled.eval((Map<String, Object>) state)));
}
```

Add `import java.util.Map;` to the imports.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=TerminationConditionRegistryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/TerminationConditionRegistry.java agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/TerminationConditionRegistryTest.java
git commit -m "feat(#242): compile GoalReached when-expression against Map context

Refs casehubio/blocks#242"
```

### Task 3: JudgmentTriggerRegistry — ConfidenceThreshold compilation

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/JudgmentTriggerRegistry.java`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/JudgmentTriggerRegistryTest.java`

**Interfaces:**
- Consumes: `JudgmentSpec.TriggerSpec.ConfidenceThreshold(threshold, extractor)` spec record, `ExpressionEngine.compile()`, `JudgmentContext<T>(executionContext, iterationResults, aggregationResult, iteration, previousFeedback)` record
- Produces: `JudgmentTrigger<T>` — `ConfidenceThreshold.below(threshold, ToDoubleFunction<JudgmentContext<T>>)` with compiled extractor

- [ ] **Step 1: Write the failing test — ConfidenceThreshold compiles extractor**

```java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.judgment.ConfidenceThreshold;
import io.casehub.blocks.agentic.yaml.spec.JudgmentSpec.TriggerSpec;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JudgmentTriggerRegistryTest {

    private final JudgmentTriggerRegistry registry = new JudgmentTriggerRegistry();
    private final MvelExpressionEngine engine = new MvelExpressionEngine();

    @Test
    void confidenceThresholdCompilesExtractor() {
        var spec = new TriggerSpec.ConfidenceThreshold(0.8, "iteration");
        var trigger = registry.resolve(spec, engine);
        assertThat(trigger).isInstanceOf(ConfidenceThreshold.class);
    }

    @Test
    void confidenceThresholdThrowsWithoutEngine() {
        var spec = new TriggerSpec.ConfidenceThreshold(0.8, "iteration");
        assertThatThrownBy(() -> registry.resolve(spec, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ExpressionEngine");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=JudgmentTriggerRegistryTest`
Expected: FAIL — `ConfidenceThreshold` case throws `UnsupportedOperationException`

- [ ] **Step 3: Implement ConfidenceThreshold compilation**

The `ConfidenceThreshold` spec has a `threshold` (double) and `extractor` (String expression). The runtime `ConfidenceThreshold.below()` takes a `ToDoubleFunction<JudgmentContext<T>>`. The extractor expression evaluates against `JudgmentContext` (which is a record with fields `executionContext`, `iterationResults`, `aggregationResult`, `iteration`, `previousFeedback`).

Since `JudgmentContext` is a concrete record (not generic at the field level — `T` only affects `executionContext`), compile against `JudgmentContext` directly:

```java
case TriggerSpec.ConfidenceThreshold ct -> {
    if (engine == null) {
        throw new IllegalStateException(
                "ConfidenceThreshold requires ExpressionEngine for extractor compilation");
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    var compiled = engine.compile(ct.extractor(),
            (Class) JudgmentContext.class, Number.class);
    yield ConfidenceThreshold.below(ct.threshold(),
            ctx -> compiled.eval(ctx).doubleValue());
}
```

Add `import io.casehub.blocks.agentic.judgment.JudgmentContext;` to the imports.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=JudgmentTriggerRegistryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/JudgmentTriggerRegistry.java agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/registry/JudgmentTriggerRegistryTest.java
git commit -m "feat(#242): compile ConfidenceThreshold extractor against JudgmentContext

Refs casehubio/blocks#242"
```

## Batch 2: End-to-end integration + coverage doc

### Task 4: PatternCompilerTest — end-to-end YAML with expressions

**Files:**
- Create: `agentic-yaml/src/test/resources/patterns/supervisor-with-expressions.yaml`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/PatternCompilerTest.java`

**Interfaces:**
- Consumes: `PatternCompiler.compile(PatternSpec)`, all three registry changes from Batch 1
- Produces: Test proving end-to-end YAML → compiled ExecutionModel with expressions

- [ ] **Step 1: Create YAML test fixture with expressions**

```yaml
# agentic-yaml/src/test/resources/patterns/supervisor-with-expressions.yaml
type: supervisor
routing:
  type: first-match
  guard: "ref.name() == \"specialist\""
termination:
  - type: goal-reached
    when: "completed == true"
  - type: max-iterations
    iterations: 10
agents:
  - type: worker
    name: specialist
  - type: worker
    name: generalist
```

- [ ] **Step 2: Write the failing test — end-to-end compilation**

Add to `PatternCompilerTest.java`:

```java
@Test
void compilesSupervisorWithExpressions() throws IOException {
    var spec = loadPattern("supervisor-with-expressions");
    var model = compiler.compile(spec);
    assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
    assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
    assertThat(model.candidateSupplier().get()).hasSize(2);
}
```

- [ ] **Step 3: Run to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternCompilerTest#compilesSupervisorWithExpressions`
Expected: FAIL — GoalReached or FirstMatch guard throws

- [ ] **Step 4: Verify it passes with the Batch 1 changes**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=PatternCompilerTest#compilesSupervisorWithExpressions`
Expected: PASS (all registry changes from Batch 1 are already in place)

- [ ] **Step 5: Run the full test suite**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All tests PASS — no regressions

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml/src/test/resources/patterns/supervisor-with-expressions.yaml agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/PatternCompilerTest.java
git commit -m "test(#242): end-to-end YAML with expression compilation

Refs casehubio/blocks#242"
```

### Task 5: Update coverage matrix

**Files:**
- Modify: `docs/yaml-coverage.md` — update §7 Expression Compilation section

**Interfaces:**
- Consumes: completion status of Tasks 1-4
- Produces: updated coverage doc reflecting implemented expression sites

- [ ] **Step 1: Read current §7 in yaml-coverage.md**

Read `docs/yaml-coverage.md` and find the Expression Compilation section.

- [ ] **Step 2: Update the section**

Mark the implemented expression sites as done:
- `RoutingSpec.FirstMatch.guard` → done
- `TerminationSpec.GoalReached.when` → done
- `JudgmentSpec.TriggerSpec.ConfidenceThreshold.extractor` → done
- Note remaining sites (Static method guards, Convergence extractors) as partial/future

- [ ] **Step 3: Commit**

```bash
git add docs/yaml-coverage.md
git commit -m "docs(#242): update expression compilation coverage

Refs casehubio/blocks#242"
```

## References

- [2026-09-10-typed-expr-compilation-design.md] — design spec this plan implements
- [RoutingStrategyRegistry.java:17] — FirstMatch hardcoded guard
- [TerminationConditionRegistry.java:24-25] — GoalReached UnsupportedOperationException
- [JudgmentTriggerRegistry.java:23-26] — ConfidenceThreshold UnsupportedOperationException
- [GoalReached.java:9] — constructor takes `Predicate<T>`
- [ConfidenceThreshold.java:15-16] — `below(threshold, ToDoubleFunction)` factory
- [RoutingCandidate.java:18] — record with `ref` and `descriptor` fields
- [JudgmentContext.java:28-33] — record with `executionContext`, `iteration`, etc.
- [PatternCompilerTest.java] — existing test patterns and fixture loading
- [ThresholdClassifySummariser.java:46] — Map.class compilation precedent
- [GitHub #242] — focal issue
