---
title: "The YAML That Compiles to Orchestration"
date: 2026-09-09
entry_type: note
subtype: diary
author: mdp
tags: [agentic, yaml, orchestration, sealed-interfaces, jackson, victools]
projects: [casehub-agency]
series: blocks
status: draft
---

The agentic package has eight orchestration topologies — supervisor, debate, loop, parallel, voting, conditional, sequence, HTN — each assembled through Java builders that produce an `ExecutionModel`. The builders are good for Java developers. But YAML authors couldn't touch any of it. This session changes that.

The approach follows the DSL-STYLE-GUIDE principle we established for the platform: "Three pathways, one family: pure YAML, pure Java, and hybrid. Each pathway is first-class." The summarisation-yaml module proved the pattern six months ago with `PipelineDefinition` → `PipelineCompiler` → `SummarisationRunner` chains. The agentic module is the same architecture at larger scale — 56 spec record subtypes across 13 sealed interfaces instead of summarisation's handful.

## Sealed interfaces as the YAML contract

Each strategy family — routing, termination, aggregation, activation, decomposition — becomes a sealed interface with Jackson `@JsonTypeInfo` discriminator:

```java
@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
    @Type(value = RoutingSpec.FirstMatch.class, name = "first-match"),
    @Type(value = RoutingSpec.RoundRobin.class, name = "round-robin"),
    // ...
})
public sealed interface RoutingSpec { ... }
```

The sealed interface does three jobs at once. Jackson uses it for polymorphic YAML deserialization. The registries use exhaustive switch expressions — add a subtype and the compiler forces you to handle it. victools uses it for JSON Schema generation with discriminated unions. One type hierarchy, three consumers, zero drift.

The pattern-specific fields are where it gets interesting. `DebateSpec` has `judge` and `maxRounds`. `LoopSpec` has `maxIterations` and `exitCondition` (an MVEL expression string). `ConditionalSpec` has `List<BranchSpec>`. `HtnSpec` has a recursive `TaskNodeSpec` tree. And `ComposedAgentSpec` holds a nested `PatternDefinition` — patterns inside agents inside patterns. The mutual recursion between `AgentRefSpec` and `PatternDefinition` just works because Java sealed interfaces resolve by name, not by order.

## The expression compilation question

This is where I hit the real design tension. The design spec says to use the platform `ExpressionEngine` SPI for MVEL predicates. In summarisation-yaml, this works perfectly — events are `Map<String, Object>`, and `ExpressionEngine.compile(expr, Map.class, Boolean.class)` gives you a typed compiled expression you can evaluate against any map context.

But agentic patterns operate on typed Java objects. A routing guard evaluates against `RoutingCandidate`. A goal predicate evaluates against the generic `T` of the execution context. MVEL's `ExpressionEngine.compile()` with `Object.class` as the context type throws an `IntrospectionException` — Java's `Introspector.getBeanInfo(Object.class, Object.class)` rejects it because the stop class can't be the same as the bean class. The error message ("java.lang.Object not superclass of java.lang.Object") is perfectly circular and tells you nothing about the fix.

I deferred full expression compilation to a follow-up issue. The spec records capture expression strings correctly. The registries handle all non-expression strategies. The compiler handles everything except evaluating MVEL against unknown runtime types. This is the right gap to leave open — it needs a typed compilation strategy per expression site, not a generic workaround.

## Schema discriminator alignment

The second surprise: victools' `SealedHierarchyModule` generates discriminator values from Java class simple names — `FirstMatch` → `"firstMatch"`. But Jackson's `@JsonSubTypes` uses kebab-case — `name = "first-match"`. The schema generates successfully with wrong discriminator values. Silent. Tests pass for deserialization (Jackson reads its own annotations). Tests fail for schema content (victools ignores Jackson's annotations entirely).

The fix: extract `@JsonSubTypes` annotations reflectively and pass them as discriminator overrides to `SealedHierarchyModule`. Both tools now agree on `"first-match"`. This is the kind of integration gotcha that costs an hour the first time and zero thereafter — exactly what the garden is for.

## What this opens up

The immediate consumer is the TypeScript CDK generation tracked in parent#422. With typed spec records and JSON Schema, TypeScript types can be generated mechanically — `PatternDefinition` → TypeScript discriminated union, `RoutingSpec` → union type, etc. The schema is the single source of truth for both Java and TypeScript surfaces.

The deferred items — type-safe expression compilation, AgentDescriptor wiring, schema drift CI, registry extensibility — are each focused enough for a single session. The expression compilation is the most interesting: it needs a per-site compilation strategy that knows the context type, not a generic `Object.class` escape hatch. The type information exists in the sealed interface hierarchy — `FirstMatch.guard` always evaluates against `RoutingCandidate`. The compiler just needs to thread that knowledge through.
