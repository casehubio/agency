# Audio8 TTS + Composable Lip-Sync Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #213 — Audio8 TTS integration — DualAR ONNX voice synthesis with voice cloning
**Issue group:** #213

**Goal:** Integrate Audio8 TTS as a `TextToSpeechService` and introduce a composable lip-sync pipeline reusable across all TTS engines.

**Architecture:** Three layers — (1) `PhonemeAligner` SPI + `LipSyncEnricher` decorator in speech-api for universal lip-sync enrichment, (2) `EspeakPhonemeAligner` in speech-sherpa as the initial aligner, (3) `Audio8TextToSpeech` in speech-sherpa with DualAR autoregressive inference ported from the [Audio8_TTS Python reference](https://github.com/Audio8-AI/Audio8_TTS/tree/master/onnx_runtime/arktts_runtime).

**Tech Stack:** Java 22 (FFM/Panama), OnnxRuntimeLibrary (existing), EspeakLibrary (existing), JUnit 5, AssertJ, Mockito

## Global Constraints

- Java 22+ (stable FFM/Panama API)
- No Quarkus runtime — plain JUnit 5 tests with Mockito
- All new classes in `io.casehub.blocks.speech` (speech-api) or `io.casehub.blocks.speech.sherpa` (speech-sherpa)
- Follow existing test patterns: AssertJ assertions, `@Test` methods, no CDI container
- Provisioner downloads go to `~/.casehub/models/` via existing `provision()` infrastructure
- `OnnxRuntimeLibrary` is the ONNX inference layer — no direct onnxruntime Java bindings
- SPI contracts use `@FunctionalInterface` where single-method

---

## Batch 1: Composable Lip-Sync Pipeline

Independently useful — after this batch, Kokoro and SherpaOnnx gain lip-sync. No Audio8 dependency.

### Task 1: PhonemeAligner SPI + LipSyncEnricher decorator

**Files:**
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/PhonemeAligner.java`
- Create: `speech-api/src/main/java/io/casehub/blocks/speech/LipSyncEnricher.java`
- Test: `speech-api/src/test/java/io/casehub/blocks/speech/LipSyncEnricherTest.java`

**Interfaces:**
- Consumes: `TextToSpeechService`, `SynthesisResult`, `SynthesisOptions`, `PhonemeTiming` (all existing)
- Produces: `PhonemeAligner` SPI (`List<PhonemeTiming> align(String text, byte[] audioData, int sampleRate)`), `LipSyncEnricher` (decorator implementing `TextToSpeechService`)

- [ ] **Step 1: Write the PhonemeAligner SPI**

```java
package io.casehub.blocks.speech;

import java.util.List;

@FunctionalInterface
public interface PhonemeAligner {
    List<PhonemeTiming> align(String text, byte[] audioData, int sampleRate);
}
```

- [ ] **Step 2: Write failing tests for LipSyncEnricher**

```java
package io.casehub.blocks.speech;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class LipSyncEnricherTest {

    private final PhonemeAligner stubAligner = (text, audio, rate) ->
            List.of(new PhonemeTiming("ah", 0, 100), new PhonemeTiming("l", 100, 200));

    @Test
    void enrichesWhenDelegateReturnsEmptyPhonemes() {
        TextToSpeechService delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1, 2, 3}, "wav", List.of());

        var enriched = LipSyncEnricher.wrap(delegate, stubAligner);
        var result = enriched.synthesise("hello", SynthesisOptions.defaults());

        assertThat(result.audioData()).containsExactly(1, 2, 3);
        assertThat(result.audioFormat()).isEqualTo("wav");
        assertThat(result.phonemes()).hasSize(2);
        assertThat(result.phonemes().getFirst().phoneme()).isEqualTo("ah");
    }

    @Test
    void passesThroughWhenDelegateReturnsNonEmptyPhonemes() {
        var nativePhonemes = List.of(new PhonemeTiming("n", 0, 50));
        TextToSpeechService delegate = (text, opts) ->
                new SynthesisResult(new byte[]{1}, "wav", nativePhonemes);

        var enriched = LipSyncEnricher.wrap(delegate, stubAligner);
        var result = enriched.synthesise("hi", SynthesisOptions.defaults());

        assertThat(result.phonemes()).hasSize(1);
        assertThat(result.phonemes().getFirst().phoneme()).isEqualTo("n");
    }

    @Test
    void passesThroughTextAndOptionsToDelegate() {
        final String[] capturedText = {null};
        final SynthesisOptions[] capturedOpts = {null};
        TextToSpeechService delegate = (text, opts) -> {
            capturedText[0] = text;
            capturedOpts[0] = opts;
            return new SynthesisResult(new byte[]{1}, "wav", List.of());
        };

        var opts = new SynthesisOptions("voice1", "en", "wav", true);
        LipSyncEnricher.wrap(delegate, stubAligner).synthesise("test", opts);

        assertThat(capturedText[0]).isEqualTo("test");
        assertThat(capturedOpts[0]).isSameAs(opts);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-api -Dtest=LipSyncEnricherTest`
Expected: FAIL — `LipSyncEnricher` does not exist

- [ ] **Step 4: Implement LipSyncEnricher**

```java
package io.casehub.blocks.speech;

import java.util.List;
import java.util.Objects;

public final class LipSyncEnricher implements TextToSpeechService {

    private final TextToSpeechService delegate;
    private final PhonemeAligner aligner;
    private final int sampleRate;

    private LipSyncEnricher(TextToSpeechService delegate, PhonemeAligner aligner, int sampleRate) {
        this.delegate = Objects.requireNonNull(delegate);
        this.aligner = Objects.requireNonNull(aligner);
        this.sampleRate = sampleRate;
    }

    public static TextToSpeechService wrap(TextToSpeechService delegate, PhonemeAligner aligner) {
        return wrap(delegate, aligner, 22050);
    }

    public static TextToSpeechService wrap(TextToSpeechService delegate, PhonemeAligner aligner, int sampleRate) {
        return new LipSyncEnricher(delegate, aligner, sampleRate);
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        SynthesisResult result = delegate.synthesise(text, options);
        if (!result.phonemes().isEmpty()) {
            return result;
        }
        List<PhonemeTiming> timing = aligner.align(text, result.audioData(), sampleRate);
        return new SynthesisResult(result.audioData(), result.audioFormat(), timing);
    }
}
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl speech-api -Dtest=LipSyncEnricherTest`
Expected: PASS (3 tests)

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-api/src/main/java/io/casehub/blocks/speech/PhonemeAligner.java speech-api/src/main/java/io/casehub/blocks/speech/LipSyncEnricher.java speech-api/src/test/java/io/casehub/blocks/speech/LipSyncEnricherTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#213): PhonemeAligner SPI + LipSyncEnricher decorator Refs #213"
```

### Task 2: EspeakPhonemeAligner

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/EspeakPhonemeAligner.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/EspeakPhonemeAlignerTest.java`

**Interfaces:**
- Consumes: `PhonemeAligner` (from Task 1), `EspeakLibrary.textToPhonemes(String, String)` (existing), `WavReader` (existing — decodes WAV to get duration)
- Produces: `EspeakPhonemeAligner implements PhonemeAligner`

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.PhonemeAligner;
import io.casehub.blocks.speech.PhonemeTiming;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class EspeakPhonemeAlignerTest {

    @Test
    void alignsPhonemeTimingsProportionallyToAudioDuration() {
        // Stub EspeakLibrary: "hello" → "hɛloʊ" (4 phonemes)
        PhonemeAligner aligner = new EspeakPhonemeAligner(
                (text, voice) -> "hɛloʊ");

        // 1 second of audio at 22050 Hz = 22050 samples = 44100 bytes (16-bit PCM)
        byte[] wavData = createWavBytes(22050, 1.0f);
        List<PhonemeTiming> result = aligner.align("hello", wavData, 22050);

        assertThat(result).hasSize(4);
        // Proportional: each phoneme ≈ 250ms in 1s of audio
        assertThat(result.getFirst().startMs()).isZero();
        assertThat(result.getLast().endMs()).isEqualTo(1000);
        // All timings are contiguous (no gaps)
        for (int i = 1; i < result.size(); i++) {
            assertThat(result.get(i).startMs()).isEqualTo(result.get(i - 1).endMs());
        }
    }

    @Test
    void returnsEmptyListForEmptyText() {
        PhonemeAligner aligner = new EspeakPhonemeAligner((text, voice) -> "");
        List<PhonemeTiming> result = aligner.align("", new byte[0], 22050);
        assertThat(result).isEmpty();
    }

    @Test
    void handlesNullPhonemeOutputGracefully() {
        PhonemeAligner aligner = new EspeakPhonemeAligner((text, voice) -> null);
        List<PhonemeTiming> result = aligner.align("hi", createWavBytes(22050, 0.5f), 22050);
        assertThat(result).isEmpty();
    }

    private static byte[] createWavBytes(int sampleRate, float durationSecs) {
        int numSamples = (int) (sampleRate * durationSecs);
        byte[] pcm = new byte[numSamples * 2]; // 16-bit mono
        return WavWriter.encode(new float[numSamples], sampleRate);
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=EspeakPhonemeAlignerTest`
Expected: FAIL — `EspeakPhonemeAligner` does not exist

- [ ] **Step 3: Implement EspeakPhonemeAligner**

```java
package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.PhonemeAligner;
import io.casehub.blocks.speech.PhonemeTiming;
import java.util.ArrayList;
import java.util.List;

public final class EspeakPhonemeAligner implements PhonemeAligner {

    @FunctionalInterface
    interface Phonemizer {
        String phonemize(String text, String voice);
    }

    private final Phonemizer phonemizer;
    private final String voice;

    public EspeakPhonemeAligner(Phonemizer phonemizer) {
        this(phonemizer, "en");
    }

    public EspeakPhonemeAligner(Phonemizer phonemizer, String voice) {
        this.phonemizer = phonemizer;
        this.voice = voice;
    }

    public static EspeakPhonemeAligner withEspeak(EspeakLibrary espeak) {
        return new EspeakPhonemeAligner(espeak::textToPhonemes);
    }

    @Override
    public List<PhonemeTiming> align(String text, byte[] audioData, int sampleRate) {
        if (text == null || text.isBlank()) return List.of();

        String ipa = phonemizer.phonemize(text, voice);
        if (ipa == null || ipa.isBlank()) return List.of();

        List<String> phonemes = splitIpaPhonemes(ipa);
        if (phonemes.isEmpty()) return List.of();

        long audioDurationMs = estimateAudioDurationMs(audioData, sampleRate);
        if (audioDurationMs <= 0) return List.of();

        long perPhonemeMs = audioDurationMs / phonemes.size();
        long remainder = audioDurationMs % phonemes.size();

        var result = new ArrayList<PhonemeTiming>(phonemes.size());
        long cursor = 0;
        for (int i = 0; i < phonemes.size(); i++) {
            long duration = perPhonemeMs + (i < remainder ? 1 : 0);
            result.add(new PhonemeTiming(phonemes.get(i), cursor, cursor + duration));
            cursor += duration;
        }
        return List.copyOf(result);
    }

    private static List<String> splitIpaPhonemes(String ipa) {
        var phonemes = new ArrayList<String>();
        for (int i = 0; i < ipa.length(); ) {
            int cp = ipa.codePointAt(i);
            if (!Character.isWhitespace(cp)) {
                phonemes.add(new String(Character.toChars(cp)));
            }
            i += Character.charCount(cp);
        }
        return phonemes;
    }

    private static long estimateAudioDurationMs(byte[] wavData, int sampleRate) {
        try {
            WavData wav = WavReader.read(wavData);
            return (long) (wav.samples().length * 1000.0 / wav.sampleRate());
        } catch (Exception e) {
            // Fallback: assume raw 16-bit PCM mono
            int numSamples = wavData.length / 2;
            return (long) (numSamples * 1000.0 / sampleRate);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=EspeakPhonemeAlignerTest`
Expected: PASS (3 tests)

- [ ] **Step 5: Run full speech-api + speech-sherpa tests**

Run: `mvn --batch-mode test -pl speech-api,speech-sherpa`
Expected: All existing tests pass + new tests pass

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/EspeakPhonemeAligner.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/EspeakPhonemeAlignerTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#213): EspeakPhonemeAligner — espeak-ng based phoneme timing Refs #213"
```

---

## Batch 2: Audio8 Foundation

Tokenizer, config, and provisioner. After this batch, the model can be downloaded and text tokenized — but not yet synthesised.

### Task 3: Audio8Tokenizer

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Audio8Tokenizer.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/Audio8TokenizerTest.java`

**Interfaces:**
- Consumes: nothing (self-contained — parses JSON tokenizer file)
- Produces: `Audio8Tokenizer` with `int[] encode(String text)` and `String decode(int[] ids)`

**Implementation approach:** Before writing this task, clone and read the Audio8_TTS Python reference: `onnx_runtime/arktts_runtime/prompt.py`. This file shows how text is tokenized for the DualAR model. The tokenizer format is HuggingFace `tokenizer.json` — a JSON file with vocabulary mappings. Port the encoding logic to Java.

- [ ] **Step 1: Clone Audio8_TTS and read the tokenizer logic**

```bash
git clone --depth 1 https://github.com/Audio8-AI/Audio8_TTS.git /tmp/audio8-tts-ref
```

Read: `/tmp/audio8-tts-ref/onnx_runtime/arktts_runtime/prompt.py`
Extract: how text is encoded to token IDs, what special tokens exist, how padding/BOS/EOS work.

- [ ] **Step 2: Download and inspect a tokenizer.json**

```bash
python3 -c 'from huggingface_hub import hf_hub_download; print(hf_hub_download("Audio8/audio8-TTS-0.1B-ONNX-INT8", "tokenizer/tokenizer.json"))'
```

Read the downloaded file to understand the vocabulary structure.

- [ ] **Step 3: Write failing tests**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class Audio8TokenizerTest {

    @Test
    void encodesTextToTokenIds(@TempDir Path tempDir) throws Exception {
        // Write a minimal tokenizer.json with known vocabulary
        Path tokenizerJson = tempDir.resolve("tokenizer.json");
        Files.writeString(tokenizerJson, """
                {"model":{"vocab":{"h":0,"e":1,"l":2,"o":3},"type":"BPE"}}
                """);

        var tokenizer = Audio8Tokenizer.load(tokenizerJson);
        int[] ids = tokenizer.encode("hello");

        assertThat(ids).isNotEmpty();
    }

    @Test
    void roundTripsEncodeDecode(@TempDir Path tempDir) throws Exception {
        // Test with actual tokenizer.json from model — guarded by integration flag
        // For unit tests, use a synthetic tokenizer
        Path tokenizerJson = tempDir.resolve("tokenizer.json");
        Files.writeString(tokenizerJson, """
                {"model":{"vocab":{"h":0,"e":1,"l":2,"o":3," ":4},"type":"BPE"}}
                """);

        var tokenizer = Audio8Tokenizer.load(tokenizerJson);
        int[] ids = tokenizer.encode("hello");
        String decoded = tokenizer.decode(ids);

        assertThat(decoded).isEqualTo("hello");
    }
}
```

Note: The exact test assertions depend on what `prompt.py` reveals about the tokenizer format. Update after Step 1-2.

- [ ] **Step 4: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=Audio8TokenizerTest`
Expected: FAIL — `Audio8Tokenizer` does not exist

- [ ] **Step 5: Implement Audio8Tokenizer**

Port the tokenization logic from `prompt.py`. Use `com.google.code.gson:gson` (already a dependency of speech-sherpa) to parse `tokenizer.json`.

```java
package io.casehub.blocks.speech.sherpa;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

final class Audio8Tokenizer {

    private final Map<String, Integer> vocab;
    private final Map<Integer, String> reverseVocab;
    // BOS/EOS/PAD token IDs — set from tokenizer.json or prompt.py patterns

    private Audio8Tokenizer(Map<String, Integer> vocab) {
        this.vocab = Map.copyOf(vocab);
        var reverse = new HashMap<Integer, String>();
        vocab.forEach((k, v) -> reverse.put(v, k));
        this.reverseVocab = Map.copyOf(reverse);
    }

    static Audio8Tokenizer load(Path tokenizerJson) throws IOException {
        String json = Files.readString(tokenizerJson);
        var root = new Gson().fromJson(json, JsonObject.class);
        var model = root.getAsJsonObject("model");
        var vocabObj = model.getAsJsonObject("vocab");
        var vocab = new HashMap<String, Integer>();
        vocabObj.entrySet().forEach(e -> vocab.put(e.getKey(), e.getValue().getAsInt()));
        return new Audio8Tokenizer(vocab);
    }

    int[] encode(String text) {
        // Port encoding logic from prompt.py
        // This is a placeholder — update after reading the Python reference
        var ids = new ArrayList<Integer>();
        for (char c : text.toCharArray()) {
            Integer id = vocab.get(String.valueOf(c));
            if (id != null) ids.add(id);
        }
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    String decode(int[] ids) {
        var sb = new StringBuilder();
        for (int id : ids) {
            String token = reverseVocab.get(id);
            if (token != null) sb.append(token);
        }
        return sb.toString();
    }
}
```

Note: The exact encoding logic MUST match `prompt.py`. The above is a skeleton — update after reading the Python source in Step 1.

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=Audio8TokenizerTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Audio8Tokenizer.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/Audio8TokenizerTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#213): Audio8Tokenizer — HuggingFace tokenizer.json parsing Refs #213"
```

### Task 4: Audio8Config + Provisioner extensions

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Audio8Config.java`
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/Audio8ConfigTest.java`

**Interfaces:**
- Consumes: `Provisioner.provision()` (existing), HuggingFace URLs for Audio8 models
- Produces: `Audio8Config` record, `Provisioner.ensureAudio8Model(String variant)` → `Path`, `Provisioner.audio8ModelDir(String variant)` → `Path`

- [ ] **Step 1: Write Audio8Config**

```java
package io.casehub.blocks.speech.sherpa;

public record Audio8Config(
        Path modelDir,
        String variant,
        int numThreads,
        String provider,
        int maxTokens,
        float temperature,
        int topK
) {
    public static Audio8Config defaults(Path modelDir) {
        return defaults(modelDir, "0.1b");
    }

    public static Audio8Config defaults(Path modelDir, String variant) {
        return new Audio8Config(modelDir, variant, 4, "cpu", 2048, 0.7f, 50);
    }
}
```

- [ ] **Step 2: Write failing test for config validation**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class Audio8ConfigTest {

    @Test
    void defaultsUsesCorrectValues(@TempDir Path tempDir) {
        var config = Audio8Config.defaults(tempDir);
        assertThat(config.variant()).isEqualTo("0.1b");
        assertThat(config.numThreads()).isEqualTo(4);
        assertThat(config.maxTokens()).isEqualTo(2048);
    }

    @Test
    void defaultsAcceptsVariantOverride(@TempDir Path tempDir) {
        var config = Audio8Config.defaults(tempDir, "0.6b");
        assertThat(config.variant()).isEqualTo("0.6b");
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=Audio8ConfigTest`
Expected: FAIL — `Audio8Config` does not exist

- [ ] **Step 4: Create Audio8Config and run tests**

Create the file above with `ide_insert_member` / IDE tooling. Run tests:

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=Audio8ConfigTest`
Expected: PASS

- [ ] **Step 5: Add Provisioner extensions for Audio8 models**

Add to `Provisioner.java`:
- `AUDIO8_MODEL_EXPECTED_FILES` map — `slow_ar_*.onnx`, `fast_ar_*.onnx`, `codec_decoder_fp16.onnx`, `tokenizer/tokenizer.json`, `runtime_manifest.json`
- `AUDIO8_MODEL_CHECKSUMS` map — SHA-256 checksums (obtain from HuggingFace)
- `audio8ModelDir(String variant)` → `Path`
- `audio8ModelUrl(String variant)` → URL string
- `ensureAudio8Model(String variant)` → `Path` — follows `ensureKokoroModel()` pattern

Note: Exact URLs and checksums must be obtained from [Audio8/audio8-TTS-0.1B-ONNX-INT8](https://huggingface.co/Audio8/audio8-TTS-0.1B-ONNX-INT8) and [Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4](https://huggingface.co/Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4). Download a model archive, compute the SHA-256, and add it to the map.

- [ ] **Step 6: Run full speech-sherpa tests**

Run: `mvn --batch-mode test -pl speech-sherpa`
Expected: All tests pass (existing + new)

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Audio8Config.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/Audio8ConfigTest.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#213): Audio8Config + Provisioner model download Refs #213"
```

---

## Batch 3: Audio8 DualAR Engine

The core inference engine. After this batch, Audio8 TTS is fully functional.

### Task 5: DualARLoop + CodecDecoder

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/DualARLoop.java`
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/CodecDecoder.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/DualARLoopTest.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/CodecDecoderTest.java`

**Interfaces:**
- Consumes: `OnnxRuntimeLibrary.Session` (existing), `Audio8Config` (Task 4), `Audio8Tokenizer` (Task 3)
- Produces: `DualARLoop` (`int[][] generate(int[] tokenIds, @Nullable int[] voiceCodes, Audio8Config config)` → codec token matrix), `CodecDecoder` (`float[] decode(int[][] codecTokens)` → PCM samples)

**CRITICAL: Port from Python reference.** Read these files BEFORE writing any code:
- `/tmp/audio8-tts-ref/onnx_runtime/arktts_runtime/runtime.py` — core inference loop with KV-cache management, slow AR → fast AR token generation, sampling
- `/tmp/audio8-tts-ref/onnx_runtime/arktts_runtime/prompt.py` — input tensor preparation

The autoregressive loop protocol (how KV-cache tensors are passed between calls, how the slow AR hidden state feeds the fast AR, how codec codebooks are structured) MUST match the Python implementation exactly.

- [ ] **Step 1: Study runtime.py and prompt.py**

Read `/tmp/audio8-tts-ref/onnx_runtime/arktts_runtime/runtime.py` carefully. Document:
1. ONNX session input/output tensor names for slow AR and fast AR
2. KV-cache tensor shapes and how they grow
3. Sampling logic (temperature, top-k)
4. End-of-sequence detection
5. How slow AR hidden state is passed to fast AR
6. Codec token matrix shape (frames × codebooks)

Read `/tmp/audio8-tts-ref/onnx_runtime/arktts_runtime/prompt.py`. Document:
1. How text token IDs are prepared as input tensors
2. How voice reference codes condition the generation
3. Any special token handling (BOS, EOS, padding)

- [ ] **Step 2: Write failing tests for DualARLoop**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DualARLoopTest {

    @Test
    void generatesCodecTokensFromTextTokens() {
        // Mock ONNX sessions — slow AR returns semantic tokens, fast AR returns codec codebooks
        // Exact mock setup depends on runtime.py tensor protocol (Step 1)
        // Structure: feed token IDs → slow AR generates semantic tokens →
        //            fast AR generates codec codebooks per frame → return codec matrix
    }

    @Test
    void stopsAtMaxTokenCount() {
        // Verify the degenerate loop guard works
        // Mock slow AR to never produce EOS
        // Verify generation stops at config.maxTokens()
    }

    @Test
    void stopsAtEndOfSequenceToken() {
        // Mock slow AR to produce EOS after N tokens
        // Verify generation stops and returns N frames of codecs
    }

    @Test
    void conditionsOnVoiceCodesWhenProvided() {
        // Verify voice codes are prepended/injected into the input
        // Exact mechanism depends on prompt.py (Step 1)
    }
}
```

Note: Test bodies depend on the tensor protocol discovered in Step 1. Write mocks that match the exact ONNX session input/output names and shapes.

- [ ] **Step 3: Implement DualARLoop**

Port the autoregressive loop from `runtime.py` to Java using `OnnxRuntimeLibrary.Session`:
- Create slow AR and fast AR sessions via `OnnxRuntimeLibrary.createSession()`
- Implement token-by-token generation with KV-cache state management
- Use `session.runFloat()` or `session.runRaw()` for each step
- Implement temperature/top-k sampling
- Add max-token guard and EOS detection

```java
package io.casehub.blocks.speech.sherpa;

import java.lang.foreign.Arena;

final class DualARLoop implements AutoCloseable {

    private final OnnxRuntimeLibrary.Session slowAr;
    private final OnnxRuntimeLibrary.Session fastAr;

    DualARLoop(OnnxRuntimeLibrary.Session slowAr, OnnxRuntimeLibrary.Session fastAr) {
        this.slowAr = slowAr;
        this.fastAr = fastAr;
    }

    int[][] generate(int[] tokenIds, int[] voiceCodes, Audio8Config config) {
        // Port from runtime.py — exact implementation follows Python reference
        // 1. Prepare input tensors from tokenIds (and voiceCodes if non-null)
        // 2. Run slow AR step-by-step, accumulating semantic tokens
        // 3. After each slow AR step, run fast AR to get codec codebooks
        // 4. Manage KV-cache tensors between steps
        // 5. Stop on EOS or maxTokens
        // 6. Return codec token matrix (frames × codebooks)
        throw new UnsupportedOperationException("Port from runtime.py");
    }

    @Override
    public void close() {
        slowAr.close();
        fastAr.close();
    }
}
```

- [ ] **Step 4: Write failing tests for CodecDecoder**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class CodecDecoderTest {

    @Test
    void decodesCodecTokensToAudioSamples() {
        // Mock codec decoder ONNX session
        // Feed known codec token matrix → verify float[] output shape
    }

    @Test
    void outputSampleRateMatchesModelSpec() {
        // Audio8 codec operates at specific sample rate (from runtime_manifest.json)
        // Verify the decoder reports the correct rate
    }
}
```

- [ ] **Step 5: Implement CodecDecoder**

```java
package io.casehub.blocks.speech.sherpa;

final class CodecDecoder implements AutoCloseable {

    private final OnnxRuntimeLibrary.Session session;
    private final int sampleRate;

    CodecDecoder(OnnxRuntimeLibrary.Session session, int sampleRate) {
        this.session = session;
        this.sampleRate = sampleRate;
    }

    float[] decode(int[][] codecTokens) {
        // Port from runtime.py codec decoding section
        // 1. Reshape codec tokens to tensor (codebooks × frames)
        // 2. Run codec decoder session
        // 3. Extract float[] audio samples from output
        throw new UnsupportedOperationException("Port from runtime.py");
    }

    int sampleRate() { return sampleRate; }

    @Override
    public void close() { session.close(); }
}
```

- [ ] **Step 6: Run tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=DualARLoopTest,CodecDecoderTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/DualARLoop.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/CodecDecoder.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/DualARLoopTest.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/CodecDecoderTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#213): DualARLoop + CodecDecoder — autoregressive inference engine Refs #213"
```

### Task 6: VoiceRegistry

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VoiceRegistry.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VoiceRegistryTest.java`

**Interfaces:**
- Consumes: `OnnxRuntimeLibrary.Session` (codec encoder — transient), `WavReader` (existing)
- Produces: `VoiceRegistry` with `String register(Path referenceAudio)`, `int[] getVoiceCodes(String voiceId)`, `void release(String voiceId)`, `Set<String> registeredVoices()`

**Reference:** Read `/tmp/audio8-tts-ref/onnx_runtime/arktts_runtime/registration.py` and `voices.py` for the voice encoding protocol.

- [ ] **Step 1: Write failing tests**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VoiceRegistryTest {

    @Test
    void registerReturnsUniqueId() {
        var registry = new VoiceRegistry(mockEncoderFactory());
        String id1 = registry.register(Path.of("voice1.wav"));
        String id2 = registry.register(Path.of("voice2.wav"));
        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void getVoiceCodesReturnsRegisteredCodes() {
        var registry = new VoiceRegistry(mockEncoderFactory());
        String id = registry.register(Path.of("voice.wav"));
        int[] codes = registry.getVoiceCodes(id);
        assertThat(codes).isNotNull().isNotEmpty();
    }

    @Test
    void getVoiceCodesThrowsForUnknownId() {
        var registry = new VoiceRegistry(mockEncoderFactory());
        assertThatThrownBy(() -> registry.getVoiceCodes("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void releaseRemovesVoice() {
        var registry = new VoiceRegistry(mockEncoderFactory());
        String id = registry.register(Path.of("voice.wav"));
        registry.release(id);
        assertThat(registry.registeredVoices()).doesNotContain(id);
    }

    @Test
    void registeredVoicesReturnsAllIds() {
        var registry = new VoiceRegistry(mockEncoderFactory());
        registry.register(Path.of("v1.wav"));
        registry.register(Path.of("v2.wav"));
        assertThat(registry.registeredVoices()).hasSize(2);
    }

    private VoiceRegistry.EncoderFactory mockEncoderFactory() {
        return (modelDir) -> (audioData) -> new int[]{1, 2, 3, 4};
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VoiceRegistryTest`
Expected: FAIL

- [ ] **Step 3: Implement VoiceRegistry**

```java
package io.casehub.blocks.speech.sherpa;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class VoiceRegistry {

    @FunctionalInterface
    interface VoiceEncoder {
        int[] encode(byte[] audioData);
    }

    @FunctionalInterface
    interface EncoderFactory {
        VoiceEncoder create(Path modelDir);
    }

    private final EncoderFactory encoderFactory;
    private final Map<String, int[]> voices = new ConcurrentHashMap<>();

    VoiceRegistry(EncoderFactory encoderFactory) {
        this.encoderFactory = encoderFactory;
    }

    String register(Path referenceAudio) {
        try {
            byte[] audioData = Files.readAllBytes(referenceAudio);
            VoiceEncoder encoder = encoderFactory.create(referenceAudio.getParent());
            int[] codes = encoder.encode(audioData);
            String id = UUID.randomUUID().toString();
            voices.put(id, codes);
            return id;
        } catch (IOException e) {
            throw new SherpaException("Failed to read reference audio: " + referenceAudio, e);
        }
    }

    int[] getVoiceCodes(String voiceId) {
        int[] codes = voices.get(voiceId);
        if (codes == null) throw new IllegalArgumentException("Unknown voice: " + voiceId);
        return codes.clone();
    }

    void release(String voiceId) {
        voices.remove(voiceId);
    }

    Set<String> registeredVoices() {
        return Set.copyOf(voices.keySet());
    }

    void close() {
        voices.clear();
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VoiceRegistryTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VoiceRegistry.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VoiceRegistryTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#213): VoiceRegistry — voice cloning lifecycle Refs #213"
```

### Task 7: Audio8TextToSpeech + integration test

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Audio8TextToSpeech.java`
- Test: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/Audio8TextToSpeechTest.java`

**Interfaces:**
- Consumes: `Audio8Tokenizer` (Task 3), `DualARLoop` (Task 5), `CodecDecoder` (Task 5), `VoiceRegistry` (Task 6), `Audio8Config` (Task 4), `Provisioner.ensureAudio8Model()` (Task 4), `WavWriter` (existing)
- Produces: `Audio8TextToSpeech implements TextToSpeechService, AutoCloseable` with `withDefaults()`, `withDefaults(String variant)`, `registerVoice(Path)`, `releaseVoice(String)`, `registeredVoices()`

- [ ] **Step 1: Write failing unit tests (mocked sessions)**

```java
package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.SynthesisOptions;
import io.casehub.blocks.speech.SynthesisResult;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class Audio8TextToSpeechTest {

    @Test
    void synthesiseReturnsWavAudioWithEmptyPhonemes() {
        // Construct with mock DualARLoop, CodecDecoder, Audio8Tokenizer
        // Verify: synthesise() returns SynthesisResult with wav audio and empty phonemes
        // (LipSyncEnricher adds phonemes — Audio8 itself returns empty)
    }

    @Test
    void synthesiseUsesRegisteredVoiceWhenSpecifiedInOptions() {
        // Register a voice, pass voiceId in SynthesisOptions.voice()
        // Verify: DualARLoop receives voice codes
    }

    @Test
    void synthesiseUsesDefaultVoiceWhenNoneSpecified() {
        // No voice in options → DualARLoop receives null voice codes
    }

    @Test
    void closeReleasesAllResources() {
        // Verify: close() calls DualARLoop.close(), CodecDecoder.close(), VoiceRegistry.close()
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=Audio8TextToSpeechTest`
Expected: FAIL

- [ ] **Step 3: Implement Audio8TextToSpeech**

```java
package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.*;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public final class Audio8TextToSpeech implements TextToSpeechService, AutoCloseable {

    private final Audio8Tokenizer tokenizer;
    private final DualARLoop dualAr;
    private final CodecDecoder codecDecoder;
    private final VoiceRegistry voiceRegistry;
    private final Audio8Config config;

    Audio8TextToSpeech(Audio8Tokenizer tokenizer, DualARLoop dualAr,
                       CodecDecoder codecDecoder, VoiceRegistry voiceRegistry,
                       Audio8Config config) {
        this.tokenizer = tokenizer;
        this.dualAr = dualAr;
        this.codecDecoder = codecDecoder;
        this.voiceRegistry = voiceRegistry;
        this.config = config;
    }

    public static Audio8TextToSpeech withDefaults() {
        return withDefaults("0.1b");
    }

    public static Audio8TextToSpeech withDefaults(String variant) {
        Path modelDir = Provisioner.ensureAudio8Model(variant);
        var config = Audio8Config.defaults(modelDir, variant);
        // Load ONNX sessions, tokenizer, create DualARLoop, CodecDecoder, VoiceRegistry
        var ort = OnnxRuntimeLibrary.load();
        var tokenizer = Audio8Tokenizer.load(modelDir.resolve("tokenizer/tokenizer.json"));
        var slowAr = ort.createSession(modelDir.resolve(slowArFilename(variant)), config.numThreads());
        var fastAr = ort.createSession(modelDir.resolve(fastArFilename(variant)), config.numThreads());
        var codec = ort.createSession(modelDir.resolve("codec_decoder_fp16.onnx"), config.numThreads());
        var dualAr = new DualARLoop(slowAr, fastAr);
        var codecDecoder = new CodecDecoder(codec, sampleRate(variant));
        var voiceRegistry = new VoiceRegistry(dir -> audioData -> {
            // Transient codec encoder for voice registration
            var encoder = ort.createSession(modelDir.resolve("codec_encoder_fp16.onnx"), config.numThreads());
            try { return encodeVoice(encoder, audioData); }
            finally { encoder.close(); }
        });
        return new Audio8TextToSpeech(tokenizer, dualAr, codecDecoder, voiceRegistry, config);
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        int[] tokenIds = tokenizer.encode(text);
        int[] voiceCodes = options.voice() != null ? voiceRegistry.getVoiceCodes(options.voice()) : null;
        int[][] codecTokens = dualAr.generate(tokenIds, voiceCodes, config);
        float[] samples = codecDecoder.decode(codecTokens);
        byte[] wav = WavWriter.encode(samples, codecDecoder.sampleRate());
        return new SynthesisResult(wav, "wav", List.of());
    }

    public String registerVoice(Path referenceAudio) {
        return voiceRegistry.register(referenceAudio);
    }

    public void releaseVoice(String voiceId) {
        voiceRegistry.release(voiceId);
    }

    public Set<String> registeredVoices() {
        return voiceRegistry.registeredVoices();
    }

    @Override
    public void close() {
        dualAr.close();
        codecDecoder.close();
        voiceRegistry.close();
    }

    private static String slowArFilename(String variant) {
        return "0.1b".equals(variant) ? "slow_ar_int8.onnx" : "slow_ar_int4.onnx";
    }

    private static String fastArFilename(String variant) {
        return "0.1b".equals(variant) ? "fast_ar_int8.onnx" : "fast_ar_int4.onnx";
    }

    private static int sampleRate(String variant) {
        return 44100; // Audio8 codec operates at 44.1kHz — verify from runtime_manifest.json
    }

    private static int[] encodeVoice(OnnxRuntimeLibrary.Session encoder, byte[] audioData) {
        // Port from registration.py — encode reference audio to codec tokens
        throw new UnsupportedOperationException("Port from registration.py");
    }
}
```

- [ ] **Step 4: Run unit tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=Audio8TextToSpeechTest`
Expected: PASS

- [ ] **Step 5: Write integration test (guarded by system property)**

```java
package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "speech.integration", matches = "true")
class Audio8TextToSpeechIntegrationTest {

    @Test
    void synthesisesAudioWithDefaultModel() {
        try (var tts = Audio8TextToSpeech.withDefaults("0.1b")) {
            SynthesisResult result = tts.synthesise("Hello world", SynthesisOptions.defaults());
            assertThat(result.audioData()).isNotEmpty();
            assertThat(result.audioFormat()).isEqualTo("wav");
            assertThat(result.phonemes()).isEmpty(); // no native phonemes
        }
    }

    @Test
    void synthesisesWithLipSyncEnricher() {
        try (var tts = Audio8TextToSpeech.withDefaults("0.1b")) {
            var aligner = EspeakPhonemeAligner.withEspeak(
                    EspeakLibrary.load(Provisioner.ensureNativeLibrary(), Provisioner.ensureEspeak()));
            var enriched = LipSyncEnricher.wrap(tts, aligner);

            SynthesisResult result = enriched.synthesise("Hello world", SynthesisOptions.defaults());
            assertThat(result.audioData()).isNotEmpty();
            assertThat(result.phonemes()).isNotEmpty(); // enricher added phonemes
        }
    }
}
```

- [ ] **Step 6: Add Audio8 to SpeechCli (optional — for manual testing)**

Add an `audio8` subcommand to `SpeechCli` for easy manual testing:
```
java -cp ... SpeechCli audio8 "Hello world" output.wav
java -cp ... SpeechCli audio8 --model 0.6b "Hello world" output.wav
```

- [ ] **Step 7: Run full test suite**

Run: `mvn --batch-mode test -pl speech-api,speech-sherpa`
Expected: All tests pass

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Audio8TextToSpeech.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/Audio8TextToSpeechTest.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/Audio8TextToSpeechIntegrationTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#213): Audio8TextToSpeech — DualAR TTS with voice cloning Refs #213"
```

---

## References

- [2026-08-28-audio8-tts-composable-lipsync-design.md](/Users/mdproctor/claude/casehub/slots/161/wsp-casehub-blocks/specs/issue-213-fish-speech-tts/2026-08-28-audio8-tts-composable-lipsync-design.md) — design spec this plan implements
- [Audio8-AI/Audio8_TTS](https://github.com/Audio8-AI/Audio8_TTS) — Python reference implementation (onnx_runtime/arktts_runtime/)
- [Audio8/audio8-TTS-0.1B-ONNX-INT8](https://huggingface.co/Audio8/audio8-TTS-0.1B-ONNX-INT8) — 0.1B model
- [Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4](https://huggingface.co/Audio8/Audio8-TTS-Preview-0.6B-ONNX-INT4) — 0.6B model
- `TextToSpeechService.java:4` — SPI contract
- `SynthesisResult.java:6` — return type
- `Provisioner.java:296-316` — ensureKokoroModel pattern (template for ensureAudio8Model)
- `EspeakLibrary.java:81-135` — textToPhonemes method
- `KokoroTextToSpeech.java:20-28` — withDefaults() factory pattern
- `OnnxRuntimeLibrary.java` — Session.runFloat(), Session.runRaw()
- GitHub #213 — focal issue
