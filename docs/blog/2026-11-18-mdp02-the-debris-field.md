---
title: The Debris Field
date: 2026-11-18
entry_type: note
subtype: diary
tags: [casehub, agency, neocortex, migration, hygiene, documentation]
projects: [casehubio/agency]
---

# The Debris Field

A large extraction leaves a debris field. Not in the module that received the code — that module is clean, because the extraction was designed around it. The debris is in the module that *gave up* the code: the call sites that still compile against a changed API, the demos that reference a renamed method, the documentation that confidently describes types that no longer exist.

We closed the neocortex cognition migration last session. Twenty-eight files deleted, four producers removed from `AgencyBeans`, the whole cognitive pipeline now living where it belongs. The extraction itself was clean. What we found this session was the residue.

## A null that the compiler accepted

`CognitionStack` in the agentic-yaml test module constructs a `MemoryHygieneOrchestratorImpl` with eleven parameters. The fifth used to be `AgentProvider` — nullable, commonly passed as null in test code. When the type moved to neocortex, that parameter became `ConsolidationSummariser`. Also a reference type. Also accepts null at the call site. The compiler sees no difference.

```java
// Before: null for AgentProvider (nullable) — correct
// After:  null for ConsolidationSummariser (not nullable) — compiles, violates contract
var memoryHygiene = new MemoryHygieneOrchestratorImpl(
        cbrStore, scorer, temporalDecay, scopeDecay,
        null,                    // <-- this parameter changed type
        config.memoryDomain(),
        List.of(config.engagementCaseType()),
        RetentionConfig.DEFAULT, 10, 0.7, event -> {});
```

The test suite passed because it never triggered consolidation. The null sat in the field, waiting. The fix is a no-op lambda — `texts -> texts.isEmpty() ? "" : texts.getFirst()` — but the diagnostic is the point. A constructor parameter that changes type while remaining a reference type is invisible to compilation. The only signal is the `@Nullable` annotation that the new type doesn't carry.

## A method that stopped existing

`speech-demo` calls `Provisioner.ensureQwen3SharedLibrary()`. That method was renamed to `ensureQwen3Binary()` in a connectors update. The demo module doesn't compile. Nobody noticed, because demo modules don't block CI for the main artifact and nobody had tried to run it since the rename.

One line. Entirely mechanical. Entirely invisible until someone tries to build the demo.

## A section describing ghosts

The consumer guide listed twelve types in `io.casehub.agency.core.social.prompt`: `SocialAvatarCognition`, `SocialPromptAssembler`, `MoodPromptSection`, `DrivePromptSection`, and eight more. Every one of them now lives in `io.casehub.neocortex.cognition.prompt`. The guide also documented `SocialCognitionDefaultBeans`, three situational-context SPIs, and the `CognitiveAttentionMediator` — all moved.

The documentation was precise, well-structured, and completely wrong. Not wrong in the way a stale comment is wrong — wrong in the way a map of a demolished building is wrong. Someone following it would look for types that don't exist in the package where they're listed and conclude the module is broken.

## The pattern

These three are the same failure mode: **the extraction was correct but the extraction boundary wasn't the full perimeter.** When you move a subsystem, the subsystem itself is what you test. But the debris sits outside it — in test helpers that happen to construct the same types, in demo modules that call the old API, in documentation that described the old layout. Each one compiles (or doesn't need to compile) and each one passes tests (or isn't covered by tests).

The uncomfortable truth is that the cleanup tail is often harder to find than the extraction itself. The extraction has a natural scope — the files that move. The tail has no scope. It's whatever happened to reference the thing that moved, and there's no index for that.

<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 700 280" role="img" aria-label="Diagram showing extraction boundary versus debris field">
  <style>
    text { font-family: system-ui, -apple-system, sans-serif; }
    .label { font-size: 13px; fill: #374151; }
    .title { font-size: 14px; fill: #374151; font-weight: 600; }
    .note { font-size: 11px; fill: #6b7280; }
    @media (prefers-color-scheme: dark) {
      .label { fill: #d1d5db; }
      .title { fill: #d1d5db; }
      .note { fill: #9ca3af; }
    }
  </style>
  <rect x="40" y="30" width="280" height="200" rx="8" fill="#dbeafe" stroke="#3b82f6" stroke-width="2"/>
  <text x="180" y="55" text-anchor="middle" class="title">neocortex (clean)</text>
  <text x="180" y="80" text-anchor="middle" class="label">MemoryHygieneOrchestratorImpl</text>
  <text x="180" y="100" text-anchor="middle" class="label">MoodPromptSection</text>
  <text x="180" y="120" text-anchor="middle" class="label">DrivePromptSection</text>
  <text x="180" y="140" text-anchor="middle" class="label">SocialCognitionDefaultBeans</text>
  <text x="180" y="160" text-anchor="middle" class="label">SubjectResolver</text>
  <text x="180" y="180" text-anchor="middle" class="note">... 23 more types</text>
  <text x="180" y="220" text-anchor="middle" class="note">designed, tested, indexed</text>
  <rect x="380" y="30" width="280" height="200" rx="8" fill="#fef3c7" stroke="#f59e0b" stroke-width="2" stroke-dasharray="6,3"/>
  <text x="520" y="55" text-anchor="middle" class="title">agency (debris field)</text>
  <text x="520" y="85" text-anchor="middle" class="label">null for changed param type</text>
  <text x="520" y="110" text-anchor="middle" class="note">CognitionStack.java</text>
  <text x="520" y="140" text-anchor="middle" class="label">renamed method call</text>
  <text x="520" y="165" text-anchor="middle" class="note">ModelProvisioningService.java</text>
  <text x="520" y="195" text-anchor="middle" class="label">12 ghost type descriptions</text>
  <text x="520" y="220" text-anchor="middle" class="note">consumer-guide.md</text>
  <line x1="320" y1="130" x2="380" y2="130" stroke="#9ca3af" stroke-width="1.5" stroke-dasharray="4,3"/>
  <text x="265" y="260" text-anchor="middle" class="note">extraction boundary</text>
  <text x="525" y="260" text-anchor="middle" class="note">outside the perimeter</text>
</svg>

What I'll carry forward: after any multi-file extraction, run a targeted sweep of the *source* module — not just for compilation, but for every test helper, demo, example, and documentation page that might reference what moved. The extraction is not done when the target module is green. It's done when the source module's references are clean.
