# TTS Pipeline Framework + CosyVoice3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #217 — CosyVoice3 TTS pipeline framework
**Issue group:** #217, #214

**Goal:** Build a manifest-driven TTS pipeline framework and implement CosyVoice3 as the first pipeline configuration with 14 ONNX models across 4 stages.

**Architecture:** Sealed hierarchies for type safety (VoiceData, GeneratorOutput, TtsPipelineManifest). Stage SPIs (TtsTokenizer, TtsGenerator, TtsDecoder, TtsVoiceEncoder) are typed contracts — the framework manages lifecycle, stages own inference logic. CosyVoice3 pipeline: Qwen2 tokenizer → LLM (KV-cache transformer) → flow decoder (10-step Euler ODE) → HiFT vocoder. VoiceRegistry generalised from `int[]` to `VoiceData`.

**Tech Stack:** Java 22 (FFM/Panama), onnxruntime C API, JUnit 5, Mockito, AssertJ

## Global Constraints

- Java 22 compiler target (speech-sherpa module)
- All new classes in `io.casehub.blocks.speech.sherpa` package
- All tests are plain JUnit 5 + Mockito — no CDI container
- Integration tests guarded by `-Dspeech.integration=true`
- ONNX sessions via `OnnxRuntimeLibrary` FFM C API — no Java ONNX Runtime binding
- CosyVoice3 sample rate: 24000 Hz
- CosyVoice3 model repo: `ayousanz/cosy-voice3-onnx` (Apache 2.0)
- Existing Audio8 tests must pass after VoiceRegistry migration

---

## Batch 1: Pipeline framework types + VoiceRegistry generalisation

After this batch: all framework types compile, VoiceRegistry is generalised, all existing Audio8 tests pass.

### Task 1: Foundation types — VoiceData, GeneratorOutput, GeneratorConfig, Stage SPIs

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VoiceData.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/GeneratorOutput.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/GeneratorConfig.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsTokenizer.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsGenerator.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsDecoder.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsVoiceEncoder.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VoiceDataTest.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/GeneratorOutputTest.java`

**Interfaces:**
- Produces: `VoiceData` sealed interface (CodecVoiceData, EmbeddingVoiceData), `GeneratorOutput` sealed interface (SpeechTokenOutput, CodecFrameOutput), `GeneratorConfig` record, `TtsTokenizer`, `TtsGenerator`, `TtsDecoder`, `TtsVoiceEncoder` SPIs

- [ ] **Step 1: Write VoiceData sealed hierarchy test**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class VoiceDataTest {
    @Test void codecVoiceDataStoresTokens() {
        var vd = new VoiceData.CodecVoiceData(new int[]{1, 2, 3});
        assertThat(vd.codecTokens()).containsExactly(1, 2, 3);
        assertThat(vd).isInstanceOf(VoiceData.class);
    }

    @Test void embeddingVoiceDataStoresAllFields() {
        var vd = new VoiceData.EmbeddingVoiceData(
                new float[]{0.1f, 0.2f}, new int[]{10, 20},
                new float[]{0.5f, 0.6f}, "hello world");
        assertThat(vd.speakerEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(vd.speechTokens()).containsExactly(10, 20);
        assertThat(vd.promptMel()).containsExactly(0.5f, 0.6f);
        assertThat(vd.promptText()).isEqualTo("hello world");
    }

    @Test void exhaustiveSwitchCoversAllVariants() {
        VoiceData codec = new VoiceData.CodecVoiceData(new int[]{1});
        VoiceData embed = new VoiceData.EmbeddingVoiceData(
                new float[]{0.1f}, new int[]{10}, new float[]{0.5f}, "hi");
        assertThat(describe(codec)).isEqualTo("codec:1");
        assertThat(describe(embed)).isEqualTo("embedding:1");
    }

    private String describe(VoiceData vd) {
        return switch (vd) {
            case VoiceData.CodecVoiceData c -> "codec:" + c.codecTokens().length;
            case VoiceData.EmbeddingVoiceData e -> "embedding:" + e.speechTokens().length;
        };
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VoiceDataTest`
Expected: FAIL — `VoiceData` class not found

- [ ] **Step 3: Implement VoiceData**

```java
package io.casehub.blocks.speech.sherpa;

public sealed interface VoiceData permits VoiceData.CodecVoiceData, VoiceData.EmbeddingVoiceData {
    record CodecVoiceData(int[] codecTokens) implements VoiceData {}
    record EmbeddingVoiceData(float[] speakerEmbedding, int[] speechTokens,
                              float[] promptMel, String promptText) implements VoiceData {}
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VoiceDataTest`
Expected: PASS

- [ ] **Step 5: Write GeneratorOutput test**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class GeneratorOutputTest {
    @Test void speechTokenOutputWrapsTokens() {
        var out = new GeneratorOutput.SpeechTokenOutput(new int[]{100, 200});
        assertThat(out.speechTokens()).containsExactly(100, 200);
    }

    @Test void codecFrameOutputWrapsFrames() {
        var out = new GeneratorOutput.CodecFrameOutput(new int[][]{{1, 2}, {3, 4}});
        assertThat(out.codecFrames()).hasSize(2);
    }

    @Test void exhaustiveSwitchCoversAllVariants() {
        GeneratorOutput speech = new GeneratorOutput.SpeechTokenOutput(new int[]{1});
        GeneratorOutput codec = new GeneratorOutput.CodecFrameOutput(new int[][]{{1}});
        assertThat(length(speech)).isEqualTo(1);
        assertThat(length(codec)).isEqualTo(1);
    }

    private int length(GeneratorOutput out) {
        return switch (out) {
            case GeneratorOutput.SpeechTokenOutput s -> s.speechTokens().length;
            case GeneratorOutput.CodecFrameOutput c -> c.codecFrames().length;
        };
    }
}
```

- [ ] **Step 6: Implement GeneratorOutput, GeneratorConfig, and all 4 SPIs**

```java
// GeneratorOutput.java
package io.casehub.blocks.speech.sherpa;

public sealed interface GeneratorOutput
        permits GeneratorOutput.SpeechTokenOutput, GeneratorOutput.CodecFrameOutput {
    record SpeechTokenOutput(int[] speechTokens) implements GeneratorOutput {}
    record CodecFrameOutput(int[][] codecFrames) implements GeneratorOutput {}
}
```

```java
// GeneratorConfig.java
package io.casehub.blocks.speech.sherpa;

public record GeneratorConfig(float temperature, int topK, float topP,
                               int maxTokens, int minTokens) {
    public static GeneratorConfig defaults() {
        return new GeneratorConfig(1.0f, 25, 0.9f, 500, 10);
    }
}
```

```java
// TtsTokenizer.java
package io.casehub.blocks.speech.sherpa;

@FunctionalInterface
interface TtsTokenizer {
    int[] encode(String text);
}
```

```java
// TtsGenerator.java
package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

interface TtsGenerator {
    GeneratorOutput generate(int[] tokens, @Nullable VoiceData voiceData,
                             GeneratorConfig config);
}
```

```java
// TtsDecoder.java
package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

interface TtsDecoder {
    float[] decode(GeneratorOutput generatorOutput, @Nullable VoiceData voiceData);
}
```

```java
// TtsVoiceEncoder.java
package io.casehub.blocks.speech.sherpa;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface TtsVoiceEncoder {
    VoiceData encode(byte[] audioData, @Nullable String transcriptText);
}
```

- [ ] **Step 7: Run all tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VoiceDataTest,GeneratorOutputTest`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VoiceData.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/GeneratorOutput.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/GeneratorConfig.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsTokenizer.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsGenerator.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsDecoder.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsVoiceEncoder.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VoiceDataTest.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/GeneratorOutputTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#217): pipeline framework foundation types — VoiceData, GeneratorOutput, GeneratorConfig, stage SPIs  Refs #217"
```

### Task 2: VoiceRegistry generalisation + Audio8 caller migration

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VoiceRegistry.java`
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Audio8TextToSpeech.java`
- Modify: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VoiceRegistryTest.java`
- Modify: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/Audio8TextToSpeechTest.java`

**Interfaces:**
- Consumes: `VoiceData` (sealed hierarchy from Task 1), `TtsVoiceEncoder` (SPI from Task 1)
- Produces: Generalised `VoiceRegistry` with `VoiceData get(String)`, `register(Path, String)` overload

- [ ] **Step 1: Update VoiceRegistry test for VoiceData**

Read existing `VoiceRegistryTest.java` to understand current assertions. Update tests to use `VoiceData` return type — change `getVoiceCodes()` calls to `get()` returning `VoiceData`, verify with `instanceof CodecVoiceData`. Add tests for `register(Path, String)` with transcript. Add test that transcript-requiring encoder throws on null transcript.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VoiceRegistryTest`
Expected: FAIL — `get()` method doesn't exist, `VoiceData` return type mismatch

- [ ] **Step 3: Generalise VoiceRegistry**

Modify `VoiceRegistry.java`:
- Change `VoiceEncoder` inner interface to accept `TtsVoiceEncoder` (from Task 1) in constructor
- Change `Map<String, int[]> voices` → `Map<String, VoiceData> voices`
- Add `register(Path referenceAudio, String transcript)` overload
- Change `getVoiceCodes(String)` → `get(String)` returning `VoiceData`
- Keep `register(Path)` calling `encoder.encode(audioData, null)` for backward compat
- Add `@Nullable VoiceData defaultVoice` field + constructor param

```java
final class VoiceRegistry implements AutoCloseable {
    private final TtsVoiceEncoder encoder;
    private final Map<String, VoiceData> voices = new ConcurrentHashMap<>();
    private final @Nullable VoiceData defaultVoice;

    VoiceRegistry(TtsVoiceEncoder encoder) { this(encoder, null); }
    VoiceRegistry(TtsVoiceEncoder encoder, @Nullable VoiceData defaultVoice) {
        this.encoder = encoder;
        this.defaultVoice = defaultVoice;
    }

    String register(Path referenceAudio) { return register(referenceAudio, null); }
    String register(Path referenceAudio, String transcript) {
        byte[] audioData;
        try { audioData = Files.readAllBytes(referenceAudio); }
        catch (IOException e) { throw new SherpaException("Failed to read: " + referenceAudio, e); }
        VoiceData vd = encoder.encode(audioData, transcript);
        String id = UUID.randomUUID().toString();
        voices.put(id, vd);
        return id;
    }

    VoiceData get(String voiceId) {
        VoiceData vd = voices.get(voiceId);
        if (vd == null) throw new IllegalArgumentException("Unknown voice: " + voiceId);
        return vd;
    }

    @Nullable VoiceData defaultVoice() { return defaultVoice; }
    void release(String voiceId) { voices.remove(voiceId); }
    Set<String> registeredVoices() { return Set.copyOf(voices.keySet()); }
    void close() { voices.clear(); }
}
```

- [ ] **Step 4: Run VoiceRegistry test to verify it passes**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VoiceRegistryTest`
Expected: PASS

- [ ] **Step 5: Update Audio8TextToSpeech callers**

In `Audio8TextToSpeech.java`:
- The `VoiceRegistry.VoiceEncoder` lambda in `fromModelDir()` wraps its `int[]` result in `CodecVoiceData`:
  ```java
  TtsVoiceEncoder voiceEncoder = (audioData, transcript) -> {
      // ... existing codec encoder logic ...
      return new VoiceData.CodecVoiceData(codes);
  };
  ```
- In `synthesise()`, change:
  ```java
  // Before: int[] voiceCodes = voiceRegistry.getVoiceCodes(options.voice());
  // After:
  VoiceData vd = voiceRegistry.get(options.voice());
  int[] voiceCodes = ((VoiceData.CodecVoiceData) vd).codecTokens();
  ```
- Also handle defaultVoiceCodes via `voiceRegistry.defaultVoice()`:
  ```java
  VoiceData dv = voiceRegistry.defaultVoice();
  if (dv instanceof VoiceData.CodecVoiceData cd) {
      voiceCodes = cd.codecTokens();
  }
  ```

- [ ] **Step 6: Update Audio8TextToSpeechTest for new API**

Mechanically update test assertions from `getVoiceCodes()` to `get()` + `CodecVoiceData` cast. Behavioral assertions remain identical.

- [ ] **Step 7: Run all Audio8 tests to verify regression-free**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=Audio8TextToSpeechTest,VoiceRegistryTest,Audio8TokenizerTest,Audio8ConfigTest`
Expected: ALL PASS

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): generalise VoiceRegistry to VoiceData + Audio8 caller migration  Refs #217"
```

### Task 3: TtsPipelineManifest + TtsPipeline + OnnxRuntimeLibrary INT32

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsPipelineManifest.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/PipelineHeader.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/ExecutionProviderConfig.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/CosyVoice3Manifest.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsPipeline.java`
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/OnnxRuntimeLibrary.java` (add INT32 constant)
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/TtsPipelineManifestTest.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/TtsPipelineTest.java`

**Interfaces:**
- Consumes: `VoiceData`, `GeneratorOutput`, `GeneratorConfig`, `TtsTokenizer`, `TtsGenerator`, `TtsDecoder`, `TtsVoiceEncoder`, `VoiceRegistry` (from Tasks 1-2)
- Produces: `TtsPipeline` implementing `TextToSpeechService`, `TtsPipelineManifest` sealed hierarchy, `CosyVoice3Manifest` record, `OnnxRuntimeLibrary.INT32` constant

- [ ] **Step 1: Write manifest JSON parsing test**

Test that `CosyVoice3Manifest` can be parsed from a JSON string via Gson (already a dependency). Test `PipelineHeader` fields, `CosyVoice3Manifest` hyperparameters, and `defaultPrompts` map.

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=TtsPipelineManifestTest`
Expected: FAIL

- [ ] **Step 3: Implement manifest types**

`TtsPipelineManifest` sealed interface with `header()` method. `PipelineHeader`, `ExecutionProviderConfig`, `CosyVoice3Manifest` records. Static `parse(Path manifestFile)` factory using Gson to read JSON and dispatch on a `"type"` field to the correct manifest record.

- [ ] **Step 4: Run manifest test to verify it passes**

- [ ] **Step 5: Write TtsPipeline test with mocked stages**

Test `synthesise()` flow: tokenizer → generator → decoder → WAV encoding. Test voice resolution (explicit voice, default voice, no voice). Test lifecycle (`close()` releases sessions). Test `sessionCount()` and `modelName()`. Use Mockito for stage mocks.

- [ ] **Step 6: Run test to verify it fails**

- [ ] **Step 7: Implement TtsPipeline**

```java
public final class TtsPipeline implements TextToSpeechService, AutoCloseable {
    private final TtsPipelineManifest manifest;
    private final TtsTokenizer tokenizer;
    private final TtsGenerator generator;
    private final TtsDecoder decoder;
    private final VoiceRegistry voiceRegistry;
    private final int sampleRate;
    private final AutoCloseable[] ownedResources;

    TtsPipeline(TtsPipelineManifest manifest, TtsTokenizer tokenizer,
                TtsGenerator generator, TtsDecoder decoder,
                VoiceRegistry voiceRegistry, int sampleRate,
                AutoCloseable... ownedResources) { ... }

    public static TtsPipeline fromModelDir(Path modelDir) {
        // Parse manifest, detect type, instantiate stages, create sessions
        // Implemented per-model in Batch 4 — for now, throws UnsupportedOperationException
        // if model type is not yet supported
        ...
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        int[] tokens = tokenizer.encode(text);
        VoiceData voice = resolveVoice(options);
        GeneratorConfig config = generatorConfig();
        GeneratorOutput output = generator.generate(tokens, voice, config);
        float[] samples = decoder.decode(output, voice);
        byte[] wav = WavWriter.encode(samples, sampleRate, 1);
        return new SynthesisResult(wav, "wav", List.of());
    }

    public String registerVoice(Path referenceAudio) {
        return voiceRegistry.register(referenceAudio);
    }
    public String registerVoice(Path referenceAudio, String transcript) {
        return voiceRegistry.register(referenceAudio, transcript);
    }
    public void releaseVoice(String voiceId) { voiceRegistry.release(voiceId); }
    public int sessionCount() { return ownedResources.length; }
    public String modelName() { return manifest.header().name(); }

    private VoiceData resolveVoice(SynthesisOptions options) {
        if (options.voice() != null && !options.voice().isEmpty()) {
            return voiceRegistry.get(options.voice());
        }
        return voiceRegistry.defaultVoice();
    }

    private GeneratorConfig generatorConfig() {
        return GeneratorConfig.defaults(); // manifest override in future
    }

    @Override
    public void close() {
        for (AutoCloseable r : ownedResources) {
            try { r.close(); } catch (Exception ignored) {}
        }
        voiceRegistry.close();
    }
}
```

- [ ] **Step 8: Add INT32 constant to OnnxRuntimeLibrary**

Add at line 421 (after BOOL):
```java
static final int INT32 = 6;
```

- [ ] **Step 9: Run all tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=TtsPipelineManifestTest,TtsPipelineTest,VoiceRegistryTest,Audio8TextToSpeechTest`
Expected: ALL PASS

- [ ] **Step 10: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): TtsPipelineManifest + TtsPipeline framework + OnnxRuntimeLibrary INT32  Refs #217"
```

---

## Batch 2: DSP primitives

After this batch: FFT, mel spectrogram extraction (3 configs), STFT/ISTFT, and audio resampling are implemented and tested against reference outputs.

### Task 4: FFT + MelSpectrogram

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Fft.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/MelConfig.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/MelSpectrogram.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/FftTest.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/MelSpectrogramTest.java`

**Interfaces:**
- Produces: `Fft.forward(float[], float[])` (in-place Cooley-Tukey radix-2), `MelConfig` record, `MelSpectrogram.compute()`, `.logMel()`, `.whisperNormalize()`, `.meanNormalize()`

- [ ] **Step 1: Write FFT test**

Test forward FFT of known signals: DC signal (all 1s → bin 0 only), pure sinusoid (single bin), impulse (flat spectrum). Verify inverse FFT round-trips within tolerance (1e-6). Test zero-padding to next power of 2. Compare against numpy.fft reference values for a specific input.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement Cooley-Tukey radix-2 FFT**

```java
final class Fft {
    static void forward(float[] real, float[] imag) { ... }
    static void inverse(float[] real, float[] imag) { ... }
    static int nextPowerOfTwo(int n) { ... }
}
```

In-place butterfly operations. Input arrays must be power-of-two length. `nextPowerOfTwo()` for zero-padding.

- [ ] **Step 4: Run FFT test to verify it passes**

- [ ] **Step 5: Write MelSpectrogram test**

Test all 3 mel configs from the spec:
1. Campplus: 16kHz, nFft=400, hop=160, 80 mels, fmin=20, fmax=7600
2. Whisper: 16kHz, nFft=400, hop=160, 128 mels, fmin=0, fmax=8000
3. Flow: 24kHz, nFft=1024, hop=256, 80 mels, fmin=0, fmax=12000

Test mel filter bank shape (nMels × nFft/2+1). Test logMel, whisperNormalize, meanNormalize. Compare against librosa reference for a known signal (440Hz sine at 16kHz, 1 second).

- [ ] **Step 6: Run test to verify it fails**

- [ ] **Step 7: Implement MelSpectrogram**

```java
record MelConfig(int sampleRate, int nFft, int hopLength, int nMels,
                 float fMin, float fMax) {}

final class MelSpectrogram {
    static float[][] compute(float[] audio, MelConfig config) { ... }
    static float[][] logMel(float[][] mel) { ... }
    static float[][] whisperNormalize(float[][] logMel) { ... }
    static void meanNormalize(float[][] mel) { ... }
    static float[][] melFilterBank(int nMels, int nFft, int sampleRate,
                                    float fMin, float fMax) { ... }
}
```

Implementation: frame audio with hop_length, apply Hann window, FFT each frame, compute power spectrum, apply mel filter bank (triangular filters on mel scale), optional log/normalize transforms.

- [ ] **Step 8: Run all DSP tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=FftTest,MelSpectrogramTest`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): FFT + MelSpectrogram — DSP primitives for CosyVoice3  Refs #217"
```

### Task 5: STFT/ISTFT + AudioResampler

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/StftUtils.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/AudioResampler.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/StftUtilsTest.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/AudioResamplerTest.java`

**Interfaces:**
- Consumes: `Fft` (from Task 4)
- Produces: `StftUtils.stft()`, `StftUtils.istft()`, `AudioResampler.resample()`

- [ ] **Step 1: Write STFT/ISTFT test**

Test round-trip: `istft(stft(signal)) ≈ signal` within tolerance. Test with HiFT-specific params (nFft=16, hopLength=4). Verify output shapes: STFT of N samples → `[nFft/2+1, frames]` where `frames = (N - nFft) / hopLength + 1`. Test magnitude/phase decomposition and reconstruction.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement StftUtils**

```java
final class StftUtils {
    static float[][][] stft(float[] signal, int nFft, int hopLength) {
        // Returns [2][nBins][frames] — [0] = real, [1] = imag
        // Frame signal, apply Hann window, FFT each frame
    }
    static float[] istft(float[][] magnitude, float[][] phase,
                         int nFft, int hopLength) {
        // Reconstruct complex spectrum, inverse FFT, overlap-add with Hann window
    }
}
```

- [ ] **Step 4: Run STFT test to verify it passes**

- [ ] **Step 5: Write AudioResampler test**

Test 48kHz→16kHz (÷3), 48kHz→24kHz (÷2), 44.1kHz→16kHz (non-integer ratio). Verify output length matches expected `input.length * targetRate / sourceRate`. Test that resampled 440Hz sine remains a 440Hz sine (verify peak frequency in spectrum). Compare against scipy.signal.resample_poly reference.

- [ ] **Step 6: Run test to verify it fails**

- [ ] **Step 7: Implement AudioResampler**

```java
final class AudioResampler {
    static float[] resample(float[] audio, int sourceSampleRate, int targetSampleRate) {
        // Windowed sinc interpolation with anti-aliasing lowpass
        // Integer-ratio cases use polyphase decimation
    }
}
```

- [ ] **Step 8: Run all tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=StftUtilsTest,AudioResamplerTest,FftTest,MelSpectrogramTest`
Expected: ALL PASS

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): STFT/ISTFT + AudioResampler — DSP primitives for CosyVoice3 HiFT  Refs #217"
```

---

## Batch 3: CosyVoice3 tokenizer + voice encoder

After this batch: Qwen2 BPE tokenization and voice encoding (speaker embedding + speech tokens + mel) are working and tested.

### Task 6: CosyVoice3Tokenizer — Qwen2 BPE

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/CosyVoice3Tokenizer.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/CosyVoice3TokenizerTest.java`

**Interfaces:**
- Produces: `CosyVoice3Tokenizer` implementing `TtsTokenizer`, `load(Path)` factory, `decode(int[])` for test verification

- [ ] **Step 1: Write tokenizer test**

Test BPE encoding of English text, CJK characters, mixed text. Verify encode→decode round-trip. Compare against Python `transformers.AutoTokenizer.from_pretrained("Qwen/Qwen2-0.5B")` reference outputs for specific strings: `"Hello world"`, `"CosyVoice3 generates speech"`, `"你好世界"`, `"mixed 中英文 text"`. Test that no special tokens are added (CosyVoice3 spec: `add_special_tokens=False`).

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement CosyVoice3Tokenizer**

Parse `vocab.json` (token → ID mapping) and `merges.txt` (BPE merge rules). Implement byte-level BPE: UTF-8 encode → byte-to-unicode mapping → pre-tokenization regex split → greedy BPE merge → token IDs. Follow GPT-2 BPE algorithm with Qwen2's vocabulary.

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): CosyVoice3Tokenizer — Qwen2 byte-level BPE tokenizer  Refs #217"
```

### Task 7: CosyVoice3VoiceEncoder

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/CosyVoice3VoiceEncoder.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/CosyVoice3VoiceEncoderTest.java`

**Interfaces:**
- Consumes: `TtsVoiceEncoder` (SPI from Task 1), `MelSpectrogram` + `MelConfig` (from Task 4), `AudioResampler` (from Task 5), `VoiceData.EmbeddingVoiceData` (from Task 1)
- Produces: `CosyVoice3VoiceEncoder` implementing `TtsVoiceEncoder`

- [ ] **Step 1: Write voice encoder test with mocked ONNX sessions**

Test mel extraction configs (campplus: 80-band at 16kHz, whisper: 128-band at 16kHz, flow: 80-band at 24kHz). Test input shape to campplus mock (`[1, frames, 80]`). Test input shape to speech_tokenizer mock (`[1, 128, frames]`). Test output is `EmbeddingVoiceData` with all 4 fields populated. Test null transcript throws `IllegalArgumentException`.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement CosyVoice3VoiceEncoder**

```java
final class CosyVoice3VoiceEncoder implements TtsVoiceEncoder {
    private final OnnxRuntimeLibrary.Session campplus;
    private final OnnxRuntimeLibrary.Session speechTokenizer;
    private final CosyVoice3Manifest manifest;

    // Mel configs for each stage
    private static final MelConfig CAMPPLUS_MEL = new MelConfig(16000, 400, 160, 80, 20f, 7600f);
    private static final MelConfig WHISPER_MEL = new MelConfig(16000, 400, 160, 128, 0f, 8000f);
    private static final MelConfig FLOW_MEL = new MelConfig(24000, 1024, 256, 80, 0f, 12000f);

    @Override
    public VoiceData encode(byte[] audioData, @Nullable String transcriptText) {
        if (transcriptText == null || transcriptText.isBlank()) {
            throw new IllegalArgumentException(
                "CosyVoice3 voice cloning requires a transcript of the reference audio");
        }
        WavData wav = WavReader.parse(audioData);
        float[] audio16k = AudioResampler.resample(wav.samples(), wav.sampleRate(), 16000);
        float[] audio24k = AudioResampler.resample(wav.samples(), wav.sampleRate(), 24000);

        float[] speakerEmbedding = extractSpeakerEmbedding(audio16k);
        int[] speechTokens = extractSpeechTokens(audio16k);
        float[] promptMel = extractPromptMel(audio24k);

        return new VoiceData.EmbeddingVoiceData(speakerEmbedding, speechTokens,
                                                 promptMel, transcriptText);
    }
    // ... private methods for each extraction stage using ORT sessions
}
```

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): CosyVoice3VoiceEncoder — campplus + speech tokenizer + mel extraction  Refs #217"
```

---

## Batch 4: CosyVoice3 generator + decoder + end-to-end wiring

After this batch: full CosyVoice3 pipeline works end-to-end via `TtsPipeline.fromModelDir()`.

### Task 8: CosyVoice3Generator — LLM pipeline

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/CosyVoice3Generator.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/CosyVoice3GeneratorTest.java`

**Interfaces:**
- Consumes: `TtsGenerator` (SPI from Task 1), `VoiceData.EmbeddingVoiceData` (Task 1), `GeneratorConfig` (Task 1), `GeneratorOutput.SpeechTokenOutput` (Task 1), `CosyVoice3Manifest` (Task 3), `CosyVoice3Tokenizer` (Task 6)
- Produces: `CosyVoice3Generator` implementing `TtsGenerator`

- [ ] **Step 1: Write generator test with mocked ONNX sessions**

Test input sequence construction: verify that the generator builds `[SOS_emb, text_emb(prompt+tts), TASK_ID_emb, speech_emb]` in correct order. Test KV-cache protocol: initial pass receives full sequence, decode steps receive single-token embeddings. Test EOS detection: mock `llm_decoder` to return EOS token (6562) after N steps, verify loop terminates. Test max token guard. Test NaN logits detection. Test sampling: mock logits with known distribution, verify top-k filtering.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement CosyVoice3Generator**

The generator manages 5 ONNX sessions. Key implementation details:
- Input sequence: tokenize `promptText + text` via held `CosyVoice3Tokenizer`, embed special tokens (SOS=6561, TASK_ID=6563) via `llm_speech_embedding`, embed prompt speech tokens
- KV-cache: `[48, 1, 2, seq_len, 64]` allocated per-call in confined arena, grows by 1 each decode step via concatenation along sequence axis
- Attention mask: `float[1][total_len]`, all ones, grows each step
- Sampling: top-k=25 from `GeneratorConfig`, log-softmax → top-k selection → softmax → categorical
- Termination: EOS (6562) after `max(10, text_tokens * 2)` min length

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): CosyVoice3Generator — LLM autoregressive pipeline with KV-cache  Refs #217"
```

### Task 9: CosyVoice3Decoder — flow + HiFT

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/CosyVoice3Decoder.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/CosyVoice3DecoderTest.java`

**Interfaces:**
- Consumes: `TtsDecoder` (SPI from Task 1), `GeneratorOutput.SpeechTokenOutput` (Task 1), `VoiceData.EmbeddingVoiceData` (Task 1), `CosyVoice3Manifest` (Task 3), `StftUtils` (Task 5)
- Produces: `CosyVoice3Decoder` implementing `TtsDecoder`

- [ ] **Step 1: Write decoder test with mocked ONNX sessions**

Test flow pipeline: mock `flow_token_embedding` → `flow_pre_lookahead` → `flow_speaker_projection` → `flow_estimator`. Verify 10 Euler steps. Verify speaker embedding L2-normalization. Verify conditioning tensor construction (prompt mel placement + zero-padding). Test HiFT pipeline: mock `hift_f0` → `hift_source` → `hift_decoder`. Verify STFT/ISTFT wiring. Verify audio clamping to [-0.99, 0.99]. Test deterministic output with seeded Random.

- [ ] **Step 2: Run test to verify it fails**

- [ ] **Step 3: Implement CosyVoice3Decoder**

The decoder manages 7 ONNX sessions. Key implementation details:
- Flow: concatenate prompt + generated tokens → embed → pre-lookahead (repeat×2) → transpose to `[1, dim, mel_len]` → 10-step Euler ODE with Gaussian noise init → slice off prompt mel frames
- HiFT: f0 prediction → source generation → STFT (nFft=16, hop=4) → decoder → ISTFT → clamp

- [ ] **Step 4: Run test to verify it passes**

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): CosyVoice3Decoder — flow diffusion + HiFT vocoder  Refs #217"
```

### Task 10: Provisioner + manifest + end-to-end wiring

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java`
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/TtsPipeline.java` (add CosyVoice3 branch to `fromModelDir`)
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/CosyVoice3IntegrationTest.java`

**Interfaces:**
- Consumes: All previous tasks
- Produces: `Provisioner.ensureCosyVoice3Model()`, `TtsPipeline.fromModelDir()` CosyVoice3 wiring

- [ ] **Step 1: Add provisioner for CosyVoice3**

Add `ensureCosyVoice3Model()` to `Provisioner.java` following the `ensureAudio8Model()` pattern. HuggingFace repo: `ayousanz/cosy-voice3-onnx`. 14 ONNX files + tokenizer dir + prompts dir.

- [ ] **Step 2: Wire CosyVoice3 into TtsPipeline.fromModelDir()**

In `TtsPipeline.fromModelDir()`, when manifest type is `CosyVoice3Manifest`:
1. Create all 14 ONNX sessions via `OnnxRuntimeLibrary`
2. Instantiate `CosyVoice3Tokenizer.load(tokenizerDir)`
3. Instantiate `CosyVoice3VoiceEncoder(campplus, speechTokenizer, manifest)`
4. Instantiate `CosyVoice3Generator(textEmbed, speechEmbed, llmInit, llmDecode, llmDecoder, manifest, tokenizer)`
5. Instantiate `CosyVoice3Decoder(flowTokenEmbed, flowPreLookahead, flowSpeakerProj, flowEstimator, hiftF0, hiftSource, hiftDecoder, manifest)`
6. Create `VoiceRegistry` with encoder, register default voice from manifest's `defaultPrompts`
7. Return `TtsPipeline` with all stages and sessions as owned resources

- [ ] **Step 3: Write integration test (guarded)**

```java
@EnabledIfSystemProperty(named = "speech.integration", matches = "true")
class CosyVoice3IntegrationTest {
    @Test void endToEndSynthesis() {
        Path modelDir = Provisioner.ensureCosyVoice3Model();
        try (var pipeline = TtsPipeline.fromModelDir(modelDir)) {
            SynthesisResult result = pipeline.synthesise("Hello world",
                    SynthesisOptions.defaults());
            assertThat(result.audioData()).isNotEmpty();
            assertThat(result.audioFormat()).isEqualTo("wav");
        }
    }
}
```

- [ ] **Step 4: ORT version compatibility check (blocking)**

This is the spec's blocking verification (§ORT Version Compatibility). The integration test implicitly validates FP16 auto-cast — if onnxruntime's C API doesn't auto-cast FP32 inputs to FP16 for FP16 models, the integration test fails with incorrect output or ORT errors. If failures occur:
1. Check if the issue is FP16-specific (compare FP32 model files if available in the CosyVoice3 repo)
2. If FP16 auto-cast doesn't work via C API, pin to onnxruntime 1.18.0 native library
3. If the issue is broader, investigate ORT vtable compatibility

- [ ] **Step 5: Run unit tests (all batches)**

Run: `mvn --batch-mode test -pl speech-sherpa`
Expected: ALL PASS (integration test skipped without `-Dspeech.integration=true`)

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -am "feat(#217): Provisioner + end-to-end CosyVoice3 wiring in TtsPipeline.fromModelDir  Refs #217"
```

---

## References

- [2026-08-30-tts-pipeline-framework-cosyvoice3-design.md](/Users/mdproctor/claude/casehub/slots/161/wsp-casehub-blocks/specs/main-217/2026-08-30-tts-pipeline-framework-cosyvoice3-design.md) — design spec this plan implements
- [decisions.md](/Users/mdproctor/claude/casehub/slots/161/wsp-casehub-blocks/specs/main-217/decisions.md) — D1-D13 design decisions
- `VoiceRegistry.java:11-55` — existing voice registry to generalise
- `Audio8TextToSpeech.java:16-371` — existing TTS implementation (Audio8 caller migration)
- `OnnxRuntimeLibrary.java:17-422` — FFM C API bindings (INT32 constant addition)
- `Provisioner.java:486-507` — existing `ensureAudio8Model()` pattern
- `TextToSpeechService.java:3-5` — target SPI for TtsPipeline
- `SynthesisResult.java:6-12` — return type contract
- `SynthesisOptions.java:3-7` — input options (voice, language)
- [ayousanz/cosy-voice3-onnx](https://huggingface.co/ayousanz/cosy-voice3-onnx) — reference inference
- GitHub #217 — CosyVoice TTS integration
