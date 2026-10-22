# Execution Infrastructure — YAML Surface

**Issue:** casehubio/blocks#251
**Date:** 2026-09-11
**Scope:** 6 capabilities from coverage matrix §18 → spec records, registries

## Problem

Execution backends, concurrency policies, listeners, coalition evaluators, and
intention types require Java construction. YAML-only application definition
cannot configure these aspects of agent execution.

## Solution

Add spec records and registries to agentic-yaml for all 6 §18 capabilities.
These are standalone registries referenced by other compilers — no new root
type, compiler, or `@BuildStep` needed.

## Spec Records

### ExecutionBackendSpec (named type registry)

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = ExecutionBackendSpec.Reactive.class, name = "reactive"),
    @Type(value = ExecutionBackendSpec.Choreographed.class, name = "choreographed")
})
public sealed interface ExecutionBackendSpec {

    record Reactive() implements ExecutionBackendSpec {}

    record Choreographed(EventConcurrencyPolicySpec policy)
            implements ExecutionBackendSpec {
        public Choreographed {
            Objects.requireNonNull(policy, "policy");
        }
    }
}
```

`Choreographed` requires a nested `EventConcurrencyPolicySpec`. `EventSource`
instances are CDI-wired at runtime — not YAML-expressible.

### EventConcurrencyPolicySpec (named type registry)

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = EventConcurrencyPolicySpec.Serialize.class, name = "serialize"),
    @Type(value = EventConcurrencyPolicySpec.Coalesce.class, name = "coalesce"),
    @Type(value = EventConcurrencyPolicySpec.CoalesceBySource.class,
          name = "coalesce-by-source")
})
public sealed interface EventConcurrencyPolicySpec {

    record Serialize() implements EventConcurrencyPolicySpec {}

    record Coalesce(@Nullable Duration window) implements EventConcurrencyPolicySpec {}

    record CoalesceBySource() implements EventConcurrencyPolicySpec {}
}
```

`Coalesce` optionally takes a Duration window. When null, uses the no-arg
`EventConcurrencyPolicy.coalesce()` factory.

### ExecutionListenerSpec (named type registry)

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = ExecutionListenerSpec.EventLog.class, name = "event-log"),
    @Type(value = ExecutionListenerSpec.Ledger.class, name = "ledger"),
    @Type(value = ExecutionListenerSpec.Metrics.class, name = "metrics")
})
public sealed interface ExecutionListenerSpec {

    record EventLog() implements ExecutionListenerSpec {}

    record Ledger(@Nullable String supervisorActorId)
            implements ExecutionListenerSpec {}

    record Metrics() implements ExecutionListenerSpec {}
}
```

All listeners need CDI-injected sinks (EventSink, LedgerSink, Meter).
`Ledger` additionally takes a `supervisorActorId` string from YAML.

### CoalitionEvaluatorSpec (named type registry)

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = CoalitionEvaluatorSpec.CapabilityCoverage.class,
          name = "capability-coverage")
})
public sealed interface CoalitionEvaluatorSpec {

    record CapabilityCoverage() implements CoalitionEvaluatorSpec {}
}
```

Single implementation — extensible when new evaluators are added.

### JointIntentionSpec (adapted record)

```java
public record JointIntentionSpec(
        String intentionId,
        String planDescription,
        Set<String> parties) {

    public JointIntentionSpec {
        Objects.requireNonNull(intentionId, "intentionId");
        Objects.requireNonNull(planDescription, "planDescription");
        if (parties == null || parties.isEmpty())
            throw new IllegalArgumentException("at least one party required");
        parties = Set.copyOf(parties);
    }
}
```

Captures formation parameters only. Lifecycle methods (activate, reconsider,
drop, fulfill) are runtime operations.

### ReconsiderationSignal — direct reuse

Already a Jackson-compatible record with `ReconsiderationReason` enum,
`String detail`, and `boolean shouldDrop`. No spec wrapper needed.

## Registries

### ExecutionBackendRegistry

```java
public class ExecutionBackendRegistry {

    private final EventConcurrencyPolicyRegistry policyRegistry;

    public ExecutionBackendRegistry(EventConcurrencyPolicyRegistry policyRegistry) {
        this.policyRegistry = policyRegistry;
    }

    public <T> ExecutionBackend<T> resolve(ExecutionBackendSpec spec) {
        return switch (spec) {
            case ExecutionBackendSpec.Reactive ignored -> ExecutionBackend.reactive();
            case ExecutionBackendSpec.Choreographed c -> {
                var policy = policyRegistry.resolve(c.policy());
                yield ExecutionBackend.choreographed(policy);
            }
        };
    }
}
```

### EventConcurrencyPolicyRegistry

```java
public class EventConcurrencyPolicyRegistry {

    public EventConcurrencyPolicy resolve(EventConcurrencyPolicySpec spec) {
        return switch (spec) {
            case EventConcurrencyPolicySpec.Serialize ignored ->
                    EventConcurrencyPolicy.serialize();
            case EventConcurrencyPolicySpec.Coalesce c ->
                    c.window() != null
                            ? EventConcurrencyPolicy.coalesce(c.window())
                            : EventConcurrencyPolicy.coalesce();
            case EventConcurrencyPolicySpec.CoalesceBySource ignored ->
                    EventConcurrencyPolicy.coalesceBySource();
        };
    }
}
```

### ExecutionListenerRegistry

```java
public class ExecutionListenerRegistry {

    public ExecutionEventListener resolve(ExecutionListenerSpec spec,
                                           @Nullable EventSink eventSink,
                                           @Nullable LedgerSink ledgerSink,
                                           @Nullable Meter meter) {
        return switch (spec) {
            case ExecutionListenerSpec.EventLog ignored -> {
                if (eventSink == null)
                    throw new IllegalStateException("event-log listener requires EventSink");
                yield new EventLogListener(eventSink);
            }
            case ExecutionListenerSpec.Ledger l -> {
                if (ledgerSink == null)
                    throw new IllegalStateException("ledger listener requires LedgerSink");
                yield new LedgerExecutionListener(ledgerSink,
                        l.supervisorActorId() != null ? l.supervisorActorId() : "system");
            }
            case ExecutionListenerSpec.Metrics ignored -> {
                if (meter == null)
                    throw new IllegalStateException("metrics listener requires Meter");
                yield new MetricsListener(meter);
            }
        };
    }
}
```

### CoalitionEvaluatorRegistry

```java
public class CoalitionEvaluatorRegistry {

    public CoalitionEvaluator resolve(CoalitionEvaluatorSpec spec) {
        return switch (spec) {
            case CoalitionEvaluatorSpec.CapabilityCoverage ignored ->
                    new CapabilityCoverageEvaluator();
        };
    }
}
```

## Schema Generation

Add to `AgencySchemaGenerator.buildDiscriminatorOverrides()`:
- `ExecutionBackendSpec.class`
- `EventConcurrencyPolicySpec.class`
- `ExecutionListenerSpec.class`
- `CoalitionEvaluatorSpec.class`

## Coverage Matrix Update

All 6 capabilities in §18 → Done.

## Test Plan

| Test | What it verifies |
|------|-----------------|
| `ExecutionInfraSpecSerializationTest` | Round-trip YAML for all spec records |
| `ExecutionInfraRegistryTest` | All registry resolve methods |

## References

- `blocks/src/main/java/io/casehub/agency/agentic/model/ExecutionBackend.java`
- `blocks/src/main/java/io/casehub/agency/agentic/model/EventConcurrencyPolicy.java`
- `blocks/src/main/java/io/casehub/agency/agentic/listener/EventLogListener.java`
- `blocks/src/main/java/io/casehub/agency/agentic/listener/LedgerExecutionListener.java`
- `blocks/src/main/java/io/casehub/agency/agentic/listener/MetricsListener.java`
- `blocks/src/main/java/io/casehub/agency/agentic/coalition/CoalitionEvaluator.java`
- `blocks/src/main/java/io/casehub/agency/agentic/coalition/CapabilityCoverageEvaluator.java`
- `blocks/src/main/java/io/casehub/agency/agentic/intention/JointIntention.java`
- `blocks/src/main/java/io/casehub/agency/agentic/intention/ReconsiderationSignal.java`
- [GitHub #251]
