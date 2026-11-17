# Qwen3-TTS Multi-Voice Server Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #451 — Qwen3-TTS multi-voice server — serve cloned .qvoice files
**Issue group:** #451

**Goal:** Serve multiple cloned .qvoice files from a pooled set of qwen_tts Base model server processes with LRU eviction, and register them in the speech demo.

**Architecture:** New `Qwen3VoicePool` in connectors-voice-sherpa manages a pool of qwen_tts server processes (one per active .qvoice voice). `Qwen3ClonedVoice` implements `StreamingTextToSpeech` and delegates to pool-managed servers. Agency's `SpeechProducers` creates a pool and registers all discovered voices in the TtsModelRegistry.

**Tech Stack:** Java 22, JUnit 5, AssertJ, java.net.http.HttpClient, java.lang.Process

## Global Constraints

- Connectors repo changes must be installed to local Maven (`mvn install -DskipTests`) before agency can compile against them
- The `qwen_tts` binary only loads one `.qvoice` per server instance — this is a hard binary limitation, not a bug to fix
- Base model variant is `0.6b-base` (distinct from `0.6b-customvoice` for presets)
- Default pool size 3 servers, base port 8800
- Voice names derived from `.qvoice` filenames (strip extension, e.g. `jodi_krangle.qvoice` → `jodi_krangle`)

---

## Batch 1: Voice Pool (connectors-voice-sherpa)

### Task 1: Qwen3VoicePoolConfig and Qwen3VoicePool core

**Files:**
- Create: `connectors/voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePoolConfig.java`
- Create: `connectors/voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePool.java`
- Test: `connectors/voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePoolTest.java`

**Interfaces:**
- Consumes: `Provisioner.ensureQwen3Binary()` → `Path`, `Provisioner.ensureQwen3Model(String)` → `Path`, `Qwen3TextToSpeech.withExternalServer(String, String)` → `Qwen3TextToSpeech`
- Produces: `Qwen3VoicePool.create(Qwen3VoicePoolConfig)` → `Qwen3VoicePool`, `availableVoices()` → `List<String>`, `forVoice(String)` → `StreamingTextToSpeech`, `close()` → void

- [ ] **Step 1: Write the Qwen3VoicePoolConfig record**

```java
package io.casehub.connectors.voice.sherpa;

import java.nio.file.Path;

public record Qwen3VoicePoolConfig(
    Path voiceDir,
    String modelVariant,
    int maxActive,
    int basePort
) {
    public Qwen3VoicePoolConfig {
        if (maxActive < 1) throw new IllegalArgumentException("maxActive must be >= 1");
        if (basePort < 1 || basePort > 65535) throw new IllegalArgumentException("invalid basePort");
    }

    public static Qwen3VoicePoolConfig defaults() {
        return new Qwen3VoicePoolConfig(
            Path.of(System.getProperty("user.home"), ".casehub", "voice-samples"),
            "0.6b-base", 3, 8800);
    }
}
```

- [ ] **Step 2: Write failing test for config validation**

```java
package io.casehub.connectors.voice.sherpa;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Qwen3VoicePoolTest {

    @Test
    void configRejectsZeroMaxActive(@TempDir Path dir) {
        assertThatThrownBy(() -> new Qwen3VoicePoolConfig(dir, "0.6b-base", 0, 8800))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configRejectsInvalidPort(@TempDir Path dir) {
        assertThatThrownBy(() -> new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void configDefaultsAreValid() {
        var config = Qwen3VoicePoolConfig.defaults();
        assertThat(config.maxActive()).isEqualTo(3);
        assertThat(config.basePort()).isEqualTo(8800);
        assertThat(config.modelVariant()).isEqualTo("0.6b-base");
    }
}
```

- [ ] **Step 3: Run tests to verify they pass**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3VoicePoolTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml --batch-mode`
Expected: 3 tests PASS

- [ ] **Step 4: Write failing test for voice discovery**

Add to `Qwen3VoicePoolTest`:

```java
@Test
void discoversQvoiceFilesInDirectory(@TempDir Path dir) throws Exception {
    java.nio.file.Files.createFile(dir.resolve("alice.qvoice"));
    java.nio.file.Files.createFile(dir.resolve("bob.qvoice"));
    java.nio.file.Files.createFile(dir.resolve("readme.txt"));

    var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
    var pool = Qwen3VoicePool.create(config);

    assertThat(pool.availableVoices()).containsExactlyInAnyOrder("alice", "bob");
}

@Test
void emptyDirectoryReturnsNoVoices(@TempDir Path dir) {
    var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
    var pool = Qwen3VoicePool.create(config);

    assertThat(pool.availableVoices()).isEmpty();
}
```

- [ ] **Step 5: Run tests to verify they fail**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3VoicePoolTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml --batch-mode`
Expected: FAIL — `Qwen3VoicePool.create` does not exist

- [ ] **Step 6: Write Qwen3VoicePool with voice discovery and pool structure**

```java
package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.spi.StreamingTextToSpeech;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class Qwen3VoicePool implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger("casehub-speech");

    private final Qwen3VoicePoolConfig config;
    private final Map<String, Path> voices;
    private final LinkedHashMap<String, Slot> active;
    private final ReentrantLock lock = new ReentrantLock();
    private final Path binary;
    private final Path modelDir;

    record Slot(Process process, int port, Qwen3TextToSpeech tts) {}

    private Qwen3VoicePool(Qwen3VoicePoolConfig config, Map<String, Path> voices,
                           Path binary, Path modelDir) {
        this.config = config;
        this.voices = Map.copyOf(voices);
        this.active = new LinkedHashMap<>(16, 0.75f, true);
        this.binary = binary;
        this.modelDir = modelDir;
    }

    public static Qwen3VoicePool create(Qwen3VoicePoolConfig config) {
        Map<String, Path> discovered = new LinkedHashMap<>();
        if (Files.isDirectory(config.voiceDir())) {
            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(config.voiceDir(), "*.qvoice")) {
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    name = name.substring(0, name.length() - ".qvoice".length());
                    discovered.put(name, path);
                }
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to scan voice directory: {0}", e.getMessage());
            }
        }
        return new Qwen3VoicePool(config, discovered, null, null);
    }

    public static Qwen3VoicePool createProvisioned(Qwen3VoicePoolConfig config) {
        Map<String, Path> discovered = new LinkedHashMap<>();
        if (Files.isDirectory(config.voiceDir())) {
            try (DirectoryStream<Path> stream =
                         Files.newDirectoryStream(config.voiceDir(), "*.qvoice")) {
                for (Path path : stream) {
                    String name = path.getFileName().toString();
                    name = name.substring(0, name.length() - ".qvoice".length());
                    discovered.put(name, path);
                }
            } catch (IOException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to scan voice directory: {0}", e.getMessage());
            }
        }
        Path binary = Provisioner.ensureQwen3Binary();
        Path modelDir = Provisioner.ensureQwen3Model(config.modelVariant());
        return new Qwen3VoicePool(config, discovered, binary, modelDir);
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

    Slot acquireSlot(String voiceName) {
        lock.lock();
        try {
            Slot existing = active.get(voiceName);
            if (existing != null && existing.process().isAlive()) {
                return existing;
            }
            if (existing != null) {
                active.remove(voiceName);
            }

            if (active.size() >= config.maxActive()) {
                evictLru();
            }

            int port = allocatePort(voiceName);
            Slot slot = startVoiceServer(voiceName, port);
            active.put(voiceName, slot);
            return slot;
        } finally {
            lock.unlock();
        }
    }

    private void evictLru() {
        var it = active.entrySet().iterator();
        if (it.hasNext()) {
            var entry = it.next();
            LOG.log(System.Logger.Level.INFO, "Evicting LRU voice server: {0}", entry.getKey());
            killSlot(entry.getValue());
            it.remove();
        }
    }

    private int allocatePort(String voiceName) {
        var sorted = new ArrayList<>(voices.keySet());
        Collections.sort(sorted);
        return config.basePort() + sorted.indexOf(voiceName);
    }

    private Slot startVoiceServer(String voiceName, int port) {
        Path voicePath = voices.get(voiceName);
        String baseUrl = "http://localhost:" + port;
        try {
            var cmd = List.of(
                    binary.toString(),
                    "-d", modelDir.toString(),
                    "--load-voice", voicePath.toString(),
                    "--serve", String.valueOf(port),
                    "--eos-strategy", "v2",
                    "-l", "en"
            );
            var process = new ProcessBuilder(cmd)
                    .redirectErrorStream(true)
                    .start();

            Thread.ofVirtual().name("qwen3-pool-" + voiceName).start(() -> {
                try (var reader = process.inputReader()) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        LOG.log(System.Logger.Level.DEBUG,
                                "[qwen3-pool:{0}] {1}", voiceName, line);
                    }
                } catch (IOException ignored) {}
            });

            for (int i = 0; i < 60; i++) {
                Thread.sleep(500);
                try {
                    var health = HttpRequest.newBuilder()
                            .uri(URI.create(baseUrl + "/v1/health"))
                            .timeout(Duration.ofSeconds(2))
                            .GET().build();
                    var resp = HttpClient.newHttpClient().send(health,
                            HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        LOG.log(System.Logger.Level.INFO,
                                "Voice pool: {0} ready on port {1}", voiceName, port);
                        var tts = Qwen3TextToSpeech.withExternalServer(baseUrl, null);
                        return new Slot(process, port, tts);
                    }
                } catch (IOException ignored) {}
            }
            process.destroyForcibly();
            throw new SherpaException(
                    "Voice server for " + voiceName + " failed to start within 30s");
        } catch (SherpaException e) {
            throw e;
        } catch (Exception e) {
            throw new SherpaException("Failed to start voice server for " + voiceName
                    + ": " + e.getMessage(), e);
        }
    }

    private void killSlot(Slot slot) {
        if (slot.process().isAlive()) {
            slot.process().destroy();
            try {
                slot.process().waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                slot.process().destroyForcibly();
            }
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            for (Slot slot : active.values()) {
                killSlot(slot);
            }
            active.clear();
        } finally {
            lock.unlock();
        }
    }
}
```

- [ ] **Step 7: Run tests to verify discovery passes**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3VoicePoolTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml --batch-mode`
Expected: All 5 tests PASS

- [ ] **Step 8: Write failing test for LRU eviction logic**

Add to `Qwen3VoicePoolTest`:

```java
@Test
void forVoiceRejectsUnknownName(@TempDir Path dir) throws Exception {
    java.nio.file.Files.createFile(dir.resolve("alice.qvoice"));

    var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
    var pool = Qwen3VoicePool.create(config);

    assertThatThrownBy(() -> pool.forVoice("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown voice");
}

@Test
void forVoiceReturnsStreamingTextToSpeech(@TempDir Path dir) throws Exception {
    java.nio.file.Files.createFile(dir.resolve("alice.qvoice"));

    var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
    var pool = Qwen3VoicePool.create(config);

    var tts = pool.forVoice("alice");
    assertThat(tts).isInstanceOf(io.casehub.connectors.voice.spi.StreamingTextToSpeech.class);
}

@Test
void portAllocationIsDeterministic(@TempDir Path dir) throws Exception {
    java.nio.file.Files.createFile(dir.resolve("alice.qvoice"));
    java.nio.file.Files.createFile(dir.resolve("bob.qvoice"));
    java.nio.file.Files.createFile(dir.resolve("charlie.qvoice"));

    var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8900);
    var pool = Qwen3VoicePool.create(config);

    // Ports are basePort + alphabetical index: alice=8900, bob=8901, charlie=8902
    assertThat(pool.allocatePort("alice")).isEqualTo(8900);
    assertThat(pool.allocatePort("bob")).isEqualTo(8901);
    assertThat(pool.allocatePort("charlie")).isEqualTo(8902);
}
```

Note: `allocatePort` needs to be made package-private for testability.

- [ ] **Step 9: Run tests to verify they pass**

Run: `mvn test -pl voice-sherpa -Dtest=Qwen3VoicePoolTest -f /Users/mdproctor/claude/casehub/connectors/pom.xml --batch-mode`
Expected: All 8 tests PASS

- [ ] **Step 10: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/connectors add voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePoolConfig.java voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePool.java voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3VoicePoolTest.java
git -C /Users/mdproctor/claude/casehub/connectors commit -m "feat: add Qwen3VoicePool — pooled multi-voice .qvoice server management

Refs casehubio/agency#451"
```

### Task 2: Qwen3ClonedVoice delegate and speaker-field fix

**Files:**
- Create: `connectors/voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3ClonedVoice.java`
- Modify: `connectors/voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3TextToSpeech.java:112-146` (synthesise — handle null speaker)
- Modify: `connectors/voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3TextToSpeech.java:149-207` (synthesiseStreaming — handle null speaker)
- Test: `connectors/voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3ClonedVoiceTest.java`

**Interfaces:**
- Consumes: `Qwen3VoicePool.acquireSlot(String)` → `Slot`, `Qwen3TextToSpeech.synthesise/synthesiseStreaming`
- Produces: `Qwen3ClonedVoice` implements `StreamingTextToSpeech` — `synthesise(String, SynthesisOptions)` → `SynthesisResult`, `synthesiseStreaming(String, SynthesisOptions, Consumer<byte[]>)` → void

The `Qwen3ClonedVoice` delegates to a pool-managed `Qwen3TextToSpeech`. The loaded `.qvoice` IS the voice, so the `speaker` field must be omitted from HTTP requests (the server uses the loaded voice when no speaker is specified). `Qwen3TextToSpeech` currently always sends the `speaker` field — it needs to handle `null` by omitting the field.

- [ ] **Step 1: Write Qwen3ClonedVoice**

```java
package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.model.SynthesisOptions;
import io.casehub.connectors.voice.model.SynthesisResult;
import io.casehub.connectors.voice.spi.StreamingTextToSpeech;

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
        var slot = pool.acquireSlot(voiceName);
        return slot.tts().synthesise(text, options);
    }

    @Override
    public void synthesiseStreaming(String text, SynthesisOptions options,
                                    Consumer<byte[]> onWavChunk) {
        var slot = pool.acquireSlot(voiceName);
        slot.tts().synthesiseStreaming(text, options, onWavChunk);
    }

    public String voiceName() {
        return voiceName;
    }
}
```

- [ ] **Step 2: Fix Qwen3TextToSpeech to handle null speaker**

In `Qwen3TextToSpeech.synthesise()`, change the voice field to be conditional:

Replace line 118 (`body.addProperty("voice", speaker);`) with:
```java
if (speaker != null) body.addProperty("voice", speaker);
```

In `Qwen3TextToSpeech.synthesiseStreaming()`, change the speaker field to be conditional:

Replace line 153 (`body.addProperty("speaker", speaker);`) with:
```java
if (speaker != null) body.addProperty("speaker", speaker);
```

- [ ] **Step 3: Write failing test for Qwen3ClonedVoice**

```java
package io.casehub.connectors.voice.sherpa;

import io.casehub.connectors.voice.spi.StreamingTextToSpeech;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Qwen3ClonedVoiceTest {

    @Test
    void implementsStreamingTextToSpeech(@TempDir Path dir) throws Exception {
        java.nio.file.Files.createFile(dir.resolve("test.qvoice"));
        var config = new Qwen3VoicePoolConfig(dir, "0.6b-base", 3, 8800);
        var pool = Qwen3VoicePool.create(config);

        StreamingTextToSpeech voice = pool.forVoice("test");
        assertThat(voice).isInstanceOf(Qwen3ClonedVoice.class);
        assertThat(((Qwen3ClonedVoice) voice).voiceName()).isEqualTo("test");
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn test -pl voice-sherpa -Dtest="Qwen3ClonedVoiceTest,Qwen3VoicePoolTest" -f /Users/mdproctor/claude/casehub/connectors/pom.xml --batch-mode`
Expected: All tests PASS

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/connectors add voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3ClonedVoice.java voice-sherpa/src/main/java/io/casehub/connectors/voice/sherpa/Qwen3TextToSpeech.java voice-sherpa/src/test/java/io/casehub/connectors/voice/sherpa/Qwen3ClonedVoiceTest.java
git -C /Users/mdproctor/claude/casehub/connectors commit -m "feat: add Qwen3ClonedVoice delegate, handle null speaker in Qwen3TextToSpeech

Refs casehubio/agency#451"
```

- [ ] **Step 6: Install connectors to local Maven**

Run: `mvn install -DskipTests -f /Users/mdproctor/claude/casehub/connectors/pom.xml --batch-mode`

This makes the new classes available to the agency repo.

## Batch 2: Demo Integration (agency speech-demo)

### Task 3: Register cloned voices in SpeechProducers

**Files:**
- Modify: `agency/speech-demo/src/main/java/io/casehub/agency/speech/demo/SpeechProducers.java:85-97` (add cloned voice registration after preset Qwen3 block)

**Interfaces:**
- Consumes: `Qwen3VoicePool.create(Qwen3VoicePoolConfig)` → `Qwen3VoicePool`, `availableVoices()` → `List<String>`, `forVoice(String)` → `StreamingTextToSpeech`
- Produces: TtsModelRegistry entries keyed as `qwen3:clone:<voiceName>`

- [ ] **Step 1: Add cloned voice registration to SpeechProducers.ttsRegistry()**

After the existing Qwen3 preset voices block (around line 96, after the closing brace of the try/catch), add:

```java
// Qwen3-TTS cloned voices (pooled Base model servers)
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

- [ ] **Step 2: Verify agency compiles with the new dependency**

Run: `mvn compile -pl speech-demo -f /Users/mdproctor/claude/casehub/slots/223/agency/pom.xml --batch-mode`
Expected: BUILD SUCCESS

- [ ] **Step 3: Run existing speech-ws tests to ensure no regression**

Run: `mvn test -pl speech-ws -f /Users/mdproctor/claude/casehub/slots/223/agency/pom.xml --batch-mode`
Expected: All 88 tests PASS

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/223/agency add speech-demo/src/main/java/io/casehub/agency/speech/demo/SpeechProducers.java
git -C /Users/mdproctor/claude/casehub/slots/223/agency commit -m "feat: register Qwen3-TTS cloned voices from voice pool in demo

Refs #451"
```

## References

- [2026-11-10-qwen3-multi-voice-server-design.md] — design spec this plan implements
- [connectors/voice-sherpa/.../Qwen3TextToSpeech.java] — existing HTTP client wrapper (lines 112-207 for synthesis methods)
- [connectors/voice-sherpa/.../Provisioner.java] — binary and model provisioning (`ensureQwen3Binary`, `ensureQwen3Model`)
- [agency/speech-demo/.../SpeechProducers.java:85-97] — existing Qwen3 preset voice registration
- [connectors/voice-spi/.../StreamingTextToSpeech.java] — SPI interface
- [connectors/voice-spi/.../TextToSpeech.java] — base SPI interface
- [GitHub #451] — focal issue
