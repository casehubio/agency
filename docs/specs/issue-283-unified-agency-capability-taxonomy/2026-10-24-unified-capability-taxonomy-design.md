# Unified Capability Taxonomy — Design Spec

**Epic:** casehubio/casehub-mosaic#283
**Date:** 2026-10-24
**Branch:** issue-283-unified-agency-capability-taxonomy
**Slot:** 219 (agency, engine, neocortex, casehub-mosaic)

## Problem

CaseHub capabilities are defined across Java repos (agency, neocortex, qhorus, engine)
and consumed by TypeScript (mosaic, pages). The type definitions have drifted in three ways:

1. **Naming inconsistency** — `*Spec` in agentic-yaml (47+ types), `*Definition` in
   summarisation-yaml (9 types), mixed in cognition. Same semantic role, different suffix.
2. **No shared taxonomy** — the 7-category structure exists implicitly in package hierarchy
   (agency#337) but no enum or type codifies it.
3. **TS/Java divergence** — mosaic hand-authored 27 entries across 8 categories with no
   staleness enforcement against Java.

## Design

### Representation: flat in Java, hierarchical in TS

Java uses flat enums with category metadata. TS uses a discriminated union with
per-category interfaces. Both representations are valid for their context:

- **Java** — flat enums enable exhaustive switch statements (PatternCompiler) and
  pattern matching. Category is a method on the enum, not a type hierarchy change.
- **TS** — hierarchical union types drive UI navigation (gallery categories, sidebar
  grouping). Each category interface carries UI-specific fields (icons, form layouts).

**Contract:** 1:1 mapping between Java CapabilityType values and TS PatternCategory
entries. No gaps in either direction. A CI staleness test enforces this.

### New types (in agency-core)

#### CapabilityCategory enum

```java
public enum CapabilityCategory {
    ORCHESTRATION,
    DELIBERATION,
    PERCEPTION,
    COMMUNICATION,
    GOVERNANCE,
    OPTIMISATION,
    SOCIAL,
    PLAYBOOK
}
```

Category membership criteria: a category exists when capabilities share (1) a common
execution model or behavioral contract, (2) a common owner/tier provenance, or (3) a
distinct UI grouping need. A capability's primary behavioral contract determines its
category.

#### CapabilityType enum

```java
public enum CapabilityType {
    // Orchestration
    SUPERVISOR(ORCHESTRATION),
    CHOREOGRAPHY(ORCHESTRATION),
    SAGA(ORCHESTRATION),
    DECOMPOSITION(ORCHESTRATION),

    // Deliberation
    VOTING(DELIBERATION),
    DEBATE(DELIBERATION),
    NEGOTIATION(DELIBERATION),
    COALITION(DELIBERATION),
    NORMATIVE(DELIBERATION),

    // Perception
    SUMMARISATION(PERCEPTION),
    NARRATIVE(PERCEPTION),
    OBSERVATION(PERCEPTION),

    // Communication
    CHANNEL_EXECUTION(COMMUNICATION),
    CHANNEL_SUMMARY(COMMUNICATION),

    // Governance
    JUDGMENT(GOVERNANCE),
    OVERSIGHT(GOVERNANCE),
    ATTESTATION(GOVERNANCE),
    TRUST(GOVERNANCE),

    // Optimisation
    PROMPT_OPTIMISATION(OPTIMISATION),

    // Social
    MEMORY_HYGIENE(SOCIAL),
    COGNITION(SOCIAL),
    REFLECTION(SOCIAL),

    // Playbook (pages-owned)
    SEQUENCE(PLAYBOOK),
    PARALLEL(PLAYBOOK),
    LOOP(PLAYBOOK),
    CONDITIONAL(PLAYBOOK),
    MATCH(PLAYBOOK),
    TRY_CATCH(PLAYBOOK);

    private final CapabilityCategory category;

    CapabilityType(CapabilityCategory category) {
        this.category = category;
    }

    public CapabilityCategory category() {
        return category;
    }

    public Optional<PatternType> toPatternType() {
        return switch (this) {
            case SUPERVISOR -> Optional.of(PatternType.SUPERVISOR);
            case VOTING -> Optional.of(PatternType.VOTING);
            case DEBATE -> Optional.of(PatternType.DEBATE);
            case DECOMPOSITION -> Optional.of(PatternType.HTN);
            case SEQUENCE -> Optional.of(PatternType.SEQUENCE);
            case PARALLEL -> Optional.of(PatternType.PARALLEL);
            case LOOP -> Optional.of(PatternType.LOOP);
            case CONDITIONAL -> Optional.of(PatternType.CONDITIONAL);
            default -> Optional.empty();
        };
    }
}
```

#### PatternType bridge

PatternType (8 values, existing) gains a `toCapabilityType()` method:

```java
public CapabilityType toCapabilityType() {
    return switch (this) {
        case SUPERVISOR -> CapabilityType.SUPERVISOR;
        case VOTING -> CapabilityType.VOTING;
        case DEBATE -> CapabilityType.DEBATE;
        case HTN -> CapabilityType.DECOMPOSITION;
        case SEQUENCE -> CapabilityType.SEQUENCE;
        case PARALLEL -> CapabilityType.PARALLEL;
        case LOOP -> CapabilityType.LOOP;
        case CONDITIONAL -> CapabilityType.CONDITIONAL;
    };
}
```

The HTN ↔ DECOMPOSITION mapping resolves the existing name mismatch between Java and TS.

### Naming unification

YAML document roots use `*Definition`. Composable sub-shapes use `*Spec`.

**Renames (3 types):**

| Current | New | Module |
|---|---|---|
| `PatternDefinition` | `PatternDefinition` | agentic-yaml |
| `NegotiationDefinition` | `NegotiationDefinition` | agentic-yaml |
| `ConversationDefinition` | `ConversationDefinition` | agentic-yaml |

**Already correct (no change needed):**

| Type | Module |
|---|---|
| `CognitionDefinition` | agentic-yaml |
| `WorldDefinition` | agentic-yaml |
| `PromptOptimisationDefinition` | agentic-yaml |
| `PipelineDefinition` | summarisation-yaml |
| `SummariserDefinition` | summarisation-yaml |

**Sub-shapes stay as `*Spec` (~50 types):**
RoutingSpec, TerminationSpec, AggregationSpec, ActivationSpec, DecompositionSpec,
AgentRefSpec, BranchSpec, TaskNodeSpec, JudgmentSpec, GovernanceSpec, etc.

Note: JudgmentSpec and GovernanceSpec are fields on PatternDefinition (composable
sub-shapes), not standalone document roots. They stay as `*Spec`.

**Blast radius:** Zero leaf app imports of any type being renamed. Rename scope
is contained to agency modules. IntelliJ refactor-rename across the slot workspace
handles all internal references mechanically.

### Catalog generation

A new `CapabilityCatalogGenerator` (in agentic-yaml) emits a JSON catalog artifact:

```json
{
  "version": 1,
  "categories": [
    { "name": "orchestration", "displayName": "Orchestration" },
    ...
  ],
  "capabilities": [
    {
      "name": "supervisor",
      "category": "orchestration",
      "hasYamlRoot": true,
      "yamlRootType": "PatternDefinition"
    },
    {
      "name": "choreography",
      "category": "orchestration",
      "hasYamlRoot": false
    },
    ...
  ]
}
```

This is separate from AgencySchemaGenerator (which produces structural JSON Schema
from Java record types). Different responsibility: catalog enumerates what exists;
schema describes what each type looks like.

### Mosaic staleness test

A CI test in mosaic compares the generated catalog against the committed TS types:

1. Read the catalog JSON artifact (published as a Maven/npm artifact or checked
   into mosaic as a baseline file)
2. For each catalog entry, verify a corresponding TS type exists with matching
   `category` and `type` fields
3. For each TS type, verify a corresponding catalog entry exists
4. Fail if any direction has gaps

This catches drift in both directions: Java adds a capability without TS update,
or TS invents a capability without Java backing.

### Direction of truth

Java defines the taxonomy structure (names, categories, hasYamlRoot). TS consumes
it via the generated catalog artifact and adds UI-specific metadata (display names,
icons, form layouts, component props) on top. The UI team cannot add a new capability
without a Java change — every entry must have a backing implementation.

### Cross-repo implementation order

```
agency#353 (taxonomy enums + naming + catalog generator)
     |
     v
neocortex#673 (naming alignment — follows convention)
engine#1258 (decide choreography/saga formal status — follows taxonomy)
     |
     v
mosaic#284 (consume catalog, staleness test, drift removal)
```

## Constraints

- PatternDefinition (renamed from PatternSpec) sealed interface must stay flat —
  categories cannot be type hierarchy changes
- PatternType enum stays as-is for exhaustive switch in PatternCompiler
  (3 exhaustive switches on PatternDefinition subtypes, 1 on PatternType)
- Pre-release platform — breaking changes cost nothing
- All slot repos (agency, engine, neocortex, mosaic) in a single IntelliJ workspace
  for mechanical refactoring
- Linked issue per repo for each commit

## Out of scope

- Changing the mosaic TS interfaces beyond name/category alignment — UI-specific
  fields (icons, form layouts) remain hand-maintained
- Adding YAML definitions for runtime-only capabilities (choreography, saga, etc.)
- Module splitting — agency-core stays as one Maven module
- Pages playbook pattern implementation changes — only taxonomy metadata

## Test plan

- [ ] CapabilityType.values().length matches expected count (27+)
- [ ] Every PatternType value maps to a CapabilityType and back (bridge completeness)
- [ ] CapabilityType.toPatternType() returns empty for non-pattern capabilities
- [ ] CapabilityCatalogGenerator produces valid JSON with all entries
- [ ] Generated catalog matches CapabilityType enum (no drift between enum and generator)
- [ ] Mosaic staleness test: TS types match catalog 1:1
- [ ] PatternCompiler exhaustive switches still compile (no default cases needed)
- [ ] All existing tests pass after renames (PatternSpec → PatternDefinition, etc.)
- [ ] Schema baseline update (agentic-yaml schema update test)

## References

- casehubio/casehub-mosaic#283 — parent epic
- casehubio/agency#353 — agency child issue
- casehubio/casehub-mosaic#284 — mosaic child issue
- casehubio/neocortex#673 — neocortex child issue
- casehubio/engine#1258 — engine child issue
- casehubio/agency#337 — repo restructure (7-category hierarchy, completed)
- mosaic-core/src/types/patterns/categories.ts — existing TS category types
- mosaic-core/src/types/patterns/base.ts — existing TS per-category interfaces
- agency-core PatternType enum — existing 8-value pattern discriminator
- agentic-yaml PatternSpec — sealed interface (to be renamed PatternDefinition)
- agentic-yaml AgencySchemaGenerator — existing structural schema generator
- docs/platform/capability-ownership.md — 160 capabilities across 30+ repos
- docs/platform/boundary-rules.md — module placement rules
- docs/platform/overview.md — 4-tier architecture
