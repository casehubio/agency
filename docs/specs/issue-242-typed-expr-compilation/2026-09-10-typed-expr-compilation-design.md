# Type-safe Expression Compilation for YAML Predicates

**Issue:** casehubio/agency#242
**Date:** 2026-09-10
**Scope:** Layer 1 — per-site compilation with `Class<?>`. Layer 2 (yaml-core descriptor model) deferred.

## Problem

The agentic-yaml module stores MVEL expression strings in spec records but defers compilation to runtime. Several registries throw `UnsupportedOperationException` because they don't know the context type to compile against:

- `TerminationConditionRegistry`: `GoalReached` — "requires runtime expression compilation"
- `JudgmentTriggerRegistry`: `ConfidenceThreshold` — "requires runtime expression compilation"
- `RoutingStrategyRegistry`: `FirstMatch.guard` — hardcoded `c -> true` placeholder

The root cause: `MvelExpressionEngine.compile(expr, Object.class, Boolean.class)` throws `IntrospectionException` because MVEL3 can't introspect `Object.class`. Each site needs its real context type.

## Solution

Per-site compilation — each expression site in the sealed spec hierarchy maps to a known `Class<?>`. The compiler switch statements use the concrete type when calling `ExpressionEngine.compile()`. Uses existing `CompiledExpression<C, R>` from platform-api.

## Per-site Type Mapping

| Spec site | Expression field | Context type | Result type | Rationale |
|-----------|-----------------|-------------|-------------|-----------|
| `RoutingSpec.FirstMatch` | `guard` | `RoutingCandidate` | `Boolean` | Guard evaluates each candidate |
| `TerminationSpec.GoalReached` | `when` | `Map<String, Object>` | `Boolean` | Generic T — MVEL duck-types maps |
| `JudgmentSpec.TriggerSpec.ConfidenceThreshold` | `extractor` | `JudgmentContext` | `Boolean` | Erased generic — use raw type |
| `DecompositionSpec.Static` | method guards | `Map<String, Object>` | `Boolean` | Generic task context |
| `TerminationSpec.Convergence` | state extractors | `Map<String, Object>` | `Object` | Generic convergence state |

For generic `T` sites, `Map<String, Object>` is the universal fallback. MVEL duck-types property access against maps — `score > 0.8` works whether the context has a `getScore()` method or a `"score"` map key. This is the same approach `ThresholdClassifySummariser` already uses successfully.

## Changes by File

### `RoutingStrategyRegistry.java`

The `FirstMatch` case currently returns a hardcoded `c -> true` predicate. With expression compilation:

```java
case RoutingSpec.FirstMatch fm -> {
    if (fm.guard() != null && engine != null) {
        var compiled = engine.compile(fm.guard(), RoutingCandidate.class, Boolean.class);
        yield new FirstMatchRouting<>(c -> compiled.eval((RoutingCandidate) c));
    }
    yield new FirstMatchRouting<>(c -> true);
}
```

When no guard expression is declared or no engine is available, the fallback remains `c -> true`.

### `TerminationConditionRegistry.java`

The `GoalReached` case currently throws. With expression compilation:

```java
case TerminationSpec.GoalReached gr -> {
    if (engine == null) {
        throw new IllegalStateException(
            "GoalReached requires ExpressionEngine for predicate compilation");
    }
    @SuppressWarnings("unchecked")
    var compiled = engine.compile(gr.when(),
        (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
    yield new GoalReached<>(state -> compiled.eval((Map<String, Object>) state));
}
```

### `JudgmentTriggerRegistry.java`

The `ConfidenceThreshold` case currently throws. With expression compilation:

```java
case TriggerSpec.ConfidenceThreshold ct -> {
    if (engine == null) {
        throw new IllegalStateException(
            "ConfidenceThreshold requires ExpressionEngine for predicate compilation");
    }
    @SuppressWarnings("unchecked")
    var compiled = engine.compile(ct.extractor(),
        (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
    yield new ConfidenceThreshold<>(ctx -> compiled.eval((Map<String, Object>) ctx));
}
```

### `TerminationConditionRegistry.java` (continued)

The `Convergence` case with state extractors will compile against `Map<String, Object>` with `Object` result type, following the same pattern as `GoalReached`.

### `DecompositionStrategyRegistry.java`

The `Static` case with method guards will use expression compilation for guard predicates. Each method guard compiles against `Map<String, Object>`.

### `PatternCompiler.java`

No structural changes needed. The compiler already passes `ExpressionEngine` to each registry. The `@Nullable ExpressionEngine` parameter becomes effectively required for any spec that uses expressions — registries throw `IllegalStateException` if an expression site is used without an engine.

## ExpressionEngine Null Handling

The `@Nullable ExpressionEngine` pattern continues — it's null when no expression engine is on the classpath (pure Java DSL usage). Registries that encounter expression fields with a null engine throw `IllegalStateException` with a clear message. This is the correct behavior: YAML with expressions requires an expression engine; Java DSL never reaches these code paths.

## Validation

Expression validation happens at compilation time via `ExpressionEngine.compile()` — if the expression is syntactically invalid, MVEL3 throws. No separate validation pass is needed for Layer 1.

## Testing

Each registry gets a test for every expression-using spec variant:

- `RoutingStrategyRegistryTest`: FirstMatch with guard expression, verified against a RoutingCandidate
- `TerminationConditionRegistryTest`: GoalReached with when expression, verified against a Map context
- `JudgmentTriggerRegistryTest`: ConfidenceThreshold with extractor expression, verified against a Map context
- `PatternCompilerTest`: end-to-end YAML → compiled ExecutionModel with expressions

Tests use `MvelExpressionEngine` (already a test dependency).

## Layer 2 — Future (not in scope)

When Tier 2 summarisation pipelines or dynamic YAML loading need YAML-declared context schemas, a descriptor model will be added to platform's yaml-core:

- `TypeDescriptor` sealed interface (Pojo/MapType/ListType) with string type names
- `PropertyDescriptor` with nested structure support
- `ExpressionCompiler` SPI in yaml-core (zero deps)
- Bridge module (`yaml-expression`) with `TypeResolver` (Map<String, Class<?>>)

This is additive — Layer 1 per-site compilation continues to work. Layer 2 provides an alternative compilation path for sites where the context type is declared in YAML rather than known from the sealed hierarchy.

## References

- `PatternCompiler.java` — existing compiler switch walk
- `TerminationConditionRegistry.java:24-25` — GoalReached UnsupportedOperationException
- `JudgmentTriggerRegistry.java:23-26` — ConfidenceThreshold UnsupportedOperationException
- `RoutingStrategyRegistry.java:17` — FirstMatch hardcoded guard
- `ThresholdClassifySummariser.java:46` — Map.class compilation precedent
- `CompiledExpression.java` (platform-api) — existing typed expression handle
- `ExpressionEngine.java` (platform-api) — existing compilation API
- MVEL3 `CompilerParameters.java`, `Type.java`, `Declaration.java` — internal type model
- R1-07 reviewer challenge — "per-site compilation is dramatically simpler"
- summarisation-yaml D9 — "typed compilation via CompiledExpression"
- summarisation-yaml D7 — "Tier 1 uses Map, Tier 2 uses domain types"
