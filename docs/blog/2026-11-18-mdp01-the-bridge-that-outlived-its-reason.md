---
title: The Bridge That Outlived Its Reason
date: 2026-11-18
author: Mark Proctor
tags: [casehub, agency, neocortex, migration, memory-hygiene, architecture]
issue: 447
entry_type: note
subtype: diary
---

# The Bridge That Outlived Its Reason

When you extract a subsystem from one module to another, the extraction itself is the easy part. The hard part is finding everything that grew up around the old boundary — the adapters, the no-ops, the bridge classes that were necessary when the boundary was in one place and become dead weight when it moves.

Agency's memory hygiene code was a textbook case. The `MemoryHygieneOrchestrator` lived in `agency-core` but implemented an SPI defined in neocortex's `cognition-api`. To make this work, a `MemoryHygieneSpiAdapter` sat between the two — taking agency's implementation and presenting it as neocortex's interface. `AgencyBeans` wired it all up with a conditional producer: if the orchestrator was resolvable, wrap it in the adapter; otherwise return null.

This was correct architecture at the time. The implementation had dependencies on agency types (ContentSummariser, KeyedLock) while the consumer lived in neocortex's CognitionCore. The adapter bridged the gap.

Then neocortex absorbed the implementation. The types moved, the tests moved, the package changed from `io.casehub.agency.social.memory` to `io.casehub.neocortex.cognition.memory`. CognitionCoreFactory now discovers `MemoryHygieneOrchestratorImpl` directly through CDI. The bridge was no longer bridging anything — both ends were in the same module.

## What the cleanup actually involved

The deletion list was 28 files — 23 source files and 5 test files. That sounds dramatic until you realise they were all dead code: exact duplicates of types that now live in neocortex. The real work was in the files that *stayed*.

`AgencyBeans` needed four producers removed and one retype. The `ReflectionQueryStore` case was instructive — agency had its own `ReflectionQueryStore` interface that extended neocortex's, but the two were now identical (same methods, same defaults). The agency version was a pure redundant wrapper. Replacing it with the neocortex type in the `narrativePipeline()` signature was the entire change.

The evolution loop types — seven files implementing engine-api SPIs like `ImprovementProposalSource` and `RegressionEvaluator` — stayed in agency. They have zero neocortex imports. They implement engine SPIs, not cognitive SPIs. The boundary is clean: neocortex owns the cognitive pipeline and its parameters; agency owns the evolution loop that tunes those parameters from outside.

<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 680 320" style="max-width:680px;width:100%">
  <style>
    text { font-family: -apple-system, system-ui, sans-serif; font-size: 13px; fill: #333; }
    .title { font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.5px; fill: #666; }
    .label { font-size: 12px; fill: #444; }
    .small { font-size: 11px; fill: #888; }
    .deleted { fill: #fee; stroke: #e88; }
    .stays { fill: #eef6ee; stroke: #6a6; }
    .neocortex { fill: #eef; stroke: #88c; }
    .arrow { stroke: #999; stroke-width: 1.5; fill: none; marker-end: url(#arrowhead); }
    .dead { stroke: #c66; stroke-width: 1.5; stroke-dasharray: 6,4; fill: none; }
  </style>
  <defs>
    <marker id="arrowhead" markerWidth="8" markerHeight="6" refX="8" refY="3" orient="auto">
      <polygon points="0 0, 8 3, 0 6" fill="#999"/>
    </marker>
  </defs>
  <!-- neocortex -->
  <rect x="20" y="20" width="300" height="130" rx="8" class="neocortex"/>
  <text x="35" y="42" class="title">neocortex cognition</text>
  <text x="35" y="64" class="label">MemoryHygieneOrchestratorImpl</text>
  <text x="35" y="82" class="label">Scorers, IntegrityCheckers</text>
  <text x="35" y="100" class="label">RetentionConfig, HygieneEvents</text>
  <text x="35" y="118" class="small">CognitionCoreFactory discovers via CDI</text>
  <text x="35" y="136" class="small">cognition-api: SPI interfaces</text>
  <!-- deleted -->
  <rect x="360" y="20" width="300" height="130" rx="8" class="deleted"/>
  <text x="375" y="42" class="title">agency social/memory (deleted)</text>
  <text x="375" y="64" class="label" style="text-decoration:line-through">MemoryHygieneOrchestrator</text>
  <text x="375" y="82" class="label" style="text-decoration:line-through">MemoryHygieneSpiAdapter</text>
  <text x="375" y="100" class="label" style="text-decoration:line-through">ReflectionStore, NoOps</text>
  <text x="375" y="118" class="small">28 files — duplicates of neocortex types</text>
  <text x="375" y="136" class="small">bridge adapter no longer needed</text>
  <!-- stays -->
  <rect x="360" y="180" width="300" height="120" rx="8" class="stays"/>
  <text x="375" y="202" class="title">agency social/memory/evolution (stays)</text>
  <text x="375" y="224" class="label">MemoryHygieneProposalSource</text>
  <text x="375" y="242" class="label">MemoryHygieneRegressionEvaluator</text>
  <text x="375" y="260" class="label">CapabilityArea, ConflictStrategy</text>
  <text x="375" y="278" class="small">engine-api SPIs — zero neocortex imports</text>
  <text x="375" y="294" class="small">tunes parameters from outside</text>
  <!-- arrows -->
  <path d="M 510 180 L 510 155" class="arrow"/>
  <path d="M 360 240 L 320 100" class="arrow"/>
  <text x="290" y="176" class="small">reads</text>
  <!-- dead arrow -->
  <path d="M 360 80 L 320 80" class="dead"/>
  <text x="322" y="74" class="small" style="fill:#c66">was</text>
</svg>

## The diagnostic that matters

The telling metric wasn't file count — it was import count. Seventeen of the 33 original files imported from `io.casehub.neocortex`. They were agency residents commuting to neocortex every day. When more than half your types depend on a module you don't own, you're not an independent subsystem — you're an annex that hasn't moved yet.

The evolution types tell the opposite story: zero neocortex imports across all seven files. They implement engine-api SPIs and consume their own local records. The dependency arrow points the right way. That's why they stay.

## The gotcha

The one surprise was `agentic-yaml`. Its `ConfidenceScorerRegistry` and `CognitionCompiler` imported scorer types from the old agency package. The imports needed updating to point at neocortex — straightforward — but the module also needed a new `provided` dependency on `casehub-neocortex-cognition` since the implementations (not just the SPIs) live in that jar now.

Pre-existing build failures in `agency-spring` and `agentic-yaml` from an unrelated rename in neocortex (`PersonalityEvolutionConfig` removed) masked our changes during the first build attempt. Isolating the core modules confirmed the migration was clean before the noise cleared.
