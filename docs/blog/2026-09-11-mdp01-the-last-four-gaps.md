---
title: "The Last Four Gaps"
date: 2026-09-11
entry_type: note
subtype: diary
author: mdp
tags: [agentic, yaml, schema-drift, extensibility, descriptor-wiring]
projects: [casehub-agency]
series: blocks
status: draft
---

# The Last Four Gaps

The YAML surface epic started with a coverage matrix — 165 capabilities across 13 Maven modules, 39% expressible in YAML. The pattern orchestration layer was done. The application configuration layer was wide open: cognition configs, world models, channel infrastructure, prompt optimisation, speech, summarisation, engine adapters. Ten priority tiers of work.

Twelve child issues later, the matrix is complete. The last four were the cleanup tier — the kind of work that doesn't add capabilities but makes the surface trustworthy.

The schema drift problem was the simplest and the most overdue. `AgencySchemaGenerator` produces JSON Schema from sealed spec records via victools. The schema changes every time a spec record gains a field or a new sealed variant appears. Without a committed baseline, those changes are invisible. The fix is a 43-line test: generate the schema, compare against a 1026-line committed JSON file, fail if they differ. Run with `-Dschema.update` to regenerate. Approval testing for schemas — nothing novel, but the kind of safety net that only matters the first time it catches something.

The descriptor wiring was more interesting. `PatternCompiler` translates YAML specs into runtime `ExecutionModel` objects. Each agent in a pattern becomes a `RoutingCandidate` — a dispatch reference paired with an optional `AgentDescriptor`. The descriptor feeds LLM-based routing: when a supervisor needs to choose which agent handles a task, the descriptor's briefing and capabilities are what the LLM sees. Until now, every YAML-declared agent got a null descriptor. The spec carried `name`, `description`, and `capabilities` fields, but the compiler threw them away.

The challenge was `AgentDescriptor` itself. It's an eidos-api record with 22 fields and a compact constructor that validates four of them as non-null: `agentId`, `name`, `slot`, `tenancyId`. A YAML spec has a name and maybe a description. It doesn't know its deployment slot or tenancy — those are runtime concerns. The pragmatic fix: synthetic placeholders. `slot="yaml"`, `tenancyId="yaml"`. They satisfy the validator, they're descriptive if they surface in a routing card, and they don't pretend to be real deployment metadata. The builder handles the rest — null for everything the spec doesn't carry.

Registry extensibility was the gap I found most satisfying to close. Every registry uses an exhaustive switch on a sealed interface — the compiler enforces that all variants are handled. But several cases throw `UnsupportedOperationException` because they need CDI-managed dependencies the registry doesn't have: `LlmSelected` routing needs an `AgentProvider`, `Auction` aggregation needs a `BidExtractor`. The fix is a `registerFallback(Function)` checked before the switch. Return null to fall through to built-in resolution; return a strategy to override. The Quarkus deployment module can now register the CDI-provided implementations at startup, and the sealed hierarchy stays sealed.

The comprehensive examples were the last piece — five YAML fixtures that exercise composition across multiple capabilities. A supervisor with a composed parallel sub-team and judgment. A debate with multi-termination and failure policy. A voting pattern with an escalation chain. An interactive agent combining cognition config with a world model. A sequence with a composed parallel analysis tier. Each one verifies that the compilation surface works end-to-end, not just per-type. They also serve as documentation — a developer who wants to see what a real pattern looks like can read the YAML before touching the API.

The whole YAML surface is now a bet that configuration should be declarative until you need it not to be. The fallback mechanism is the escape hatch — built-in types cover the common cases, CDI injection handles the rest. Whether that bet pays depends on whether app developers actually reach for YAML first or skip straight to the Java builders. The comprehensive examples are the argument that YAML is worth reaching for.
