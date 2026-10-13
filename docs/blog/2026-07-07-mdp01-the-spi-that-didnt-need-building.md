---
layout: post
title: "The SPI That Didn't Need Building"
date: 2026-07-07
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [routing, cbr, feature-extraction, design-review]
---

# CaseHub Blocks — The SPI That Didn't Need Building

**Date:** 2026-07-07
**Type:** phase-update

---

## What I was trying to achieve: structured CBR similarity for domain routing

CBR routing had a gap. The `RoutingFeatureExtractor` SPI existed — two methods, a `@DefaultBean` default, clean CDI displacement. But every domain was running on `TextOnlyFeatureExtractor`, which returns empty features and `caseContext.toString()` as problem text. Raw JSON with brackets, field names, structural noise. AML's risk scores, clinical's CTCAE grades, devtown's code analysis flags — all reduced to the same noisy text similarity signal.

## What we believed going in: this needed infrastructure in blocks

The issue said "structured RoutingFeatureExtractor for domain-specific routing." The natural assumption was that blocks needed to provide something — a builder, a DSL, a `JsonPathFeatureExtractor` base class. Something to justify the issue living in the blocks repo.

I did the first-principles analysis and the answer was: no. The SPI is two methods returning `Map<String, Object>` and `String`. Jackson's `JsonNode` API already provides `path()`, `asDouble()`, `asBoolean()`, `asText()`. A builder would save five lines per consumer while adding a hundred lines of abstraction in blocks — for cases that will need custom logic anyway. AML needs to compute derived features from fact lists. Clinical needs to parse `"GRADE_3"` into the integer 3. A declarative DSL would handle the simple cases and force escape hatches for exactly the ones where feature extraction matters.

The right answer was: no new code in blocks. Write the domain extractors directly.

## The design review caught real bugs

We ran an adversarial spec review before implementing. It caught things I'd have shipped wrong:

The AML feature paths were incorrect. I had `riskScore` and `entityType` at the root level. The reviewer traced through `AmlPriorContext.toContextMap()` and `AmlEngineCoordinator.prepareAndMarkRequested()` — prior context features live under `/priorEntityContext/`, and entity resolution output appears under `/entityResolution/` only after the first capability completes. Early routing decisions get a sparse feature vector. That's not a problem — CBR handles mixed-cardinality maps — but getting the paths wrong would have produced empty features silently.

The clinical grade field stores the `CtcaeGrade` enum name — `"GRADE_3"`, not `3`. Calling `asInt()` on a text node returns 0 for every grade. The reviewer verified this against `AeEscalationCaseService.prepareAndMarkRequested()` which stores `event.grade().name()`. The fix is a regex that parses the numeric suffix, but without the review we'd have shipped an extractor that silently scored every adverse event as grade zero.

The CDI pattern was over-specified. I had `@Alternative @Priority(1)` in the spec. The reviewer pointed to `alternative-extension-patterns.md` Pattern C — a plain `@ApplicationScoped` displaces `@DefaultBean` automatically. No alternative annotation needed.

## What it is now

Three extractors are live. AML extracts `knownHighRisk`, `entityRiskCount`, `networkCount`, `patternCount` from prior context (always available), plus `entityType` and `riskScore` from entity resolution output (available after first capability). Clinical extracts `ctcaeGrade` (parsed from enum), `unexpected`, `suspected`, `siteId`, `hasPriorGrade3OrAbove`, `hasPriorEscalation`, `aeCount`. Both produce clean problem text instead of raw JSON dumps.

During implementation we hit a pre-existing breakage: `AgentRoutingContext` had gained a fifth field (`experiences`) upstream, but blocks' tests still used the four-arg constructor. Every routing test exploded with `NoSuchMethodError` — silently, no compile error in IntelliJ, no IDE warning. The fix was mechanical but the failure mode is worth knowing about: Java records have exactly one canonical constructor, so adding a field deletes the old one. The IDE resolves from source (which shows the new field), but Maven compiles against the JAR (which only has the new constructor).

The interesting thing about this issue is what it proved about blocks' scope criteria. The SPI belonged in blocks — it composes across the CBR memory API and the engine routing API. The implementations don't — they're 30 lines of Jackson navigation with no AI integration, no classical AI, no foundational platform composition. The line was exactly where the blocks scope criteria said it should be.
