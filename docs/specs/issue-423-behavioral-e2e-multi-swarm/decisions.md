## D1: Test organization

**Choice:** Single test class `MultiSwarmBehavioralTest` with `@Nested` groups
**Alternatives:**
- Separate test classes per scenario — more isolated, but duplicates setup and fragments a cohesive API surface
**Rationale:** Scenarios share a common consumer preamble (builder → register → go). Matches existing codebase pattern (TieredCoordinationIntegrationTest, BjtPipelineIntegrationTest). @Nested gives logical separation without file proliferation.
**Trade-offs:** Larger single file, but scenarios are facets of one API, not independent systems
**Sources:** TieredCoordinationIntegrationTest.java, BjtPipelineIntegrationTest.java
**Exploration:** quick
**Status:** captured

## D2: Timing strategy for async signal processing

**Choice:** CountDownLatch + Awaitility for deterministic async assertions
**Alternatives:**
- Thread.sleep — simple but flaky, fails on slow CI or drags on fast machines
- Synchronous test bus — eliminates timing but changes execution model, defeating behavioral test purpose
**Rationale:** Behavioral tests should exercise real async behavior (virtual thread dispatch via CoordinationBus) but assert deterministically. Awaitility is already a test dependency.
**Trade-offs:** Slightly more verbose test code than Thread.sleep
**Sources:** CoordinationBus.java (virtual thread dispatch), pom.xml (awaitility dependency)
**Exploration:** quick
**Status:** captured

## D3: YAML round-trip — out of scope

**Choice:** Drop scenario 6 (YAML round-trip) from this issue
**Alternatives:**
- Include YAML round-trip in this issue — tests a different consumer surface (YAML layer) rather than the Java API
**Rationale:** Java needs to be tested with Java. YAML is a separate layer that gets its own tests when that layer is reached. These behavioral e2e tests prove the Java API works for a consumer. YAML tests belong with the YAML layer's own test cycle.
**Trade-offs:** YAML round-trip not covered here, but it's the YAML layer's responsibility
**Sources:** MultiSwarmTopologyCompiler.java, MultiSwarmTopologyCompilerTest.java (already has 3 tests)
**Exploration:** quick
**Status:** captured

## D4: Test helpers

**Choice:** Reuse `TestMultiSwarmRegistry`, add `RecordingConvergenceEvaluator` as inner class
**Alternatives:**
- Create `MultiSwarmTestFixture` builder — reduces boilerplate but hides the API the tests are meant to exercise
**Rationale:** The test's purpose is to exercise builder + registration API explicitly. Hiding setup behind a fixture defeats the consumer-reference purpose.
**Trade-offs:** Some setup repetition in @BeforeEach, but that IS the consumer experience
**Sources:** TestMultiSwarmRegistry.java, CrossSwarmConvergenceEvaluator (functional interface)
**Exploration:** quick
**Status:** captured

## D5: Module placement

**Choice:** All tests in agency-core — single `MultiSwarmBehavioralTest` in `agency-core/src/test/`
**Depends on:** D3 (dropping YAML scenario eliminates the cross-module concern)
**Alternatives:**
- Split across modules — no longer needed since YAML scenario is out of scope
**Rationale:** All 6 remaining scenarios use only agency-core types. No agentic-yaml involvement. Lives alongside existing multi-swarm tests.
**Trade-offs:** None — clean single-module placement
**Sources:** agency-core/src/test/java/io/casehub/agency/orchestration/multiswarm/ (existing test location)
**Exploration:** quick
**Status:** captured