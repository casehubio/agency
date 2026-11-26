# Avatar Pipeline as Composable Playbook — Design Spec

**Issue:** casehubio/agency#536
**Date:** 2026-11-26
**Status:** Draft

## Overview

Extend `avatar.yaml` with an optional `pipeline:` section that makes the speech pipeline composable. Users can insert custom text-processing steps between the built-in stages (STT, cleanup, LLM, TTS, lip-sync), reorder cleanup passes, and compose multiple filters declaratively. When the `pipeline:` section is absent, the compiler infers the default pipeline from the flat config — fully backward compatible.

The design builds on the existing `TextFilter` interface from `connectors-voice-spi` rather than creating a parallel text-filtering SPI. Custom text steps implement `TextFilter` directly. Discovery uses a registry pattern (`TextFilterRegistry`) following the established `SummariserRegistry`/`PipelineCompiler` precedent.

**Why not playbook infrastructure?** Issue #536 suggested using the existing playbook infrastructure. The existing playbook templates (`request-response`, `task-completion`, etc.) are event monitors — they count events and emit advisories. The speech pipeline is a request-response data chain with heterogeneous step types and streaming constraints (per-sentence callbacks, per-segment TTS). These don't fit the playbook model. The `pipeline:` section in `avatar.yaml` follows the same YAML + compiler + compiled pattern used by `PipelineDefinition` (summarisation) and `PatternDefinition` (agentic orchestration) — purpose-built surfaces for their respective domains.

## YAML Surface

### Current (unchanged, backward compatible)

```yaml
agent: speech-demo
model: claude-haiku-4-5@20251001
tts-voice: kokoro:af_heart
sample-rate: 16000
speed: 0.9

tts:
  engines:
    - type: kokoro
      voices: [af_heart, am_adam]

stt:
  type: whisper-fallback

cleanup:
  max-destructiveness: 3
```

### Extended (with pipeline section)

```yaml
agent: speech-demo
model: claude-haiku-4-5@20251001
sample-rate: 16000
speed: 0.9

tts:
  engines:
    - type: kokoro
      voices: [af_heart, am_adam]

stt:
  type: whisper-fallback

cleanup:
  max-destructiveness: 3        # still used for post-stt default

pipeline:
  post-stt:                       # overrides cleanup section for post-STT
    - filler-removal: {}
    - casing: {}
    - punctuation: {}
    - grammar: {}
    - vocabulary-filter:          # custom TextFilter, resolved by registry
        dictionary: medical-terms
  post-llm:
    - profanity-filter: {}        # custom TextFilter
  post-segmenter:
    - emphasis-marker: {}         # custom TextFilter operating per-segment
```

### Partial pipeline — coexistence with cleanup

`pipeline:` and `cleanup:` coexist. Each insertion point in `pipeline:` overrides only that position. Undeclared positions use the default from `cleanup:`. This avoids forcing users to replicate the full cleanup chain when they only want to add post-LLM filtering:

```yaml
cleanup:
  max-destructiveness: 3    # drives default post-stt chain

pipeline:                    # only declares post-llm — post-stt uses cleanup default
  post-llm:
    - profanity-filter: {}
```

### Semantics

- Steps within each insertion point execute in YAML list order (preserved, not sorted)
- Built-in cleanup filters (`filler-removal`, `casing`, `punctuation`, `grammar`) are pre-registered in the `TextFilterRegistry`
- When `pipeline:` is absent, the compiler uses `cleanup.max-destructiveness` to build the default post-STT chain (the current behaviour)
- When `pipeline:` declares `post-stt:`, it overrides the `cleanup:`-derived default for that position
- Undeclared positions in `pipeline:` use defaults (post-STT from cleanup, empty for post-LLM and post-segmenter)
- The same filter name may appear in multiple insertion points — each creates a separate instance

## Architecture

### Insertion Points

The speech pipeline has three insertion points, each with different granularity dictated by the streaming architecture:

| Insertion Point | Granularity | Where in Code | What Steps See |
|-----------------|-------------|---------------|----------------|
| `post-stt` | Per-turn | `SpeechSession.handleStop()` and `handleText()` | Full STT transcription or text input |
| `post-llm` | Per-sentence-callback | `TurnExecutor.processUserTurn()` streaming callback | One sentence at a time (streaming path) |
| `post-segmenter` | Per-segment | `TurnExecutor.synthesiseAndSendSegment()` | Individual `ParsedSegment` text |

**Streaming constraint:** In the streaming path, `streamingGenerator.generate()` fires a callback per sentence as the LLM produces it. `segmenter.segment(sentence)` runs inside that callback. Post-LLM steps see one sentence at a time — they cannot see the full turn. This is an explicit design constraint, not a limitation to fix later.

**Two post-STT code paths:** Both `SpeechSession.handleStop()` (STT input) and `handleText()` (direct text input) apply the post-STT chain. The pipeline treats both paths identically — text input is equivalent to already-transcribed speech.

### New Types (in `avatar-yaml`)

```
SpeechPipelineSpec           — record: postStt, postLlm, postSegmenter (all @Nullable List<StepSpec>)
StepSpec                     — record: name, config (Map<String, Object>)
SpeechPipelineCompiler       — compiles SpeechPipelineSpec → CompiledSpeechPipeline
CompiledSpeechPipeline       — record: postSttChain, postLlmChain, postSegmenterChain (all FilterChain)
TextFilterRegistry           — name→factory map (same shape as SummariserRegistry)
TextFilterFactory            — @FunctionalInterface: TextFilter create(Map<String, Object> config)
FilterChain                  — ordered TextFilter[] with apply(String) → String (preserves insertion order)
```

### FilterChain — ordered filter composition

`CleanupConfig` sorts filters by destructiveness, which is correct for the default post-STT chain but wrong for user-specified pipeline ordering. The pipeline uses `FilterChain` — a simple ordered composition that preserves YAML insertion order:

```java
public record FilterChain(TextFilter[] filters) {
    public static final FilterChain EMPTY = new FilterChain(new TextFilter[0]);

    public String apply(String text) {
        for (TextFilter filter : filters) {
            text = filter.apply(text);
        }
        return text;
    }

    public static FilterChain of(TextFilter... filters) {
        return filters.length == 0 ? EMPTY : new FilterChain(filters.clone());
    }
}
```

This lives in `avatar-yaml` (not `connectors-voice-spi`) — it's a pipeline-level composition type, not a change to the SPI. The existing `CleanupConfig` with destructiveness sorting continues to be used for the default post-STT chain when no `pipeline:` section is present.

### TextFilterRegistry

Follows the `SummariserRegistry` pattern:

```java
public class TextFilterRegistry {
    private final Map<String, TextFilterFactory> factories = new ConcurrentHashMap<>();

    public TextFilterRegistry() {
        register("filler-removal", config -> new FillerRemovalFilter());
        register("casing", config -> new CasingFilter());
        register("punctuation", config -> PunctuationFilter.withDefaults());
        register("grammar", config -> GectorFilter.withDefaults());
    }

    public void register(String name, TextFilterFactory factory) {
        factories.put(name, factory);
    }

    public TextFilter create(String name, Map<String, Object> config) {
        var factory = factories.get(name);
        if (factory == null) {
            throw new IllegalArgumentException(
                "Unknown text filter: " + name + ". Registered: " + factories.keySet());
        }
        return factory.create(config);
    }
}
```

**Filter lifecycle:** `TextFilterRegistry.create()` returns a new instance per call. Each `CompiledSpeechPipeline` owns its filter instances. Filters that hold native resources (`PunctuationFilter`, `GectorFilter`) implement `AutoCloseable` — the `CompiledSpeechPipeline` tracks them and closes them when the avatar is shut down. Custom `TextFilter` implementations must be thread-safe if the same pipeline instance is shared across concurrent sessions (which it is — `SpeechWebSocket` creates sessions per connection but shares the compiled pipeline from the CDI-scoped bean).

### Compilation Flow

```
avatar.yaml
    │
    ├── AvatarDefinition (existing)
    │       ├── tts → AvatarCompiler.compileTts() → TtsModelRegistry (existing)
    │       ├── stt → AvatarCompiler.compileStt() → StreamingSpeechToText (existing)
    │       └── cleanup → compileDefault() when pipeline.post-stt absent
    │
    └── SpeechPipelineSpec (new, optional)
            └── SpeechPipelineCompiler.compile(spec, registry, cleanupFallback) → CompiledSpeechPipeline
                    ├── postSttChain → FilterChain (from pipeline.post-stt, or CleanupConfig fallback)
                    ├── postLlmChain → FilterChain (from pipeline.post-llm, or EMPTY)
                    └── postSegmenterChain → FilterChain (from pipeline.post-segmenter, or EMPTY)
```

### Default Pipeline (backward compatibility)

When `pipeline:` is absent, `SpeechPipelineCompiler` synthesises the default pipeline from `AvatarDefinition.cleanup()`. The post-STT chain uses `CleanupConfig` (preserving destructiveness-based ordering), wrapped as a `FilterChain`:

```java
public CompiledSpeechPipeline compileDefault(CleanupConfigSpec cleanup) {
    CleanupConfig defaultCleanup = AvatarCompiler.compileCleanup(cleanup);
    return new CompiledSpeechPipeline(
        FilterChain.of(defaultCleanup),   // wraps CleanupConfig, preserving its ordering
        FilterChain.EMPTY,                // no post-LLM
        FilterChain.EMPTY);               // no post-segmenter
}
```

When `pipeline:` is present but `post-stt:` is absent, the default from `cleanup:` is used for post-STT. Each insertion point is independently defaulted.

### Runtime Integration

`SpeechSession` and `TurnExecutor` receive a `CompiledSpeechPipeline`.

**SpeechSession changes:**
- Constructor accepts `CompiledSpeechPipeline` instead of `CleanupConfig`
- The existing 4-overload constructor chain is consolidated: introduce a `SpeechSessionConfig` record that bundles pipeline, generators, sinks, and optional services. This addresses existing constructor proliferation (R1-11)
- `handleStop()` uses `pipeline.postSttChain().apply(raw)` instead of `cleanupConfig.apply(raw)`
- `handleText()` uses the same `pipeline.postSttChain()` — text input gets the same filtering as STT output

**TurnExecutor changes:**
- Constructor accepts `CompiledSpeechPipeline` as a new parameter (passed from `SpeechSession`)
- Streaming callback applies `pipeline.postLlmChain()`:
  ```java
  responseText = streamingGenerator.generate(prompt, sentence -> {
      String filtered = pipeline.postLlmChain().apply(sentence);
      var segments = segmenter.segment(filtered);
      // ... existing segment processing
  });
  ```
- `synthesiseAndSendSegment()` applies `pipeline.postSegmenterChain()`:
  ```java
  String text = pipeline.postSegmenterChain().apply(seg.text());
  ```
- Non-streaming fallback path: post-LLM chain applied to full response before segmentation

**SpeechWebSocket changes:**
- Inject `CompiledSpeechPipeline` instead of `CleanupConfig`
- Pass pipeline to `SpeechSession` constructor

### Compile-time Validation

`SpeechPipelineCompiler` validates at compile time:
1. All step names resolve in the `TextFilterRegistry`
2. No duplicate step name within the same insertion point (duplicates across insertion points are allowed — each creates a separate instance)

### CorrectionHooks

`CorrectionHooks` from `speech-api` provides a `corrector` (`UnaryOperator<String>`) that is functionally a post-STT text filter. In the current code, the corrector parameter is accepted by `SpeechSession` but not wired into the cleanup chain. This is existing design debt.

The pipeline design does not integrate `CorrectionHooks` — that's a follow-up issue. If integrated later, the corrector would become a registered step (e.g. `correction: {}`) in the post-STT chain, replacing the constructor parameter.

## Module Changes

| Module | Changes |
|--------|---------|
| `avatar-yaml` (spec) | Add `SpeechPipelineSpec`, `StepSpec` records |
| `avatar-yaml` (compiler) | Add `SpeechPipelineCompiler`, `FilterChain`, `TextFilterRegistry`, `TextFilterFactory`. Modify `AvatarCompiler` to compile `pipeline:` section |
| `avatar-yaml` (runtime) | Modify `CompiledAvatar` to include `CompiledSpeechPipeline`. Modify `AvatarRecorder` to add `extractPipeline()` |
| `speech-deployment` | Modify `SpeechAvatarProcessor` to register `CompiledSpeechPipeline` as synthetic CDI bean |
| `speech-ws` | Add `SpeechSessionConfig` record. Modify `SpeechSession` constructor to use config record with `CompiledSpeechPipeline`. Modify `handleStop()`, `handleText()`. Modify `TurnExecutor` constructor and `processUserTurn()`, `synthesiseAndSendSegment()` |
| `speech-ws` (tests) | Add pipeline integration tests. Update existing tests for `CompiledSpeechPipeline` |
| `connectors-voice-spi` | No changes — `TextFilter` used as-is |

## Testing Strategy

1. **Unit tests (avatar-yaml):**
   - `TextFilterRegistry` — register, create, unknown-name error
   - `SpeechPipelineCompiler` — compile explicit pipeline, compile default from cleanup spec, partial pipeline with cleanup fallback, duplicate-name-within-position validation
   - `FilterChain` — preserves insertion order, EMPTY identity
   - `StepSpec` YAML deserialization — kebab-case, config passthrough

2. **Unit tests (speech-ws):**
   - `SpeechSession` with custom post-STT chain — verify filters apply in `handleStop()` and `handleText()`
   - `TurnExecutor` with custom post-LLM chain — verify per-sentence filtering in streaming path
   - `TurnExecutor` with custom post-segmenter chain — verify per-segment filtering before TTS
   - Backward compatibility — no pipeline section produces identical behaviour to current

3. **Integration test (Podman):**
   - Deploy speech-demo with custom pipeline in `avatar.yaml`
   - Verify full roundtrip: speak → STT → custom filter → LLM → TTS → audio out
   - Verify backward compatibility: remove `pipeline:` section, verify default behaviour
   - Verify partial pipeline: pipeline with only `post-llm`, default post-STT from cleanup

## Migration Path

1. No breaking changes — `pipeline:` is optional
2. Existing `avatar.yaml` files continue to work without modification
3. The `cleanup:` section remains valid and provides the default post-STT chain
4. `pipeline:` only overrides insertion points it explicitly declares

## Deferred

- **Async side-effect steps:** Removed from this design. `FilterChain.apply()` is synchronous. Async side-effects (logging, analytics) would require a wrapper type with virtual-thread dispatch. Deferred until a concrete use case exists.
- **Non-text step types:** Audio transforms (volume normalisation, effects) would require a new SPI. Deferred under YAGNI — no concrete use case today.
- **CorrectionHooks integration:** The existing corrector parameter becomes a registered pipeline step in a follow-up.

## References

- `connectors-voice-spi: TextFilter` — existing text→text SPI (4 implementations)
- `connectors-voice-spi: CleanupConfig` — existing filter composition with destructiveness ordering
- `avatar-yaml/compiler/AvatarCompiler.java` — existing compilation pattern
- `avatar-yaml/spec/AvatarDefinition.java` — existing YAML spec records
- `summarisation-yaml/SummariserRegistry.java` — registry pattern precedent
- `summarisation-yaml/PipelineCompiler.java` — registry-based compilation precedent
- `speech-ws/SpeechSession.handleStop()` — post-STT cleanup chain
- `speech-ws/SpeechSession.handleText()` — text input cleanup chain
- `speech-ws/TurnExecutor.processUserTurn()` — streaming sentence callback
- `speech-ws/UtteranceSegmenter.java` — sentence segmentation
- `speech-deployment/SpeechAvatarProcessor` — Quarkus deployment synthetic bean registration
- casehubio/agency#526 — zero-Java avatar self-wiring (predecessor)
