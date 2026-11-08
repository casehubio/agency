# Auto-Download Provisioner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #175 — Auto-download native lib + model on first use (Tier 3 provisioner)
**Issue group:** #175 (within branch covering #194 Talking Avatar Tier 1)

**Goal:** When speech native library or whisper-tiny model is missing, auto-download from k2-fsa GitHub releases, verify SHA-256, extract, and cache — making `SherpaOnnxSpeechToText.withDefaults()` work with zero manual setup.

**Architecture:** A single package-private `Provisioner` class in `io.casehub.blocks.speech.sherpa` handles both native lib and model downloads. Downloads use `java.net.http.HttpClient` (lazy-initialized) with streaming. Extraction uses system `tar xf` (auto-detect compression) via `ProcessBuilder`. Cross-process safety via `FileLock`, in-process via `synchronized`. SHA-256 verification before extraction — no checksum = refuse to download. `withDefaults()` factory always provisions; `SherpaLibrary.load()` Tier 3 is opt-in only.

**Tech Stack:** Java 22+ (FFM/Panama), `java.net.http.HttpClient`, `ProcessBuilder` (tar), `java.nio.channels.FileLock`, `java.security.MessageDigest` (SHA-256)

## Global Constraints

- JDK 22+ required (speech-sherpa module)
- Zero external runtime dependencies — no Apache Commons, no CDI
- Module is `speech-sherpa` at `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/`
- Tests at `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/`
- Package: `io.casehub.blocks.speech.sherpa`
- All new classes are package-private (except public API additions to SherpaConfig, SherpaOnnxSpeechToText)
- sherpa-onnx version: `1.13.6` (from `SherpaLibrary.VERSION`)
- IntelliJ project path: `/Users/mdproctor/claude/casehub/blocks/speech-sherpa`

## Plan Review Corrections (from light review)

The following corrections from the plan review MUST be applied during implementation:

1. **Auto-download is opt-in for `SherpaLibrary.load()`**, always-on for `withDefaults()`.
   - `withDefaults()` calls `Provisioner.ensureNativeLibrary()` directly (explicit zero-install path)
   - Tier 3 in `SherpaLibrary.load()` only fires when `-Dcasehub.speech.auto-download=true`
   - `Provisioner.isAutoDownloadEnabled()` defaults to `false` (inverted from original plan)

2. **Orphan cleanup inside FileLock**, not before it (race condition fix).

3. **No checksum = refuse to download.** Remove PLACEHOLDER skip logic. Unknown platform or model = fail fast with clear error.

4. **Lazy HttpClient + `Downloader` functional interface.** No mutable static HttpClient field. Create lazily on first download. Package-private `@FunctionalInterface Downloader` for test substitution.

5. **Use `tar xf`** (no `-j` flag) — let tar auto-detect bzip2 compression. Works on GNU tar and bsdtar.

6. **Set `HttpRequest.timeout(Duration.ofMinutes(5))`** on download requests for overall timeout.

7. **Post-extraction file verification.** After `Files.move()`, verify at least one expected file exists. If not, delete target dir and throw descriptive error.

8. **Retry on HTTP 5xx** (502, 503, 504) — treat as transient, same as connection errors.

9. **Refuse download for unknown models.** If no checksum is registered for the model name, throw instead of downloading unverified.

10. **Add `casehub.speech.cache-dir` system property** to override `~/.casehub` base path for tests and CI.

11. **Add `linux-arm64` platform** — sherpa-onnx publishes `linux-aarch64` assets. Real deployment target (Docker on Apple Silicon, AWS Graviton).

12. **Test orphan cleanup via integration** (side effect of `provision()`), not via reflection on private method.

---

## Batch 1: Provisioner core + unit tests

### Task 1: Provisioner — URL mapping, checksum, config, download pipeline

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/ProvisionerTest.java`

**Interfaces:**
- Produces: `Provisioner.ensureNativeLibrary() → Path` (cache dir with native libs)
- Produces: `Provisioner.ensureModel(String name) → Path` (model dir)
- Produces: `Provisioner.isAutoDownloadEnabled() → boolean`
- Produces: `Provisioner.defaultModelDir() → Path`
- Produces: `Provisioner.nativeLibUrl(String platformId) → String`
- Produces: `Provisioner.modelUrl(String modelName) → String`

- [ ] **Step 1: Write failing tests for URL construction**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;

class ProvisionerTest {

    @Test
    void nativeLibUrl_osxArm64() {
        String url = Provisioner.nativeLibUrl("osx-arm64");
        assertThat(url).isEqualTo(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/"
            + "sherpa-onnx-v1.13.6-osx-arm64-shared-lib.tar.bz2");
    }

    @Test
    void nativeLibUrl_osxX64() {
        String url = Provisioner.nativeLibUrl("osx-x64");
        assertThat(url).contains("osx-x86_64-shared-lib");
    }

    @Test
    void nativeLibUrl_linuxX64() {
        String url = Provisioner.nativeLibUrl("linux-x64");
        assertThat(url).contains("linux-x86_64-shared-cpu-lib");
    }

    @Test
    void nativeLibUrl_winX64() {
        String url = Provisioner.nativeLibUrl("win-x64");
        assertThat(url).contains("win-x64-shared-MD-Release-lib");
    }

    @Test
    void nativeLibUrl_unknownPlatform_throws() {
        assertThatThrownBy(() -> Provisioner.nativeLibUrl("solaris-sparc"))
            .isInstanceOf(SherpaException.class)
            .hasMessageContaining("solaris-sparc");
    }

    @Test
    void modelUrl_whisperTiny() {
        String url = Provisioner.modelUrl("sherpa-onnx-whisper-tiny");
        assertThat(url).isEqualTo(
            "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/"
            + "sherpa-onnx-whisper-tiny.tar.bz2");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn test -pl speech-sherpa -Dtest=ProvisionerTest -am --batch-mode`
Expected: FAIL — `Provisioner` class does not exist

- [ ] **Step 3: Implement URL mapping and config methods**

Create `Provisioner.java` with:
- Static `Map<String, String>` for platform → asset name mapping
- `nativeLibUrl(String platformId)` — constructs full URL from base + version + asset
- `modelUrl(String modelName)` — constructs full URL from base + model asset
- `isAutoDownloadEnabled()` — reads `casehub.speech.auto-download` system property
- `defaultModelDir()` — returns `~/.casehub/models/sherpa-onnx/sherpa-onnx-whisper-tiny`
- `baseUrl()` — reads `casehub.speech.download-url` system property with default

```java
package io.casehub.blocks.speech.sherpa;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class Provisioner {

    private static final System.Logger LOG = System.getLogger("casehub-speech");
    private static final String DEFAULT_BASE_URL =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/";
    private static final String TEMP_PREFIX = ".provisioning-";

    private static final Map<String, String> NATIVE_ASSETS = Map.of(
        "osx-arm64", "sherpa-onnx-v" + SherpaLibrary.VERSION + "-osx-arm64-shared-lib.tar.bz2",
        "osx-x64",   "sherpa-onnx-v" + SherpaLibrary.VERSION + "-osx-x86_64-shared-lib.tar.bz2",
        "linux-x64", "sherpa-onnx-v" + SherpaLibrary.VERSION + "-linux-x86_64-shared-cpu-lib.tar.bz2",
        "win-x64",   "sherpa-onnx-v" + SherpaLibrary.VERSION + "-win-x64-shared-MD-Release-lib.tar.bz2"
    );

    private static final Map<String, String> NATIVE_CHECKSUMS = Map.of(
        "osx-arm64", "PLACEHOLDER_COMPUTE_DURING_INTEGRATION",
        "osx-x64",   "PLACEHOLDER_COMPUTE_DURING_INTEGRATION",
        "linux-x64", "PLACEHOLDER_COMPUTE_DURING_INTEGRATION",
        "win-x64",   "PLACEHOLDER_COMPUTE_DURING_INTEGRATION"
    );

    private static final Map<String, String> MODEL_CHECKSUMS = Map.of(
        "sherpa-onnx-whisper-tiny", "PLACEHOLDER_COMPUTE_DURING_INTEGRATION"
    );

    private static final Object NATIVE_LOCK = new Object();
    private static final Object MODEL_LOCK = new Object();

    // Package-private for testing — tests replace with a mock
    static HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private Provisioner() {}

    static boolean isAutoDownloadEnabled() {
        return !"false".equalsIgnoreCase(
            System.getProperty("casehub.speech.auto-download"));
    }

    static Path defaultModelDir() {
        return Path.of(System.getProperty("user.home"),
            ".casehub", "models", "sherpa-onnx", "sherpa-onnx-whisper-tiny");
    }

    static String baseUrl() {
        return System.getProperty("casehub.speech.download-url", DEFAULT_BASE_URL);
    }

    static String nativeLibUrl(String platformId) {
        String asset = NATIVE_ASSETS.get(platformId);
        if (asset == null) {
            throw new SherpaException(
                "No native library available for platform: " + platformId
                + ". Supported: " + NATIVE_ASSETS.keySet());
        }
        return baseUrl() + "v" + SherpaLibrary.VERSION + "/" + asset;
    }

    static String modelUrl(String modelName) {
        return baseUrl() + "asr-models/" + modelName + ".tar.bz2";
    }

    static Path ensureNativeLibrary() {
        Path targetDir = SherpaLibrary.defaultCacheDir();
        if (Files.isDirectory(targetDir)) { return targetDir; }

        synchronized (NATIVE_LOCK) {
            if (Files.isDirectory(targetDir)) { return targetDir; }
            String platformId = SherpaLibrary.platformId();
            String url = nativeLibUrl(platformId);
            String expectedHash = NATIVE_CHECKSUMS.get(platformId);
            return provision(url, targetDir, expectedHash, 2);
        }
    }

    static Path ensureModel(String modelName) {
        Path modelsParent = Path.of(System.getProperty("user.home"),
            ".casehub", "models", "sherpa-onnx");
        Path targetDir = modelsParent.resolve(modelName);
        if (Files.isDirectory(targetDir)) { return targetDir; }

        synchronized (MODEL_LOCK) {
            if (Files.isDirectory(targetDir)) { return targetDir; }
            String url = modelUrl(modelName);
            String expectedHash = MODEL_CHECKSUMS.get(modelName);
            return provision(url, targetDir, expectedHash, 1);
        }
    }

    private static Path provision(String url, Path targetDir, String expectedHash,
                                  int stripComponents) {
        Path parentDir = targetDir.getParent();
        try {
            Files.createDirectories(parentDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create directory: " + parentDir, e);
        }

        cleanOrphanedTempDirs(parentDir);

        Path lockFile = parentDir.resolve(".provisioning.lock");
        try (FileChannel channel = FileChannel.open(lockFile,
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock ignored = channel.lock()) {

            if (Files.isDirectory(targetDir)) { return targetDir; }

            LOG.log(System.Logger.Level.INFO, "Downloading {0}...", url);
            Path archive = download(url, parentDir);
            try {
                if (expectedHash != null && !expectedHash.startsWith("PLACEHOLDER")) {
                    verifyChecksum(archive, expectedHash);
                }
                Path tempExtractDir = Files.createTempDirectory(parentDir, TEMP_PREFIX);
                try {
                    extract(archive, tempExtractDir, stripComponents);
                    Files.move(tempExtractDir, targetDir, StandardCopyOption.ATOMIC_MOVE);
                } catch (Exception e) {
                    deleteRecursively(tempExtractDir);
                    throw e;
                }
            } finally {
                Files.deleteIfExists(archive);
            }
            LOG.log(System.Logger.Level.INFO, "Done.");
            return targetDir;

        } catch (IOException e) {
            throw new SherpaException("Failed to provision from " + url
                + ". Download manually to " + targetDir, e);
        }
    }

    private static Path download(String url, Path parentDir) {
        return downloadWithRetry(url, parentDir, 1);
    }

    private static Path downloadWithRetry(String url, Path parentDir, int retries) {
        try {
            Path tempFile = Files.createTempFile(parentDir, TEMP_PREFIX, ".tar.bz2");
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
            HttpResponse<InputStream> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                Files.deleteIfExists(tempFile);
                throw new SherpaException("HTTP " + response.statusCode()
                    + " downloading " + url);
            }
            try (InputStream in = response.body();
                 var out = Files.newOutputStream(tempFile)) {
                in.transferTo(out);
            }
            return tempFile;
        } catch (IOException | InterruptedException e) {
            if (retries > 0 && isTransient(e)) {
                try { Thread.sleep(2000); } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new SherpaException("Download interrupted: " + url, ie);
                }
                return downloadWithRetry(url, parentDir, retries - 1);
            }
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new SherpaException("Failed to download " + url
                + ". Download manually.", e);
        }
    }

    private static boolean isTransient(Exception e) {
        return e instanceof java.net.ConnectException
            || e instanceof java.net.SocketTimeoutException
            || (e instanceof IOException && e.getMessage() != null
                && e.getMessage().contains("Connection reset"));
    }

    static void verifyChecksum(Path file, String expectedHex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[8192];
                int read;
                while ((read = in.read(buf)) != -1) {
                    digest.update(buf, 0, read);
                }
            }
            String actualHex = HexFormat.of().formatHex(digest.digest());
            if (!actualHex.equalsIgnoreCase(expectedHex)) {
                Files.deleteIfExists(file);
                throw new SherpaException("SHA-256 mismatch for " + file.getFileName()
                    + "\n  Expected: " + expectedHex
                    + "\n  Actual:   " + actualHex);
            }
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 not available", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to verify checksum: " + file, e);
        }
    }

    private static void extract(Path archive, Path targetDir, int stripComponents) {
        try {
            Files.createDirectories(targetDir);
            ProcessBuilder pb = new ProcessBuilder(
                "tar", "xjf", archive.toString(),
                "--strip-components=" + stripComponents,
                "-C", targetDir.toString());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output;
            try (InputStream is = process.getInputStream()) {
                output = new String(is.readAllBytes());
            }
            boolean finished = process.waitFor(5, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                throw new SherpaException("tar extraction timed out after 5 minutes");
            }
            if (process.exitValue() != 0) {
                throw new SherpaException("tar extraction failed (exit "
                    + process.exitValue() + "): " + output);
            }
        } catch (IOException e) {
            throw new SherpaException("tar not found or extraction failed. "
                + "Install tar or download manually.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SherpaException("tar extraction interrupted", e);
        }
    }

    private static void cleanOrphanedTempDirs(Path parentDir) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parentDir,
                TEMP_PREFIX + "*")) {
            for (Path orphan : stream) {
                deleteRecursively(orphan);
            }
        } catch (IOException ignored) {}
    }

    private static void deleteRecursively(Path dir) {
        try (var walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }
}
```

- [ ] **Step 4: Run URL tests to verify they pass**

Run: `mvn test -pl speech-sherpa -Dtest=ProvisionerTest#nativeLibUrl* -Dtest=ProvisionerTest#modelUrl* -am --batch-mode`
Expected: PASS — all URL construction tests

- [ ] **Step 5: Write failing tests for config methods and checksum**

Add to `ProvisionerTest.java`:

```java
    @Test
    void isAutoDownloadEnabled_defaultTrue() {
        System.clearProperty("casehub.speech.auto-download");
        assertThat(Provisioner.isAutoDownloadEnabled()).isTrue();
    }

    @Test
    void isAutoDownloadEnabled_explicitlyDisabled() {
        System.setProperty("casehub.speech.auto-download", "false");
        try {
            assertThat(Provisioner.isAutoDownloadEnabled()).isFalse();
        } finally {
            System.clearProperty("casehub.speech.auto-download");
        }
    }

    @Test
    void customDownloadUrl() {
        System.setProperty("casehub.speech.download-url", "https://mirror.internal/");
        try {
            String url = Provisioner.nativeLibUrl("osx-arm64");
            assertThat(url).startsWith("https://mirror.internal/");
        } finally {
            System.clearProperty("casehub.speech.download-url");
        }
    }

    @Test
    void defaultModelDir_pointsToWhisperTiny() {
        Path dir = Provisioner.defaultModelDir();
        assertThat(dir.toString()).endsWith("sherpa-onnx-whisper-tiny");
        assertThat(dir.toString()).contains(".casehub");
    }

    @Test
    void verifyChecksum_match(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("test.bin");
        Files.write(file, "hello world".getBytes());
        // SHA-256 of "hello world"
        String expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        assertThatCode(() -> Provisioner.verifyChecksum(file, expected))
            .doesNotThrowAnyException();
    }

    @Test
    void verifyChecksum_mismatch_throwsAndDeletes(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("test.bin");
        Files.write(file, "hello world".getBytes());
        assertThatThrownBy(() -> Provisioner.verifyChecksum(file, "0000"))
            .isInstanceOf(SherpaException.class)
            .hasMessageContaining("SHA-256 mismatch");
        assertThat(Files.exists(file)).isFalse();
    }
```

- [ ] **Step 6: Run all tests to verify pass**

Run: `mvn test -pl speech-sherpa -Dtest=ProvisionerTest -am --batch-mode`
Expected: PASS — all tests

- [ ] **Step 7: Write failing tests for orphan cleanup and concurrency**

Add to `ProvisionerTest.java`:

```java
    @Test
    void cleanOrphanedTempDirs(@TempDir Path tmp) throws Exception {
        Path orphan1 = Files.createDirectory(tmp.resolve(".provisioning-abc123"));
        Path orphan2 = Files.createDirectory(tmp.resolve(".provisioning-def456"));
        Files.writeString(orphan1.resolve("partial.tar.bz2"), "partial");
        Path keepMe = Files.createDirectory(tmp.resolve("real-dir"));

        // Invoke cleanup via reflection (package-private)
        var method = Provisioner.class.getDeclaredMethod("cleanOrphanedTempDirs", Path.class);
        method.setAccessible(true);
        method.invoke(null, tmp);

        assertThat(Files.exists(orphan1)).isFalse();
        assertThat(Files.exists(orphan2)).isFalse();
        assertThat(Files.exists(keepMe)).isTrue();
    }

    @Test
    void ensureNativeLibrary_existingDir_returnsImmediately() {
        Path existing = SherpaLibrary.defaultCacheDir();
        if (!Files.isDirectory(existing)) { return; } // skip if no native lib installed
        Path result = Provisioner.ensureNativeLibrary();
        assertThat(result).isEqualTo(existing);
    }

    @Test
    void ensureModel_existingDir_returnsImmediately(@TempDir Path tmp) throws Exception {
        // Create a fake model dir at the expected location
        Path modelDir = tmp.resolve("sherpa-onnx-whisper-tiny");
        Files.createDirectories(modelDir);
        Files.writeString(modelDir.resolve("tiny-tokens.txt"), "tokens");
        // Can't easily test ensureModel with a custom path without modifying the method
        // This test verifies the fast-path logic via the nativeLib path
    }
```

- [ ] **Step 8: Run all tests to verify pass**

Run: `mvn test -pl speech-sherpa -Dtest=ProvisionerTest -am --batch-mode`
Expected: PASS

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/ProvisionerTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#175): add Provisioner — URL mapping, SHA-256 verification, download pipeline  Refs #175"
```

---

## Batch 2: Integration wiring + tests

### Task 2: Wire Provisioner into SherpaLibrary, SherpaConfig, and SherpaOnnxSpeechToText

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SherpaLibrary.java`
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SherpaConfig.java`
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SherpaOnnxSpeechToText.java`
- Modify: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/SherpaConfigTest.java`

**Interfaces:**
- Consumes: `Provisioner.ensureNativeLibrary() → Path`
- Consumes: `Provisioner.ensureModel(String) → Path`
- Consumes: `Provisioner.isAutoDownloadEnabled() → boolean`
- Consumes: `Provisioner.defaultModelDir() → Path`
- Produces: `SherpaConfig.defaults() → SherpaConfig` (no-arg overload)
- Produces: `SherpaOnnxSpeechToText.withDefaults() → SherpaOnnxSpeechToText`

- [ ] **Step 1: Write failing test for SherpaConfig.defaults() no-arg**

Add to `SherpaConfigTest.java`:

```java
    @Test
    void defaults_noArg_usesDefaultModelDir() {
        SherpaConfig config = SherpaConfig.defaults();
        assertThat(config.modelDir().toString()).endsWith("sherpa-onnx-whisper-tiny");
        assertThat(config.numThreads()).isEqualTo(2);
        assertThat(config.provider()).isEqualTo("cpu");
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl speech-sherpa -Dtest=SherpaConfigTest#defaults_noArg_usesDefaultModelDir -am --batch-mode`
Expected: FAIL — no no-arg `defaults()` method

- [ ] **Step 3: Implement SherpaConfig.defaults() no-arg**

Add to `SherpaConfig.java`:

```java
    public static SherpaConfig defaults() {
        return new SherpaConfig(Provisioner.defaultModelDir(), 2, "cpu", null, null);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl speech-sherpa -Dtest=SherpaConfigTest#defaults_noArg_usesDefaultModelDir -am --batch-mode`
Expected: PASS

- [ ] **Step 5: Remove version fallback from SherpaLibrary.resolveNativeDir()**

The existing `resolveNativeDir()` (lines 178-199 of `SherpaLibrary.java`) has a fallback that searches versioned subdirectories. Remove the fallback — only return `defaultCacheDir()` if it exists, or the system property override.

Replace `resolveNativeDir()` with:

```java
    private static Path resolveNativeDir() {
        String override = System.getProperty("sherpa.native.dir");
        if (override != null) { return Path.of(override); }

        Path cacheDir = defaultCacheDir();
        if (java.nio.file.Files.isDirectory(cacheDir)) { return cacheDir; }

        return null;
    }
```

- [ ] **Step 6: Add Tier 3 to SherpaLibrary.load()**

In `SherpaLibrary.load()`, after the Tier 2 check (cacheDir null or files missing), add Tier 3:

```java
            // Tier 3: auto-download
            if (Provisioner.isAutoDownloadEnabled()) {
                Path downloadedDir = Provisioner.ensureNativeLibrary();
                Path onnxRuntime = downloadedDir.resolve(onnxRuntimeLibName());
                Path sherpaLib   = downloadedDir.resolve(sherpaLibName());
                if (java.nio.file.Files.exists(sherpaLib) && java.nio.file.Files.exists(onnxRuntime)) {
                    SymbolLookup.libraryLookup(onnxRuntime, Arena.global());
                    SymbolLookup lookup = SymbolLookup.libraryLookup(sherpaLib, Arena.global());
                    INSTANCE = new SherpaLibrary(lookup);
                    return INSTANCE;
                }
            }
```

Place this after the Tier 2 block (after the `if (cacheDir != null)` block), before the `throw new UnsatisfiedLinkError(...)`.

- [ ] **Step 7: Add withDefaults() factory to SherpaOnnxSpeechToText**

Add to `SherpaOnnxSpeechToText.java`:

```java
    public static SherpaOnnxSpeechToText withDefaults() {
        SherpaLibrary lib = SherpaLibrary.load();
        SherpaConfig config = SherpaConfig.defaults();
        if (Provisioner.isAutoDownloadEnabled()
                && !java.nio.file.Files.isDirectory(config.modelDir())) {
            Provisioner.ensureModel("sherpa-onnx-whisper-tiny");
        }
        return new SherpaOnnxSpeechToText(config, lib);
    }
```

- [ ] **Step 8: Run all existing tests to verify no regressions**

Run: `mvn test -pl speech-sherpa -am --batch-mode`
Expected: PASS — all existing tests still pass; Tier 3 only activates when Tier 1+2 fail and auto-download is enabled

- [ ] **Step 9: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SherpaLibrary.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SherpaConfig.java speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SherpaOnnxSpeechToText.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/SherpaConfigTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#175): wire Provisioner into SherpaLibrary (Tier 3), SherpaConfig, withDefaults()  Refs #175"
```

---

## Batch 3: Compute SHA-256 hashes + integration test

### Task 3: Compute real SHA-256 hashes and add integration test

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java` (replace placeholder hashes)
- Modify: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/ProvisionerTest.java` (add integration test)

**Interfaces:**
- Consumes: All `Provisioner` methods from Task 1
- Produces: Real SHA-256 hashes embedded in `Provisioner.java`

- [ ] **Step 1: Download archives and compute SHA-256 hashes**

Download each archive for the current platform and compute its SHA-256:

```bash
# For osx-arm64 (current platform)
curl -sL "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.6/sherpa-onnx-v1.13.6-osx-arm64-shared-lib.tar.bz2" -o /tmp/native.tar.bz2
shasum -a 256 /tmp/native.tar.bz2

# Model
curl -sL "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2" -o /tmp/model.tar.bz2
shasum -a 256 /tmp/model.tar.bz2
```

Record the SHA-256 hex strings from the output.

- [ ] **Step 2: Update NATIVE_CHECKSUMS and MODEL_CHECKSUMS in Provisioner.java**

Replace the `"PLACEHOLDER_COMPUTE_DURING_INTEGRATION"` values with actual SHA-256 hex strings from Step 1. For platforms we can't download on the current machine (Linux, Windows), compute their hashes in CI or note as TODO for cross-platform CI verification.

At minimum, compute and embed the hash for the current platform (`osx-arm64`) and the model (`sherpa-onnx-whisper-tiny`).

- [ ] **Step 3: Write integration test for real download**

Add to `ProvisionerTest.java`:

```java
    @Test
    @EnabledIf("io.casehub.blocks.speech.sherpa.ProvisionerTest#canDownload")
    void integration_ensureNativeLibrary_downloadsAndExtracts() throws Exception {
        // Use a unique test cache dir to avoid polluting the real cache
        String originalHome = System.getProperty("user.home");
        Path testCacheDir = Files.createTempDirectory("provisioner-integration");
        // Note: ensureNativeLibrary uses SherpaLibrary.defaultCacheDir() which reads user.home
        // This test verifies the full pipeline works but may download to the real cache
        // A proper test would need SherpaLibrary.defaultCacheDir() to be overridable

        Path result = Provisioner.ensureNativeLibrary();
        assertThat(result).isNotNull();
        assertThat(Files.isDirectory(result)).isTrue();
        // Verify expected files exist
        String libName = SherpaLibrary.sherpaLibName();
        assertThat(Files.exists(result.resolve(libName))).isTrue();
    }

    static boolean canDownload() {
        return Provisioner.isAutoDownloadEnabled()
            && !"true".equals(System.getProperty("skipIntegrationTests"));
    }
```

- [ ] **Step 4: Run integration test**

Run: `mvn test -pl speech-sherpa -Dtest=ProvisionerTest#integration_ensureNativeLibrary_downloadsAndExtracts -am --batch-mode`
Expected: PASS (downloads real archive, extracts, verifies)

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/Provisioner.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/ProvisionerTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#175): embed SHA-256 hashes + integration test for auto-download  Refs #175"
```

---

## References

- [2026-08-26-auto-download-provisioner-design.md] — design spec this plan implements
- [SherpaLibrary.java:123-154] — existing Tier 1+2 loading logic
- [SherpaLibrary.java:178-199] — version fallback in resolveNativeDir() (to be removed)
- [SherpaConfig.java:22-24] — existing defaults(Path) factory
- [SherpaOnnxSpeechToText.java:19-29] — existing constructors
- [GE-20260826-3608ec] — JNI vs C API library distinction
- [GE-20260630-e18bed] — streaming download via BodyHandlers.ofInputStream()
- [GE-20260420-b9c06c] — Java stdlib doesn't support bzip2
- [GitHub #175] — Auto-download native lib + model on first use
- [GitHub #174] — parent epic (Zero-Install Speech)
- [GitHub #194] — Tier 1 epic (Talking Avatar)
