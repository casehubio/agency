# Decisions — #438 L3 LLM-Mediated Conflict Resolution

## D1: Mediation architecture

**Choice:** Direct LLM invocation now with strategy SPI for channel upgrade path
**Alternatives:**
- Full channel-based mediation — heavyweight for a first implementation with no consumer
- Direct-only without upgrade path — locks out future channel-based mediators
**Rationale:** ConflictMediator SPI gives a clean seam. LlmConflictMediator does one-shot reasoning via StructuredAgentInvoker. A future NegotiationProtocol-backed mediator implements the same interface. MultiSwarmTopology doesn't change.
**Trade-offs:** Single-shot LLM call doesn't capture multi-turn negotiation nuance. Acceptable because no consumer needs it yet.
**Sources:** MultiSwarmTopology.java:57 (agentProvider slot), 2026-11-07-multi-swarm-coordination-design.md L3 section
**Exploration:** quick
**Status:** captured

## D2: Conflict type coverage

**Choice:** All four conflict types from the start (conflict, resource contention, joint decision, handoff)
**Alternatives:**
- Belief conflict only — simpler but leaves heuristic fallbacks unimplemented
- Conflict + resource contention — partial coverage
**Rationale:** The heuristic fallback table is already defined in the spec. Implementing all four types now is incremental cost with full coverage.
**Trade-offs:** Broader scope means more testing surface.
**Sources:** 2026-11-07-multi-swarm-coordination-design.md §L3 fallback table
**Exploration:** quick
**Status:** captured

## D3: Budget model

**Choice:** Shared pool grant from AutonomyBudgetPool
**Alternatives:**
- Reserved L3 partition — guarantees availability but starves other consumers
- Per-conflict count cap — independent of token budget, less precise
**Rationale:** Consistent with existing patterns (BudgetAwareSummariser). Falls back to heuristic on budget denial. No special L3 budget allocation needed.
**Trade-offs:** L3 competes with other budget consumers. Acceptable — heuristic fallback is always available.
**Sources:** AutonomyBudgetPool, BudgetAwareSummariser, BudgetGrant sealed hierarchy
**Exploration:** quick
**Status:** captured

## D4: SPI location

**Choice:** ConflictMediator in the multiswarm package
**Alternatives:**
- deliberation package — more general but premature abstraction
- core package — maximally reusable but L3 is multi-swarm-specific
**Rationale:** The SPI is specific to multi-swarm coordination. Move later if reuse emerges.
**Trade-offs:** Less discoverable for non-multiswarm consumers. Acceptable — no other consumer exists.
**Sources:** Package structure in agency-core
**Exploration:** quick
**Status:** captured
