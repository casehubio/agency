---
layout: post
title: "The Prompt That Knows Its Audience"
date: 2026-09-05
entry_type: article
subtype: diary
projects: [casehubio/agency]
tags: [social-cognition, avatar, speech, architecture, composition]
---

# The Prompt That Knows Its Audience

The standard approach to avatar speech is a static system prompt. "You are a helpful assistant. Be friendly." The avatar sounds the same to everyone, regardless of context or history.

Nine social cognition orchestrators now feed into the speech pipeline as composable prompt sections. Personality, mood, drives, narrative identity, goals, mental models of the user, per-subject profiles, learned interaction strategies, and proactive initiation — each adds context to every prompt the avatar constructs.

The architecture that makes this work is more interesting than the feature list.

## One functional interface

```java
@FunctionalInterface
public interface PromptSection {
    @Nullable String contribute(PromptContext context);
}
```

Each capability implements this. MoodPromptSection reads PAD state and renders emotional context. MentalModelPromptSection reads BDI beliefs about the current speaker. StrategyPromptSection delegates to the strategy profile's own rendering. Return null when there's nothing to contribute.

A failing section is logged and skipped. If narrative identity isn't on the classpath, the avatar simply doesn't narrate. The system degrades gracefully to whatever social cognition is available — this is not error handling, it's architectural intent. The avatar works with one section or nine.

## Who are you talking to?

Some capabilities describe the avatar itself — mood, drives, personality. These are agent-scoped. Others describe the person being spoken to — mental model, user profile, interaction strategies. These are subject-scoped.

`PromptContext` carries a `subjectId`, resolved per-turn by speaker identification. Subject-scoped sections return null when no speaker is identified — the avatar can't adapt to someone it doesn't recognise. This distinction threads through the entire signal path: interaction signals only fire when a speaker is identified. User model updates, mental state cues, engagement metrics — all gated on knowing who you're talking to.

## Fast recording, slow understanding

Signal recording happens on the conversation path — synchronous ConcurrentHashMap writes, microseconds. Signal processing happens on a background tick thread — periodic calls that may involve LLM inference. PromptSections read the processed state, always one tick behind.

One tick behind with fresh data beats blocking the conversation for inference. The avatar records that something happened (fast) and separately processes what it means (slow, periodic). The proactive tick loop serves double duty — it processes accumulated signals into queryable state so the next conversation turn has current context, and it evaluates whether the avatar's drives are strong enough to initiate speech.

## The proactive path

When intrinsic drives peak — curiosity from knowledge gaps, affiliation from a neglected relationship — the avatar initiates conversation. The content from InnerLifeOrchestrator is the utterance itself. No second LLM call. No signal recording, because self-initiated speech must not pollute user models. Added to history as an "assistant" turn, not a phantom "user" turn.

This has to be a distinct path from user-initiated conversation. Routing proactive content through the same pipeline as user messages would corrupt history, trigger false user model updates, and make the avatar believe it was responding to something a human said.

## Three layers, zero coupling

speech-api defines the contracts — pure Java, no framework. blocks provides the integration — `SocialAvatarCognition` as an `@ApplicationScoped` bean wiring all nine orchestrators. speech-ws injects `Instance<AvatarCognition>` — if blocks is on the classpath, social cognition activates. If not, the avatar works exactly as before.

The composition root pattern lets consumers pick their depth. An avatar with just mood and personality is useful. Adding user models makes it personal. Adding proactive speech makes it alive. Each layer of social intelligence is optional, and the functional interface guarantees that adding one never breaks another.
