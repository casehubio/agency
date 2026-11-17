---
title: "The Factory Nobody Called"
date: 2026-11-17
entry_type: note
subtype: diary
tags: [cognition, pipeline, factory, builder, cdi, spring, construction, agency]
projects: [casehubio/agency]
---

# The Factory Nobody Called

`CognitionCoreFactory` has been in the neocortex repo for months. It's `@ApplicationScoped`. It injects every cognitive SPI through CDI. It calls `CognitivePipelineBuilder`, which wires appraisal, emotion regulation, gut feeling, drive-goal bridge, effective cognitive defaults, entity rendering, cognitive journal, and a dozen other capabilities through a sequence of fourteen `configure*()` calls. It is the correct way to build a `CognitionCore`.

Zero callers.

Instead, three sites — `AgencyBeans` (Quarkus CDI), `AgencyAutoConfiguration` (Spring), and `CognitionStack` (agentic-yaml test harness) — each hand-built a `CognitionCore` by calling the constructor directly. AgencyBeans passed 18 arguments. The Spring config passed 17. CognitionStack passed 8. All three then manually added participants and chained section customizers in ad hoc blocks of code that duplicated (poorly) what the builder already does.

The gap wasn't hypothetical. Production agents ran without appraisal, without emotion regulation, without gut feeling, without drive-goal bridging, without effective defaults, and without entity rendering. The builder wires these. The hand-builds didn't.

## Why three sites diverge

The root cause is older than the factory. CognitionCore started with an 8-argument constructor. Each new capability added more parameters — first to 11, then 18. The constructor grew but the call sites were copy-pasted forward, each one manually wiring whatever the engineer remembered to include.

The builder and factory were written to stop this drift. But nobody went back and routed the call sites through them. The constructors stayed public, so there was no compile error forcing migration. The old code kept working — in the sense that it compiled and didn't throw exceptions. It just quietly ran agents without half their cognitive capabilities.

## The migration

**Quarkus (AgencyBeans):** Delegates to `CognitionCoreFactory.create(CognitionConfig.all())`. Three SPI bridge producers make agency's adapter types visible to the factory's CDI resolution — `MemoryHygieneSpiAdapter` for the neocortex hygiene SPI, `EngagementRecorderCore::record` as `Consumer<EngagementEvent>`, and `CognitiveGoalOrchestrator` with the agency-specific mood-to-PAD lambda. The old method was ~100 lines of hand-wiring. The new one is ~30 lines: a factory call and the one customizer the factory can't handle — the richer `EmergentGoalPromptSection` that includes drive-goal awareness.

Nine `Instance<T>` fields removed from AgencyBeans. The factory resolves those SPIs itself.

**Spring (AgencyAutoConfiguration):** No CDI factory available, so this path uses `CognitivePipelineBuilder` directly. Same migration pattern — replace the 17-arg constructor with the builder's fluent API, let the builder call the fourteen `configure*()` methods, then chain the agency-specific customizer.

**CognitionStack (test):** The simplest change — a 2-line constructor call becomes a 5-line builder chain. But the effect is real: at REAL_DRIVES and above, the builder wires emotion regulation, which the hand-build never did. Test agents are now closer to production agents.

## The enforcement

Making the constructors package-private is the final step, and the important one. The three-argument, eleven-argument, and eighteen-argument constructors all became package-private in `CognitionCore`. The builder (same package) still has access. External code gets a compile error.

This is the difference between a guideline and a constraint. "Use the builder" is a guideline — everyone nods and nobody changes their code. A package-private constructor is a constraint — the compiler enforces it. Four test files in neocortex needed migration too, in packages outside `core`. Every one was a `new CognitionCore(null, null, null, ...)` that now goes through the builder.

The practical value: the next time neocortex adds a cognitive capability to the builder, it's available to every agent automatically. No manual wiring. No remembering which call sites need updating. The builder is the single construction path, and the compiler guarantees it stays that way.
