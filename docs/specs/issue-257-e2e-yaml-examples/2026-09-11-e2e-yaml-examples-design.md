# End-to-End YAML Examples — Design Spec

## Goal

Six consumer-facing examples demonstrating the full blocks YAML surface.
Each is a compilable YAML scenario with Java tests proving the YAML
compiles through the full pipeline. Shared infrastructure so developers
see how to build similar solutions.

## Architecture

### File layout (per example)

```
examples/<scenario>/
├── pattern.yaml          # agentic orchestration
├── pipeline.yaml         # summarisation levels (if applicable)
├── cognition.yaml        # agent personality, drives, mood
├── world.yaml            # what agents can perceive and do
└── case.yaml             # engine case definition (extends engine YAML)
```

Not every example needs every file — only the YAML files relevant to
what that example demonstrates.

### Shared test infrastructure

`ExampleTestBase` — abstract base class providing:
- `ObjectMapper` with YAML + JavaTimeModule pre-configured
- `PatternCompiler`, `CognitionCompiler`, `WorldCompiler` instances
- Helper: `loadPattern(scenario, filename)` → `PatternDefinition`
- Helper: `loadCognition(scenario)` → `CognitionDefinition`
- Helper: `loadWorld(scenario)` → `WorldDefinition`
- Helper: `loadPipeline(scenario)` → `PipelineDefinition`
- Resource path: `/examples/<scenario>/<file>.yaml`

Each example has its own test class extending `ExampleTestBase`.

### Engine YAML integration

Examples 3-5 extend engine case definitions. They include `case.yaml`
using the engine's `dsl: "1.0.0"` format alongside blocks YAML files.
The test verifies blocks compilation; the `case.yaml` is documentation
showing how the two surfaces compose.

---

## Examples

### 1. Fleet Logistics (pure YAML)

**Scenario:** A logistics company monitors its delivery fleet. GPS pings,
delivery confirmations, and delay reports flow in as raw events.

**Summarisation pipeline (pipeline.yaml):**
- L1 (raw): CloudEvent ingestion — GPS pings, delivery status, delay reports
- L2 (grouped): Keyed by vehicle ID — per-truck activity summary
  (position history, deliveries completed, delays encountered)
- L3 (aggregated): Keyed by route — route health score
  (on-time %, delay patterns, bottleneck locations)
- L4 (decision): Threshold classification — routes flagged as
  NORMAL / DEGRADED / CRITICAL based on delay ratio and delivery rate

**Agentic pattern (pattern.yaml):**
- Supervisor with 3 agents:
  - `route-analyst` — examines degraded routes, identifies root causes
  - `dispatcher` — recommends rerouting for critical routes
  - `reporter` — compiles fleet health summary
- Judgment: iteration-based (every 3 cycles) with single caller
- Failure policy: RETRY_BROADER on routing failure

**Cognition (cognition.yaml):**
- Drive config: high COMPETENCE weight (performance-oriented)
- Mood: moderate displacement, stable baseline
- Narrative: episode tracking for fleet incidents

**Demonstrates:** Multi-level summarisation with keyed grouping,
threshold classification driving agent dispatch, full cognition stack.

---

### 2. Hub Monitoring (pure YAML)

**Scenario:** A distribution hub has sensors across zones — temperature
monitors, throughput counters, equipment status beacons.

**Summarisation pipeline (pipeline.yaml):**
- L1 (raw): Sensor readings — temperature, throughput, equipment heartbeats
- L2 (grouped): Keyed by zone ID — zone health summary
  (avg temp, throughput rate, equipment online count)
- L3 (phase detection): Phase-detect state machine per zone:
  NORMAL → DEGRADED (temp out of range OR throughput below threshold)
  DEGRADED → CRITICAL (multiple sensors in alarm OR equipment offline)
  CRITICAL → NORMAL (all sensors return to range)
- L4 (decision): Count summariser — active alerts per severity level

**Agentic pattern (pattern.yaml):**
- Conditional pattern with 3 branches:
  - CRITICAL → `safety-officer` agent (immediate response)
  - DEGRADED → `maintenance-coordinator` agent (schedule repair)
  - NORMAL with anomaly count > 0 → `logistics-planner` (preventive action)
- Termination: single-pass (one dispatch per evaluation cycle)

**World model (world.yaml):**
- Entities: zones, sensors, equipment (with affordances: inspect, isolate, reset)
- Sections: zone status (entity groups), alert summary (item list)
- Annotated section: equipment internals (requires `maintenance` tag)

**Demonstrates:** Phase-detect state machine, conditional pattern routing
by severity, world model with capability-gated visibility.

---

### 3. Research Analysis (extends engine LLM example)

**Scenario:** Extends `llm-research-analysis.yaml` — adds peer review
via a blocks debate pattern and quality judgment.

**Engine case (case.yaml):** Based on the engine's LLM research analysis
with capabilities for gathering, analysis, and synthesis.

**Agentic pattern (pattern.yaml):**
- Debate pattern with 2 agents + judge:
  - `methodology-critic` — challenges research methodology
  - `evidence-critic` — challenges evidence quality and sourcing
  - `senior-reviewer` — judge, resolves disputes
- maxRounds: 3
- Termination: all-agreed OR max-iterations(6)
- Judgment: always-yield with single caller

**Cognition (cognition.yaml):**
- Different personality configs per agent role:
  - Critics: high CURIOSITY drive, skeptical mood baseline
  - Reviewer: balanced drives, high AUTONOMY
- Strategy learning: interaction strategy adaptation enabled
- Mental model: confidence floor 0.3 for cross-agent ToM

**Demonstrates:** Debate pattern with judgment, per-agent cognition
differentiation, layering blocks patterns on engine case definitions.

---

### 4. Contract Review (extends engine GOAP example)

**Scenario:** Extends `goap-contract-review.yaml` — adds oversight
risk classification and a world model for document affordances.

**Engine case (case.yaml):** Based on the engine's GOAP contract review
with precondition/effect planning for review steps.

**Agentic pattern (pattern.yaml):**
- Voting pattern with 3 reviewers:
  - `legal-analyst` — regulatory compliance check
  - `financial-analyst` — financial terms assessment
  - `risk-analyst` — risk exposure evaluation
- Aggregation: majority-vote
- Judgment: escalation chain (junior → senior → principal)

**World model (world.yaml):**
- Entities: contract sections (with affordances: examine, annotate, flag)
- Actions: EXAMINE, ANNOTATE, FLAG, APPROVE, REJECT
- Annotated sections: confidential clauses (requires `senior-review` tag),
  financial terms (requires `financial-clearance` tag)
- Perception filter: role-based visibility gating

**Trust routing config (included in pattern.yaml):**
- CbrOutcomeWeights for reviewer selection
- DispositionProfile: desired cautious + detail-oriented disposition

**Demonstrates:** Voting + judgment escalation, world model with
capability-gated document sections, trust routing, oversight config.

---

### 5. Market Intelligence (extends engine A2A example)

**Scenario:** Extends `a2a-market-research.yaml` — adds a supervisor
with composed parallel sub-teams and trust routing.

**Engine case (case.yaml):** Based on the engine's A2A market research
with remote agent invocation.

**Agentic pattern (pattern.yaml):**
- Supervisor with 2 composed teams + 1 synthesiser:
  - `primary-research` (composed parallel):
    - `web-analyst` — public source intelligence
    - `database-analyst` — proprietary data analysis
    - `social-analyst` — social media sentiment
  - `competitive-intel` (composed parallel):
    - `patent-analyst` — IP landscape
    - `financial-analyst` — competitor financials
  - `chief-analyst` — synthesises all research
- Routing: first-match with guard expression
- Failure policy: ESCALATE on deadlock
- Termination: max-iterations(15) OR goal-reached

**Trust routing:**
- TrustRoutingPolicyKeys with threshold and blend factor
- CoordinationOutcomeWeights for team composition scoring

**Demonstrates:** Nested composed patterns, supervisor with
expression-guarded routing, trust routing config, team composition.

---

### 6. Incident Response (escape hatch — YAML + Java)

**Scenario:** An operations team responds to production incidents.
Most orchestration is YAML; specific pieces need Java because the
computation is inherently procedural.

**What's in YAML:**
- Supervisor pattern with escalation chain judgment
- Cognition config for responder agents
- World model with incident affordances (acknowledge, investigate,
  mitigate, escalate, resolve)
- Summarisation pipeline: incident events → severity timeline →
  impact assessment

**What's in Java (the genuine boundary):**

| Java class | Why YAML can't express it |
|------------|--------------------------|
| `IncidentSeveritySummariser` | `ContentSummariser<IncidentEvent, SeverityReport>` — the leaf summarisation logic that classifies incident severity from raw events. The pipeline structure is YAML; the classification algorithm is domain code. |
| `OnCallBidExtractor` | `BidExtractor` for auction aggregation — scores which responder is best-positioned based on on-call schedule lookups from an external API. The auction pattern is YAML; the scoring function is code. |
| `ClearanceObservationFilter` | `ObservationFilter` — determines whether an agent can see classified incident data based on runtime security clearance checks. The world model is YAML; the visibility predicate is code. |

Each is a single class implementing a `@FunctionalInterface` —
minimal Java, maximum demonstration of where the boundary is.

**Test structure:**
- YAML files compile through standard pipeline (same as examples 1-5)
- Java classes are instantiated in the test and wired via
  `registerFallback()` on the appropriate registries
- Test verifies the combined YAML + Java model produces a working
  `ExecutionModel` with the custom strategies in place

**Demonstrates:** The escape hatch — where YAML ends and Java begins.
Registry fallback mechanism (#245) for CDI-provided strategies.

---

## Test Strategy

Each example has a test class `<Scenario>ExampleTest extends ExampleTestBase`:

1. **Compilation test** — all YAML files parse and compile without error
2. **Structure test** — compiled model has expected types (pattern type,
   routing strategy, aggregation, termination, agent count)
3. **Integration test** — cross-file composition works (cognition config
   applied to agents declared in pattern, world model entities resolve)
4. **Escape hatch test** (example 6 only) — custom Java classes wire
   through `registerFallback()` and the compiled model uses them

## References

- Engine YAML examples: `casehub-engine/examples/yaml/`
- Blocks agentic-yaml test fixtures: `agentic-yaml/src/test/resources/examples/`
- Coverage matrix: `docs/yaml-coverage.md`
- Registry extensibility: #245
- Comprehensive examples: #256
