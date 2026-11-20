---
entry_type: note
subtype: diary
title: "The Producers That Didn't Need to Exist"
projects:
  - casehubio/agency
---

# The Producers That Didn't Need to Exist

The speech-demo app had a file called `SpeechProducers.java` — 322 lines of hardcoded CDI producers wiring every voice engine, STT pipeline, cleanup filter, speaker service, and transcript corrector by hand. It was the kind of file that grows one producer at a time until nobody wants to touch it. Adding a Kokoro voice meant editing Java code. Adding a Qwen3 engine meant editing Java code. Changing the STT backend meant editing Java code. The whole point of the `AvatarDefinition` YAML surface we'd built in #449 was to make this declarative — but the demo was still doing it the old way.

The fix is an `AvatarCompiler` that reads YAML and produces the same runtime beans the hardcoded producers used to create. Drop an `avatar.yaml` in `META-INF/`, and the compiler handles TTS engine instantiation, STT selection, cleanup filter chains, and lip-sync wrapping. No Java code for any of it.

What made this interesting was the precedence model. I wanted three tiers of bean resolution:

1. **Defaults** — `@DefaultBean` producers in connectors-voice-sherpa. Zero config. Any app with the module on classpath gets a working avatar.
2. **YAML** — `avatar.yaml` for when you want specific voices or non-default parameters. Displaces the defaults.
3. **CDI override** — app-level `@Produces` for things YAML can't express. Displaces everything.

The trick is making these compose cleanly in Quarkus CDI. `@DefaultBean` handles tier 1 → tier 2 (a normal-priority bean suppresses a default bean). But tier 2 → tier 3 is harder — two normal-priority beans of the same type produce an `AmbiguousResolutionException`. The build step introspects `BeanDiscoveryFinishedBuildItem` for existing app-level producers and skips synthetic bean registration when it finds them. Clean three-tier precedence with no ambiguity.

The compiler itself follows the existing registry pattern — `KokoroConfigRegistry`, `SherpaConfigRegistry` already translate spec records to runtime objects. `AvatarCompiler` does the same for the full avatar pipeline: iterate engine specs, dispatch per type (vits, sherpa, kokoro, qwen3), merge into a single `TtsModelRegistry`. One compilation pass, one `CompiledAvatar` record, all beans extracted from it.

The `TtsEngineSpec` gained two new fields along the way. `cloneDiscovery` (boolean) replaces the old `List<String> clonedVoices` — you don't enumerate cloned voices in YAML, you tell the compiler to discover them from disk via `Qwen3VoicePool`. And `LipSyncMode` (`auto`/`enabled`/`disabled`) makes lip-sync wrapping configurable per engine instead of hardcoding it in the producer. Auto-detect is the right default — the factory knows whether its engine returns phoneme timing — but the YAML escape hatch exists for performance-constrained deployments.

The `avatar.yaml` itself is 71 lines. It declares four TTS engine families (5 VITS voices, 5 Sherpa voices, 53 Kokoro voices, 6 Qwen3 voices plus cloned-voice discovery), whisper-fallback STT, and cleanup with max destructiveness 3. The frontend's voice dropdown now populates from `/api/avatar/config` at page load instead of hardcoding 40 `<option>` elements. Adding a voice is a YAML edit — add a line, reload.

The bigger question this surfaced: why does the demo app need *any* Java code? The `AgentProvider` for Vertex AI is still a CDI producer because it's deployment-specific (env vars, auth tokens, HTTP client). But `agent-config` exists in the platform. If the LLM provider were declarative too, speech-demo becomes pure configuration — YAML files and static HTML. Any Pages + Mosaic app could have a talking avatar by adding a dependency and dropping a config file. That's the next step: avatar as a self-wiring Quarkus extension with zero Java. This session laid the foundation — the compiler, the registry pattern, the three-tier precedence — and the next one removes the remaining scaffolding.
