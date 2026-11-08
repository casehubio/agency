## D1: Behavioral fidelity — tiered state machine with metadata

**Choice:** ~~Hybrid — tiered state machine approximation with metadata~~ **SUPERSEDED by D6**
**Status:** superseded

## D2: Template location — agency repo

**Choice:** agency repo
**Alternatives:**
- qhorus — closer to the Java implementations but has no YAML surface
- platform — near PlaybookProtocolEvaluator and existing playbooks but these are protocol domain templates, not platform infrastructure
**Rationale:** Agency already owns YAML surfaces for orchestration (agentic-yaml) and summarisation (summarisation-yaml). Protocol templates are the same pattern.
**Trade-offs:** Templates are one repo away from both the Java protocols they model (qhorus) and the runtime that will execute them (platform)
**Sources:** agentic-yaml module structure, summarisation-yaml module structure
**Exploration:** quick
**Status:** captured

## D3: Scope — ~~YAML + docs only~~ SUPERSEDED by D7

**Choice:** ~~YAML + docs only~~ **SUPERSEDED by D7**
**Status:** superseded

## D4: Protocol scope — two templates only

**Choice:** REQUEST_RESPONSE and TASK_COMPLETION as specified in the issue
**Status:** captured

## D5: YAML format — ~~playbook envelope~~ SUPERSEDED by D6

**Choice:** ~~Playbook envelope with protocol schema~~ **SUPERSEDED by D6**
**Status:** superseded

## D6: Protocol playbooks are standard playbooks

**Choice:** Protocol playbooks use the standard playbook YAML format with the standard playbook compiler and executor. No separate protocol engine, no separate state map format.
**Alternatives:**
- Bare state map via CompiledProtocol.from() — the original approach. Cannot express counting, guards, or pattern matching. Only tracks current state name.
- Extended PlaybookProtocolEvaluator — bolting counters/guards onto the parallel engine. Wrong: duplicates the standard executor.
**Rationale:** The playbook system already has counters (OrcCounter), guards (Predicate on transitions), pattern matching (match/cases), loops, signals — all the primitives needed for exact behavioral equivalence. PlaybookProtocolEvaluator reimplements a strict subset. Protocol playbooks should use the same engine as every other playbook.
**Trade-offs:** None — this is strictly better. The standard executor has more capabilities and is already tested.
**Sources:** OrcCounter (yaml-core), OrcStateMachine with guards (yaml-core), playbook examples (counter.playbook.yaml, state-machine.playbook.yaml, match-cases.playbook.yaml), PlaybookProtocolEvaluator limitations found via exploration
**Exploration:** deep-analysis (multi-agent exploration + IntelliJ verification)
**Status:** captured

## D7: Scope includes tests against standard playbook executor

**Choice:** Playbook YAML files + tests that compile and run them through the standard playbook executor + consumer guide documentation
**Alternatives:**
- YAML + docs only (original D3) — cannot validate the playbooks actually work
**Rationale:** The standard playbook executor exists and works. Testing protocol playbooks against it validates the concept and catches errors before qhorus#497 wires channel dispatch.
**Depends on:** D6 (standard playbook format enables testing)
**Sources:** PlaybookParsingTest.java (existing test pattern), PlaybookCompiler, OrcCounter tests
**Exploration:** quick
**Status:** captured

## D8: Front-matter metadata serves functional purposes

**Choice:** Front-matter metadata declares the playbook's role (protocol) and pre-initializes resources (counter defaults, policyOverride keys). Not just documentation — enables registry search, validation, conformance checks, and resource pre-initiation.
**Alternatives:**
- No metadata / pure documentation — loses registry, validation, and conformance capabilities
- Separate protocol-specific format — unnecessary; standard playbook front-matter already supports metadata
**Rationale:** A protocol playbook is a normal playbook whose metadata declares its role. The metadata enables the system to find it, validate it, apply constraints, and bootstrap resources.
**Sources:** PlaybookFrontMatter record (platform yaml-core), playbook.schema.json
**Exploration:** quick
**Status:** captured

## D9: Platform cleanup is independent

**Choice:** Filed platform#669 to delete PlaybookProtocolEvaluator and its parallel engine. This is independently useful cleanup but does not block #360.
**Alternatives:**
- Expand #360 to include the platform cleanup — mixes concerns
- Block #360 on the cleanup — unnecessary, the templates don't depend on the evaluator
**Rationale:** The templates use the standard playbook executor. The evaluator is dead code that should be removed, but removing it is a platform concern.
**Sources:** platform#669
**Exploration:** quick
**Status:** captured
