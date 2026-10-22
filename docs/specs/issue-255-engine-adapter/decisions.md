## D1: CallerConfig adaptation scope

**Choice:** Full mirror — all 12 Human fields adapted to YAML-safe types
**Alternatives:**
- Practical subset — core fields only, skip expression/quorum/payloadType
- Defer — skip PatternJudgmentConfig entirely
**Rationale:** User wants comprehensive YAML surface. All fields are YAML-safe after adaptation (ExpressionEvaluator → String, Class<?> → String).
**Trade-offs:** More spec types to maintain; CallerConfig.Human is a wide record
**Sources:** CallerConfig.java (engine-api), PatternJudgmentConfig.java (engine-adapter)
**Exploration:** quick
**Status:** captured

## D2: Direct reuse vs spec mirrors

**Choice:** Direct reuse for YAML-safe types, spec mirrors for types with runtime deps
**Alternatives:**
- Mirror everything — consistent but unnecessary duplication
- Reuse everything — impossible, some types have ExpressionEvaluator/Class<?> fields
**Rationale:** EvidenceRequirement, EvidenceType, JudgmentMode, OnThresholdReached are plain records/enums that deserialize from YAML directly. CallerConfig and QuorumConfig need adaptation.
**Trade-offs:** Mixed approach — some types from engine-api, some from agentic-yaml spec
**Sources:** EvidenceRequirement.java, QuorumConfig.java (validation constructor)
**Exploration:** quick
**Status:** captured
**Depends on:** D1 (full mirror requires knowing which types need adaptation)

## D3: Module placement

**Choice:** All specs in agentic-yaml/spec/, registries in agentic-yaml/registry/
**Alternatives:**
- New engine-adapter-yaml module — unnecessary, no new dependencies needed
**Rationale:** agentic-yaml already depends on casehub-agency → engine-api transitively. No new module or dependency changes required.
**Trade-offs:** None — follows established pattern
**Sources:** agentic-yaml/pom.xml, existing spec/registry layout
**Exploration:** quick
**Status:** captured
