# End-to-End YAML Examples Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #257 — End-to-end YAML examples
**Issue group:** #257

**Goal:** Six compilable YAML examples demonstrating the full blocks
surface — summarisation pipelines, agentic patterns, cognition, world
models — with shared test infrastructure.

**Architecture:** YAML resources at `agentic-yaml/src/test/resources/examples/<scenario>/`,
shared `ExampleTestBase` in `agentic-yaml/src/test/java/`, one test class
per example. Example 6 adds Java escape-hatch classes in the test tree.
Summarisation pipeline YAML goes through `PipelineCompiler` (summarisation-yaml module);
pattern/cognition/world go through the agentic-yaml compilers.

**Tech Stack:** JUnit 5, AssertJ, Jackson YAML, MVEL3 ExpressionEngine,
PatternCompiler, CognitionCompiler, WorldCompiler, PipelineCompiler,
SummariserRegistry.

## Global Constraints

- All YAML must parse and compile without runtime dependencies (no CDI, no Quarkus)
- Tests are plain JUnit 5 — no container, no injection
- ExpressionEngine uses `MvelExpressionEngine` for guard/expression compilation
- Pipeline YAML uses `PipelineDefinition` from summarisation-yaml module
- Pattern/cognition/world YAML uses agentic-yaml compilers
- Follow existing fixture patterns from `examples/` and `patterns/` directories

---

## Batch 1: Shared Infrastructure + Fleet Logistics

### Task 1: ExampleTestBase shared infrastructure

**Files:**
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/ExampleTestBase.java`

**Interfaces:**
- Produces: `loadPattern(scenario)`, `loadCognition(scenario)`, `loadWorld(scenario)`,
  `loadPipeline(scenario)`, `mapper()`, `patternCompiler()`, `cognitionCompiler()`,
  `worldCompiler()`, `pipelineCompiler()`, `summariserRegistry()`, `expressionEngine()`

- [ ] **Step 1: Write ExampleTestBase**

```java
package io.casehub.blocks.agentic.yaml.examples;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.agentic.yaml.compiler.CognitionCompiler;
import io.casehub.blocks.agentic.yaml.compiler.ObservationFilterRegistry;
import io.casehub.blocks.agentic.yaml.compiler.PatternCompiler;
import io.casehub.blocks.agentic.yaml.compiler.WorldCompiler;
import io.casehub.blocks.agentic.yaml.spec.PatternSpec;
import io.casehub.blocks.agentic.yaml.spec.cognition.CognitionDefinition;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import io.casehub.blocks.summarisation.yaml.PipelineCompiler;
import io.casehub.blocks.summarisation.yaml.PipelineDefinition;
import io.casehub.blocks.summarisation.yaml.SummariserRegistry;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.io.InputStream;

public abstract class ExampleTestBase {

    protected ObjectMapper mapper;
    protected PatternCompiler patternCompiler;
    protected CognitionCompiler cognitionCompiler;
    protected WorldCompiler worldCompiler;
    protected PipelineCompiler pipelineCompiler;
    protected SummariserRegistry summariserRegistry;
    protected MvelExpressionEngine expressionEngine;

    @BeforeEach
    void setUpBase() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        expressionEngine = new MvelExpressionEngine();
        patternCompiler = new PatternCompiler(expressionEngine);
        cognitionCompiler = new CognitionCompiler();
        worldCompiler = new WorldCompiler(new ObservationFilterRegistry());
        pipelineCompiler = new PipelineCompiler();
        summariserRegistry = new SummariserRegistry();
    }

    protected PatternSpec loadPattern(String scenario) throws IOException {
        return load(scenario, "pattern.yaml", PatternSpec.class);
    }

    protected CognitionDefinition loadCognition(String scenario) throws IOException {
        return load(scenario, "cognition.yaml", CognitionDefinition.class);
    }

    protected WorldDefinition loadWorld(String scenario) throws IOException {
        return load(scenario, "world.yaml", WorldDefinition.class);
    }

    protected PipelineDefinition loadPipeline(String scenario) throws IOException {
        return load(scenario, "pipeline.yaml", PipelineDefinition.class);
    }

    private <T> T load(String scenario, String file, Class<T> type) throws IOException {
        String path = "/examples/" + scenario + "/" + file;
        try (InputStream is = getClass().getResourceAsStream(path)) {
            if (is == null) throw new IOException("Resource not found: " + path);
            return mapper.readValue(is, type);
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `mvn --batch-mode test-compile -pl agentic-yaml`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/ExampleTestBase.java
git commit -m "feat(#257): ExampleTestBase shared infrastructure for e2e examples Refs #257"
```

### Task 2: Fleet Logistics YAML + test

**Files:**
- Create: `agentic-yaml/src/test/resources/examples/fleet-logistics/pattern.yaml`
- Create: `agentic-yaml/src/test/resources/examples/fleet-logistics/pipeline.yaml`
- Create: `agentic-yaml/src/test/resources/examples/fleet-logistics/cognition.yaml`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/FleetLogisticsExampleTest.java`

**Interfaces:**
- Consumes: `ExampleTestBase` (loadPattern, loadPipeline, loadCognition, compilers)

- [ ] **Step 1: Write fleet-logistics/pipeline.yaml**

4-level pipeline: raw events → per-vehicle grouping → per-route aggregation → threshold classification.

- [ ] **Step 2: Write fleet-logistics/pattern.yaml**

Supervisor with 3 agents (route-analyst, dispatcher, reporter), judgment (iteration-based every 3),
failure policy (RETRY_BROADER).

- [ ] **Step 3: Write fleet-logistics/cognition.yaml**

Drive config with high COMPETENCE, moderate mood, narrative with episode tracking.

- [ ] **Step 4: Write FleetLogisticsExampleTest**

Tests:
- `pipelineCompiles()` — 4 levels, keyed grouping on L2/L3, threshold-classify on L4
- `patternCompiles()` — SUPERVISOR type, 3 agents, judgment present, failure policy RETRY_BROADER
- `cognitionCompiles()` — drive COMPETENCE weight overridden, mood and narrative present

- [ ] **Step 5: Run tests**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=FleetLogisticsExampleTest`
Expected: 3 tests pass

- [ ] **Step 6: Commit**

```
git add agentic-yaml/src/test/resources/examples/fleet-logistics/ agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/FleetLogisticsExampleTest.java
git commit -m "feat(#257): fleet-logistics example — multi-level summarisation + supervisor Refs #257"
```

---

## Batch 2: Hub Monitoring + Research Analysis

### Task 3: Hub Monitoring YAML + test

**Files:**
- Create: `agentic-yaml/src/test/resources/examples/hub-monitoring/pattern.yaml`
- Create: `agentic-yaml/src/test/resources/examples/hub-monitoring/pipeline.yaml`
- Create: `agentic-yaml/src/test/resources/examples/hub-monitoring/world.yaml`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/HubMonitoringExampleTest.java`

**Interfaces:**
- Consumes: `ExampleTestBase`

- [ ] **Step 1: Write hub-monitoring/pipeline.yaml**

4-level pipeline: sensor readings → zone grouping (keyed) → phase-detect state machine
(NORMAL/DEGRADED/CRITICAL) → count summariser.

- [ ] **Step 2: Write hub-monitoring/pattern.yaml**

Conditional pattern with 3 branches: CRITICAL→safety-officer, DEGRADED→maintenance-coordinator,
NORMAL with anomalies→logistics-planner. Single-pass termination.

- [ ] **Step 3: Write hub-monitoring/world.yaml**

Entities (zones, sensors, equipment with affordances), text/entity-group/item-list sections,
annotated section requiring `maintenance` tag.

- [ ] **Step 4: Write HubMonitoringExampleTest**

Tests:
- `pipelineCompiles()` — 4 levels, keyed grouping, phase-detect with 3 states + transitions
- `patternCompiles()` — CONDITIONAL type, 3 agents, single-pass termination
- `worldCompiles()` — entities with affordances, annotated section, renderer thresholds

- [ ] **Step 5: Run tests, commit**

### Task 4: Research Analysis YAML + test

**Files:**
- Create: `agentic-yaml/src/test/resources/examples/research-analysis/pattern.yaml`
- Create: `agentic-yaml/src/test/resources/examples/research-analysis/cognition.yaml`
- Create: `agentic-yaml/src/test/resources/examples/research-analysis/case.yaml`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/ResearchAnalysisExampleTest.java`

**Interfaces:**
- Consumes: `ExampleTestBase`

- [ ] **Step 1: Write research-analysis/case.yaml**

Based on engine's `llm-research-analysis.yaml` — capabilities for gathering, analysis, synthesis.
Engine `dsl: "1.0.0"` format. This file is documentation (not compiled by blocks tests).

- [ ] **Step 2: Write research-analysis/pattern.yaml**

Debate with 2 critics + judge, maxRounds 3, termination: all-agreed OR max-iterations(6),
judgment: always-yield with single caller.

- [ ] **Step 3: Write research-analysis/cognition.yaml**

Per-role cognition: critics with high CURIOSITY + skeptical mood, reviewer with balanced drives.
Strategy learning enabled, mental model confidence floor 0.3.

- [ ] **Step 4: Write ResearchAnalysisExampleTest**

Tests:
- `patternCompiles()` — DEBATE type, 2 agents, judgment present
- `cognitionCompiles()` — drive, mood, strategyLearning, mentalModel all present
- `caseYamlParses()` — engine YAML parses as generic map (no engine dependency)

- [ ] **Step 5: Run tests, commit**

---

## Batch 3: Contract Review + Market Intelligence

### Task 5: Contract Review YAML + test

**Files:**
- Create: `agentic-yaml/src/test/resources/examples/contract-review/pattern.yaml`
- Create: `agentic-yaml/src/test/resources/examples/contract-review/world.yaml`
- Create: `agentic-yaml/src/test/resources/examples/contract-review/case.yaml`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/ContractReviewExampleTest.java`

**Interfaces:**
- Consumes: `ExampleTestBase`

- [ ] **Step 1: Write contract-review/case.yaml**

Based on engine's GOAP contract review. Documentation only.

- [ ] **Step 2: Write contract-review/pattern.yaml**

Voting with 3 reviewers, majority-vote, judgment: escalation-chain (junior/senior/principal).

- [ ] **Step 3: Write contract-review/world.yaml**

Entities: contract sections with affordances (examine, annotate, flag).
Actions: EXAMINE, ANNOTATE, FLAG, APPROVE, REJECT.
Annotated sections: confidential (requires `senior-review`), financial (requires `financial-clearance`).
Perception filter pipeline.

- [ ] **Step 4: Write ContractReviewExampleTest**

Tests:
- `patternCompiles()` — VOTING type, majority-vote, 3 agents, judgment present
- `worldCompiles()` — 5 actions, entities with affordances, 2 annotated sections with required tags, pipeline

- [ ] **Step 5: Run tests, commit**

### Task 6: Market Intelligence YAML + test

**Files:**
- Create: `agentic-yaml/src/test/resources/examples/market-intelligence/pattern.yaml`
- Create: `agentic-yaml/src/test/resources/examples/market-intelligence/case.yaml`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/MarketIntelligenceExampleTest.java`

**Interfaces:**
- Consumes: `ExampleTestBase`

- [ ] **Step 1: Write market-intelligence/case.yaml**

Based on engine's A2A market research. Documentation only.

- [ ] **Step 2: Write market-intelligence/pattern.yaml**

Supervisor with 2 composed parallel sub-teams + 1 synthesiser.
First-match routing with guard expression, ESCALATE on deadlock,
termination: max-iterations(15) OR goal-reached.

- [ ] **Step 3: Write MarketIntelligenceExampleTest**

Tests:
- `patternCompiles()` — SUPERVISOR type, 3 candidates (2 composed + 1 worker)
- `composedTeamsAreParallel()` — nested patterns are PARALLEL with correct agent counts
- `routingHasGuard()` — FirstMatchRouting compiled with expression

- [ ] **Step 4: Run tests, commit**

---

## Batch 4: Incident Response (Escape Hatch)

### Task 7: Incident Response YAML + Java escape hatch + test

**Files:**
- Create: `agentic-yaml/src/test/resources/examples/incident-response/pattern.yaml`
- Create: `agentic-yaml/src/test/resources/examples/incident-response/pipeline.yaml`
- Create: `agentic-yaml/src/test/resources/examples/incident-response/cognition.yaml`
- Create: `agentic-yaml/src/test/resources/examples/incident-response/world.yaml`
- Create: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/IncidentResponseExampleTest.java`

**Interfaces:**
- Consumes: `ExampleTestBase`, `registerFallback()` on registries

- [ ] **Step 1: Write incident-response YAML files**

pattern.yaml: Supervisor with escalation chain judgment, agents for triage/investigate/mitigate.
pipeline.yaml: Incident events → severity timeline → impact assessment (3 levels).
cognition.yaml: Responder personality (high AUTONOMY, alert mood baseline).
world.yaml: Incident affordances (acknowledge, investigate, mitigate, escalate, resolve).

- [ ] **Step 2: Write IncidentResponseExampleTest with escape hatch Java**

The test class contains:
- Inner class `IncidentSeveritySummariser` — `ContentSummariser` stub that classifies events
- Inner class `OnCallBidExtractor` — `BidExtractor` stub that scores responders
- Inner class `ClearanceObservationFilter` — `ObservationFilter` stub for visibility gating

Tests:
- `yamlCompiles()` — all 4 YAML files parse and compile
- `escapeHatchWiresCustomSummariser()` — pipeline compiles, custom summariser registered
- `escapeHatchWiresCustomAggregation()` — aggregation registry fallback provides auction with custom BidExtractor
- `escapeHatchWiresCustomFilter()` — world compiler with custom observation filter

Each test demonstrates `registerFallback()` wiring the Java class into the YAML-compiled model.

- [ ] **Step 3: Run tests**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=IncidentResponseExampleTest`
Expected: 4 tests pass

- [ ] **Step 4: Run full suite**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All tests pass (existing + new)

- [ ] **Step 5: Commit**

```
git add agentic-yaml/src/test/resources/examples/incident-response/ agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/examples/IncidentResponseExampleTest.java
git commit -m "feat(#257): incident-response example — escape hatch with custom Java Refs #257"
```

---

## References

- [2026-09-11-e2e-yaml-examples-design.md] — design spec
- [ComprehensiveExamplesTest.java] — existing test patterns
- [PipelineCompilerTest.java] — pipeline compilation patterns
- [logistics-hub.yaml] — existing summarisation pipeline fixture
- [engine/examples/yaml/] — engine YAML examples (case.yaml source)
- [#245] — registry extensibility (registerFallback)
- [#256] — comprehensive examples (prior art)
- [#257] — focal issue
