---
title: "The Field Your YAML Never Sees"
date: 2026-11-17
entry_type: note
subtype: diary
tags: [yaml, records, compilation, cognition, drives, stability, multi-repo, agency]
projects: [casehubio/agency]
---

# The Field Your YAML Never Sees

`DriveConfig` is a Java record with fourteen components. It controls how an agent's motivational drives — curiosity, competence, affiliation, autonomy — are modulated by mood, personality, and time. Eleven of those components are exposed through the YAML surface. Three are not. The newest invisible one, `smoothingAlpha`, broke the build today.

The neocortex repo added it: asymmetric exponential smoothing for drive deactivation. The idea is that drives should activate instantly — a curiosity trigger should spike the drive on the same tick — but deactivate gradually. If nothing continues to stimulate curiosity, it shouldn't snap back to zero. It should decay over a few ticks. The formula is straightforward:

```
smoothed = α × new + (1 − α) × previous
```

Applied only on the downward path. Upward changes bypass smoothing entirely. At `α = 0.3`, a drive ramps down over roughly three ticks. At `α = 1.0`, there's no smoothing — the old behaviour. The asymmetry is the point: threat activation needs to be instant; the feeling of safety returns gradually. Self-Determination Theory calls this the difference between need satisfaction and need frustration, and the temporal dynamics are genuinely asymmetric in the literature.

The compile error was mechanical. `CognitionCompiler.compileDrive` calls the `DriveConfig` canonical constructor with positional arguments. Neocortex published a snapshot with fourteen components; the compiler still passed thirteen. Java records don't have named parameters, so a new field at the end breaks every positional call site.

But here's what didn't break: every YAML file that configures drive behaviour. Not one of them needed a change.

## The compilation layer as a stability boundary

`DriveConfigSpec` — the YAML-facing record — has eleven nullable fields. Each one maps to a DriveConfig component that a user might reasonably want to tune: axis weights, change thresholds, mood modulation, personality modulation, the affiliation decay window. When a field is null, the compiler fills it from `DriveConfig.defaults()`.

Three components are deliberately excluded from the spec: `subThoughtModulationStrength`, `secondaryPleasureModulationFactor`, and now `smoothingAlpha`. These are internal tuning knobs — the kind of parameter that matters for the system's internal coherence but that a user configuring agent behaviour has no reason to touch. They always take defaults.

```java
// The last three arguments — always from defaults
d.subThoughtModulationStrength(),
d.secondaryPleasureModulationFactor(),
d.smoothingAlpha());
```

The compiler is the seam. It absorbs record evolution from one side and presents a stable configuration surface on the other. When neocortex adds a field like `smoothingAlpha`, two things happen:

1. **The compiler breaks** — it needs the new argument
2. **The YAML surface doesn't change** — no migration, no deprecation, no user-visible impact

That's the design working as intended. The compile error is the signal that the boundary held. If YAML users had to update their files every time the upstream record gained an internal parameter, the configuration surface would be as unstable as the implementation.

## When to promote a field

The natural question is whether `smoothingAlpha` should eventually appear in `DriveConfigSpec`. The answer depends on whether a user would have a principled reason to change it. For most deployments, the default decay rate is a system invariant — you wouldn't tune it per agent any more than you'd tune a TCP retransmission timeout per request. But for research scenarios, or for agents with dramatically different temporal profiles (a fast-reacting trading agent vs. a slow-adapting counselling agent), the tuning knob could be valuable.

That decision doesn't need to happen now. The pattern handles it cleanly either way: add the field to `DriveConfigSpec`, add the null-check to the compiler, and the YAML surface gains a new optional knob with a sensible default. No existing YAML breaks. The stability boundary absorbs both directions — adding internal fields and promoting them to user-facing ones.

The broader pattern applies anywhere a configuration layer sits over evolving domain records: the config spec is not a mirror of the record. It's a curated subset, and the compiler mediates between the two. The gap between them is a feature.
