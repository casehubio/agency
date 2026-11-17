# Qwen3-TTS Panama/FFM Shared Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #453 — Qwen3-TTS Panama/FFM shared library integration
**Issue group:** #453

**Goal:** Replace the HTTP subprocess approach for Qwen3-TTS with native shared library calls via Java FFM/Panama.

**Architecture:** Create `Qwen3Library` (singleton FFM binding) and `Qwen3Layouts` (C struct layouts) following the `SherpaLibrary`/`SherpaLayouts` pattern. Rewrite `Qwen3TextToSpeech` internals from HTTP to native calls. Simplify `Qwen3VoicePool` from process management to a voice-ref cache. Update `Provisioner` for GGUF models and shared library downloads.

**Tech Stack:** Java 22 FFM/Panama, GGML/qwentts.cpp (C99 ABI), Maven

## Global Constraints

- Java 22 minimum (stable FFM/Panama API)
- All work in `connectors/voice-sherpa` module except consumer updates in `agency/speech-demo`
- Package: `io.casehub.connectors.voice.sherpa`
- Pre-built `libqwen` binaries must exist at casehubio/qwentts.cpp GitHub releases (prerequisite — out of scope for this plan)
- GGUF models from `Serveurperso/Qwen3-TTS-GGUF` on HuggingFace
- Follow existing `SherpaLibrary`/`SherpaLayouts` patterns exactly

---

## Batch 1: FFM Binding Foundation

### Task 1: Qwen3Layouts — C struct layout constants

**Files:**
- Create: `voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3Layouts.java`
- Test: `voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3LayoutsTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `Qwen3Layouts.INIT_PARAMS_SIZE`, `Qwen3Layouts.TTS_PARAMS_SIZE`, `Qwen3Layouts.AUDIO_SIZE`, `Qwen3Layouts.VOICE_REF_SIZE`, field offset constants (`INIT_TALKER_PATH`, `INIT_CODEC_PATH`, `TTS_TEXT`, `TTS_LANG`, `TTS_REF_TEXT`, `TTS_REF_SPK_EMB`, `TTS_REF_CODES`, `AUDIO_SAMPLES`, `AUDIO_N_SAMPLES`, `AUDIO_SAMPLE_RATE`, `AUDIO_CHANNELS`, `VOICE_REF_SPK_EMB`, `VOICE_REF_CODES`, `VOICE_REF_N_CODES`, `VOICE_REF_N_CODEBOOKS`)

- [ ] **Step 1: Write the test**

```java
package io.casehub.connectors.voice.sherpa;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class Qwen3LayoutsTest {

    @Test
    void initParamsSize_isPositive() {
        assertThat(Qwen3Layouts.INIT_PARAMS_SIZE).isGreaterThan(0);
    }

    @Test
    void ttsParamsSize_isPositive() {
        assertThat(Qwen3Layouts.TTS_PARAMS_SIZE).isGreaterThan(0);
    }

    @Test
    void audioLayout_hasExpectedFieldOrder() {
        // samples pointer before n_samples, n_samples before sample_rate
        assertThat(Qwen3Layouts.AUDIO_SAMPLES).isLessThan(Qwen3Layouts.AUDIO_N_SAMPLES);
        assertThat(Qwen3Layouts.AUDIO_N_SAMPLES).isLessThan(Qwen3Layouts.AUDIO_SAMPLE_RATE);
        assertThat(Qwen3Layouts.AUDIO_SAMPLE_RATE).isLessThan(Qwen3Layouts.AUDIO_CHANNELS);
    }

    @Test
    void initParamsLayout_talkerBeforeCodec() {
        assertThat(Qwen3Layouts.INIT_TALKER_PATH).isLessThan(Qwen3Layouts.INIT_CODEC_PATH);
    }

    @Test
    void ttsParamsLayout_textBeforeLang() {
        assertThat(Qwen3Layouts.TTS_TEXT).isLessThan(Qwen3Layouts.TTS_LANG);
    }

    @Test
    void voiceRefLayout_hasExpectedFieldOrder() {
        assertThat(Qwen3Layouts.VOICE_REF_SPK_EMB).isLessThan(Qwen3Layouts.VOICE_REF_CODES);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3LayoutsTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: FAIL — `Qwen3Layouts` class does not exist

- [ ] **Step 3: Write the implementation**

```java
package io.casehub.connectors.voice.sherpa;

// Byte offsets into qwentts.cpp C structs (64-bit platforms).
// Derived from qwen.h in casehubio/qwentts.cpp.
// Zero-filled allocation — qwentts.cpp treats zero/NULL as defaults,
// matching qt_init_default_params() / qt_tts_default_params() behaviour.
final class Qwen3Layouts {

    private Qwen3Layouts() {}

    // === qt_init_params ===
    // struct qt_init_params {
    //     const char *talker_path;   // offset 0
    //     const char *codec_path;    // offset 8
    //     int n_threads;             // offset 16
    //     int n_gpu_layers;          // offset 20
    //     ... (padding/future fields)
    // };
    static final int INIT_PARAMS_SIZE = 256;
    static final long INIT_TALKER_PATH = 0;
    static final long INIT_CODEC_PATH  = 8;
    static final long INIT_N_THREADS   = 16;
    static final long INIT_N_GPU_LAYERS = 20;

    // === qt_tts_params ===
    // struct qt_tts_params {
    //     const char *text;          // offset 0
    //     const char *lang;          // offset 8
    //     const char *ref_text;      // offset 16  (ICL voice cloning transcript)
    //     const float *ref_spk_emb;  // offset 24  (speaker embedding)
    //     int ref_spk_emb_size;      // offset 32
    //     const int *ref_codes;      // offset 40  (RVQ codes matrix)
    //     int ref_n_codes;           // offset 48  (number of code frames)
    //     int ref_n_codebooks;       // offset 52  (number of codebooks)
    //     float temperature;         // offset 56
    //     float top_p;               // offset 60
    //     int top_k;                 // offset 64
    //     int seed;                  // offset 68
    //     ... (padding/future fields)
    // };
    static final int TTS_PARAMS_SIZE = 256;
    static final long TTS_TEXT           = 0;
    static final long TTS_LANG           = 8;
    static final long TTS_REF_TEXT       = 16;
    static final long TTS_REF_SPK_EMB    = 24;
    static final long TTS_REF_SPK_EMB_SIZE = 32;
    static final long TTS_REF_CODES      = 40;
    static final long TTS_REF_N_CODES    = 48;
    static final long TTS_REF_N_CODEBOOKS = 52;
    static final long TTS_TEMPERATURE    = 56;
    static final long TTS_TOP_P          = 60;
    static final long TTS_TOP_K          = 64;
    static final long TTS_SEED           = 68;

    // === qt_audio ===
    // struct qt_audio {
    //     float *samples;            // offset 0
    //     int n_samples;             // offset 8
    //     int sample_rate;           // offset 12
    //     int channels;              // offset 16
    // };
    static final int AUDIO_SIZE = 24;
    static final long AUDIO_SAMPLES     = 0;
    static final long AUDIO_N_SAMPLES   = 8;
    static final long AUDIO_SAMPLE_RATE = 12;
    static final long AUDIO_CHANNELS    = 16;

    // === qt_voice_ref ===
    // struct qt_voice_ref {
    //     float *spk_emb;            // offset 0
    //     int spk_emb_size;          // offset 8
    //     int *codes;                // offset 16  (code matrix)
    //     int n_codes;               // offset 24  (frames)
    //     int n_codebooks;           // offset 28
    // };
    static final int VOICE_REF_SIZE = 32;
    static final long VOICE_REF_SPK_EMB      = 0;
    static final long VOICE_REF_SPK_EMB_SIZE = 8;
    static final long VOICE_REF_CODES        = 16;
    static final long VOICE_REF_N_CODES      = 24;
    static final long VOICE_REF_N_CODEBOOKS  = 28;
}
```

Note: The exact byte offsets MUST be verified against the actual `qwen.h` header from casehubio/qwentts.cpp once the fork is built. Use `offsetof()` in a C test program or check `tests/abi-c.c` in the fork. The values above are derived from the documented struct fields assuming standard C struct packing on 64-bit platforms (pointer=8 bytes, int=4 bytes, float=4 bytes).

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3LayoutsTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/connectors add voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3Layouts.java voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3LayoutsTest.java
git -C /Users/mdproctor/claude/casehub/connectors commit -m "feat(#453): add Qwen3Layouts — C struct byte offsets for qwentts.cpp FFM binding

Refs casehubio/agency#453"
```

### Task 2: Qwen3Library — singleton FFM binding

**Files:**
- Create: `voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3Library.java`
- Test: `voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3LibraryTest.java`

**Interfaces:**
- Consumes: `Qwen3Layouts` (struct sizes for allocation)
- Produces: `Qwen3Library.load()` → `Qwen3Library` singleton, `Qwen3Library.isAvailable()` → boolean, `Qwen3Library.load(Path)` → `Qwen3Library`. MethodHandle fields: `initDefaultParams`, `init`, `ttsDefaultParams`, `synthesize`, `synthesizeStreaming`, `extractVoiceRef`, `audioFree`, `voiceRefFree`, `free`. Constants: `VERSION`, `defaultCacheDir()`, `platformId()`, `qwenLibName()`.

- [ ] **Step 1: Write the test**

```java
package io.casehub.connectors.voice.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;

class Qwen3LibraryTest {

    @Test
    void platformId_returnsValidPlatform() {
        String pid = Qwen3Library.platformId();
        assertThat(pid).matches("(osx|linux|win)-(arm64|x64)");
    }

    @Test
    void qwenLibName_matchesPlatformConvention() {
        String name = Qwen3Library.qwenLibName();
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) {
            assertThat(name).isEqualTo("libqwen.dylib");
        } else if (os.contains("win")) {
            assertThat(name).isEqualTo("qwen.dll");
        } else {
            assertThat(name).isEqualTo("libqwen.so");
        }
    }

    @Test
    void defaultCacheDir_containsVersionAndPlatform() {
        var dir = Qwen3Library.defaultCacheDir();
        assertThat(dir.toString()).contains("qwen3-tts");
        assertThat(dir.toString()).contains(Qwen3Library.VERSION);
        assertThat(dir.toString()).contains(Qwen3Library.platformId());
    }

    @Test
    void isAvailable_returnsFalseWhenLibraryNotInstalled() {
        // On CI / dev machines without libqwen, this should return false not throw
        // (unless the library happens to be installed, in which case true is also correct)
        assertThatCode(() -> Qwen3Library.isAvailable()).doesNotThrowAnyException();
    }

    @Test
    @EnabledIfSystemProperty(named = "casehub.speech.integration-tests", matches = "true")
    void load_resolvesAllMethodHandles() {
        var lib = Qwen3Library.load();
        assertThat(lib).isNotNull();
        assertThat(lib.init).isNotNull();
        assertThat(lib.synthesize).isNotNull();
        assertThat(lib.synthesizeStreaming).isNotNull();
        assertThat(lib.extractVoiceRef).isNotNull();
        assertThat(lib.audioFree).isNotNull();
        assertThat(lib.voiceRefFree).isNotNull();
        assertThat(lib.free).isNotNull();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3LibraryTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: FAIL — `Qwen3Library` class does not exist

- [ ] **Step 3: Write the implementation**

```java
package io.casehub.connectors.voice.sherpa;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

public final class Qwen3Library {

    static final String VERSION = "0.1.0";

    private static volatile Qwen3Library INSTANCE;

    private final SymbolLookup lookup;

    final MethodHandle initDefaultParams;
    final MethodHandle init;
    final MethodHandle ttsDefaultParams;
    final MethodHandle synthesize;
    final MethodHandle synthesizeStreaming;
    final MethodHandle extractVoiceRef;
    final MethodHandle audioFree;
    final MethodHandle voiceRefFree;
    final MethodHandle free;

    private Qwen3Library(SymbolLookup lookup) {
        this.lookup = lookup;
        Linker linker = Linker.nativeLinker();

        initDefaultParams = downcall(linker, "qt_init_default_params",
                FunctionDescriptor.ofVoid(ADDRESS));
        init = downcall(linker, "qt_init",
                FunctionDescriptor.of(ADDRESS, ADDRESS));
        ttsDefaultParams = downcall(linker, "qt_tts_default_params",
                FunctionDescriptor.ofVoid(ADDRESS));
        synthesize = downcall(linker, "qt_synthesize",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
        synthesizeStreaming = downcall(linker, "qt_synthesize_streaming",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS));
        extractVoiceRef = downcall(linker, "qt_extract_voice_ref",
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS));
        audioFree = downcall(linker, "qt_audio_free",
                FunctionDescriptor.ofVoid(ADDRESS));
        voiceRefFree = downcall(linker, "qt_voice_ref_free",
                FunctionDescriptor.ofVoid(ADDRESS));
        free = downcall(linker, "qt_free",
                FunctionDescriptor.ofVoid(ADDRESS));
    }

    public static Qwen3Library load() {
        if (INSTANCE != null) { return INSTANCE; }
        synchronized (Qwen3Library.class) {
            if (INSTANCE != null) { return INSTANCE; }

            // Tier 1: system library path
            try {
                SymbolLookup lookup = SymbolLookup.libraryLookup("qwen", Arena.global());
                INSTANCE = new Qwen3Library(lookup);
                return INSTANCE;
            } catch (IllegalArgumentException | UnsatisfiedLinkError ignored) {}

            // Tier 2: local cache
            Path cacheDir = defaultCacheDir();
            if (Files.isDirectory(cacheDir)) {
                Path lib = cacheDir.resolve(qwenLibName());
                if (Files.exists(lib)) {
                    SymbolLookup lookup = SymbolLookup.libraryLookup(lib, Arena.global());
                    INSTANCE = new Qwen3Library(lookup);
                    return INSTANCE;
                }
            }

            // Tier 3: auto-download
            if (Provisioner.isAutoDownloadEnabled()) {
                Path downloadedDir = Provisioner.ensureQwen3SharedLibrary();
                Path lib = downloadedDir.resolve(qwenLibName());
                if (Files.exists(lib)) {
                    SymbolLookup lookup = SymbolLookup.libraryLookup(lib, Arena.global());
                    INSTANCE = new Qwen3Library(lookup);
                    return INSTANCE;
                }
            }

            throw new UnsatisfiedLinkError(
                    "libqwen native library not found. Install it system-wide or place "
                    + qwenLibName() + " in " + defaultCacheDir());
        }
    }

    static Qwen3Library load(Path libraryPath) {
        SymbolLookup lookup = SymbolLookup.libraryLookup(libraryPath, Arena.global());
        return new Qwen3Library(lookup);
    }

    static boolean isAvailable() {
        try {
            load();
            return true;
        } catch (UnsatisfiedLinkError | IllegalArgumentException e) {
            return false;
        }
    }

    private MethodHandle downcall(Linker linker, String name, FunctionDescriptor descriptor) {
        Optional<MemorySegment> symbol = lookup.find(name);
        if (symbol.isEmpty()) {
            throw new UnsatisfiedLinkError("Symbol not found in libqwen: " + name);
        }
        return linker.downcallHandle(symbol.get(), descriptor);
    }

    static Path defaultCacheDir() {
        return Path.of(System.getProperty("user.home"), ".casehub", "native",
                "qwen3-tts", VERSION, platformId());
    }

    static String platformId() {
        String os   = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "");
        String osKey   = os.contains("mac") ? "osx" : os.contains("linux") ? "linux" : "win";
        String archKey = arch.equals("aarch64") || arch.equals("arm64") ? "arm64" : "x64";
        return osKey + "-" + archKey;
    }

    static String qwenLibName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac")) { return "libqwen.dylib"; }
        if (os.contains("win")) { return "qwen.dll"; }
        return "libqwen.so";
    }
}
```

Note: The exact `FunctionDescriptor` signatures for `qt_synthesize_streaming` and `qt_extract_voice_ref` depend on the final C API from casehubio/qwentts.cpp. The callback in `qt_synthesize_streaming` uses an upcall stub — the fourth parameter is a function pointer `ADDRESS`. Adjust descriptors once `qwen.h` is finalized.

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3LibraryTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: PASS (non-integration tests pass without native library)

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/connectors add voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3Library.java voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3LibraryTest.java
git -C /Users/mdproctor/claude/casehub/connectors commit -m "feat(#453): add Qwen3Library — FFM/Panama binding for libqwen shared library

3-tier loading (system → cache → auto-download), same pattern as SherpaLibrary.
9 MethodHandle fields for all qt_* functions including streaming.

Refs casehubio/agency#453"
```

## Batch 2: Provisioner Updates

### Task 3: Update Provisioner — shared library + GGUF model downloads

**Files:**
- Modify: `voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Provisioner.java`
- Test: `voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/ProvisionerTest.java` (existing — add new tests)

**Interfaces:**
- Consumes: `Qwen3Library.VERSION`, `Qwen3Library.platformId()`, `Qwen3Library.qwenLibName()`
- Produces: `Provisioner.ensureQwen3SharedLibrary()` → `Path` (directory containing `libqwen.{so,dylib,dll}`), updated `Provisioner.ensureQwen3Model(String variant)` → `Path` (directory containing GGUF files)

- [ ] **Step 1: Write tests for new provisioning methods**

```java
// Add to existing ProvisionerTest.java

@Test
void ensureQwen3SharedLibrary_cacheDirContainsVersionAndPlatform() {
    Path dir = Provisioner.qwen3SharedLibDir();
    assertThat(dir.toString()).contains("qwen3-tts");
    assertThat(dir.toString()).contains(Qwen3Library.VERSION);
    assertThat(dir.toString()).contains(Qwen3Library.platformId());
}

@Test
void qwen3GgufModelDir_containsVariant() {
    Path dir = Provisioner.qwen3GgufModelDir("0.6b-q8");
    assertThat(dir.toString()).contains("qwen3-tts");
    assertThat(dir.toString()).contains("0.6b-q8");
}

@Test
void ensureQwen3Model_rejectsUnknownVariant() {
    assertThatThrownBy(() -> Provisioner.ensureQwen3Model("nonexistent"))
            .isInstanceOf(SherpaException.class)
            .hasMessageContaining("Unknown");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl voice-sherpa -Dtest=ProvisionerTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: FAIL — new methods/assertions don't exist yet

- [ ] **Step 3: Add shared library provisioning**

Add to `Provisioner.java` after the existing Qwen3 section (replacing `ensureQwen3Binary` and updating `ensureQwen3Model`):

```java
// --- Qwen3-TTS shared library ---

private static final String QWEN3_LIB_BASE_URL =
    "https://github.com/casehubio/qwentts.cpp/releases/download/";

private static final Map<String, String> QWEN3_LIB_ASSETS = Map.of(
        "osx-arm64",   "libqwen-osx-arm64.tar.gz",
        "osx-x64",     "libqwen-osx-x64.tar.gz",
        "linux-x64",   "libqwen-linux-x64.tar.gz",
        "linux-arm64",  "libqwen-linux-arm64.tar.gz"
);

private static final Map<String, String> QWEN3_LIB_CHECKSUMS = Map.of(
        // Checksums populated after first release build
        "osx-arm64",   "",
        "osx-x64",     "",
        "linux-x64",   "",
        "linux-arm64",  ""
);

// GGUF model variants: variant → {talker filename, codec filename}
private static final Map<String, String> QWEN3_GGUF_VARIANTS = Map.of(
        "0.6b-q8",  "qwen3-tts-0.6B-q8_0.gguf",
        "0.6b-q4",  "qwen3-tts-0.6B-q4_k_m.gguf",
        "1.7b-q8",  "qwen3-tts-1.7B-q8_0.gguf"
);
private static final String QWEN3_CODEC_GGUF = "qwen3-tts-codec-q8_0.gguf";
private static final String QWEN3_GGUF_REPO = "Serveurperso/Qwen3-TTS-GGUF";

static Path qwen3SharedLibDir() {
    return cacheBaseDir().resolve("native").resolve("qwen3-tts")
                         .resolve(Qwen3Library.VERSION).resolve(Qwen3Library.platformId());
}

public static Path ensureQwen3SharedLibrary() {
    String platformId = Qwen3Library.platformId();
    String asset = QWEN3_LIB_ASSETS.get(platformId);
    if (asset == null) {
        throw new SherpaException("No Qwen3 shared library for platform: " + platformId
                                  + ". Supported: " + QWEN3_LIB_ASSETS.keySet());
    }
    Path targetDir = qwen3SharedLibDir();
    Path lib = targetDir.resolve(Qwen3Library.qwenLibName());
    if (Files.exists(lib)) { return targetDir; }

    synchronized (NATIVE_LOCK) {
        if (Files.exists(lib)) { return targetDir; }
        String url = QWEN3_LIB_BASE_URL + "v" + Qwen3Library.VERSION + "/" + asset;
        String expectedHash = QWEN3_LIB_CHECKSUMS.get(platformId);
        LOG.log(System.Logger.Level.INFO, "Downloading Qwen3 shared library: {0}", url);
        provision(url, targetDir, expectedHash, 0, Qwen3Library.qwenLibName());
        LOG.log(System.Logger.Level.INFO, "Qwen3 shared library ready at {0}", targetDir);
        return targetDir;
    }
}

static Path qwen3GgufModelDir(String variant) {
    return cacheBaseDir().resolve("models").resolve("qwen3-tts-gguf").resolve(variant);
}
```

- [ ] **Step 4: Update `ensureQwen3Model` for GGUF**

Replace the existing `ensureQwen3Model` method body:

```java
public static Path ensureQwen3Model(String variant) {
    String talkerFile = QWEN3_GGUF_VARIANTS.get(variant);
    if (talkerFile == null) {
        throw new SherpaException("Unknown Qwen3-TTS variant: " + variant
                                  + ". Known: " + QWEN3_GGUF_VARIANTS.keySet());
    }
    Path targetDir = qwen3GgufModelDir(variant);
    if (Files.isDirectory(targetDir)
            && Files.exists(targetDir.resolve(talkerFile))
            && Files.exists(targetDir.resolve(QWEN3_CODEC_GGUF))) {
        return targetDir;
    }
    synchronized (MODEL_LOCK) {
        if (Files.isDirectory(targetDir)
                && Files.exists(targetDir.resolve(talkerFile))
                && Files.exists(targetDir.resolve(QWEN3_CODEC_GGUF))) {
            return targetDir;
        }
        provisionFromHuggingFace(QWEN3_GGUF_REPO,
                java.util.List.of(talkerFile, QWEN3_CODEC_GGUF), targetDir);
        return targetDir;
    }
}
```

- [ ] **Step 5: Remove old CLI binary provisioning**

Remove from `Provisioner.java`:
- `QWEN3_TTS_VERSION` constant
- `QWEN3_BINARY_ASSETS` map
- `QWEN3_MODEL_REPOS` map (safetensors repos)
- `QWEN3_MODEL_FILES` list (safetensors files)
- `ensureQwen3Binary()` method
- `qwen3BinaryDir()` method
- Old `qwen3ModelDir()` method (replaced by `qwen3GgufModelDir`)

- [ ] **Step 6: Run tests to verify they pass**

Run: `mvn test -pl voice-sherpa -Dtest=ProvisionerTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/connectors add voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Provisioner.java voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/ProvisionerTest.java
git -C /Users/mdproctor/claude/casehub/connectors commit -m "feat(#453): update Provisioner — shared library + GGUF model downloads

Replace CLI binary provisioning (ensureQwen3Binary) with shared library
provisioning (ensureQwen3SharedLibrary). Switch model format from
HuggingFace safetensors to GGUF from Serveurperso/Qwen3-TTS-GGUF.

Refs casehubio/agency#453"
```

## Batch 3: Qwen3TextToSpeech Rewrite + VoicePool Simplification

### Task 4: Rewrite Qwen3TextToSpeech — FFM-based synthesis

**Files:**
- Modify: `voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3TextToSpeech.java`
- Test: `voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3TextToSpeechTest.java` (create new)

**Interfaces:**
- Consumes: `Qwen3Library` (load, init, synthesize, synthesizeStreaming, free, audioFree), `Qwen3Layouts` (struct offsets), `Provisioner.ensureQwen3SharedLibrary()`, `Provisioner.ensureQwen3Model(String)`
- Produces: `Qwen3TextToSpeech implements StreamingTextToSpeech, AutoCloseable` — same public API as before. `withDefaults(String speaker)`, `withDefaults(String speaker, String variant)`, `synthesise(String, SynthesisOptions)` → `SynthesisResult`, `synthesiseStreaming(String, SynthesisOptions, Consumer<byte[]>)`, `close()`

- [ ] **Step 1: Write test for batch synthesis**

```java
package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.model.SynthesisOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import static org.assertj.core.api.Assertions.*;

class Qwen3TextToSpeechTest {

    @Test
    void wrapWav_producesValidWavHeader() {
        byte[] pcm = new byte[48000]; // 1 second of silence at 24kHz 16-bit
        byte[] wav = Qwen3TextToSpeech.wrapWav(pcm);
        assertThat(wav).hasSizeGreaterThan(44);
        // RIFF header
        assertThat(new String(wav, 0, 4)).isEqualTo("RIFF");
        assertThat(new String(wav, 8, 4)).isEqualTo("WAVE");
        assertThat(new String(wav, 12, 4)).isEqualTo("fmt ");
    }

    @Test
    void floatPcmToS16le_convertsCorrectly() {
        float[] samples = {0.0f, 0.5f, -0.5f, 1.0f, -1.0f};
        byte[] s16 = Qwen3TextToSpeech.floatPcmToS16le(samples, samples.length);
        assertThat(s16).hasSize(samples.length * 2);
        // 0.0 → 0x0000
        assertThat(s16[0]).isEqualTo((byte) 0);
        assertThat(s16[1]).isEqualTo((byte) 0);
    }

    @Test
    @EnabledIfSystemProperty(named = "casehub.speech.integration-tests", matches = "true")
    void synthesise_producesWavOutput() {
        try (var tts = Qwen3TextToSpeech.withDefaults("ryan", "0.6b-q8")) {
            var result = tts.synthesise("Hello world", SynthesisOptions.defaults());
            assertThat(result.audioData()).isNotEmpty();
            assertThat(result.format()).isEqualTo("wav");
            // WAV header check
            assertThat(new String(result.audioData(), 0, 4)).isEqualTo("RIFF");
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "casehub.speech.integration-tests", matches = "true")
    void synthesiseStreaming_deliversChunks() {
        try (var tts = Qwen3TextToSpeech.withDefaults("ryan", "0.6b-q8")) {
            var chunks = new java.util.ArrayList<byte[]>();
            tts.synthesiseStreaming("Hello streaming world",
                    SynthesisOptions.defaults(), chunks::add);
            assertThat(chunks).isNotEmpty();
            for (byte[] chunk : chunks) {
                assertThat(new String(chunk, 0, 4)).isEqualTo("RIFF");
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3TextToSpeechTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: FAIL — `wrapWav` is private, `floatPcmToS16le` doesn't exist

- [ ] **Step 3: Rewrite Qwen3TextToSpeech**

Replace entire class body:

```java
package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.StreamingTextToSpeech;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static java.lang.foreign.ValueLayout.*;

public class Qwen3TextToSpeech implements StreamingTextToSpeech, AutoCloseable {

    private static final System.Logger LOG = System.getLogger("casehub-speech");
    private static final int SAMPLE_RATE = 24000;

    private final Qwen3Library lib;
    private final MemorySegment context; // qt_context* — lives in global arena
    private final String speaker;

    Qwen3TextToSpeech(Qwen3Library lib, MemorySegment context, String speaker) {
        this.lib = lib;
        this.context = context;
        this.speaker = speaker;
    }

    public static Qwen3TextToSpeech withDefaults(String speaker) {
        return withDefaults(speaker, "0.6b-q8");
    }

    public static Qwen3TextToSpeech withDefaults(String speaker, String variant) {
        Path modelDir = Provisioner.ensureQwen3Model(variant);
        Qwen3Library lib = Qwen3Library.load();
        MemorySegment ctx = initContext(lib, modelDir, variant);
        LOG.log(System.Logger.Level.INFO, "Qwen3-TTS ready (variant={0}, speaker={1})", variant, speaker);
        return new Qwen3TextToSpeech(lib, ctx, speaker);
    }

    private static MemorySegment initContext(Qwen3Library lib, Path modelDir, String variant) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment params = arena.allocate(Qwen3Layouts.INIT_PARAMS_SIZE);
            params.fill((byte) 0);
            lib.initDefaultParams.invokeExact(params);

            String talkerFile = modelDir.resolve(
                    "qwen3-tts-" + variant.replace("-q", "-q").toUpperCase()
                            .replace("0.6B", "0.6B").replace("1.7B", "1.7B")
                    + ".gguf").toString();
            // Simplified: use the variant to find the right file
            java.nio.file.DirectoryStream<Path> stream =
                    java.nio.file.Files.newDirectoryStream(modelDir, "*.gguf");
            String talkerPath = null;
            String codecPath = null;
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (name.contains("codec")) { codecPath = p.toString(); }
                else { talkerPath = p.toString(); }
            }
            stream.close();
            if (talkerPath == null || codecPath == null) {
                throw new SherpaException("GGUF model files not found in " + modelDir);
            }

            MemorySegment talkerStr = arena.allocateFrom(talkerPath);
            MemorySegment codecStr = arena.allocateFrom(codecPath);
            params.set(ADDRESS, Qwen3Layouts.INIT_TALKER_PATH, talkerStr);
            params.set(ADDRESS, Qwen3Layouts.INIT_CODEC_PATH, codecStr);

            MemorySegment ctx = (MemorySegment) lib.init.invokeExact(params);
            if (ctx.equals(MemorySegment.NULL)) {
                throw new SherpaException("qt_init failed — check model paths: "
                                          + talkerPath + ", " + codecPath);
            }
            return ctx.reinterpret(Long.MAX_VALUE);
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable e) {
            throw new SherpaException("Failed to initialize Qwen3-TTS context: " + e.getMessage(), e);
        }
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ttsParams = arena.allocate(Qwen3Layouts.TTS_PARAMS_SIZE);
            ttsParams.fill((byte) 0);
            lib.ttsDefaultParams.invokeExact(ttsParams);

            ttsParams.set(ADDRESS, Qwen3Layouts.TTS_TEXT, arena.allocateFrom(text));
            ttsParams.set(ADDRESS, Qwen3Layouts.TTS_LANG, arena.allocateFrom("en"));

            MemorySegment audio = arena.allocate(Qwen3Layouts.AUDIO_SIZE);
            audio.fill((byte) 0);

            long start = System.currentTimeMillis();
            int status = (int) lib.synthesize.invokeExact(context, ttsParams, audio);
            if (status != 0) {
                throw new SherpaException("qt_synthesize failed with status " + status);
            }

            MemorySegment samplesPtr = audio.get(ADDRESS, Qwen3Layouts.AUDIO_SAMPLES)
                    .reinterpret(Long.MAX_VALUE);
            int nSamples = audio.get(JAVA_INT, Qwen3Layouts.AUDIO_N_SAMPLES);
            int sampleRate = audio.get(JAVA_INT, Qwen3Layouts.AUDIO_SAMPLE_RATE);

            byte[] pcmS16 = floatPcmToS16le(samplesPtr, nSamples);
            byte[] wavBytes = wrapWav(pcmS16);

            lib.audioFree.invokeExact(audio);

            long elapsed = System.currentTimeMillis() - start;
            LOG.log(System.Logger.Level.INFO,
                    "Qwen3-TTS [{0}]: {1} samples ({2} Hz) in {3}ms",
                    speaker, nSamples, sampleRate, elapsed);

            return new SynthesisResult(wavBytes, "wav", List.of());
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable e) {
            throw new SherpaException("Qwen3-TTS synthesis failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void synthesiseStreaming(String text, SynthesisOptions options,
                                    Consumer<byte[]> onWavChunk) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ttsParams = arena.allocate(Qwen3Layouts.TTS_PARAMS_SIZE);
            ttsParams.fill((byte) 0);
            lib.ttsDefaultParams.invokeExact(ttsParams);

            ttsParams.set(ADDRESS, Qwen3Layouts.TTS_TEXT, arena.allocateFrom(text));
            ttsParams.set(ADDRESS, Qwen3Layouts.TTS_LANG, arena.allocateFrom("en"));

            // Create upcall stub for streaming callback
            var callbackDesc = FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS);
            MemorySegment callback = Linker.nativeLinker().upcallStub(
                    java.lang.invoke.MethodHandles.lookup()
                            .bind(new StreamingCallback(onWavChunk),
                                    "onChunk",
                                    java.lang.invoke.MethodType.methodType(
                                            void.class, MemorySegment.class, int.class, MemorySegment.class)),
                    callbackDesc,
                    arena);

            long start = System.currentTimeMillis();
            int status = (int) lib.synthesizeStreaming.invokeExact(
                    context, ttsParams, callback, MemorySegment.NULL);
            if (status != 0) {
                throw new SherpaException("qt_synthesize_streaming failed with status " + status);
            }

            long elapsed = System.currentTimeMillis() - start;
            LOG.log(System.Logger.Level.INFO,
                    "Qwen3-TTS streaming [{0}]: completed in {1}ms", speaker, elapsed);
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable e) {
            throw new SherpaException("Qwen3-TTS streaming failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        try {
            lib.free.invokeExact(context);
        } catch (Throwable e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to free Qwen3 context: {0}", e.getMessage());
        }
    }

    static byte[] floatPcmToS16le(MemorySegment samplesPtr, int nSamples) {
        byte[] result = new byte[nSamples * 2];
        for (int i = 0; i < nSamples; i++) {
            float sample = samplesPtr.getAtIndex(JAVA_FLOAT, i);
            short s16 = (short) Math.max(-32768, Math.min(32767, (int) (sample * 32767)));
            result[i * 2]     = (byte) (s16 & 0xFF);
            result[i * 2 + 1] = (byte) ((s16 >> 8) & 0xFF);
        }
        return result;
    }

    static byte[] floatPcmToS16le(float[] samples, int nSamples) {
        byte[] result = new byte[nSamples * 2];
        for (int i = 0; i < nSamples; i++) {
            short s16 = (short) Math.max(-32768, Math.min(32767, (int) (samples[i] * 32767)));
            result[i * 2]     = (byte) (s16 & 0xFF);
            result[i * 2 + 1] = (byte) ((s16 >> 8) & 0xFF);
        }
        return result;
    }

    static byte[] wrapWav(byte[] pcmS16le) {
        int dataLen = pcmS16le.length;
        ByteBuffer header = ByteBuffer.allocate(44 + dataLen).order(ByteOrder.LITTLE_ENDIAN);
        header.put("RIFF".getBytes());
        header.putInt(36 + dataLen);
        header.put("WAVE".getBytes());
        header.put("fmt ".getBytes());
        header.putInt(16);
        header.putShort((short) 1);
        header.putShort((short) 1);
        header.putInt(SAMPLE_RATE);
        header.putInt(SAMPLE_RATE * 2);
        header.putShort((short) 2);
        header.putShort((short) 16);
        header.put("data".getBytes());
        header.putInt(dataLen);
        header.put(pcmS16le);
        return header.array();
    }

    // Inner class for streaming callback
    private static class StreamingCallback {
        private final Consumer<byte[]> onWavChunk;

        StreamingCallback(Consumer<byte[]> onWavChunk) {
            this.onWavChunk = onWavChunk;
        }

        @SuppressWarnings("unused") // called via upcall stub
        void onChunk(MemorySegment samples, int nSamples, MemorySegment userdata) {
            byte[] pcm = floatPcmToS16le(samples.reinterpret((long) nSamples * JAVA_FLOAT.byteSize()), nSamples);
            onWavChunk.accept(wrapWav(pcm));
        }
    }

    // Package-private for voice cloning support
    void setVoiceRef(MemorySegment ttsParams, MemorySegment voiceRef) {
        MemorySegment spkEmb = voiceRef.get(ADDRESS, Qwen3Layouts.VOICE_REF_SPK_EMB);
        int spkEmbSize = voiceRef.get(JAVA_INT, Qwen3Layouts.VOICE_REF_SPK_EMB_SIZE);
        MemorySegment codes = voiceRef.get(ADDRESS, Qwen3Layouts.VOICE_REF_CODES);
        int nCodes = voiceRef.get(JAVA_INT, Qwen3Layouts.VOICE_REF_N_CODES);
        int nCodebooks = voiceRef.get(JAVA_INT, Qwen3Layouts.VOICE_REF_N_CODEBOOKS);

        ttsParams.set(ADDRESS, Qwen3Layouts.TTS_REF_SPK_EMB, spkEmb);
        ttsParams.set(JAVA_INT, Qwen3Layouts.TTS_REF_SPK_EMB_SIZE, spkEmbSize);
        ttsParams.set(ADDRESS, Qwen3Layouts.TTS_REF_CODES, codes);
        ttsParams.set(JAVA_INT, Qwen3Layouts.TTS_REF_N_CODES, nCodes);
        ttsParams.set(JAVA_INT, Qwen3Layouts.TTS_REF_N_CODEBOOKS, nCodebooks);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3TextToSpeechTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: PASS (unit tests pass; integration tests skipped without system property)

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/connectors add voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3TextToSpeech.java voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3TextToSpeechTest.java
git -C /Users/mdproctor/claude/casehub/connectors commit -m "feat(#453): rewrite Qwen3TextToSpeech — FFM native calls replace HTTP

Eliminates HttpClient, ProcessBuilder, and JSON serialization.
Batch synthesis via qt_synthesize, streaming via qt_synthesize_streaming
upcall stub. Same StreamingTextToSpeech public API.

Refs casehubio/agency#453"
```

### Task 5: Simplify Qwen3VoicePool + update Qwen3ClonedVoice + update Qwen3VoicePoolConfig

**Files:**
- Modify: `voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePool.java`
- Modify: `voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3ClonedVoice.java`
- Modify: `voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePoolConfig.java`
- Test: `voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePoolTest.java` (create new)

**Interfaces:**
- Consumes: `Qwen3Library` (extractVoiceRef, voiceRefFree), `Qwen3Layouts` (VOICE_REF_SIZE), `Qwen3TextToSpeech` (constructor, setVoiceRef)
- Produces: `Qwen3VoicePool.createProvisioned(Qwen3VoicePoolConfig)` → `Qwen3VoicePool`, `Qwen3VoicePool.availableVoices()` → `List<String>`, `Qwen3VoicePool.forVoice(String)` → `StreamingTextToSpeech`, `Qwen3VoicePool.close()`

- [ ] **Step 1: Write test**

```java
package io.casehub.connectors.voice.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.*;

class Qwen3VoicePoolTest {

    @Test
    void discoverVoices_findsWavFiles(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("emma.wav"), new byte[44]);
        Files.write(dir.resolve("james.wav"), new byte[44]);
        Files.write(dir.resolve("readme.txt"), new byte[10]);

        var config = new Qwen3VoicePoolConfig(dir, "0.6b-q8");
        var voices = Qwen3VoicePool.discoverVoices(config);
        assertThat(voices).containsOnlyKeys("emma", "james");
    }

    @Test
    void discoverVoices_emptyDirReturnsEmpty(@TempDir Path dir) {
        var config = new Qwen3VoicePoolConfig(dir, "0.6b-q8");
        var voices = Qwen3VoicePool.discoverVoices(config);
        assertThat(voices).isEmpty();
    }

    @Test
    void forVoice_throwsOnUnknownVoice(@TempDir Path dir) {
        var config = new Qwen3VoicePoolConfig(dir, "0.6b-q8");
        var pool = Qwen3VoicePool.create(config);
        assertThatThrownBy(() -> pool.forVoice("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown voice");
    }

    @Test
    void availableVoices_returnsSorted(@TempDir Path dir) throws IOException {
        Files.write(dir.resolve("zara.wav"), new byte[44]);
        Files.write(dir.resolve("anna.wav"), new byte[44]);
        Files.write(dir.resolve("mike.wav"), new byte[44]);

        var config = new Qwen3VoicePoolConfig(dir, "0.6b-q8");
        var pool = Qwen3VoicePool.create(config);
        assertThat(pool.availableVoices()).containsExactly("anna", "mike", "zara");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3VoicePoolTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: FAIL — constructor signature changed, discoverVoices looks for .qvoice not .wav

- [ ] **Step 3: Update Qwen3VoicePoolConfig**

Remove `maxActive` and `basePort` (no longer needed — no process pool):

```java
package io.casehub.connectors.voice.sherpa;

import java.nio.file.Path;

public record Qwen3VoicePoolConfig(
    Path voiceDir,
    String modelVariant
) {
    public static Qwen3VoicePoolConfig defaults() {
        return new Qwen3VoicePoolConfig(
            Path.of(System.getProperty("user.home"), ".casehub", "voice-samples"),
            "0.6b-q8");
    }
}
```

- [ ] **Step 4: Rewrite Qwen3VoicePool**

Replace entire class:

```java
package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.spi.StreamingTextToSpeech;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.foreign.ValueLayout.*;

public class Qwen3VoicePool implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger("casehub-speech");

    private final Qwen3VoicePoolConfig config;
    private final Map<String, Path> voices;
    private final ConcurrentHashMap<String, MemorySegment> voiceRefs = new ConcurrentHashMap<>();
    private final Qwen3Library lib;
    private final MemorySegment context;
    private final Qwen3TextToSpeech baseTts;

    private Qwen3VoicePool(Qwen3VoicePoolConfig config, Map<String, Path> voices,
                           Qwen3Library lib, MemorySegment context, Qwen3TextToSpeech baseTts) {
        this.config = config;
        this.voices = Map.copyOf(voices);
        this.lib = lib;
        this.context = context;
        this.baseTts = baseTts;
    }

    public static Qwen3VoicePool create(Qwen3VoicePoolConfig config) {
        return new Qwen3VoicePool(config, discoverVoices(config), null, null, null);
    }

    public static Qwen3VoicePool createProvisioned(Qwen3VoicePoolConfig config) {
        Path modelDir = Provisioner.ensureQwen3Model(config.modelVariant());
        Qwen3Library lib = Qwen3Library.load();
        var tts = Qwen3TextToSpeech.withDefaults(null, config.modelVariant());
        return new Qwen3VoicePool(config, discoverVoices(config), lib, null, tts);
    }

    static Map<String, Path> discoverVoices(Qwen3VoicePoolConfig config) {
        Map<String, Path> discovered = new LinkedHashMap<>();
        if (Files.isDirectory(config.voiceDir())) {
            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(config.voiceDir(), "*.wav")) {
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    name = name.substring(0, name.length() - ".wav".length());
                    discovered.put(name, path);
                }
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to scan voice directory: {0}", e.getMessage());
            }
        }
        return discovered;
    }

    public List<String> availableVoices() {
        var sorted = new ArrayList<>(voices.keySet());
        Collections.sort(sorted);
        return Collections.unmodifiableList(sorted);
    }

    public StreamingTextToSpeech forVoice(String name) {
        if (!voices.containsKey(name)) {
            throw new IllegalArgumentException("Unknown voice: " + name
                    + ". Available: " + availableVoices());
        }
        return new Qwen3ClonedVoice(this, name);
    }

    MemorySegment acquireVoiceRef(String voiceName) {
        return voiceRefs.computeIfAbsent(voiceName, this::extractVoiceRef);
    }

    private MemorySegment extractVoiceRef(String voiceName) {
        Path wavPath = voices.get(voiceName);
        try {
            float[] samples = WavReader.readAsFloat(wavPath);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment samplesSegment = arena.allocateFrom(JAVA_FLOAT, samples);
                MemorySegment voiceRef = Arena.global().allocate(Qwen3Layouts.VOICE_REF_SIZE);
                voiceRef.fill((byte) 0);

                int status = (int) lib.extractVoiceRef.invokeExact(
                        context, samplesSegment, samples.length, voiceRef);
                if (status != 0) {
                    throw new SherpaException("qt_extract_voice_ref failed for " + voiceName);
                }
                LOG.log(System.Logger.Level.INFO,
                        "Voice ref extracted for {0} ({1} samples)", voiceName, samples.length);
                return voiceRef;
            }
        } catch (SherpaException e) {
            throw e;
        } catch (Throwable e) {
            throw new SherpaException("Failed to extract voice ref for " + voiceName
                    + ": " + e.getMessage(), e);
        }
    }

    Qwen3TextToSpeech baseTts() {
        return baseTts;
    }

    @Override
    public void close() {
        for (var entry : voiceRefs.entrySet()) {
            try {
                lib.voiceRefFree.invokeExact(entry.getValue());
            } catch (Throwable e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to free voice ref for {0}: {1}", entry.getKey(), e.getMessage());
            }
        }
        voiceRefs.clear();
        if (baseTts != null) { baseTts.close(); }
    }
}
```

- [ ] **Step 5: Update Qwen3ClonedVoice**

```java
package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.StreamingTextToSpeech;

import java.lang.foreign.MemorySegment;
import java.util.function.Consumer;

public class Qwen3ClonedVoice implements StreamingTextToSpeech {

    private final Qwen3VoicePool pool;
    private final String voiceName;

    Qwen3ClonedVoice(Qwen3VoicePool pool, String voiceName) {
        this.pool = pool;
        this.voiceName = voiceName;
    }

    @Override
    public SynthesisResult synthesise(String text, SynthesisOptions options) {
        MemorySegment voiceRef = pool.acquireVoiceRef(voiceName);
        return pool.baseTts().synthesiseWithVoiceRef(text, options, voiceRef);
    }

    @Override
    public void synthesiseStreaming(String text, SynthesisOptions options,
                                    Consumer<byte[]> onWavChunk) {
        MemorySegment voiceRef = pool.acquireVoiceRef(voiceName);
        pool.baseTts().synthesiseStreamingWithVoiceRef(text, options, onWavChunk, voiceRef);
    }

    public String voiceName() {
        return voiceName;
    }
}
```

Note: This requires adding `synthesiseWithVoiceRef` and `synthesiseStreamingWithVoiceRef` package-private methods to `Qwen3TextToSpeech` — variants of `synthesise`/`synthesiseStreaming` that accept a `MemorySegment voiceRef` and call `setVoiceRef()` on the ttsParams before synthesis.

- [ ] **Step 6: Add voice-ref-aware synthesis methods to Qwen3TextToSpeech**

Add these package-private methods to `Qwen3TextToSpeech`:

```java
SynthesisResult synthesiseWithVoiceRef(String text, SynthesisOptions options,
                                       MemorySegment voiceRef) {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment ttsParams = arena.allocate(Qwen3Layouts.TTS_PARAMS_SIZE);
        ttsParams.fill((byte) 0);
        lib.ttsDefaultParams.invokeExact(ttsParams);
        ttsParams.set(ADDRESS, Qwen3Layouts.TTS_TEXT, arena.allocateFrom(text));
        ttsParams.set(ADDRESS, Qwen3Layouts.TTS_LANG, arena.allocateFrom("en"));
        setVoiceRef(ttsParams, voiceRef);

        MemorySegment audio = arena.allocate(Qwen3Layouts.AUDIO_SIZE);
        audio.fill((byte) 0);

        int status = (int) lib.synthesize.invokeExact(context, ttsParams, audio);
        if (status != 0) {
            throw new SherpaException("qt_synthesize failed with status " + status);
        }
        MemorySegment samplesPtr = audio.get(ADDRESS, Qwen3Layouts.AUDIO_SAMPLES)
                .reinterpret(Long.MAX_VALUE);
        int nSamples = audio.get(JAVA_INT, Qwen3Layouts.AUDIO_N_SAMPLES);
        byte[] pcmS16 = floatPcmToS16le(samplesPtr, nSamples);
        byte[] wavBytes = wrapWav(pcmS16);
        lib.audioFree.invokeExact(audio);
        return new SynthesisResult(wavBytes, "wav", java.util.List.of());
    } catch (SherpaException e) {
        throw e;
    } catch (Throwable e) {
        throw new SherpaException("Qwen3-TTS cloned synthesis failed: " + e.getMessage(), e);
    }
}

void synthesiseStreamingWithVoiceRef(String text, SynthesisOptions options,
                                      Consumer<byte[]> onWavChunk, MemorySegment voiceRef) {
    try (Arena arena = Arena.ofConfined()) {
        MemorySegment ttsParams = arena.allocate(Qwen3Layouts.TTS_PARAMS_SIZE);
        ttsParams.fill((byte) 0);
        lib.ttsDefaultParams.invokeExact(ttsParams);
        ttsParams.set(ADDRESS, Qwen3Layouts.TTS_TEXT, arena.allocateFrom(text));
        ttsParams.set(ADDRESS, Qwen3Layouts.TTS_LANG, arena.allocateFrom("en"));
        setVoiceRef(ttsParams, voiceRef);

        var callbackDesc = FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT, ADDRESS);
        MemorySegment callback = Linker.nativeLinker().upcallStub(
                java.lang.invoke.MethodHandles.lookup()
                        .bind(new StreamingCallback(onWavChunk), "onChunk",
                                java.lang.invoke.MethodType.methodType(
                                        void.class, MemorySegment.class, int.class, MemorySegment.class)),
                callbackDesc, arena);

        int status = (int) lib.synthesizeStreaming.invokeExact(
                context, ttsParams, callback, MemorySegment.NULL);
        if (status != 0) {
            throw new SherpaException("qt_synthesize_streaming failed with status " + status);
        }
    } catch (SherpaException e) {
        throw e;
    } catch (Throwable e) {
        throw new SherpaException("Qwen3-TTS cloned streaming failed: " + e.getMessage(), e);
    }
}
```

- [ ] **Step 7: Run tests to verify they pass**

Run: `mvn test -pl voice-sherpa -Dtest="Qwen3VoicePoolTest|Qwen3TextToSpeechTest" -f /Users/mdproctor/claude/casehub/connectors/pom.xml`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/connectors add voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePool.java voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3ClonedVoice.java voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePoolConfig.java voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePoolTest.java
git -C /Users/mdproctor/claude/casehub/connectors commit -m "feat(#453): simplify Qwen3VoicePool — voice-ref cache replaces process pool

No more subprocess management, HTTP health checks, or port allocation.
Voice refs extracted in-process via qt_extract_voice_ref and cached in
ConcurrentHashMap. Discovers .wav files instead of .qvoice files.

Refs casehubio/agency#453"
```

## Batch 4: Consumer Updates

### Task 6: Update speech-demo — ModelProvisioningService + SpeechProducers

**Files:**
- Modify: `speech-demo/src/main/java/io/casehub/agency/speech/demo/ModelProvisioningService.java`
- Modify: `speech-demo/src/main/java/io/casehub/agency/speech/demo/SpeechProducers.java`

**Interfaces:**
- Consumes: `Qwen3TextToSpeech.withDefaults(String speaker)`, `Qwen3TextToSpeech.withDefaults(String speaker, String variant)`, `Qwen3VoicePool.createProvisioned(Qwen3VoicePoolConfig)`, `Provisioner.ensureQwen3SharedLibrary()`, `Provisioner.ensureQwen3Model(String)`
- Produces: Updated speech-demo that uses FFM-based Qwen3-TTS (no behavioral change to end users)

- [ ] **Step 1: Update ModelProvisioningService**

Replace `provisionQwen3Binary()` and `provisionQwen3Model()`:

```java
void provisionQwen3SharedLibrary() {
    io.casehub.connectors.voice.sherpa.Provisioner.ensureQwen3SharedLibrary();
}

void provisionQwen3Model() {
    io.casehub.connectors.voice.sherpa.Provisioner.ensureQwen3Model("0.6b-q8");
}
```

Update `init()` — change `"qwen3-binary"` key to `"qwen3-lib"`:
```java
statuses.put("qwen3-lib", ModelStatus.PENDING);
```

Update `provisionAll()`:
```java
provisionEntry("qwen3-lib", this::provisionQwen3SharedLibrary);
```

- [ ] **Step 2: Update SpeechProducers**

Replace the Qwen3-TTS voice registration block (lines 85-111):

```java
// Qwen3-TTS voices (native FFM, GGUF quantized, streaming)
try {
    var qwen3 = io.casehub.connectors.voice.sherpa.Qwen3TextToSpeech.withDefaults("ryan");
    models.put("qwen3:ryan", qwen3);
    // Same TTS instance handles all built-in speakers via text prompting
    for (String voice : java.util.List.of("aiden", "eric", "dylan", "serena", "vivian")) {
        models.put("qwen3:" + voice,
                io.casehub.connectors.voice.sherpa.Qwen3TextToSpeech.withDefaults(voice));
    }
    LOG.log(System.Logger.Level.INFO, "Qwen3-TTS voices registered (native FFM)");
} catch (Exception e) {
    LOG.log(System.Logger.Level.WARNING, "Qwen3-TTS unavailable: " + e.getMessage());
}

// Qwen3-TTS cloned voices (in-process voice extraction from .wav files)
try {
    var pool = io.casehub.connectors.voice.sherpa.Qwen3VoicePool.createProvisioned(
            io.casehub.connectors.voice.sherpa.Qwen3VoicePoolConfig.defaults());
    for (String voice : pool.availableVoices()) {
        models.put("qwen3:clone:" + voice, pool.forVoice(voice));
    }
    LOG.log(System.Logger.Level.INFO,
            "Qwen3-TTS cloned voices registered: {0}", pool.availableVoices());
} catch (Exception e) {
    LOG.log(System.Logger.Level.WARNING,
            "Qwen3-TTS voice pool unavailable: " + e.getMessage());
}
```

Note: Each speaker now gets its own `Qwen3TextToSpeech` instance since there's no HTTP server to share. If qt_context is thread-safe, a single instance with speaker passed per-call would be more efficient — verify against qwentts.cpp docs and refactor if so.

- [ ] **Step 3: Verify compile**

Run: `mvn compile -pl speech-demo -f /Users/mdproctor/claude/casehub/slots/223/agency/pom.xml`
Expected: PASS (after installing updated connectors to local Maven repo)

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/223/agency add speech-demo/src/main/java/io/casehub/agency/speech/demo/ModelProvisioningService.java speech-demo/src/main/java/io/casehub/agency/speech/demo/SpeechProducers.java
git -C /Users/mdproctor/claude/casehub/slots/223/agency commit -m "feat(#453): update speech-demo for FFM-based Qwen3-TTS

Switch from HTTP subprocess to native FFM calls. Update provisioning
from CLI binary to shared library, models from safetensors to GGUF.

Refs casehubio/agency#453"
```

## References

- [2026-11-10-panama-ffm-shared-library-design.md] — design spec this plan implements
- [SherpaLibrary.java] — FFM binding pattern (3-tier loading, MethodHandle fields)
- [SherpaLayouts.java] — C struct byte offset pattern
- [Qwen3TextToSpeech.java] — current HTTP implementation being replaced
- [Qwen3VoicePool.java] — current process pool being simplified
- [Provisioner.java:739-842] — current Qwen3 provisioning being updated
- [ModelProvisioningService.java] — speech-demo provisioning consumer
- [SpeechProducers.java:85-111] — speech-demo voice registration consumer
- [qwentts.cpp](https://github.com/ServeurpersoCom/qwentts.cpp) — upstream C++ project
- [GitHub #453](https://github.com/casehubio/agency/issues/453) — focal issue
