---
title: "When the Reviewer Kills Your Architecture"
date: 2026-09-10
entry_type: note
subtype: diary
author: mdp
tags: [agentic, yaml, expression-compilation, mvel, design-review]
projects: [casehub-agency]
series: blocks
status: draft
---

# When the Reviewer Kills Your Architecture

The agentic-yaml module had a gap. Three expression sites in the registry switch statements — `GoalReached.when`, `ConfidenceThreshold.extractor`, `FirstMatch.guard` — all threw `UnsupportedOperationException` with a note about "runtime expression compilation." The expressions were declared in YAML spec records but nobody had wired the types through to the compiler.

The root cause was straightforward: `MvelExpressionEngine.compile(expr, Object.class, Boolean.class)` throws because MVEL3 can't introspect `Object.class`. Each expression site needs a real context type. The question was where that type information should live.

I started with what felt like the right architectural answer. Both `agentic-yaml` and `summarisation-yaml` use `ExpressionEngine`. Both need typed compilation. The shared concern should live in platform's `yaml-core` module — a descriptor model with string-based type names, a compilation SPI, a bridge module for runtime resolution. We designed five interlocking decisions: yaml-core owns the descriptors, string types keep it zero-dep, `CompiledHandle<C, R>` mirrors MVEL3's typed code generation, a `TypeResolver` maps strings to classes. Clean layering. Proper separation.

Then the design reviewer dismantled it.

The core challenge was simple: every expression site in the sealed hierarchy already knows its context type. `FirstMatch.guard` evaluates against `RoutingCandidate`. `GoalReached.when` evaluates against a generic `T` that MVEL duck-types as `Map<String, Object>`. `ConfidenceThreshold.extractor` evaluates against `JudgmentContext`. These are compile-time constants. There's nothing to resolve dynamically. The entire descriptor model — five decisions, three new types, a bridge module — existed to solve a problem the reviewer proved didn't exist.

The cascading argument was the sharpest part: D1's placement in yaml-core forced D2 (strings because no platform-api dependency), which forced D3 (descriptors to carry structural info), which forced D4 (duplicate of `CompiledExpression`), which forced D5 (bridge to undo the string→Class gap). Fix D1 and they all collapse.

I kept the descriptor model as a deferred Layer 2 — Tier 2 summarisation pipelines will eventually need YAML-declared context schemas where the type genuinely isn't known at compile time. But for Layer 1, the implementation is three switch cases in three registry files. Each case checks for a null engine, compiles against the known type, wraps the result in a lambda.

The one surprise was MVEL3 and Java records. Compiling against `RoutingCandidate.class` directly should work — the `PojoAdapterMvelExpression` introspects the type, converts to a Map, then lazy-compiles. But when a record component is typed as a sealed interface (`AgentRef`), MVEL3's transpiler can't resolve method calls through the hierarchy. The error surfaces at first evaluation, not at compile time, because the actual MVEL compilation is lazy. The fix: compile against `Map<String, Object>` and project the record's fields into a HashMap explicitly. Same approach `ThresholdClassifySummariser` already uses — proven pattern, just not obvious when you start from the POJO path.

The broader lesson is about design review pressure. I'd built a coherent five-decision architecture that solved the wrong problem. The per-site approach is less interesting architecturally — it's just a switch statement with the right `Class<?>` at each case. But it's correct, and the more interesting architecture wasn't needed yet. Building Layer 2 when no consumer exists for it is the kind of speculative infrastructure that compounds maintenance cost without delivering value. The reviewer caught it; the layered compromise preserves the option without paying for it now.
