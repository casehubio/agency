# Qwen3-TTS Panama/FFM Shared Library Integration

**Issue:** casehubio/agency#453
**Date:** 2026-11-10
**Status:** Draft

## Summary

Replace the HTTP subprocess approach for Qwen3-TTS with a native shared library binding via Java FFM/Panama. Eliminates the sidecar server process and HTTP overhead by calling `libqwen` directly through MethodHandle downcalls.

## Architecture

### Fork: casehubio/qwentts.cpp

Fork of ServeurpersoCom/qwentts.cpp. Two additions:

1. **Streaming callback API** — `qt_synthesize_streaming(ctx, params, callback, userdata)` exposing the internal frame-by-frame codec decode path that tts-server already uses. Callback signature: `void (*)(const float* samples, int n_samples, void* userdata)`. Contribute back upstream.

2. **CI release pipeline** — GitHub Actions builds `libqwen.{so,dylib,dll}` for osx-arm64, osx-x64, linux-x64, linux-arm64 on every tagged release. Artifacts published as GitHub release assets with SHA-256 checksums.

Build configuration: `cmake -DQWEN_SHARED=ON -DCMAKE_BUILD_TYPE=Release`.

### New Classes (in connectors/voice-sherpa)

#### Qwen3Library

Singleton FFM binding. Same pattern as `SherpaLibrary`.

- 3-tier loading: system library path → local cache → auto-download
- MethodHandle fields for all `qt_*` functions:
  - `qt_init_default_params` — populate init param struct with defaults
  - `qt_init` — load talker + codec GGUF models, return opaque context
  - `qt_tts_default_params` — populate TTS param struct with defaults
  - `qt_synthesize` — batch synthesis, fills qt_audio struct
  - `qt_synthesize_streaming` — streaming synthesis with per-chunk callback (our fork addition)
  - `qt_extract_voice_ref` — compute speaker embedding + RVQ codes from reference WAV
  - `qt_audio_free` — release audio buffer
  - `qt_voice_ref_free` — release voice ref buffers
  - `qt_free` — destroy context, free model resources
- `load()`, `load(Path)`, `isAvailable()` static methods
- Version constant tracking the qwentts.cpp release tag

#### Qwen3Layouts

MemoryLayout definitions for the C structs:

- `QT_INIT_PARAMS` — talker_path, codec_path, additional fields with VarHandles
- `QT_TTS_PARAMS` — text, lang, ref_text, ref_spk_emb, ref_codes, sampling params
- `QT_AUDIO` — float* samples, int n_samples, int sample_rate, int channels
- `QT_VOICE_REF` — speaker embedding array, code matrix

VarHandle accessors for reading/writing struct fields from Java.

### Modified Classes

#### Qwen3TextToSpeech

Complete rewrite of internals. Public API (`StreamingTextToSpeech`, `AutoCloseable`) unchanged.

**Before:** HttpClient + subprocess management + JSON serialization
**After:** Qwen3Library FFM calls

- Constructor takes `Qwen3Library`, `MemorySegment` (qt_context), speaker config
- `withDefaults(speaker)` — loads Qwen3Library, provisions GGUF models, calls `qt_init`
- `synthesise(text, options)` — allocates Arena, fills `qt_tts_params`, calls `qt_synthesize`, copies samples to WAV bytes, frees audio
- `synthesiseStreaming(text, options, onWavChunk)` — creates FFM upcall stub for the callback, calls `qt_synthesize_streaming`, each callback invocation wraps PCM chunk as WAV and passes to `onWavChunk`
- `close()` — calls `qt_free` (replaces process.destroy())

No HttpClient, no ProcessBuilder, no JSON.

#### Qwen3VoicePool

Dramatically simplified. No process management.

**Before:** LRU pool of OS processes, each running a server on a dedicated port
**After:** ConcurrentHashMap of extracted voice refs (MemorySegment pointers)

- `acquireVoice(name)` — checks cache, calls `qt_extract_voice_ref()` from reference WAV if missing, stores MemorySegment
- `releaseVoice(name)` — calls `qt_voice_ref_free()`, removes from cache
- No more `Slot`, `Process`, port allocation, health-check polling, LRU eviction
- Voice ref extraction is fast (sub-second) so no lazy-start needed

Input: raw WAV files in voice directory (replaces .qvoice files).

#### Provisioner

Updated provisioning methods:

- `ensureQwen3SharedLibrary()` — downloads `libqwen.{so,dylib,dll}` from casehubio/qwentts.cpp releases. Same SHA-256 verification pattern as `ensureNativeLibrary()` for sherpa-onnx. Replaces `ensureQwen3Binary()`.
- `ensureQwen3Model(variant)` — downloads GGUF models from Serveurperso/Qwen3-TTS-GGUF on HuggingFace. Two files: talker GGUF + codec GGUF. Replaces current safetensors download.
- Remove `ensureQwen3Binary()` and `QWEN3_BINARY_ASSETS` (CLI binary no longer needed).
- Remove `QWEN3_MODEL_FILES` safetensors list and `provisionFromHuggingFace` safetensors logic.

Model variants map to GGUF files:
- `0.6b-q8` → `qwen3-tts-0.6b-q8_0.gguf` + `qwen3-tts-codec.gguf`
- `0.6b-q4` → `qwen3-tts-0.6b-q4_k_m.gguf` + `qwen3-tts-codec.gguf`
- `1.7b-q8` → `qwen3-tts-1.7b-q8_0.gguf` + `qwen3-tts-codec.gguf`

#### Qwen3ClonedVoice

Updated to pass voice ref MemorySegment through `qt_tts_params` instead of relying on server-side `--load-voice`.

### Consumer Updates (agency/speech-demo)

#### ModelProvisioningService

- `provisionQwen3Binary()` → `provisionQwen3SharedLibrary()` (calls `Provisioner.ensureQwen3SharedLibrary()`)
- `provisionQwen3Model()` — updated to download GGUF variant

#### SpeechProducers

- `Qwen3TextToSpeech.withDefaults(speaker)` — same call, different internals
- `Qwen3VoicePool.createProvisioned(config)` — config updated to point at WAV voice directory instead of .qvoice directory

### Voice File Migration

Existing `.qvoice` files need to be replaced with the original reference WAV files they were derived from. The WAV files should be placed in the voice directory with the same naming convention: `{name}.wav` instead of `{name}.qvoice`.

## Data Flow

### Batch Synthesis
```
Java caller
  → Qwen3TextToSpeech.synthesise(text)
    → Arena.ofConfined()
    → fill qt_tts_params struct (text, lang, voice ref if cloned)
    → Qwen3Library.qt_synthesize(ctx, params, audio)  [FFM downcall]
    → read qt_audio.samples (float[]) from MemorySegment
    → encode as WAV bytes
    → Qwen3Library.qt_audio_free(audio)
    → return SynthesisResult(wavBytes)
```

### Streaming Synthesis
```
Java caller
  → Qwen3TextToSpeech.synthesiseStreaming(text, onWavChunk)
    → Arena.ofConfined()
    → create FFM upcall stub: (float* samples, int n, void* ud) → {
        wrap PCM as WAV chunk
        onWavChunk.accept(wavBytes)
      }
    → fill qt_tts_params struct
    → Qwen3Library.qt_synthesize_streaming(ctx, params, callback, userdata)  [FFM downcall]
    → callback fires per generated chunk during synthesis
    → Arena closes, upcall stub freed
```

### Voice Extraction
```
Java caller
  → Qwen3VoicePool.acquireVoice("emma")
    → cache miss
    → read emma.wav as float[] PCM
    → Qwen3Library.qt_extract_voice_ref(ctx, samples, n_samples, voice_ref)
    → store MemorySegment in ConcurrentHashMap
    → return voice ref
```

## Thread Safety

- `Qwen3Library` singleton is thread-safe (immutable after construction, same as SherpaLibrary)
- `qt_context` — check qwentts.cpp docs for thread safety. If not thread-safe, use `KeyedLock` per context (same pattern as LLM orchestrators)
- `Qwen3VoicePool` — ConcurrentHashMap for voice ref cache, Arena ownership per extraction call
- FFM upcall stubs — allocated per streaming call in a confined Arena, freed when Arena closes

## Testing

- `Qwen3LibraryTest` — verify library loading, struct layout correctness (field offsets match C sizeof/offsetof), method handle resolution
- `Qwen3TextToSpeechTest` — mock Qwen3Library, verify parameter passing, WAV encoding, error handling
- `Qwen3VoicePoolTest` — verify voice ref extraction lifecycle, cache behavior, cleanup
- Integration test (requires native library + GGUF model on disk) — end-to-end synthesis, gated by system property `casehub.speech.integration-tests`

## Scope Boundary

**In scope:**
- Qwen3Library FFM binding
- Qwen3Layouts struct definitions
- Qwen3TextToSpeech rewrite
- Qwen3VoicePool simplification
- Provisioner updates (shared lib + GGUF models)
- speech-demo consumer updates

**Out of scope:**
- casehubio/qwentts.cpp fork creation and CI pipeline (prerequisite, separate task)
- Native module Maven artifacts (voice-sherpa-native-qwen-{platform}) — follow-up if classpath loading is needed
- GPU/Metal/Vulkan backend selection — CPU-only initially, backend selection can be added to Qwen3Library.load() later
- Phoneme timing from Qwen3 (no duration predictor exposure in C API)

## References

- SherpaLibrary.java — FFM binding pattern (3-tier loading, MethodHandle fields, singleton)
- SherpaLayouts.java — MemoryLayout + VarHandle pattern for C struct access
- OnnxRuntimeLibrary.java — alternative FFM pattern (vtable bindings)
- Audio8TextToSpeech.java — VoiceRegistry pattern for voice cloning lifecycle
- Qwen3TextToSpeech.java — current HTTP implementation being replaced
- Qwen3VoicePool.java — current process pool being simplified
- Provisioner.java — native library and model provisioning patterns
- voice-sherpa-native-osx-arm64/pom.xml — Maven native module pattern
- [qwentts.cpp](https://github.com/ServeurpersoCom/qwentts.cpp) — upstream C++ project
- [Qwen3-TTS-GGUF](https://huggingface.co/Serveurperso/Qwen3-TTS-GGUF) — pre-converted GGUF models
