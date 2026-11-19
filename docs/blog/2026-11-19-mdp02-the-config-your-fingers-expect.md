---
layout: post
title: "The Config Your Fingers Expect"
date: 2026-11-19
entry_type: note
subtype: diary
projects: [casehubio/agency]
tags: [yaml, jackson, configuration, developer-experience]
---

# The Config Your Fingers Expect

The design spec for the avatar YAML showed kebab-case properties — `tts-voice`, `camera-view`, `sample-rate`. The actual Java records accepted only camelCase. The spec was aspirational; the code was literal. Anyone who wrote the YAML the way the spec showed it would get an `UnrecognizedPropertyException` and no explanation of what went wrong.

This is a small gap, but it's a common one, and the fix is worth talking about because the obvious answer is wrong.

## The obvious wrong answer

The first instinct is to change `@JsonProperty("ttsVoice")` to `@JsonProperty("tts-voice")`. That's what the YAML should look like, so make the code match. But `@JsonProperty` does two things: it names the property for deserialization *and* for serialization. Change it to `tts-voice` and every existing consumer that reads `ttsVoice` from JSON responses breaks. Worse, the Java accessor is still `ttsVoice()` — the record component name doesn't change — so the serialized output and the accessor would disagree about what the field is called.

The other wrong answer is `PropertyNamingStrategies.KEBAB_CASE` on the ObjectMapper. That converts *all* property names, including ones that are already single words. It also applies globally — you'd need a separate ObjectMapper or mixin for every record that mixes single-word and multi-word properties. And it makes the camelCase form stop working, breaking existing YAML files.

## The right answer: aliases, not renames

![YAML naming bridge](images/yaml-naming-bridge.svg)

Jackson's `@JsonAlias` accepts alternative names for deserialization without changing the canonical property name. The canonical name stays camelCase (what `@JsonProperty` declares, what `ttsVoice()` returns, what serialization produces). The alias adds kebab-case as an accepted input. Both forms work. Nothing breaks.

The pattern looks like this:

```java
@JsonProperty("ttsVoice") @JsonAlias("tts-voice")
@Nullable String ttsVoice,

@JsonProperty("sampleRate") @JsonAlias("sample-rate")
@Nullable Integer sampleRate,
```

For records without a `@JsonCreator` factory, `@JsonAlias` goes on the record component directly:

```java
public record TtsEngineSpec(
        String type,
        List<TtsVoiceSpec> voices,
        @JsonAlias("cloned-voices") @Nullable List<String> clonedVoices) {
```

## Scaling from three records to fifty-seven

The first pass touched three records — `AvatarDefinition`, `TtsEngineSpec`, `CleanupConfigSpec` — six properties in total. That was enough to validate the pattern. But the same gap existed across every spec record in the agentic-yaml module. `failurePolicy` on every `PatternDefinition` variant. `convergenceThreshold` on `TopologySpec.Distributed`. `minSignalsForInference` on `MentalModelConfigSpec`. Two hundred and forty-two multi-word camelCase fields across fifty-seven files, each one a potential `UnrecognizedPropertyException` waiting for someone writing kebab-case YAML.

Doing this by hand would be tedious and error-prone. The conversion itself is mechanical — `camelCase` to `kebab-case` follows a deterministic rule — so I wrote a Python script to scan every record in the spec directory, identify multi-word camelCase components, and insert the annotation. The script handles three cases: bare record components (`@JsonAlias("kebab") Type field`), components with `@Nullable` (annotation goes before `@Nullable`), and components with `@JsonProperty` (annotation goes after it).

## When aliases collide

One record threw a curveball. `CognitionDefinition` already had a `@JsonAlias` on the personality field — not for kebab-case, but as a semantic shorthand:

```java
@JsonProperty("personalityEvolution") @JsonAlias("personality")
@Nullable PersonalityEvolutionConfigSpec personality,
```

The YAML key is `personalityEvolution`. The existing alias `personality` lets authors skip the `Evolution` suffix. The kebab form `personality-evolution` needs to be a *second* alias, not a replacement. Jackson supports this with the array form:

```java
@JsonProperty("personalityEvolution")
@JsonAlias({"personality", "personality-evolution"})
@Nullable PersonalityEvolutionConfigSpec personality,
```

Now all three names resolve to the same field — the canonical `personalityEvolution`, the semantic shorthand `personality`, and the kebab-case `personality-evolution`. Each serves a different audience: serialization, brevity, convention.

This is the general principle at work. `@JsonAlias` is additive. It doesn't rename or replace — it widens the set of names that resolve to a field. When a third convention arrives, another alias costs one annotation.

## The broader principle

Config surfaces have two audiences with different conventions. The Java code that declares the shape of the config uses camelCase because that's what Java identifiers do. The YAML author who writes the config file expects kebab-case because that's what YAML conventions do. Neither audience is wrong. The serialization layer's job is to bridge the gap without forcing either side to adopt the other's convention.

The alternative — picking one convention and forcing everyone to use it — always trades one audience's comfort for another's confusion. Aliases avoid the trade entirely.
