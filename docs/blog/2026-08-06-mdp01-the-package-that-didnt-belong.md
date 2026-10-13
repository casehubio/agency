---
layout: post
title: "The Package That Didn't Belong"
date: 2026-08-06
type: phase-update
entry_type: note
subtype: diary
projects: [casehub-blocks]
tags: [architecture, trust, spi-design, prompt-optimisation]
---

When you extract shared patterns from domain repos into a reusable library, the first instinct is to group by workflow. Attestation observer, intake classifier, vouch service — they all orbit trust. They participate in the same lifecycle: classify the subject, observe what happens, write the attestation. So you put them in the same package, name it after the lifecycle, and move on.

The design review caught this. `IntakeClassifier` has zero compile-time dependency on any attestation type. Its inputs are a generic subject and a context bag. Its output is a lane name, a confidence score, and a reason string. Nothing in its type signature references `AttestationIntent`, `AttestationVerdict`, or any of the write-path infrastructure. Putting it in `io.casehub.blocks.attestation` because it *participates in a workflow* that eventually reaches attestation is conflating workflow adjacency with type coupling.

The distinction matters for evolution. Today, intake classification is trust-based — devtown classifies PRs by contributor trust score. But the SPI is generic. A clinical domain could classify by workload. A logistics domain could classify by geographic proximity. None of those have anything to do with attestation. The package name would become misleading the moment a non-trust consumer showed up, and moving types later is migration churn that the right choice now prevents.

So the trust SPIs landed in `io.casehub.blocks.trust` — a package that groups by domain concern without implying a specific mechanism. The attestation write-path types (`AttestationIntent`, `AttestationIntentWriter`, `LifecycleAttestationObserver`) stayed in `io.casehub.blocks.attestation`, where they belong. Clean separation by compile-time dependency, not by workflow diagram.

## The vouch trade-off nobody pretends doesn't exist

`VouchService` was the most interesting design call. A trusted actor sponsors an untrusted one by writing an `ENDORSED` attestation. The constraints are pluggable — minimum voucher trust, capacity limits, trust hierarchy — and the orchestrator runs them all before writing.

The question was whether to make it `@ApplicationScoped` (CDI-managed, one instance per app, constraints discovered automatically) or manually constructed by the consumer. I went with manual construction. A devtown vouch has different rules than a clinical vouch. Baking a single constraint set into a CDI singleton forces either a global lowest-common-denominator or a qualifier-based lookup that adds complexity without value.

The trade-off is honest: without container-managed transactions, two concurrent `vouch()` calls can both pass constraints and both write. The TOCTOU race is real. The spec documents it explicitly and says: consumers that need strict enforcement provide transactional boundaries or use storage-layer constraints. This is better than pretending the race doesn't exist behind a CDI façade.

## The boring bug in the interesting algorithm

The diversity selection work added `OutcomeAwareDiversityStrategy` — outcome-category seeding followed by token-level Jaccard MMR to fill remaining slots. The algorithm is straightforward: seed one example from each outcome category so you don't pick five SUCCESS examples, then iteratively select candidates that maximise relevance while penalising similarity to already-selected examples.

The implementation used `Set.of(text.split("\\s+"))` for tokenisation. Clean, immutable, one line. It also throws `IllegalArgumentException` on duplicate elements. When the input is `"same input"` and the output is `"same output"`, the concatenation produces `["same", "input", "same", "output"]` — and `Set.of()` crashes.

The fix is `new HashSet<>(List.of(text.split(...)))` — trivial once you see it. But `Set.of()` is one of those Java APIs that looks safe until real-world data hits it. Unique elements are a precondition, not a guarantee, and the failure mode is an opaque runtime exception rather than a compile-time signal.

The broader pattern these four SPIs establish is worth naming: blocks sits between the foundation platform and the domain applications, extracting AI-integration patterns that would otherwise be independently reimplemented in every consumer. The trust SPIs are the newest layer — joining the existing routing strategies, oversight gates, conversation protocol, and summarisation framework. Each extraction follows the same test: if removing the AI/integration aspect leaves a generic utility, it belongs in platform, not blocks. If removing the domain-specific aspect leaves a reusable AI-integration pattern, it belongs here.

The open question is how far this extraction pattern runs. Four domain repos currently implement their own lifecycle-to-attestation observers with near-identical structure. Two of them also have intake classification. None have vouching — that's new, driven by the contributor trust design in devtown. As more consumers adopt these SPIs, the patterns will either prove stable or reveal that the abstraction boundary is in the wrong place. The only way to find out is to ship them and see what breaks.
