---
title: "The Silo That Wasn't"
date: 2026-09-05
author: mdp
entry_type: note
subtype: diary
series: issue-233-summarisation-yaml-surface
projects: [casehubio/agency]
tags: [summarisation, unification, composition, api-design]
---

I came into this session planning to build three modules on top of the existing summarisation framework — a YAML pipeline surface, a CloudEvent bridge, and an API extraction. Straightforward layering work: take what exists, package it better, add a declarative surface.

The first two hours went exactly that way. We brainstormed the YAML grammar, settled on implicit chaining for linear topologies, chose a `grouping:` block with type discriminator, designed per-level CloudEvent emission. Fourteen design decisions captured, two review rounds. Standard build flow.

Then I asked a question that changed the shape of the work: "how does this differ from the existing summarisation stuff? Is it complementary, or are we duplicating?"

Claude's first answer was too neat — three separate subsystems, all different, no overlap. I pushed back. And when we actually looked at what each subsystem does at its core, the pattern was obvious:

- Temporal summarisation: accumulate events → batch → transform → emit
- Content summarisation: accumulate messages → batch → transform → emit text
- Observation rendering: accumulate observations → batch → transform → emit prompt text

Same fundamental pattern. Three independent implementations. Three separate accumulator classes. Two separate tiered-dispatch classes with identical routing logic. A bridge adapter (`ContentSummariserToSummariser`) that existed specifically because the subsystems couldn't compose — and that bridge threw away the `previous` state, making incremental content summarisation broken in pipeline contexts.

The fix was a `StatefulSummariser<IN, OUT, S>` SPI that extends `Summariser` with framework-managed state per partition. The runner detects it via pattern matching and handles the state lifecycle — pass previous, store new, partition by tenancyId. Content summarisation becomes a pipeline citizen through a single default method:

```java
default StatefulSummariser<T, R, R> asSummariser() {
    return (batch, prev) -> {
        var items = batch.stream().map(LevelEvent::payload).toList();
        return summarise(items, prev)
            .thenApply(out -> new SummariseResult<>(List.of(out), out));
    };
}
```

That default method replaces an entire bridge adapter class. The bridge was there because the subsystems were siloed. Remove the silo, and the bridge has no reason to exist.

The other half of the unification was generifying `ContentSummariser<T>` to `ContentSummariser<T, R>`. The old interface returned `SummaryResult` from qhorus-api — which meant the SPI was welded to qhorus at the type level. Generifying `R` breaks that coupling cleanly: consumers bind `ContentSummariser<Message, SummaryResult>`, the SPI module has zero qhorus dependency. IntelliJ's structural search/replace handled the 248-site `LevelEvent` constructor migration and the type parameter additions across both modules.

Batch 1 is done — the unified API module exists with `StatefulSummariser`, generified `ContentSummariser`, and the tenancyId addition to `LevelEvent`. Four batches remain: CloudEvent bridge, YAML model and registry, compiler with five built-in types, and the Quarkus deployment module.

The part I keep thinking about is how invisible the duplication was. Three subsystems, all working, all tested, all documented. Nobody would have called them broken. But every time someone needed to compose content summarisation with temporal summarisation, they hit the bridge adapter and lost state. The silos weren't causing bugs — they were preventing composition. And prevented composition is the kind of cost that never shows up in a bug tracker.
