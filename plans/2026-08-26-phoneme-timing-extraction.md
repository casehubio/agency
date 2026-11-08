# Phoneme Timing Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #186 — Phoneme timing extraction from VITS TTS
**Issue group:** #186 (within epic #194)

**Goal:** Extract exact per-phoneme timing from VITS TTS synthesis by
modifying the ONNX model to expose duration predictor output and calling
onnxruntime + espeak-ng directly via Java FFM.

**Architecture:** Bypass sherpa-onnx TTS. Load onnxruntime C API via FFM
vtable pattern (`OrtGetApiBase`). Load espeak-ng via FFM for
phonemization. Patch ONNX model with protobuf-java to expose
`/Ceil_output_0` duration tensor. New `VitsTextToSpeech` implementation
composes all three into a single-pass synthesis that returns audio +
`List<PhonemeTiming>`.

**Tech Stack:** Java 22 FFM/Panama, onnxruntime C API, espeak-ng C API,
protobuf-java, ONNX proto schema

## Global Constraints

- Module: `speech-sherpa` (`casehub-blocks-speech-sherpa`)
- Java target: 22 (stable FFM API)
- Package: `io.casehub.blocks.speech.sherpa`
- No CDI/Quarkus dependencies — pure Java
- All native library loading follows the tiered pattern from `SherpaLibrary` (system path → cache → provisioned)
- `OnnxRuntimeLibrary` loads `libonnxruntime` from the sherpa-onnx provisioned directory (`SherpaLibrary.defaultCacheDir()`) as Tier 2; it does NOT provision onnxruntime independently
- ORT API version: pin to version bundled with sherpa-onnx 1.13.6; validate `GetApi()` return for null
- espeak-ng methods must be `synchronized` (global state, not thread-safe)
- **VITS blank token interspersing:** Piper VITS models require pad tokens (ID 0) interspersed between every phoneme ID. For N input tokens, the interspersed sequence has 2N+1 elements: `[PAD, tok0, PAD, tok1, PAD, ..., tokN-1, PAD]`. This is applied AFTER tokenization, BEFORE tensor construction
- Duration predictor outputs one value per interspersed element (2N+1). Only non-pad, non-BOS, non-EOS entries produce `PhonemeTiming` results. Blank durations represent inter-phoneme transitions and contribute to cumulative timing
- Duration computation uses `double` throughout; round to `long` only at `PhonemeTiming` construction
- hop_length = 256 (Piper VITS standard)
- JSON parsing: use `com.google.code.gson:gson` (~300KB, zero transitive deps)
- ONNX proto manipulation: use `DynamicMessage` from protobuf-java (no generated classes, no protoc step)
- Existing `SherpaOnnxTextToSpeech` unchanged — both impls coexist
- espeak-ng `textToPhonemes()` loop concatenates per-word IPA with space separators (matching the ` ` key in `phoneme_id_map`)

---

## Batch 1: Pure Java Foundation — VitsConfig + ModelPatcher

After this batch: model config parsing, phoneme tokenization, and ONNX
model patching all work with full test coverage. No native deps required.

### Task 1: VitsConfig — model config parsing and phoneme tokenization

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VitsConfig.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VitsConfigTest.java`
- Create: `speech-sherpa/src/test/resources/test-vits-config.json` (test fixture)

**Interfaces:**
- Consumes: nothing (foundation)
- Produces:
  - `VitsConfig.fromModelDir(Path modelDir)` — factory parsing `.onnx.json` via gson
  - `VitsConfig.fromModelDir(Path modelDir, int numThreads, String provider)` — with runtime config
  - `VitsConfig.modelPath()` — `Path` to the `.onnx` file
  - `VitsConfig.sampleRate()` — `int`
  - `VitsConfig.espeakVoice()` — `String` (e.g., `"en-us"`)
  - `VitsConfig.numThreads()` — `int` (default 1)
  - `VitsConfig.provider()` — `String` (default `"cpu"`)
  - `VitsConfig.noiseScale()`, `lengthScale()`, `noiseScaleW()` — `float`
  - `VitsConfig.phonemeToIds(String phoneme)` — `List<Integer>` (from phoneme_id_map)
  - `VitsConfig.idToPhoneme(int id)` — `String` (reverse map)
  - `VitsConfig.tokenize(String ipa)` — `List<Integer>` (greedy longest-match, wraps with BOS/EOS, **interspersed with pad tokens**)

- [ ] **Step 1: Create test fixture**

Create `speech-sherpa/src/test/resources/test-vits-config.json` with a
minimal Piper config structure:

```json
{
  "audio": { "sample_rate": 22050, "quality": "medium" },
  "espeak": { "voice": "en-us" },
  "inference": { "noise_scale": 0.667, "length_scale": 1.0, "noise_w": 0.8 },
  "phoneme_type": "espeak",
  "phoneme_id_map": {
    "_": [0], "^": [1], "$": [2], " ": [3],
    "h": [20], "ə": [59], "l": [24], "oʊ": [40, 41],
    "w": [35], "ɜ": [62], "ɹ": [88], "d": [17],
    "ˈ": [120], "ˌ": [121]
  },
  "num_symbols": 256,
  "num_speakers": 1,
  "speaker_id_map": {}
}
```

- [ ] **Step 2: Write failing tests**

```java
class VitsConfigTest {

    @Test
    void fromModelDir_parsesSampleRate() {
        var config = loadTestConfig();
        assertThat(config.sampleRate()).isEqualTo(22050);
    }

    @Test
    void fromModelDir_parsesEspeakVoice() {
        var config = loadTestConfig();
        assertThat(config.espeakVoice()).isEqualTo("en-us");
    }

    @Test
    void fromModelDir_parsesInferenceDefaults() {
        var config = loadTestConfig();
        assertThat(config.noiseScale()).isEqualTo(0.667f, within(0.001f));
        assertThat(config.lengthScale()).isEqualTo(1.0f, within(0.001f));
        assertThat(config.noiseScaleW()).isEqualTo(0.8f, within(0.001f));
    }

    @Test
    void phonemeToIds_singleId() {
        var config = loadTestConfig();
        assertThat(config.phonemeToIds("h")).containsExactly(20);
    }

    @Test
    void phonemeToIds_multiId() {
        var config = loadTestConfig();
        assertThat(config.phonemeToIds("oʊ")).containsExactly(40, 41);
    }

    @Test
    void phonemeToIds_unknownReturnsEmpty() {
        var config = loadTestConfig();
        assertThat(config.phonemeToIds("ʒ")).isEmpty();
    }

    @Test
    void idToPhoneme_reverseMap() {
        var config = loadTestConfig();
        assertThat(config.idToPhoneme(20)).isEqualTo("h");
        assertThat(config.idToPhoneme(40)).isEqualTo("oʊ");
    }

    @Test
    void tokenize_interspersesPadTokens() {
        var config = loadTestConfig();
        // "hə" → raw [BOS, h, ə, EOS] → interspersed [PAD, BOS, PAD, h, PAD, ə, PAD, EOS, PAD]
        // IDs:                                        [0,   1,   0,  20, 0,  59, 0,   2,   0]
        List<Integer> ids = config.tokenize("hə");
        assertThat(ids).startsWith(0, 1, 0); // PAD, BOS, PAD
        assertThat(ids).endsWith(0, 2, 0);   // PAD, EOS, PAD
        // Length = 2*N+1 where N = 4 (BOS + h + ə + EOS) → 9
        assertThat(ids).hasSize(9);
    }

    @Test
    void tokenize_greedyLongestMatch() {
        var config = loadTestConfig();
        // "oʊ" should match as single token [40,41], not "o" + "ʊ"
        List<Integer> ids = config.tokenize("oʊ");
        assertThat(ids).contains(40, 41);
    }

    @Test
    void tokenize_unknownCharsSkipped() {
        var config = loadTestConfig();
        // "hXə" — X is not in map, should be skipped
        List<Integer> ids = config.tokenize("hXə");
        assertThat(ids).contains(20, 59); // h=20, ə=59
        assertThat(ids).doesNotContain(-1);
    }

    @Test
    void tokenize_spaceInsertsWordBoundary() {
        var config = loadTestConfig();
        List<Integer> ids = config.tokenize("h d");
        // should contain space token (3) between h and d
        assertThat(ids).contains(3);
    }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VitsConfigTest`
Expected: compilation failure (VitsConfig doesn't exist)

- [ ] **Step 4: Implement VitsConfig**

`VitsConfig` is a record with a factory method that parses the Piper
`.onnx.json` config file. JSON parsing uses `java.io` with a minimal
recursive descent parser for this well-known structure. The phoneme ID
map is loaded into a `Map<String, List<Integer>>` and a reverse
`Map<Integer, String>` is built at construction.

Key implementation details:
- `fromModelDir(Path)`: scans for `*.onnx.json` file, parses with gson
- `tokenize(String ipa)`: (1) sort phoneme_id_map keys by length descending,
  (2) greedily match from left to right, (3) wrap with BOS/EOS (looked up
  from map, not hardcoded), (4) **intersperse with pad token** (ID from
  `_` key in map): for each token in sequence, emit `[PAD, token]`, then
  trailing PAD. Result length = 2*N+1
- Unknown characters: skip with `System.Logger` warning
- Multi-ID entries: expand in sequence (each ID in the list is a separate
  element in the interspersed sequence)
- `numThreads` and `provider`: passed as constructor params, defaulting
  to 1 and "cpu"

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VitsConfigTest`
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VitsConfig.java
git add speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VitsConfigTest.java
git add speech-sherpa/src/test/resources/test-vits-config.json
git commit -m "feat(#186): add VitsConfig — model config parsing + phoneme tokenization"
```

### Task 2: ModelPatcher — ONNX model modification via protobuf-java

**Files:**
- Modify: `speech-sherpa/pom.xml` (add protobuf-java + onnx-proto deps)
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/ModelPatcher.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/ModelPatcherTest.java`

**Interfaces:**
- Consumes: nothing (standalone utility)
- Produces: `ModelPatcher.patch(Path modelDir) → boolean` — adds
  `/Ceil_output_0` as a second output to the ONNX model. Returns `true`
  if patched successfully, `false` if already patched. Throws on error.

- [ ] **Step 1: Add protobuf-java and gson dependencies to pom.xml**

Add to `speech-sherpa/pom.xml`:

```xml
<dependency>
  <groupId>com.google.protobuf</groupId>
  <artifactId>protobuf-java</artifactId>
  <version>4.29.3</version>
</dependency>
<dependency>
  <groupId>com.google.code.gson</groupId>
  <artifactId>gson</artifactId>
  <version>2.12.1</version>
</dependency>
```

For ONNX proto: use `DynamicMessage` from protobuf-java with the ONNX
descriptor built from `onnx.proto3` at compile time. No generated classes
needed, no onnxruntime Java artifact. The ONNX proto schema is stable and
small. Include `src/main/resources/onnx.proto3` (copied from the ONNX
project) and build the `FileDescriptor` at runtime from the proto text.

- [ ] **Step 2: Write failing tests**

```java
class ModelPatcherTest {

    @Test
    void patch_addsSecondOutput() throws Exception {
        Path modelDir = createTempModelDir();
        boolean result = ModelPatcher.patch(modelDir);
        assertThat(result).isTrue();
        // Verify by loading and checking output count
        assertThat(ModelPatcher.outputCount(modelDir)).isEqualTo(2);
    }

    @Test
    void patch_idempotent() throws Exception {
        Path modelDir = createTempModelDir();
        ModelPatcher.patch(modelDir);
        boolean secondResult = ModelPatcher.patch(modelDir);
        assertThat(secondResult).isFalse(); // already patched
        assertThat(ModelPatcher.outputCount(modelDir)).isEqualTo(2);
    }

    @Test
    void patch_preservesOriginalOutput() throws Exception {
        Path modelDir = createTempModelDir();
        ModelPatcher.patch(modelDir);
        // Original "output" name should still be first
        assertThat(ModelPatcher.outputName(modelDir, 0)).isEqualTo("output");
        assertThat(ModelPatcher.outputName(modelDir, 1))
            .isEqualTo("/Ceil_output_0");
    }
}
```

The `createTempModelDir()` helper creates a minimal valid ONNX model
with a single output named "output". This can be built programmatically
with protobuf-java.

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=ModelPatcherTest`
Expected: compilation failure (ModelPatcher doesn't exist)

- [ ] **Step 4: Implement ModelPatcher**

```java
final class ModelPatcher {
    static boolean patch(Path modelDir) { ... }
    static int outputCount(Path modelDir) { ... }
    static String outputName(Path modelDir, int index) { ... }
}
```

Implementation:
1. Find `*.onnx` file in modelDir
2. Read bytes, parse as ONNX ModelProto (protobuf)
3. Check if `graph.output` already contains `/Ceil_output_0` → return false
4. Create `ValueInfoProto` with name `/Ceil_output_0` and float tensor type
5. Append to `graph.output`
6. Serialize back to same file (atomic write via temp + rename)

- [ ] **Step 5: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=ModelPatcherTest`
Expected: all PASS

- [ ] **Step 6: Commit**

```bash
git add speech-sherpa/pom.xml
git add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/ModelPatcher.java
git add speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/ModelPatcherTest.java
git commit -m "feat(#186): add ModelPatcher — ONNX model duration output patching"
```

---

## Batch 2: FFM Native Bindings — OnnxRuntimeLibrary + EspeakLibrary

After this batch: both onnxruntime and espeak-ng are callable from Java
via FFM. Session creation, inference, and phonemization all work
independently.

### Task 3: OnnxRuntimeLibrary — onnxruntime C API FFM bindings

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/OnnxRuntimeLibrary.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/OnnxRuntimeLibraryTest.java`

**Interfaces:**
- Consumes: `libonnxruntime.dylib`/`.so` (native library on disk)
- Produces:
  - `OnnxRuntimeLibrary.load()` → singleton (tiered loading like SherpaLibrary)
  - `OnnxRuntimeLibrary.load(Path)` → explicit path
  - `createEnv()` → `MemorySegment` (OrtEnv*)
  - `createSessionOptions()` → `MemorySegment`
  - `createSession(env, modelPath, options)` → `MemorySegment` (OrtSession*)
  - `sessionOutputCount(session)` → `int`
  - `sessionOutputName(session, index, allocator)` → `String`
  - `createCpuMemoryInfo()` → `MemorySegment`
  - `createTensor(memInfo, data, shape, type)` → `MemorySegment` (OrtValue*)
  - `run(session, inputNames, inputs, outputNames)` → `MemorySegment[]` (OrtValue[])
  - `getTensorData(value)` → `MemorySegment` (pointer to raw data)
  - `releaseValue/Session/Options/Env/MemoryInfo(seg)` — cleanup

- [ ] **Step 1: Write failing test — library loading**

```java
@EnabledIf("onnxRuntimeAvailable")
class OnnxRuntimeLibraryTest {

    @Test
    void load_resolvesApiBase() {
        var lib = OnnxRuntimeLibrary.load();
        assertThat(lib).isNotNull();
    }

    static boolean onnxRuntimeAvailable() {
        return Files.exists(SherpaLibrary.defaultCacheDir()
            .resolve(onnxRuntimeLibName()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=OnnxRuntimeLibraryTest`
Expected: compilation failure

- [ ] **Step 3: Implement OnnxRuntimeLibrary — library loading + vtable resolution**

Core pattern:
1. `SymbolLookup.libraryLookup(path, Arena.global())` to load `libonnxruntime`
2. Resolve `OrtGetApiBase` symbol
3. Call `OrtGetApiBase()` → returns `OrtApiBase*` struct
4. Read `GetApi` function pointer at offset 0 of the struct
5. Call `GetApi(ORT_API_VERSION)` → returns `OrtApi*` vtable
6. Read individual function pointers from known offsets in the vtable

The `OrtApi` struct has function pointers at sequential 8-byte offsets.
The offset for each function is determined by its position in the C
header (e.g., `CreateEnv` is at index 3 → offset 24).

- [ ] **Step 4: Add session lifecycle tests**

```java
@Test
void createSession_withSimpleModel() throws Exception {
    var lib = OnnxRuntimeLibrary.load();
    try (Arena arena = Arena.ofConfined()) {
        var env = lib.createEnv(arena);
        var options = lib.createSessionOptions(arena);
        // Use a minimal ONNX model for testing
        var session = lib.createSession(env, testModelPath(), options, arena);
        assertThat(session).isNotEqualTo(MemorySegment.NULL);
        int outputCount = lib.sessionOutputCount(session);
        assertThat(outputCount).isGreaterThan(0);
    }
}
```

- [ ] **Step 5: Add inference test — run with tensor I/O**

```java
@Test
void run_producesOutput() throws Exception {
    var lib = OnnxRuntimeLibrary.load();
    try (Arena arena = Arena.ofConfined()) {
        var env = lib.createEnv(arena);
        var options = lib.createSessionOptions(arena);
        var session = lib.createSession(env, testModelPath(), options, arena);
        var memInfo = lib.createCpuMemoryInfo(arena);

        // Create input tensor (model-specific)
        // ... build inputs ...
        var outputs = lib.run(session, inputNames, inputs, outputNames, arena);
        assertThat(outputs).isNotEmpty();

        var data = lib.getTensorData(outputs[0]);
        assertThat(data).isNotEqualTo(MemorySegment.NULL);
    }
}
```

- [ ] **Step 6: Implement remaining OnnxRuntimeLibrary methods**

Implement all downcall handles listed in the Produces section. Each
method wraps the FFM downcall with error checking (ORT uses status
codes — non-null OrtStatus* means error).

- [ ] **Step 7: Run all tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=OnnxRuntimeLibraryTest`
Expected: all PASS (if onnxruntime lib available)

- [ ] **Step 8: Commit**

```bash
git add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/OnnxRuntimeLibrary.java
git add speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/OnnxRuntimeLibraryTest.java
git commit -m "feat(#186): add OnnxRuntimeLibrary — onnxruntime C API FFM bindings"
```

### Task 4: EspeakLibrary — espeak-ng C API FFM bindings

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/EspeakLibrary.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/EspeakLibraryTest.java`

**Interfaces:**
- Consumes: `libespeak-ng.dylib`/`.so` (native library on disk), espeak-ng-data directory
- Produces:
  - `EspeakLibrary.load(Path libPath, Path dataPath)` → singleton
  - `EspeakLibrary.textToPhonemes(String text, String voice)` → `String` (IPA)
  - All methods `synchronized`

- [ ] **Step 1: Write failing test**

```java
@EnabledIf("espeakAvailable")
class EspeakLibraryTest {

    @Test
    void textToPhonemes_producesIpa() {
        var lib = loadEspeak();
        String ipa = lib.textToPhonemes("hello", "en-us");
        assertThat(ipa).isNotEmpty();
        assertThat(ipa).contains("h");
        assertThat(ipa).contains("l");
    }

    @Test
    void textToPhonemes_multipleWords() {
        var lib = loadEspeak();
        String ipa = lib.textToPhonemes("hello world", "en-us");
        // Should contain a space separator between words
        assertThat(ipa).contains(" ");
    }

    @Test
    void textToPhonemes_threadSafe() throws Exception {
        var lib = loadEspeak();
        // Verify synchronized access doesn't deadlock
        var futures = IntStream.range(0, 10)
            .mapToObj(i -> CompletableFuture.supplyAsync(
                () -> lib.textToPhonemes("test " + i, "en-us")))
            .toList();
        var results = futures.stream()
            .map(CompletableFuture::join)
            .toList();
        assertThat(results).hasSize(10);
        assertThat(results).allMatch(s -> !s.isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=EspeakLibraryTest`
Expected: compilation failure

- [ ] **Step 3: Implement EspeakLibrary**

FFM bindings for espeak-ng C API:

```java
final class EspeakLibrary {
    // Downcall handles
    private final MethodHandle espeakInitialize;
    private final MethodHandle espeakSetVoiceByName;
    private final MethodHandle espeakTextToPhonemes;
    private final MethodHandle espeakTerminate;
}
```

Key FFM signatures:
- `espeak_Initialize(int output, int buflength, ADDRESS path, int options)`
  → returns `int` (sample rate, -1 on error)
- `espeak_SetVoiceByName(ADDRESS name)` → returns `int` (error code)
- `espeak_TextToPhonemes(ADDRESS textptr, int textmode, int phonememode)`
  → returns `ADDRESS` (pointer to phoneme string)
- `espeak_Terminate()` → returns `int`

For `espeak_TextToPhonemes`:
- `textptr` is `const void **` — pointer to pointer. The function
  advances the pointer past consumed text. Allocate in Arena, write the
  text pointer, pass the pointer-to-pointer.
- `phonememode = 0x02` for IPA output
- The returned string is in a static buffer — copy it before next call.
- Call in a loop until all text is consumed (textptr reaches end).

All public methods are `synchronized` on the instance.

- [ ] **Step 4: Run tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=EspeakLibraryTest`
Expected: PASS (if espeak-ng lib available) or SKIP

- [ ] **Step 5: Commit**

```bash
git add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/EspeakLibrary.java
git add speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/EspeakLibraryTest.java
git commit -m "feat(#186): add EspeakLibrary — espeak-ng C API FFM bindings"
```

---

## Batch 3: Integration — Provisioning + VitsTextToSpeech

After this batch: end-to-end TTS with phoneme timing works. Full
`TextToSpeechService` implementation.

### Task 5: Provisioner extensions — espeak-ng and TTS model provisioning

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java`
- Modify: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/ProvisionerTest.java`

**Interfaces:**
- Consumes: `ModelPatcher.patch(Path)` (from Task 2)
- Produces:
  - `Provisioner.ensureEspeak()` → `Path` to espeak-ng lib directory
  - `Provisioner.ensureTtsModel(String modelName)` → `Path` to model directory (calls ModelPatcher after extraction)

- [ ] **Step 1: Write failing tests**

```java
@Test
void espeakLibUrl_allPlatforms() {
    for (String platform : List.of("osx-arm64", "osx-x64", "linux-x64", "linux-arm64")) {
        assertThat(Provisioner.espeakLibUrl(platform)).isNotEmpty();
    }
}

@Test
void ttsModelUrl_constructsCorrectUrl() {
    String url = Provisioner.ttsModelUrl("vits-piper-en_US-lessac-medium");
    assertThat(url).contains("tts-models");
    assertThat(url).endsWith(".tar.bz2");
}

@Test
void ensureTtsModel_callsModelPatcher() {
    // Integration test with mock downloader (existing pattern from ProvisionerTest)
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=ProvisionerTest`
Expected: compilation failure for new methods

- [ ] **Step 3: Implement Provisioner extensions**

Add to `Provisioner.java`:

1. `ESPEAK_ASSETS` map — platform → archive filename
2. `ESPEAK_CHECKSUMS` map — platform → SHA-256
3. `ESPEAK_EXPECTED_FILES` map — platform → expected lib filename
4. `TTS_MODEL_CHECKSUMS` map — model name → SHA-256
5. `TTS_MODEL_EXPECTED_FILES` map — model name → expected ONNX filename
6. `ensureEspeak()` — same provisioning pattern as `ensureNativeLibrary()`
7. `ensureTtsModel(String modelName)` — provision model, then call
   `ModelPatcher.patch()` on the extracted directory
8. `espeakCacheDir()` — `~/.casehub/native/espeak-ng/<version>/<platform>/`

- [ ] **Step 4: Run tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=ProvisionerTest`
Expected: all PASS

- [ ] **Step 5: Commit**

```bash
git add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java
git add speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/ProvisionerTest.java
git commit -m "feat(#186): extend Provisioner — espeak-ng + TTS model provisioning"
```

### Task 6: VitsTextToSpeech — compose everything into TextToSpeechService

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VitsTextToSpeech.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VitsTextToSpeechTest.java`

**Interfaces:**
- Consumes:
  - `VitsConfig` (Task 1) — config parsing, tokenization
  - `ModelPatcher` (Task 2) — model patching check
  - `OnnxRuntimeLibrary` (Task 3) — inference
  - `EspeakLibrary` (Task 4) — phonemization
- Produces:
  - `VitsTextToSpeech(VitsConfig, OnnxRuntimeLibrary, EspeakLibrary)` — constructor
  - `VitsTextToSpeech.withDefaults()` — zero-install factory (provisions everything)
  - `synthesise(String text, SynthesisOptions options)` → `SynthesisResult`
  - Implements `TextToSpeechService`, `AutoCloseable`

- [ ] **Step 1: Write failing test — basic synthesis**

```java
@EnabledIf("allDepsAvailable")
class VitsTextToSpeechTest {

    @Test
    void synthesise_producesAudio() throws Exception {
        try (var tts = createTts()) {
            var result = tts.synthesise("hello", SynthesisOptions.defaults());
            assertThat(result.audioData()).isNotEmpty();
            assertThat(result.audioFormat()).isEqualTo("wav");
        }
    }

    @Test
    void synthesise_withPhonemes_returnsTimings() throws Exception {
        try (var tts = createTts()) {
            var options = new SynthesisOptions(null, null, "wav", true);
            var result = tts.synthesise("hello world", options);
            assertThat(result.phonemes()).isNotEmpty();
            // Every phoneme has valid timing
            for (var pt : result.phonemes()) {
                assertThat(pt.startMs()).isGreaterThanOrEqualTo(0);
                assertThat(pt.endMs()).isGreaterThanOrEqualTo(pt.startMs());
                assertThat(pt.phoneme()).isNotEmpty();
            }
        }
    }

    @Test
    void synthesise_durationSumMatchesAudioLength() throws Exception {
        try (var tts = createTts()) {
            var options = new SynthesisOptions(null, null, "wav", true);
            var result = tts.synthesise("hello", options);
            var phonemes = result.phonemes();
            if (!phonemes.isEmpty()) {
                long lastEnd = phonemes.getLast().endMs();
                // WAV header: 44 bytes, then 16-bit samples at sampleRate
                // Extract sample rate from WAV header (bytes 24-27, little-endian)
                byte[] wav = result.audioData();
                int sr = (wav[24] & 0xFF) | ((wav[25] & 0xFF) << 8)
                       | ((wav[26] & 0xFF) << 16) | ((wav[27] & 0xFF) << 24);
                int dataBytes = wav.length - 44;
                long audioMs = (long)(dataBytes / 2.0 / sr * 1000);
                assertThat(lastEnd).isCloseTo(audioMs, within(2L));
            }
        }
    }

    @Test
    void synthesise_withoutPhonemes_returnsEmptyList() throws Exception {
        try (var tts = createTts()) {
            var result = tts.synthesise("hello", SynthesisOptions.defaults());
            assertThat(result.phonemes()).isEmpty();
        }
    }

    @Test
    void synthesise_unpatchedModel_returnsEmptyPhonemes() throws Exception {
        // Create TTS with unpatched model
        try (var tts = createTtsUnpatched()) {
            var options = new SynthesisOptions(null, null, "wav", true);
            var result = tts.synthesise("hello", options);
            assertThat(result.audioData()).isNotEmpty();
            assertThat(result.phonemes()).isEmpty();
        }
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VitsTextToSpeechTest`
Expected: compilation failure

- [ ] **Step 3: Implement VitsTextToSpeech**

Key implementation flow:

```java
public final class VitsTextToSpeech implements TextToSpeechService, AutoCloseable {
    private final VitsConfig config;
    private final OnnxRuntimeLibrary ort;
    private final EspeakLibrary espeak;
    private final MemorySegment session;  // ORT session (reused)
    private final MemorySegment env;
    private final MemorySegment memInfo;
    private final boolean phonemeTimingAvailable;

    public VitsTextToSpeech(VitsConfig config, OnnxRuntimeLibrary ort,
                            EspeakLibrary espeak) {
        // Create ORT env, session from config.modelPath()
        // Query output count → set phonemeTimingAvailable
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        // 1. Phonemize via espeak
        String ipa = espeak.textToPhonemes(text, config.espeakVoice());
        // 2. Tokenize via config
        List<Integer> ids = config.tokenize(ipa);
        // 3. Build ORT input tensors (Arena.ofConfined per call)
        // 4. Run — request 1 or 2 outputs based on flags
        // 5. Read audio from output[0]
        // 6. If includePhonemes && phonemeTimingAvailable:
        //    read durations from output[1], compute timing
        // 7. Build SynthesisResult
    }

    @Override
    public void close() {
        // Release session, options, memInfo, env
    }
}
```

Duration computation (double precision, interspersed-aware):
```java
// interspersedIds has 2*N+1 elements; durFrames has 2*N+1 values
// Pad tokens at even indices, real tokens at odd indices
double cumulativeMs = 0.0;
for (int i = 0; i < interspersedIds.size(); i++) {
    double durationMs = durFrames[i] * 256.0 / sampleRate * 1000.0;
    int id = interspersedIds.get(i);
    boolean isPad = (id == padId);
    boolean isBos = (id == bosId);
    boolean isEos = (id == eosId);
    if (!isPad && !isBos && !isEos) {
        long startMs = Math.round(cumulativeMs);
        cumulativeMs += durationMs;
        long endMs = Math.round(cumulativeMs);
        if (endMs > startMs) { // skip zero-duration entries
            phonemes.add(new PhonemeTiming(
                config.idToPhoneme(id), startMs, endMs));
        } else {
            cumulativeMs += durationMs; // still advance time
        }
    } else {
        cumulativeMs += durationMs; // blanks contribute to timeline
    }
}
```

- [ ] **Step 4: Run all tests**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=VitsTextToSpeechTest`
Expected: all PASS (if all native deps available)

- [ ] **Step 5: Run full module test suite**

Run: `mvn --batch-mode test -pl speech-sherpa`
Expected: all existing tests still pass, new tests pass or skip

- [ ] **Step 6: Commit**

```bash
git add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/VitsTextToSpeech.java
git add speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/VitsTextToSpeechTest.java
git commit -m "feat(#186): add VitsTextToSpeech — TTS with exact phoneme timing"
```

### Task 7: CLAUDE.md + docs update

**Files:**
- Modify: `CLAUDE.md` (add VitsTextToSpeech, OnnxRuntimeLibrary, EspeakLibrary, VitsConfig, ModelPatcher to key directories and class tables)

- [ ] **Step 1: Update CLAUDE.md speech-sherpa section**

Add entries for all new classes to the `speech-sherpa/` key directories
table and the class descriptions.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs(#186): update CLAUDE.md — VitsTextToSpeech, phoneme timing classes"
```

---

## References

- [2026-08-26-phoneme-timing-extraction-design.md](../specs/issue-194-talking-avatar-tier1/2026-08-26-phoneme-timing-extraction-design.md) — design spec
- `SherpaLibrary.java` — existing tiered library loading pattern (template for OnnxRuntimeLibrary)
- `SherpaOnnxTextToSpeech.java` — existing TTS implementation (coexists with VitsTextToSpeech)
- `Provisioner.java` — existing provisioning infrastructure (extended in Task 5)
- `PhonemeTiming.java`, `SynthesisResult.java`, `SynthesisOptions.java` — API types (unchanged)
- [onnxruntime C API](https://github.com/microsoft/onnxruntime/blob/main/include/onnxruntime/core/session/onnxruntime_c_api.h) — vtable pattern
- [espeak-ng speak_lib.h](https://github.com/espeak-ng/espeak-ng/blob/master/src/include/espeak-ng/speak_lib.h) — C API signatures
- GitHub #186 — phoneme timing extraction issue
- GitHub #194 — talking avatar tier 1 epic
