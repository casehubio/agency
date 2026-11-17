# Qwen3-TTS Multi-Voice Server

**Issue:** casehubio/agency#451
**Date:** 2026-11-10

## Problem

The `qwen_tts` binary (v0.23.0) only loads one `.qvoice` file per server instance via `--load-voice`. There is no per-request voice switching API — the loaded voice becomes the default, and the `/v1/speakers` endpoint only lists model-declared preset names. Passing a `.qvoice` path as a speaker name returns HTTP 400.

Cloned voices require the `0.6b-base` model variant (distinct from `0.6b-customvoice` used for preset voices). The two model types cannot serve from one instance.

12 `.qvoice` files exist at `~/.casehub/voice-samples/`, each ~16MB. Running all 12 as separate servers would consume ~10GB RAM.

## Design

### `Qwen3VoicePool` (connectors-voice-sherpa)

A pool manager that lazily starts `qwen_tts` server processes — one per active `.qvoice` voice — with LRU eviction when the pool reaches capacity.

```
Qwen3VoicePool
  ├── config: Qwen3VoicePoolConfig (voiceDir, modelVariant, maxActive, basePort)
  ├── voices: Map<String, Path>          # voiceName → .qvoice file path
  ├── active: LinkedHashMap<String, Slot> # LRU-ordered active servers
  └── forVoice(name) → TextToSpeech      # returns a delegating wrapper
```

**Slot** holds: the `Process`, the allocated port, and a `Qwen3TextToSpeech` instance pointing at `localhost:<port>`.

#### Lifecycle

1. **Construction**: Scan `voiceDir` for `*.qvoice` files. Build the `voices` map (filename sans extension → path). Provision binary and model via `Provisioner`.
2. **`forVoice(String name)`**: Returns a `Qwen3ClonedVoice` (implements `StreamingTextToSpeech`, `AutoCloseable`) that delegates to the pool. The delegate is resolved lazily on first synthesis call.
3. **Slot acquisition** (inside `Qwen3ClonedVoice.synthesise/synthesiseStreaming`):
   - If voice has an active slot → use it (move to MRU end)
   - If pool has capacity → start a new server, add slot
   - If pool is full → evict LRU slot (kill process), start new server
4. **Health recovery**: Before each synthesis call, check if the server process is alive. If dead, restart it on the same port.
5. **`close()`**: Kill all active server processes. Called by the application on shutdown.

#### Configuration

```java
public record Qwen3VoicePoolConfig(
    Path voiceDir,           // directory containing .qvoice files
    String modelVariant,     // "0.6b-base" (default)
    int maxActive,           // max concurrent server processes (default 3)
    int basePort             // starting port number (default 8800)
) {
    public static Qwen3VoicePoolConfig defaults() {
        return new Qwen3VoicePoolConfig(
            Path.of(System.getProperty("user.home"), ".casehub", "voice-samples"),
            "0.6b-base", 3, 8800);
    }
}
```

#### Thread Safety

- `ReentrantLock` guards all pool mutations (start, evict, health-recover)
- `Qwen3ClonedVoice` instances are thread-safe — they acquire the lock only for slot resolution, then delegate to the thread-safe `Qwen3TextToSpeech` HTTP client
- Port allocation is sequential from `basePort` — each voice gets a deterministic port (`basePort + index`)

#### Server Startup

Each server process is started with:
```
qwen_tts -d <modelDir> --load-voice <voicePath> --serve <port> --eos-strategy v2 -l en
```

Health-check loop: poll `/v1/health` every 500ms for up to 30s (same as existing `Qwen3TextToSpeech.startServer`).

### `Qwen3ClonedVoice` (connectors-voice-sherpa)

A `StreamingTextToSpeech` implementation that delegates to a pooled server. One instance per registered voice — returned by `Qwen3VoicePool.forVoice()`.

```java
public class Qwen3ClonedVoice implements StreamingTextToSpeech, AutoCloseable {
    // Delegates synthesise/synthesiseStreaming to the pool-managed server
    // Speaker field is omitted in requests (the loaded .qvoice IS the voice)
}
```

### Demo Integration (agency `SpeechProducers`)

After the existing Qwen3 preset voice registration block:

```java
// Qwen3-TTS cloned voices (pooled Base model servers)
try {
    var pool = Qwen3VoicePool.create(Qwen3VoicePoolConfig.defaults());
    for (String voice : pool.availableVoices()) {
        models.put("qwen3:clone:" + voice, pool.forVoice(voice));
    }
    LOG.log(Level.INFO, "Qwen3-TTS cloned voices: {0}", pool.availableVoices());
} catch (Exception e) {
    LOG.log(Level.WARNING, "Qwen3-TTS voice pool unavailable: " + e.getMessage());
}
```

Voices appear in the demo dropdown as `qwen3:clone:jodi_krangle`, `qwen3:clone:cori_samuel`, etc.

### Port Allocation

| Range | Use |
|-------|-----|
| 8790 | CustomVoice server (existing, preset voices) |
| 8800–8811 | Voice pool (one port per .qvoice, deterministic by alphabetical index) |

### Memory Budget

With `maxActive=3`: ~2.5GB RAM for 3 Base model server processes. The CustomVoice server adds another ~850MB. Total: ~3.4GB for the full TTS stack.

## Testing

- Unit test `Qwen3VoicePool`: mock `Provisioner` and `Process`, verify LRU eviction logic, port allocation, health recovery
- Unit test `Qwen3ClonedVoice`: verify delegation, speaker field omission
- Integration: manual — start demo, switch between cloned voices in the UI

## Scope Exclusions

- No changes to the `qwen_tts` binary itself
- No voice quality improvements (separate issue #452)
- No voice management UI (add/remove voices at runtime)

## References

- `connectors/voice-sherpa/.../Qwen3TextToSpeech.java` — existing HTTP client wrapper
- `connectors/voice-sherpa/.../Provisioner.java` — binary and model provisioning
- `agency/speech-demo/.../SpeechProducers.java:87-96` — existing Qwen3 voice registration
- `qwen_tts --help` — binary CLI options (v0.23.0)
- Binary testing: confirmed `--load-voice` only loads one voice, no per-request voice API
