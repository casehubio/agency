# Decisions — #447 Remove residual cognitive code

## D1: Migration target module

**Choice:** Move memory hygiene + evolution code into neocortex's existing `cognition` module
**Alternatives:**
- New `neocortex-memory-hygiene` module — adds module boundary for no consumer benefit; memory hygiene is wired exclusively through CognitionCore.tick()
- Split: core to neocortex, evolution SPIs stay in agency — splits a cohesive unit across repos; evolution types have no neocortex imports anyway
**Rationale:** 17 of 33 files already import neocortex types. The MemoryHygieneOrchestrator SPI is in cognition-api; placing the implementation in cognition eliminates the circular bridge pattern. CognitionCoreFactory discovers it via CDI directly.
**Trade-offs:** cognition module grows by ~33 files. Acceptable — it's already 140+ files and this is a natural cohesive addition.
**Sources:** neocortex cognition-api/MemoryHygieneOrchestrator.java, agency social/memory/* audit, consumer audit (zero external consumers)
**Exploration:** quick
**Status:** captured

## D2: Speech-api types — defer to #490

**Choice:** #447 does not touch speech-api types or CognitionAvatarAdapter. #490 handles that.
**Alternatives:**
- Do both in #447 — larger scope, blocks on eidos#240 and neocortex#1105
- Do #490 first, then #447 — safe but delays the independent memory hygiene work
**Rationale:** #490 has cross-repo API dependencies (eidos#240, neocortex#1105). #447's memory hygiene move is self-contained. They touch different files and different AgencyBeans wiring.
**Trade-offs:** CognitionAvatarAdapter and MemoryHygieneSpiAdapter temporarily remain in agency after #447. #490 cleans them up.
**Sources:** agency#490 issue body, eidos#240, neocortex#1105
**Exploration:** quick
**Status:** captured
