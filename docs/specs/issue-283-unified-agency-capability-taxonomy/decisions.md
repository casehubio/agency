# Decisions — Unified Capability Taxonomy

## D1: Taxonomy scope — all entries as first-class

**Choice:** All 27+ mosaic entries become first-class values in a unified Java enum,
regardless of whether they have a YAML definition.
**Alternatives:**
- Only YAML-backed types — drops runtime/engine concepts the UI already shows
- Two-tier (formal + documented) — adds complexity without clear benefit
**Rationale:** The taxonomy is the exhaustive catalog of CaseHub capabilities.
Runtime services (reflection, memory-hygiene) and engine subsystems (choreography, saga)
are capabilities even though they lack YAML definitions. The UI needs all of them.
**Trade-offs:** The enum will contain entries that have no corresponding YAML root type.
Schema generator handles this by emitting structural schema only for YAML-backed entries
and catalog-only entries for the rest.
**Sources:** casehubio/casehub-mosaic#283, mosaic-core/src/types/patterns/
**Exploration:** quick
**Status:** captured

## D2: Enum home — agency-core

**Choice:** CapabilityType enum and CapabilityCategory enum live in agency-core.
**Alternatives:**
- engine-api — engine-api contains exclusively SPIs and contracts (AgentRoutingStrategy,
  MeshParticipationStrategy, CaseChannelLayout, ProvisionerConfigRegistry, RoutingPromptSection).
  A taxonomy enum is metadata, not a contract. No consumer in engine or eidos needs
  CapabilityType. Placing it there expands engine-api's scope beyond execution contracts.
- platform-api — violates platform-api scope (zero-dep foundational SPIs, not orchestration concepts)
**Rationale:** Agency owns the orchestration framework that composes all CaseHub patterns.
PatternType already lives in agency-core. AgencySchemaGenerator lives in agentic-yaml
(depends on agency-core). The consumers who need the taxonomy — the catalog generator
and mosaic (via generated schema) — already depend on agency. Placing the taxonomy in
agency-core keeps it alongside the types it catalogs.
**Trade-offs:** Agency claims taxonomy authority for capabilities it doesn't implement
(e.g., pages' playbook patterns). This is architecturally justified: agency is the
orchestration layer that composes all patterns, including pages' playbook patterns.
PatternType already includes SEQUENCE, PARALLEL, LOOP, CONDITIONAL.
**Sources:** PatternType in agency-core, AgencySchemaGenerator in agentic-yaml,
docs/platform/capability-ownership.md, docs/platform/boundary-rules.md
**Exploration:** quick
**Status:** revised (Round 1 — moved from engine-api to agency-core; R1-05 factual
error corrected: PatternType is in agency-core not engine-adapter-core; R1-06 accepted:
engine-api scope is contracts not metadata; R1-07 accepted: visibility ≠ need)

## D3: Naming convention — semantic split

**Choice:** *Definition for standalone YAML document roots, *Spec for composable sub-shapes.
**Alternatives:**
- Everything *Spec — simple but loses the root-vs-part semantic signal
- Everything *Definition — 47 renames for debatable semantic improvement
**Rationale:** Codifies the existing implicit pattern. The 3 *Definition types in agentic-yaml
(CognitionDefinition, WorldDefinition, PromptOptimisationDefinition) and all 6 *Definition
types in summarisation-yaml are already document roots. The ~50 *Spec types are composable parts.
**Trade-offs:** Requires renaming PatternSpec → PatternDefinition, NegotiationSpec →
NegotiationDefinition, ConversationSpec → ConversationDefinition. Verified: zero leaf app
imports of these types. Rename scope is contained to agency + slot repos. PatternSpec has
a dual-use case (embedded in AgentRefSpec.Composed) — the *Definition suffix denotes
primary identity (has a top-level YAML file), not a structural constraint against composition.
**Sources:** agentic-yaml spec package structure, summarisation-yaml package structure
**Exploration:** quick
**Status:** captured

## D4: TS consumption — generated catalog + staleness test

**Choice:** A dedicated CapabilityCatalogGenerator emits the full CapabilityType catalog
(entries + categories + hasYamlRoot flag) into a JSON artifact. Mosaic derives its TS types
from that catalog. CI staleness test compares generated catalog against mosaic's committed
types.
**Alternatives:**
- Shared YAML definition file — adds code-gen to both sides
- Direct TS generation from Java — tight coupling, build complexity
- Overload AgencySchemaGenerator — conflates structural JSON Schema generation
  (its current concern) with catalog metadata emission (a fundamentally different output)
**Rationale:** Catalog generation (emitting a list of {name, category, hasYamlRoot} entries)
is a different responsibility from structural JSON Schema generation. AgencySchemaGenerator
uses victools to produce structural schemas from Java record types; the catalog generator
reads from CapabilityType/CapabilityCategory enums and emits a registry listing. Keeping
these as separate classes preserves single responsibility.
**Trade-offs:** Mosaic still hand-maintains TS interfaces (they carry UI-specific concerns:
display names, icons, form layouts, component props). The staleness test validates the
catalog structure — names, categories, entry count — not the full TS interfaces. This
two-track design is intentional: catalog structure is a structural fact (automated), UI
rendering is a design decision (manual). When a capability changes shape, the catalog test
is correctly silent — the capability still exists with the same name and category. TS
interface updates require human judgment about how to surface new fields.
**Sources:** AgencySchemaGenerator in agentic-yaml, mosaic-core/src/types/patterns/
**Exploration:** quick
**Status:** revised (Round 1 — R1-12 accepted: separate CapabilityCatalogGenerator
replaces overloading AgencySchemaGenerator; corrected "already in place" claim)

## D5: Enum relationship — two enums with bridge

**Choice:** Keep PatternType (8 values) in agency-core for pattern-specific runtime logic.
Add CapabilityType (all entries) in agency-core with category(). Bridge methods:
PatternType.toCapabilityType() and CapabilityType.toPatternType() (returns Optional).
**Alternatives:**
- One enum, filtered switches — pollutes pattern-specific logic with 19+ non-pattern entries
- CapabilityType as sealed interface with subtypes — complex type hierarchy for 8 values
**Rationale:** PatternSpec (sealed interface, 8 permits) provides compile-time exhaustiveness
in PatternCompiler's 3 exhaustive switch statements (resolvePatternType, resolveRouting,
resolveTermination — all switch on PatternSpec). PatternType provides a closed domain for
pattern-specific runtime logic (resolveAggregation switches on PatternType with appropriate
defaults for PARALLEL, VOTING, DEBATE; other patterns fall through to PassThrough).
CapabilityType (27+ entries) serves a different purpose — cataloging all capabilities for
UI and schema generation. Keeping them separate avoids polluting pattern-specific switches
with non-pattern entries. Both enums live in agency-core (per D2 revised), reducing bridge
maintenance to a single-repo concern verified by tests.
**Trade-offs:** Two enums means two places to update when adding a pattern type, plus bridge
methods. All update sites are in agency-core — a single commit, single code review, single
build. A test verifies the bridge is complete.
**Sources:** PatternCompiler (3 exhaustive switches on PatternSpec, 1 PatternType switch
with default), PatternType in agency-core
**Exploration:** quick
**Status:** revised (Round 1 — R1-15: corrected switch count from "4 exhaustive on
PatternType" to "3 exhaustive on PatternSpec + 1 non-exhaustive on PatternType";
R1-05: corrected module reference from engine-adapter-core to agency-core)

## D6: Categories — 8 including PLAYBOOK

**Choice:** CapabilityCategory has 8 values: ORCHESTRATION, DELIBERATION, PERCEPTION,
COMMUNICATION, GOVERNANCE, OPTIMISATION, SOCIAL, PLAYBOOK. Includes pages-owned patterns.
**Alternatives:**
- 7 categories (exclude PLAYBOOK) — taxonomy doesn't cover all CaseHub patterns
- Rename to PlatformCategory — correct but "platform" has a specific meaning in CaseHub
**Rationale:** The unified taxonomy covers ALL CaseHub step patterns and capabilities.
Playbook patterns (sequence, parallel, conditional, loop, match, try-catch) are CaseHub
patterns even though pages owns their implementation. PatternType already includes
SEQUENCE, PARALLEL, LOOP, CONDITIONAL.
**Category criteria:** A category exists when capabilities share (1) a common execution
model or behavioral contract, (2) a common owner or tier provenance, or (3) a distinct
UI grouping need. A capability belongs to a category based on its primary behavioral
contract. Cross-concern capabilities (e.g., NEGOTIATION uses both protocol and deliberation
aspects) are assigned to the category that best describes their primary execution model.
**Trade-offs:** "CapabilityCategory" is a better name than "AgencyCategory" since the
taxonomy spans beyond agency (includes pages playbook patterns).
**Sources:** mosaic-core categories.ts, PatternType enum values
**Exploration:** quick
**Status:** revised (Round 1 — R1-19: added principled criteria for category membership)

## D7: Marker interface — not needed

**Choice:** No marker interface or annotation on YAML root types. CapabilityType enum IS the
registry. The catalog generator maintains an internal mapping from YAML root type →
CapabilityType.
**Alternatives:**
- @CapabilityDefinition annotation — formal but adds coupling, no clear consumer
- Marker interface — some entries have no YAML root, so the interface would be partial
**Rationale:** The catalog generator (in agentic-yaml) already knows which types to generate
schema for. With CapabilityType in agency-core (which agentic-yaml depends on), the
generator looks up category metadata from the enum directly. Adding an annotation or
marker interface creates coupling without adding capability — spec types shouldn't need
to know about their categorization.
**Trade-offs:** The mapping is in code (catalog generator), not in metadata. If a new YAML
root type is added, both the CapabilityType enum and the catalog generator's internal
mapping need updating. Both are in the agency repo. A test verifies completeness.
**Sources:** AgencySchemaGenerator in agentic-yaml
**Exploration:** quick
**Depends on:** D1 (all entries as first-class), D2 (enum in agency-core)
**Status:** captured

## D8: Version evolution — single-repo coordination

**Choice:** CapabilityType changes are coordinated within the agency repo. Downstream
consumers (mosaic) are notified via the staleness test in CI.
**Alternatives:**
- Cross-repo coordinated release — would have been required if enum were in engine-api;
  agency, engine, and mosaic would need synchronized releases
- Runtime registry — avoids cross-repo coordination entirely but loses compile-time
  exhaustiveness guarantees
**Rationale:** With CapabilityType in agency-core (D2 revised), adding a new capability
is a single-repo change: add the enum value, update the catalog generator, update bridge
methods if it's a pattern. Agency publishes, mosaic consumes via generated catalog artifact.
The staleness test in mosaic's CI detects drift and fails the build until mosaic updates
its TS types. This is a one-directional dependency, not a coordination chain.
**Trade-offs:** Mosaic has a build-time dependency on agency's generated catalog artifact.
This is by design — the taxonomy is authoritative in Java, consumed in TS.
**Sources:** D2 (revised), D4 (revised)
**Exploration:** quick (surfaced by reviewer R1-23)
**Depends on:** D2
**Status:** captured

## D9: Flat namespace with category grouping

**Choice:** CapabilityType is a flat enum. CapabilityCategory provides one-level grouping
via CapabilityType.category(). No hierarchical type nesting.
**Alternatives:**
- Three-level hierarchy (category → capability → variant) — adds type complexity without
  demonstrated UI need; variants within a capability (e.g., supervisor modes) are
  configuration options in YAML specs, not taxonomy entries
- Nested enum (CapabilityCategory containing CapabilityType) — Java enums don't support
  nesting natively; workarounds add complexity
**Rationale:** The enum + category() method provides a two-level taxonomy (category →
capability). The UI groups capabilities by category for rendering. No current use case
requires a third level. A capability that spans concerns (e.g., NEGOTIATION is both
protocol and deliberation) must choose one primary category — acceptable since the
category is for UI grouping, not semantic classification.
**Trade-offs:** If future evolution requires sub-variants, they would be modeled as
configuration within the capability's YAML spec rather than as taxonomy entries. This
keeps the taxonomy stable while capabilities evolve internally.
**Sources:** mosaic-core categories.ts, PatternType enum values
**Exploration:** quick (surfaced by reviewer R1-24)
**Status:** captured

## D10: Direction of truth — Java → TS

**Choice:** Java defines the taxonomy structure (names, categories, hasYamlRoot flag). TS
consumes it via generated catalog artifact. TS adds UI-specific metadata (display names,
icons, form layouts) on top.
**Alternatives:**
- TS → Java — UI layout concerns would drive type-system decisions; a phantom capability
  with no Java backing is nonsensical
- Shared YAML source — adds code-gen complexity to both Java and TS build pipelines
**Rationale:** The taxonomy is fundamentally a structural fact: "these capabilities exist
and are categorized this way." This structure is defined by Java types — adding a new
capability means adding a new spec type, a new compiler method, new tests. The Java code
change is where the capability comes into existence. The UI's rendering of that taxonomy
is a design decision layered on top. The staleness test validates structural alignment;
the TS interfaces add richer metadata that Java doesn't need.
**Trade-offs:** The UI team cannot add a new capability without a Java change. This is
intentional — every entry in the taxonomy must have a backing implementation.
**Sources:** AgencySchemaGenerator in agentic-yaml, mosaic-core/src/types/patterns/
**Exploration:** quick (surfaced by reviewer R1-25)
**Depends on:** D4
**Status:** captured
