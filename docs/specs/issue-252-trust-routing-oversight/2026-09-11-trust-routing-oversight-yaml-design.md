# Trust, Routing, Oversight — YAML Surface

**Issue:** casehubio/agency#252
**Date:** 2026-09-11
**Scope:** 5 config-shaped types from coverage matrix §19–§20 → spec records, registries

## Problem

Trust routing policy keys, outcome weights, disposition profiles, and risk
decisions require Java construction. YAML-only application definition cannot
configure governance aspects of agent routing and oversight.

Four types from the original issue (#252) are runtime data records, not config:
AttestationContext, AttestationIntent, ClassificationContext, GateOutcome.
These are excluded (marked N/A in coverage matrix). See D1.

## Solution

Add spec records and registries to agentic-yaml for 5 §19/§20 capabilities.
These are standalone registries referenced by other compilers — no new root
type, compiler, or `@BuildStep` needed.

## Spec Records

### TrustRoutingPolicyKeysSpec (adapted record)

The runtime `TrustRoutingPolicyKeys` is a builder-pattern class in engine-api
with `PreferenceKey<>` typed fields. The spec captures the YAML-friendly config
(scope prefix + floor dimension mappings) and the registry calls the builder.

```java
public record TrustRoutingPolicyKeysSpec(
        String scopePrefix,
        @Nullable Map<String, String> floors) {

    public TrustRoutingPolicyKeysSpec {
        Objects.requireNonNull(scopePrefix, "scopePrefix");
        if (floors != null) floors = Map.copyOf(floors);
    }
}
```

`floors` maps dimension name → key suffix. Each entry becomes a
`withFloor(dimension, keySuffix)` call on the builder.

YAML example:
```yaml
trust-routing:
  scopePrefix: aml
  floors:
    investigation-accuracy: investigation-accuracy-floor
    sar-quality: sar-quality-floor
```

### CbrOutcomeWeightsSpec (weight map record)

```java
public record CbrOutcomeWeightsSpec(
        Map<RoutingOutcome, Double> weights) {

    public CbrOutcomeWeightsSpec {
        Objects.requireNonNull(weights, "weights");
        weights = Map.copyOf(weights);
    }
}
```

Uses `RoutingOutcome` enum keys directly — Jackson deserializes enum constant
names. Compile-time validation against the 7-value enum (SUCCESS, FAILURE,
GATE_REJECTED, GATE_EXPIRED, DECLINED, CANCELLED, OBSOLETE).

YAML example:
```yaml
cbr-outcome-weights:
  weights:
    SUCCESS: 1.0
    GATE_EXPIRED: 0.5
    GATE_REJECTED: 0.25
    FAILURE: 0.0
```

### CoordinationOutcomeWeightsSpec (weight map record)

```java
public record CoordinationOutcomeWeightsSpec(
        Map<String, Double> weights) {

    public CoordinationOutcomeWeightsSpec {
        Objects.requireNonNull(weights, "weights");
        weights = Map.copyOf(weights);
    }
}
```

String keys — case outcomes are domain-dependent (not a fixed enum).

YAML example:
```yaml
coordination-outcome-weights:
  weights:
    COMPLETED: 1.0
    FAULTED: 0.2
    CANCELLED: 0.0
```

### DispositionProfile — direct reuse

`DispositionProfile` is already a Jackson-compatible record with
`Map<DispositionAxis, String> desired` and `@Nullable Map<DispositionAxis, Double> weights`.
The compact constructor handles null weights → empty map. No spec wrapper needed.

Note: `DispositionAxis` enum constants (SOCIAL_ORIENTATION, RULE_FOLLOWING,
RISK_APPETITE, AUTONOMY, CONFLICT_MODE) are used as YAML map keys — Jackson
uses `Enum.name()` for map keys by default. If `DispositionAxis` has
`@JsonValue` on `jsonKey()`, the camelCase forms (socialOrient, ruleFollowing,
etc.) would be used instead — verify during implementation. This is the blocks
routing type — distinct from eidos `AgentDisposition`
(see GE-20260811-e941cc).

YAML example:
```yaml
disposition:
  desired:
    SOCIAL_ORIENTATION: collaborative
    RISK_APPETITE: conservative
    RULE_FOLLOWING: strict
  weights:
    RISK_APPETITE: 0.8
    RULE_FOLLOWING: 0.6
```

### RiskDecisionSpec (sealed interface)

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = RiskDecisionSpec.Autonomous.class,
                       name = "autonomous"),
    @JsonSubTypes.Type(value = RiskDecisionSpec.GateRequired.class,
                       name = "gate-required")
})
public sealed interface RiskDecisionSpec {

    record Autonomous() implements RiskDecisionSpec {}

    record GateRequired(
            String reason,
            boolean reversible,
            CandidateSetStrategy candidateGroups,
            Duration expiresIn,
            @Nullable String scope,
            @Nullable String resolutionType,
            @Nullable QuorumConfig quorum) implements RiskDecisionSpec {

        public GateRequired {
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(candidateGroups, "candidateGroups");
            Objects.requireNonNull(expiresIn, "expiresIn");
        }
    }
}
```

`CandidateSetStrategy` and `QuorumConfig` are reused directly from engine-api
(both are already Jackson-annotated). See D3.

`resolutionType` is `String` (class name) — the runtime `RiskDecision.GateRequired`
uses `@Nullable Class<?>`. The registry resolves the class name via
`Class.forName()`. Same adaptation pattern as PromptSignatureSpec.

YAML example:
```yaml
risk-decision:
  type: gate-required
  reason: SAR filing requires human review
  reversible: false
  candidateGroups:
    type: static
    groups:
      - mlro
      - compliance-officer
  expiresIn: PT24H
  scope: sar-filing
  quorum:
    instances: 2
    required: 1
    allowSameAssignee: false
```

## Registries

### TrustRoutingPolicyKeysRegistry

```java
public class TrustRoutingPolicyKeysRegistry {

    public TrustRoutingPolicyKeys resolve(TrustRoutingPolicyKeysSpec spec) {
        var keys = TrustRoutingPolicyKeys.create(spec.scopePrefix());
        if (spec.floors() != null) {
            for (var entry : spec.floors().entrySet()) {
                keys = keys.withFloor(entry.getKey(), entry.getValue());
            }
        }
        return keys;
    }
}
```

### CbrOutcomeWeightsRegistry

```java
public class CbrOutcomeWeightsRegistry {

    public CbrOutcomeWeights resolve(CbrOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}
```

Returns a lambda implementing the `CbrOutcomeWeights` SPI.

### CoordinationOutcomeWeightsRegistry

```java
public class CoordinationOutcomeWeightsRegistry {

    public CoordinationOutcomeWeights resolve(CoordinationOutcomeWeightsSpec spec) {
        var weights = Map.copyOf(spec.weights());
        return () -> weights;
    }
}
```

### RiskDecisionRegistry

```java
public class RiskDecisionRegistry {

    public RiskDecision resolve(RiskDecisionSpec spec) {
        return switch (spec) {
            case RiskDecisionSpec.Autonomous ignored ->
                    new RiskDecision.Autonomous();
            case RiskDecisionSpec.GateRequired g -> {
                Class<?> resolutionType = null;
                if (g.resolutionType() != null) {
                    try {
                        resolutionType = Class.forName(g.resolutionType());
                    } catch (ClassNotFoundException e) {
                        throw new IllegalArgumentException(
                                "Unknown resolutionType: " + g.resolutionType(), e);
                    }
                }
                yield new RiskDecision.GateRequired(
                        g.reason(), g.reversible(), g.candidateGroups(),
                        g.expiresIn(), g.scope(), resolutionType, g.quorum());
            }
        };
    }
}
```

No registry needed for `DispositionProfile` — it's directly reused.

## Schema Generation

Add to `AgencySchemaGenerator.buildDiscriminatorOverrides()`:
- `RiskDecisionSpec.class`

Add to schema generation (non-discriminator):
- `TrustRoutingPolicyKeysSpec.class`
- `CbrOutcomeWeightsSpec.class`
- `CoordinationOutcomeWeightsSpec.class`

`DispositionProfile` is already a record in blocks — add to schema if not
already present.

## Coverage Matrix Update

§19 Trust & Routing Config:
- TrustRoutingPolicyKeys → Done (adapted record)
- CbrOutcomeWeights → Done (weight map spec)
- CoordinationOutcomeWeights → Done (weight map spec)
- DispositionProfile → Done (direct reuse)
- AttestationContext → N/A (runtime data)
- AttestationIntent → N/A (runtime data)

§20 Oversight:
- RiskDecision → Done (sealed interface spec)
- ClassificationContext → N/A (runtime data)
- GateOutcome → N/A (runtime data)

## Test Plan

| Test | What it verifies |
|------|-----------------|
| `GovernanceSpecSerializationTest` | Round-trip YAML for all spec records |
| `GovernanceRegistryTest` | All registry resolve methods including Class.forName adaptation |
| `DispositionProfileSerializationTest` | Direct reuse round-trip with nullable weights |

## References

- `engine-api: io.casehub.api.spi.routing.TrustRoutingPolicyKeys` — builder class
- `engine-api: io.casehub.api.spi.RiskDecision` — sealed interface
- `engine-api: io.casehub.api.spi.CandidateSetStrategy` — reused directly (D3)
- `engine-api: io.casehub.api.spi.QuorumConfig` — reused directly
- `engine-api: io.casehub.api.model.RoutingOutcome` — 7-value enum
- `blocks: io.casehub.agency.governance.routing.agent.CbrOutcomeWeights` — SPI
- `blocks: io.casehub.agency.governance.routing.agent.CoordinationOutcomeWeights` — SPI
- `blocks: io.casehub.agency.governance.routing.agent.DispositionProfile` — record (direct reuse)
- `eidos-api: io.casehub.eidos.api.DispositionAxis` — 5-value enum
- GE-20260811-e941cc — DispositionProfile vs AgentDisposition type split
- GE-20260530-9cdfb5 — MapPreferences.get() null default (TrustRoutingPolicyKeys context)
- GE-20260607-285229 — TrustRoutingPolicy breaking changes
- specs/issue-251-execution-infrastructure/ — prior spec pattern reference
- [GitHub #252](https://github.com/casehubio/agency/issues/252)
