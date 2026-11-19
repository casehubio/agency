# Design: Migrate SpeechProducers to AvatarDefinition YAML

**Issue:** casehubio/agency#519
**Branch:** `issue-519-migrate-speechproducers-to-avatar-yaml`

## Problem

`SpeechProducers.java` in `speech-demo` hardcodes 12 CDI producers for voice engines, STT, cleanup, speaker services, and transcript correction. The `AvatarDefinition` YAML infrastructure exists (#449) but only parses and serves config — it doesn't produce runtime beans. Apps must write CDI producers to wire speech pipelines.

## Goal

Drop an `avatar.yaml` in `META-INF/` and get a fully wired avatar speech pipeline. No CDI producers required in the app for voice/STT/cleanup. Three-tier precedence: sensible defaults → YAML customisation → CDI override.

## Architecture

### Three-Tier Bean Resolution

```
Tier 3 (highest): App CDI @Produces        → AgentProvider (Vertex AI), custom engines
Tier 2:           avatar.yaml → Recorder    → specific voices, sample rate, cleanup
Tier 1 (lowest):  @DefaultBean              → sensible zero-config defaults
```

Each YAML section independently produces its bean. Missing sections fall back to tier 1.

**CDI precedence mechanism:** Tier 1 beans use `@DefaultBean`, which Quarkus suppresses when any normal-priority bean of the same type exists. Tier 2 synthetic beans are registered as **normal-priority** (not `@DefaultBean`). To avoid `AmbiguousResolutionException` when a tier 3 app producer coexists with a tier 2 synthetic bean, each build step introspects `BeanDiscoveryFinishedBuildItem` for existing app-level producers of the same type and **skips synthetic bean registration** when found. This gives clean three-tier precedence:
- No app producer + no YAML → tier 1 `@DefaultBean` fires
- YAML present, no app producer → tier 2 synthetic bean fires, suppresses tier 1
- App producer exists → build step skips tier 2, app producer fires, suppresses tier 1

### Component Overview

```
avatar.yaml ──▶ AgenticYamlProcessor ──▶ AvatarConfigBuildItem
                     (build time)              │
                                               ▼
                                       AgenticRecorder ──▶ Synthetic CDI beans
                                        (RUNTIME_INIT)     (TtsModelRegistry,
                                               │            StreamingSpeechToText,
                                               │            CleanupConfig,
                                               │            CorrectionHooks)
                                               │
                                       AvatarCompiler
                                               │
                              ┌────────────────┼────────────────┐
                              ▼                ▼                ▼
                      TtsEngineRegistry   SttRegistry    CleanupRegistry
                       (per engine type)
```

## Module Changes

### agentic-yaml — New: AvatarCompiler + engine registries

**Location:** `io.casehub.agency.core.yaml.compiler`

**AvatarCompiler** — composition root. Takes an `AvatarDefinition`, delegates to per-type registries, returns a `CompiledAvatar` in a single pass.

```java
public class AvatarCompiler {
    public CompiledAvatar compile(AvatarDefinition definition);

    public record CompiledAvatar(
            @Nullable TtsModelRegistry ttsModelRegistry,
            @Nullable TextToSpeech defaultTts,
            @Nullable StreamingSpeechToText stt,
            @Nullable CleanupConfig cleanupConfig) {}
}
```

The compile method builds TTS engines once, picks the first voice as `defaultTts`, and delegates STT/cleanup to their respective registries. Null sections produce null fields — the build step gates bean registration on non-null.

**TtsEngineRegistry** — resolves `TtsEngineSpec` → `Map<String, TextToSpeech>`. Dispatches on `type`:

| Type | Construction | Key format |
|------|-------------|------------|
| `vits` | `VitsTextToSpeech.withDefaults(model)` per voice. Native phoneme timing. | `{voiceName}` |
| `sherpa` | `SherpaOnnxTextToSpeech.withDefaults(model)` per voice. Auto-wraps with `LipSyncEnricher` per `lipSync` field. | `sherpa:{voiceName}` |
| `kokoro` | One shared `KokoroTextToSpeech.withDefaults(0)`. Each voice → `kokoro.forVoice(index)`. Name→index mapping internal. | `kokoro:{voiceName}` |
| `qwen3` | Static voices: `Qwen3TextToSpeech.withDefaults(name)`. Cloned: `Qwen3VoicePool.createProvisioned()` → `pool.forVoice(name)`. | `qwen3:{name}`, `qwen3:clone:{name}` |

Lip-sync wrapping follows the `lipSync` field on `TtsEngineSpec`:
- `auto` (default): wrap when engine returns empty phonemes and `EspeakPhonemeAligner` is available
- `enabled`: wrap (error if no aligner)
- `disabled`: skip

**SttRegistry** — resolves `SttSpec` → `StreamingSpeechToText`:

| Type | Construction |
|------|-------------|
| `whisper` | `WhisperSpeechToText.withDefaults()` |
| `zipformer` | `SherpaOnnxStreamingSpeechToText.withDefaults()` |
| `whisper-fallback` (default) | Whisper with Zipformer fallback |

**CleanupRegistry** — resolves `CleanupConfigSpec` → `CleanupConfig`. Instantiates filters from a hardcoded list in order of increasing destructiveness:

1. `FillerRemovalFilter` (destructiveness 0) — always available
2. `CasingFilter` (destructiveness 1) — always available
3. `PunctuationFilter.withDefaults()` (destructiveness 2) — requires ONNX model, try-catch graceful degradation
4. `GectorFilter.withDefaults()` (destructiveness 3) — requires ONNX model, try-catch graceful degradation

Filters with `destructiveness > maxDestructiveness` are excluded. Filters whose native model fails to load are silently skipped (logged at WARNING). This matches the existing `SpeechProducers.cleanupConfig()` pattern exactly.

Follows the existing `KokoroConfigRegistry`/`SherpaConfigRegistry` pattern — stateless resolvers, spec record → runtime instance.

### agentic-yaml — Spec changes

**TtsEngineSpec** — add optional `lipSync` field, change `clonedVoices` to boolean:

```java
public record TtsEngineSpec(
        String type,
        List<TtsVoiceSpec> voices,
        boolean cloneDiscovery,
        @Nullable LipSyncMode lipSync) {
    // lipSync defaults to AUTO when null
    // cloneDiscovery: when true, discover cloned voices from .wav files on disk via Qwen3VoicePool
}
```

The existing `@Nullable List<String> clonedVoices` is replaced with `boolean cloneDiscovery` (`@JsonAlias("cloned-voices")`). Jackson can deserialize `cloned-voices: true` into a boolean. The voice pool discovers available voices from disk at startup — no explicit enumeration needed. Only Qwen3 supports cloned voices; the compiler ignores `cloneDiscovery` for other engine types.

```java
public enum LipSyncMode { AUTO, ENABLED, DISABLED }
```

### agentic-yaml — JDK 22+ conditional compilation

The new `AvatarCompiler`, `TtsEngineRegistry`, `SttRegistry`, and `CleanupRegistry` reference FFM-based types from `connectors-voice-sherpa` (`VitsTextToSpeech`, `SherpaOnnxTextToSpeech`, `KokoroTextToSpeech`, `Qwen3TextToSpeech`, `WhisperSpeechToText`, etc.). These classes must be excluded from compilation in the default Maven profile and only compiled in the `jdk22+` profile, matching the existing treatment of `KokoroConfigRegistry`, `SherpaConfigRegistry`, and `Audio8ConfigRegistry`.

On JDK < 22, no YAML-driven speech beans are produced. Apps on JDK < 22 must provide their own CDI producers or rely on `@DefaultBean` defaults from `connectors-voice-sherpa` (which also requires JDK 22+).

### agentic-yaml-deployment — Build step extensions

A single build step compiles the `AvatarDefinition` once via the recorder and registers all synthetic beans from the compiled result. Each bean type is registered only when (a) the YAML section is present and (b) no app-level producer for that type exists in `BeanDiscoveryFinishedBuildItem`.

```java
@BuildStep
@Record(RUNTIME_INIT)
List<SyntheticBeanBuildItem> registerAvatarSpeechBeans(
        AgenticRecorder recorder,
        AvatarConfigBuildItem avatarConfig,
        BeanDiscoveryFinishedBuildItem beanDiscovery) {
    if (avatarConfig.definition() == null) return List.of();

    var def = avatarConfig.definition();
    RuntimeValue<AvatarCompiler.CompiledAvatar> compiled = recorder.compileAvatar(def);
    var beans = new ArrayList<SyntheticBeanBuildItem>();

    // TtsModelRegistry — only if tts section present and no app producer
    if (def.tts() != null && !hasAppBean(beanDiscovery, TtsModelRegistry.class)) {
        beans.add(SyntheticBeanBuildItem.configure(TtsModelRegistry.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.extractTtsModelRegistry(compiled))
                .done());
    }

    // Default TextToSpeech — from first voice in first engine
    if (def.tts() != null && !def.tts().engines().isEmpty()
            && !hasAppBean(beanDiscovery, TextToSpeech.class)) {
        beans.add(SyntheticBeanBuildItem.configure(TextToSpeech.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.extractDefaultTts(compiled))
                .done());
    }

    // StreamingSpeechToText
    if (def.stt() != null && !hasAppBean(beanDiscovery, StreamingSpeechToText.class)) {
        beans.add(SyntheticBeanBuildItem.configure(StreamingSpeechToText.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.extractStt(compiled))
                .done());
    }

    // CleanupConfig
    if (def.cleanup() != null && !hasAppBean(beanDiscovery, CleanupConfig.class)) {
        beans.add(SyntheticBeanBuildItem.configure(CleanupConfig.class)
                .scope(Singleton.class)
                .runtimeValue(recorder.extractCleanup(compiled))
                .done());
    }

    // CorrectionHooks — assembled lazily from CDI-resolved TranscriptCorrector + ConversationVocabulary
    if (!hasAppBean(beanDiscovery, CorrectionHooks.class)) {
        beans.add(SyntheticBeanBuildItem.configure(CorrectionHooks.class)
                .scope(Singleton.class)
                .createWith(recorder.correctionHooksCreator())
                .done());
    }

    return beans;
}
```

Each section is independently gated — partial YAML is valid. `hasAppBean()` introspects `BeanDiscoveryFinishedBuildItem` for existing normal-priority producers.

### agentic-yaml runtime — Recorder extensions

The recorder compiles once and returns a `CompiledAvatar` from which individual beans are extracted:

```java
@Recorder
public class AgenticRecorder {
    // existing methods...

    public RuntimeValue<AvatarCompiler.CompiledAvatar> compileAvatar(AvatarDefinition definition) {
        return new RuntimeValue<>(new AvatarCompiler().compile(definition));
    }

    public RuntimeValue<TtsModelRegistry> extractTtsModelRegistry(
            RuntimeValue<AvatarCompiler.CompiledAvatar> compiled) {
        return new RuntimeValue<>(compiled.getValue().ttsModelRegistry());
    }

    public RuntimeValue<TextToSpeech> extractDefaultTts(
            RuntimeValue<AvatarCompiler.CompiledAvatar> compiled) {
        return new RuntimeValue<>(compiled.getValue().defaultTts());
    }

    public RuntimeValue<StreamingSpeechToText> extractStt(
            RuntimeValue<AvatarCompiler.CompiledAvatar> compiled) {
        return new RuntimeValue<>(compiled.getValue().stt());
    }

    public RuntimeValue<CleanupConfig> extractCleanup(
            RuntimeValue<AvatarCompiler.CompiledAvatar> compiled) {
        return new RuntimeValue<>(compiled.getValue().cleanupConfig());
    }

    // CorrectionHooks uses createWith() — resolves CDI beans lazily at runtime
    public Function<SyntheticCreationalContext<CorrectionHooks>, CorrectionHooks>
            correctionHooksCreator() {
        return ctx -> {
            var corrector = Arc.container().instance(TranscriptCorrector.class).get();
            var vocabulary = Arc.container().instance(ConversationVocabulary.class).get();
            return new CorrectionHooks(
                    corrector::correct,
                    response -> {
                        vocabulary.addFromText(response);
                        corrector.addVocabulary(vocabulary.terms().toArray(String[]::new));
                    },
                    vocabulary::asPromptHint);
        };
    }
}
```

**CompiledAvatar** — immutable result record:

```java
public record CompiledAvatar(
        @Nullable TtsModelRegistry ttsModelRegistry,
        @Nullable TextToSpeech defaultTts,
        @Nullable StreamingSpeechToText stt,
        @Nullable CleanupConfig cleanupConfig) {}
```

`AvatarCompiler.compile(AvatarDefinition)` builds the full result in a single pass — TTS engines are instantiated once and shared between `ttsModelRegistry` and `defaultTts`.

### speech-api — Type relocations (D9)

Move from `speech-ws` to `speech-api`:

- **`TtsModelRegistry`** — depends only on `TextToSpeech` from `connectors-voice-spi` (already a speech-api dependency). Package: `io.casehub.agency.speech`.
- **`CorrectionHooks`** — depends only on JDK functional types. Package: `io.casehub.agency.speech`.

Existing references in `speech-ws` (`SpeechWebSocket`, `SpeechSession`) update import paths. No API change.

### connectors-voice-sherpa (peer repo) — @DefaultBean producers

New `DefaultSpeechProducers` class with `@DefaultBean @ApplicationScoped` producers:

| Bean | Default |
|------|---------|
| `TextToSpeech` | `VitsTextToSpeech.withDefaults()` |
| `StreamingSpeechToText` | Whisper with Zipformer fallback |
| `CleanupConfig` | filler + casing + punctuation + GECToR (graceful degradation) |
| `SpeakerEmbeddingExtractor` | campplus model |
| `SpeakerRegistry` | `CosineDistanceSpeakerRegistry` |
| `VoiceprintStore` | `FileVoiceprintStore` at `~/.casehub/voiceprints` |
| `SpeakerDiarizationService` | sherpa diarization (segmentation + campplus) |
| `ConversationVocabulary` | empty vocabulary |
| `TranscriptCorrector` | SymSpell + phonetic + ngram (graceful degradation) |

These fire only when no YAML-driven or app-level bean exists.

### speech-demo — Simplified

**`SpeechProducers.java`** shrinks to just the `AgentProvider` producer:

```java
@ApplicationScoped
public class SpeechProducers {
    @Produces @ApplicationScoped
    AgentProvider agentProvider() { /* Vertex AI wiring — stays hardcoded */ }
}
```

**New `META-INF/avatar.yaml`:**

```yaml
agent: speech-demo
model: claude-haiku-4-5@20251001
sample-rate: 16000
speed: 0.9
min-playback-rate: 0.6

tts:
  engines:
    - type: vits
      voices:
        - lessac-medium
        - { name: lessac-high, model: vits-piper-en_US-lessac-high }
        - { name: amy, model: vits-piper-en_US-amy-medium }
        - { name: ryan, model: vits-piper-en_US-ryan-high }
        - { name: jenny, model: vits-piper-en_GB-jenny_dioco-medium }
    - type: sherpa
      voices:
        - lessac-medium
        - { name: lessac-high, model: vits-piper-en_US-lessac-high }
        - { name: amy, model: vits-piper-en_US-amy-medium }
        - { name: ryan, model: vits-piper-en_US-ryan-high }
        - { name: jenny, model: vits-piper-en_GB-jenny_dioco-medium }
    - type: kokoro
      voices:
        - af_alloy
        - af_aoede
        - af_bella
        - af_heart
        - af_jessica
        - af_kore
        - af_nicole
        - af_nova
        - af_river
        - af_sarah
        - af_sky
        - am_adam
        - am_echo
        - am_eric
        - am_fenrir
        - am_liam
        - am_michael
        - am_onyx
        - am_puck
        - am_santa
        - bf_alice
        - bf_emma
        - bf_isabella
        - bf_lily
        - bm_daniel
        - bm_fable
        - bm_george
        - bm_lewis
        - ef_dora
        - em_alex
        - ff_siwis
        - hf_alpha
        - hf_beta
        - hm_omega
        - hm_psi
        - if_sara
        - im_nicola
        - jf_alpha
        - jf_gongitsune
        - jf_nezumi
        - jf_tebukuro
        - jm_kumo
        - pf_dora
        - pm_alex
        - pm_santa
        - zf_xiaobei
        - zf_xiaoni
        - zf_xiaoxiao
        - zf_xiaoyi
        - zm_yunjian
        - zm_yunxi
        - zm_yunxia
        - zm_yunyang
    - type: qwen3
      voices: [ryan, aiden, eric, dylan, serena, vivian]
      cloned-voices: true  # discover cloned voices from .wav files on disk via Qwen3VoicePool

stt:
  type: whisper-fallback

cleanup:
  max-destructiveness: 3
```

### AvatarConfig (properties) scope (D7)

`AvatarConfig` retains runtime/platform fields only. Physical-layer fields (`sampleRate`, `minPlaybackRate`) are deprecated — YAML is authoritative for these.

**Retained in AvatarConfig:** `systemPrompt`, `agentId`, `tenantId`, `proactiveTickIntervalSeconds`, `attentionEnabled`, `attentionSubtleSeconds`, `attentionStrongSeconds`, `maxDestructiveness`.

**Authoritative in AvatarDefinition YAML:** `sampleRate`, `minPlaybackRate`, `speed`, `ttsVoice`, `cameraView`, `model`, `tts`, `stt`, `cleanup`.

`SpeechWebSocket.onOpen()` continues reading physical-layer values from `AvatarConfig` (SmallRye properties). When `avatar.yaml` is present, the deployment recorder produces the speech engine beans — `SpeechWebSocket` doesn't need to read `AvatarConfigBean` directly. The `AvatarConfig.sampleRate()` and `AvatarConfig.minPlaybackRate()` properties remain functional for backward compatibility but are deprecated in favour of YAML.

## Testing

- **AvatarCompiler unit tests** — each registry method with known specs → verify correct engine types and voice mappings
- **TtsEngineRegistry** — test each engine type (vits, sherpa, kokoro, qwen3) with voice specs
- **LipSync wrapping** — test auto/enabled/disabled modes
- **Partial YAML** — verify that missing sections fall back to @DefaultBean
- **Full integration** — speech-demo with avatar.yaml, verify `/api/avatar/config` serves correct config and `SpeechWebSocket` connects successfully
- **Backward compatibility** — speech-demo with no avatar.yaml, verify @DefaultBean path works

## Migration Path

1. Type relocations (TtsModelRegistry, CorrectionHooks → speech-api)
2. Add engine registries and AvatarCompiler to agentic-yaml
3. Extend deployment processor and recorder
4. Add @DefaultBean producers to connectors-voice-sherpa
5. Create avatar.yaml in speech-demo
6. Slim SpeechProducers to AgentProvider only
7. Verify all 205 speech-ws tests pass
8. Verify `/api/avatar/config` endpoint

## Scope Boundaries

**In scope:**
- AvatarCompiler + per-engine-type registries
- Deployment/recorder extensions for synthetic bean production
- Type relocations (TtsModelRegistry, CorrectionHooks)
- @DefaultBean producers in connectors-voice-sherpa
- avatar.yaml in speech-demo
- SpeechProducers cleanup

**Out of scope (follow-up issues to file):**
- Extending YAML for speaker diarization/embedding/registry (D4 — these are @DefaultBean infrastructure)
- Frontend index.html update to consume `/api/avatar/config` endpoint (issue #519 bullet 5 — the config endpoint already exists from #449; the frontend integration is a separate UI concern. File a follow-up issue before closing #519.)
- AgentProvider YAML surface (deployment-specific, stays CDI)

## References

- `SpeechProducers.java` — current hardcoded producers (322 lines)
- `AvatarDefinition.java`, `TtsSpec.java`, `TtsEngineSpec.java`, `TtsVoiceSpec.java`, `SttSpec.java`, `CleanupConfigSpec.java` — existing YAML spec records
- `KokoroConfigRegistry.java`, `SherpaConfigRegistry.java` — existing registry pattern
- `AgenticYamlProcessor.java` — existing deployment processor with `discoverAvatarConfig()` build step
- `AgenticRecorder.java` — existing recorder with `createAvatarConfigBean()` method
- `AvatarConfigBean.java`, `AvatarConfigResource.java` — existing runtime bean + REST endpoint
- `SpeechWebSocket.java` — consumer of all speech beans
- `AvatarConfig.java` — SmallRye config mapping for runtime/platform fields
- `TtsModelRegistry.java` — record to relocate from speech-ws to speech-api
- `CorrectionHooks.java` — record to relocate from speech-ws to speech-api
