---
layout: post
title: "The Framework Was Already Generic"
date: 2026-10-26
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [prompt-optimisation, swarm, dspy, composition]
series: issue-362-coordinator-prompt-tuning
---

# The Framework Was Already Generic

The prompt optimisation framework was built for routing. `PromptSignature` declares an optimisation target. `PromptVariant` carries versioned examples and instruction deltas. `PromptOptimisationBatch` runs the offline improvement cycle — score variants, promote winners, demote losers, create new candidates. `VariantSelector` does deterministic A/B splits with per-capability circuit breakers.

All of this was designed around routing prompts: which agent should handle this task? The runtime beans — `OptimisedFewShotSection`, `VariantAwareSystemPromptCustomiser` — implement `RoutingPromptSection` and talk `AgentRoutingContext`. Routing-specific.

Except the core isn't. `PromptSignature` is a record with an id, a description, a base system prompt, and I/O types. It doesn't know what routing is. `PromptVariant` carries examples and an instruction delta — nothing about agents or capabilities. `PromptOptimisationBatch` orchestrates optimisers against datasets — the dataset could be anything. The routing specificity lives entirely in the leaf classes: the section that renders into routing prompts, the metric that weights routing outcomes.

The swarm coordinator has three LLM call sites. Team formation asks "which agents, what roles, what strategy?" Collective reasoning synthesises multi-agent observations into agreements and disagreements. Provisioning advisory decides whether to spin up another agent. Three different prompts, three different output shapes, three different notions of "good outcome."

Wiring prompt optimisation to these call sites required no changes to the core framework. Three `PromptSignature` constants. A `SwarmPromptSection` implementation that pulls few-shot examples from the variant store — structurally identical to the routing version, just rendering "team decision" instead of "routing decision." A `SwarmOutcomeMetric` that scores CONVERGED at 1.0 and DEADLOCK at 0.0 instead of SUCCESS and FAILURE. A `SwarmOutcomeMapper` that normalises convergence outcomes to `VariantOutcome` records.

The only real work was injection points. `CollectiveReasoningAggregation` and `SwarmProvisioningAdvisorImpl` build their prompts internally — private `buildPrompt()` methods with no extension hooks. Adding an optional `SystemPromptCustomiser` to each constructor gives the variant system a place to append instruction deltas. The existing `VariantAwareSystemPromptCustomiser` handles the rest — look up the active variant, append the delta if present, return the prompt unchanged if not.

The builder got one new method: `promptCustomiser()`. It wires the customiser into the aggregation at construction time. Backward-compatible — null customiser means the prompt passes through unchanged, which is exactly what happened before.

## What this means for YAML

The ad-hoc nature of swarms makes this particularly interesting. A routing pipeline has a fixed structure: capabilities, trust scores, candidate pools. You configure it once and it runs. A swarm is compositional and ephemeral — different team formation strategies, different convergence thresholds, different provisioning policies per execution. The prompt signatures give the YAML surface something concrete to reference: declare which signatures to optimise, configure the batch cycle, let the offline loop do its work.

The `PromptSignature` id becomes the binding key. A YAML pipeline definition can say "optimise `swarm-team-formation` with these safety rails" without knowing anything about how the optimisation works. The same mechanism that auto-improves routing prompts now auto-improves swarm coordination prompts — same batch cycle, same A/B testing, same circuit breakers. Different weights, different examples, different prompts. Same infrastructure.
