# Microphone Capture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #183 — Live microphone capture → RecognitionStream
**Issue group:** #183

**Goal:** Build a `MicrophoneCapture` utility that bridges `javax.sound.sampled.TargetDataLine` to `RecognitionStream.acceptSamples()` on a background virtual thread, plus a CLI `listen` command.

**Architecture:** Single class `MicrophoneCapture` in speech-sherpa, package `io.casehub.blocks.speech.sherpa`. Constructor takes injectable `TargetDataLine` for testability; factory `openDefault()` for convenience. Pure audio feeder — caller polls `RecognitionStream` for results. New `listen` command in `SpeechCli`.

**Tech Stack:** Java 22, javax.sound.sampled, virtual threads, JUnit 5 + Mockito + AssertJ

## Global Constraints

- Java 22 compiler target (speech-sherpa module)
- No CDI/Quarkus — plain JUnit 5 tests with Mockito
- Audio format: 16-bit signed PCM, mono, little-endian
- Chunk duration: 100ms (sampleRate / 10 samples per chunk)
- Default sample rate: 16000 Hz

---

## Batch 1: MicrophoneCapture + CLI listen command

### Task 1: MicrophoneCapture class with pcmToFloat and lifecycle

**Files:**
- Create: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/MicrophoneCapture.java`
- Create: `speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/MicrophoneCaptureTest.java`

**Interfaces:**
- Consumes: `RecognitionStream` from speech-api (`acceptSamples(float[], int)`, `close()`)
- Produces: `MicrophoneCapture` — `new MicrophoneCapture(RecognitionStream, TargetDataLine, int)`, `openDefault(RecognitionStream)`, `openDefault(RecognitionStream, int)`, `start()`, `stop()`, `close()`, `static float[] pcmToFloat(byte[], int)`

- [ ] **Step 1: Write pcmToFloat unit tests**

```java
package io.casehub.blocks.speech.sherpa;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrophoneCaptureTest {

    @Test
    void pcmToFloat_silence() {
        byte[] pcm = new byte[]{0, 0, 0, 0, 0, 0};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, pcm.length);
        assertThat(result).hasSize(3);
        assertThat(result).containsExactly(0f, 0f, 0f);
    }

    @Test
    void pcmToFloat_maxPositive() {
        // 0x7FFF = 32767 in little-endian: 0xFF, 0x7F
        byte[] pcm = new byte[]{(byte) 0xFF, 0x7F};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, pcm.length);
        assertThat(result).hasSize(1);
        assertThat(result[0]).isCloseTo(32767f / 32768f, org.assertj.core.data.Offset.offset(0.0001f));
    }

    @Test
    void pcmToFloat_maxNegative() {
        // 0x8000 = -32768 in little-endian: 0x00, 0x80
        byte[] pcm = new byte[]{0x00, (byte) 0x80};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, pcm.length);
        assertThat(result).hasSize(1);
        assertThat(result[0]).isEqualTo(-1.0f);
    }

    @Test
    void pcmToFloat_oddByteCount_ignoresTrailingByte() {
        // 3 bytes = 1 full sample + 1 trailing byte
        byte[] pcm = new byte[]{0x00, 0x40, 0xFF};
        float[] result = MicrophoneCapture.pcmToFloat(pcm, pcm.length);
        assertThat(result).hasSize(1);
        // 0x4000 = 16384 → 16384/32768 = 0.5
        assertThat(result[0]).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.0001f));
    }

    @Test
    void pcmToFloat_partialLength() {
        byte[] pcm = new byte[]{0x00, 0x40, (byte) 0xFF, 0x7F};
        // Only read first 2 bytes
        float[] result = MicrophoneCapture.pcmToFloat(pcm, 2);
        assertThat(result).hasSize(1);
        assertThat(result[0]).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.0001f));
    }
}
```

- [ ] **Step 2: Run tests — verify they fail (class not found)**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=MicrophoneCaptureTest`
Expected: Compilation failure — `MicrophoneCapture` does not exist.

- [ ] **Step 3: Implement MicrophoneCapture**

Create `MicrophoneCapture.java` using `ide_create_file`:

```java
package io.casehub.blocks.speech.sherpa;

import io.casehub.blocks.speech.RecognitionStream;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.util.Objects;

public final class MicrophoneCapture implements AutoCloseable {

    private static final int DEFAULT_SAMPLE_RATE = 16_000;
    private static final int CHUNK_MILLIS = 100;

    private final RecognitionStream stream;
    private final TargetDataLine line;
    private final int sampleRate;
    private final int chunkSamples;
    private volatile boolean running;
    private volatile boolean closed;
    private volatile Throwable captureError;
    private Thread captureThread;

    public MicrophoneCapture(RecognitionStream stream, TargetDataLine line, int sampleRate) {
        this.stream = Objects.requireNonNull(stream, "stream");
        this.line = Objects.requireNonNull(line, "line");
        if (sampleRate <= 0) throw new IllegalArgumentException("sampleRate must be positive");
        this.sampleRate = sampleRate;
        this.chunkSamples = sampleRate * CHUNK_MILLIS / 1000;
    }

    public static MicrophoneCapture openDefault(RecognitionStream stream) {
        return openDefault(stream, DEFAULT_SAMPLE_RATE);
    }

    public static MicrophoneCapture openDefault(RecognitionStream stream, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        try {
            TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
            line.open(format);
            return new MicrophoneCapture(stream, line, sampleRate);
        } catch (LineUnavailableException e) {
            throw new SherpaException("No microphone available", e);
        }
    }

    public void start() {
        if (closed) throw new IllegalStateException("Capture is closed");
        if (running) throw new IllegalStateException("Capture is already running");
        running = true;
        line.start();
        captureThread = Thread.ofVirtual().name("mic-capture").start(this::captureLoop);
    }

    public void stop() {
        if (!running) return;
        running = false;
        line.stop();
        if (captureThread != null) {
            try {
                captureThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (captureError != null) {
            throw new SherpaException("Capture failed", captureError);
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        stop();
        line.close();
    }

    private void captureLoop() {
        int bytesPerChunk = chunkSamples * 2;
        byte[] buffer = new byte[bytesPerChunk];
        while (running) {
            int read = line.read(buffer, 0, bytesPerChunk);
            if (read <= 0) continue;
            try {
                float[] samples = pcmToFloat(buffer, read);
                stream.acceptSamples(samples, sampleRate);
            } catch (Throwable t) {
                captureError = t;
                running = false;
            }
        }
    }

    static float[] pcmToFloat(byte[] pcm, int length) {
        int sampleCount = length / 2;
        float[] samples = new float[sampleCount];
        for (int i = 0; i < sampleCount; i++) {
            int lo = pcm[2 * i] & 0xFF;
            int hi = pcm[2 * i + 1];
            samples[i] = (short) (lo | (hi << 8)) / 32768f;
        }
        return samples;
    }
}
```

- [ ] **Step 4: Run pcmToFloat tests — verify they pass**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=MicrophoneCaptureTest`
Expected: All 5 tests PASS.

- [ ] **Step 5: Add capture loop and lifecycle tests**

Append to `MicrophoneCaptureTest.java`. These tests use a mock `TargetDataLine` to verify the capture loop feeds correct data to `RecognitionStream`:

```java
import io.casehub.blocks.speech.RecognitionStream;
import io.casehub.blocks.speech.TranscriptionResult;
import org.junit.jupiter.api.AfterEach;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// --- Capture loop tests ---

@Test
void captureLoop_feedsSamplesToStream() throws Exception {
    // 16-bit PCM: two samples of 0x4000 (0.5f)
    byte[] pcmData = new byte[]{0x00, 0x40, 0x00, 0x40};
    var readCount = new AtomicInteger(0);
    var latch = new CountDownLatch(1);

    TargetDataLine mockLine = new StubTargetDataLine() {
        @Override
        public int read(byte[] b, int off, int len) {
            if (readCount.getAndIncrement() == 0) {
                System.arraycopy(pcmData, 0, b, off, pcmData.length);
                return pcmData.length;
            }
            latch.countDown();
            return 0;
        }
    };

    List<float[]> received = new CopyOnWriteArrayList<>();
    RecognitionStream mockStream = new StubRecognitionStream() {
        @Override
        public void acceptSamples(float[] samples, int sampleRate) {
            received.add(samples);
        }
    };

    var capture = new MicrophoneCapture(mockStream, mockLine, 16000);
    capture.start();
    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    capture.stop();

    assertThat(received).hasSize(1);
    assertThat(received.get(0)).hasSize(2);
    assertThat(received.get(0)[0]).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.001f));
    assertThat(received.get(0)[1]).isCloseTo(0.5f, org.assertj.core.data.Offset.offset(0.001f));
}

@Test
void captureLoop_stopsOnStreamException() throws Exception {
    var readCount = new AtomicInteger(0);
    TargetDataLine mockLine = new StubTargetDataLine() {
        @Override
        public int read(byte[] b, int off, int len) {
            if (readCount.getAndIncrement() < 10) {
                java.util.Arrays.fill(b, off, off + Math.min(len, 4), (byte) 0);
                return 4;
            }
            return 0;
        }
    };

    RecognitionStream failingStream = new StubRecognitionStream() {
        @Override
        public void acceptSamples(float[] samples, int sampleRate) {
            throw new RuntimeException("stream error");
        }
    };

    var capture = new MicrophoneCapture(failingStream, mockLine, 16000);
    capture.start();
    Thread.sleep(200);
    org.assertj.core.api.Assertions.assertThatThrownBy(capture::stop)
            .isInstanceOf(SherpaException.class)
            .hasMessageContaining("Capture failed");
}

@Test
void start_whenAlreadyRunning_throws() {
    TargetDataLine mockLine = new StubTargetDataLine();
    RecognitionStream mockStream = new StubRecognitionStream();
    var capture = new MicrophoneCapture(mockStream, mockLine, 16000);
    capture.start();
    org.assertj.core.api.Assertions.assertThatThrownBy(capture::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already running");
    capture.close();
}

@Test
void start_afterClose_throws() {
    TargetDataLine mockLine = new StubTargetDataLine();
    RecognitionStream mockStream = new StubRecognitionStream();
    var capture = new MicrophoneCapture(mockStream, mockLine, 16000);
    capture.close();
    org.assertj.core.api.Assertions.assertThatThrownBy(capture::start)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("closed");
}

@Test
void stop_whenNotStarted_isNoOp() {
    TargetDataLine mockLine = new StubTargetDataLine();
    RecognitionStream mockStream = new StubRecognitionStream();
    var capture = new MicrophoneCapture(mockStream, mockLine, 16000);
    capture.stop(); // no exception
}

@Test
void close_isIdempotent() {
    TargetDataLine mockLine = new StubTargetDataLine();
    RecognitionStream mockStream = new StubRecognitionStream();
    var capture = new MicrophoneCapture(mockStream, mockLine, 16000);
    capture.close();
    capture.close(); // no exception
}
```

These tests require two test doubles. Add them as static inner classes in the test file:

```java
// Minimal test doubles — only override methods used by tests

static class StubTargetDataLine implements TargetDataLine {
    @Override public void open(AudioFormat format) {}
    @Override public void open(AudioFormat format, int bufferSize) {}
    @Override public int read(byte[] b, int off, int len) { return 0; }
    @Override public void drain() {}
    @Override public void flush() {}
    @Override public void start() {}
    @Override public void stop() {}
    @Override public boolean isRunning() { return false; }
    @Override public boolean isActive() { return false; }
    @Override public AudioFormat getFormat() { return null; }
    @Override public int getBufferSize() { return 0; }
    @Override public int available() { return 0; }
    @Override public int getFramePosition() { return 0; }
    @Override public long getLongFramePosition() { return 0; }
    @Override public long getMicrosecondPosition() { return 0; }
    @Override public float getLevel() { return 0; }
    @Override public javax.sound.sampled.Line.Info getLineInfo() { return null; }
    @Override public void open() {}
    @Override public void close() {}
    @Override public boolean isOpen() { return true; }
    @Override public javax.sound.sampled.Control[] getControls() { return new javax.sound.sampled.Control[0]; }
    @Override public boolean isControlSupported(javax.sound.sampled.Control.Type control) { return false; }
    @Override public javax.sound.sampled.Control getControl(javax.sound.sampled.Control.Type control) { return null; }
    @Override public void addLineListener(javax.sound.sampled.LineListener listener) {}
    @Override public void removeLineListener(javax.sound.sampled.LineListener listener) {}
}

static class StubRecognitionStream implements RecognitionStream {
    @Override public void acceptSamples(float[] samples, int sampleRate) {}
    @Override public boolean isEndpointDetected() { return false; }
    @Override public String partialResult() { return ""; }
    @Override public TranscriptionResult finalResult() { return new TranscriptionResult("", "", 0); }
    @Override public void close() {}
}
```

- [ ] **Step 6: Run all tests — verify they pass**

Run: `mvn --batch-mode test -pl speech-sherpa -Dtest=MicrophoneCaptureTest`
Expected: All 11 tests PASS.

- [ ] **Step 7: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/MicrophoneCapture.java speech-sherpa/src/test/java/io/casehub/blocks/speech/sherpa/MicrophoneCaptureTest.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#183): add MicrophoneCapture — TargetDataLine → RecognitionStream bridge

Refs #183"
```

### Task 2: SpeechCli `listen` command

**Files:**
- Modify: `speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SpeechCli.java`

**Interfaces:**
- Consumes: `MicrophoneCapture.openDefault(RecognitionStream)`, `SherpaOnnxStreamingSpeechToText`, `RecognitionStream` (from Task 1)

- [ ] **Step 1: Add `listen` case to SpeechCli.main()**

In `SpeechCli.main()`, add `"listen"` to the switch:

```java
case "listen" -> listen(args);
```

- [ ] **Step 2: Implement listen method**

Add to `SpeechCli.java`:

```java
private static void listen(String[] args) throws Exception {
    if (args.length < 2) {
        System.err.println("Usage: listen <model-dir>");
        System.exit(1);
    }
    Path modelDir = Path.of(args[1]);

    var config = SherpaConfig.defaults(modelDir);
    var stt = new SherpaOnnxStreamingSpeechToText(config);

    System.err.println("Listening... (press Enter to stop)");

    try (RecognitionStream rs = stt.startStream(TranscriptionOptions.defaults());
         MicrophoneCapture mic = MicrophoneCapture.openDefault(rs)) {

        mic.start();
        String last = "";

        while (System.in.available() == 0) {
            String partial = rs.partialResult().trim();
            if (!partial.equals(last) && !partial.isEmpty()) {
                System.out.printf("\r> %s", partial);
                System.out.flush();
                last = partial;
            }
            if (rs.isEndpointDetected()) {
                if (!last.isEmpty()) {
                    System.out.println();
                    last = "";
                }
            }
            Thread.sleep(100);
        }
        System.in.read(); // consume the Enter

        mic.stop();
        String finalText = rs.finalResult().text().trim();
        if (!finalText.isEmpty()) {
            System.out.println();
            System.out.println(finalText);
        }
    }
    stt.close();
}
```

- [ ] **Step 3: Update usage message**

Update `usage()` to include the listen command:

```java
System.err.println("  listen     <model-dir>                     Live microphone STT (Zipformer)");
```

- [ ] **Step 4: Run full test suite to verify no regressions**

Run: `mvn --batch-mode test -pl speech-sherpa`
Expected: All tests PASS. (listen command itself can only be tested manually with a real mic)

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/161/blocks add speech-sherpa/src/main/java/io/casehub/blocks/speech/sherpa/SpeechCli.java
git -C /Users/mdproctor/claude/casehub/slots/161/blocks commit -m "feat(#183): add SpeechCli listen command — live microphone transcription

Refs #183"
```

## References

- [2026-08-26-microphone-capture-design.md] — design spec this plan implements
- [RecognitionStream.java] — SPI being fed (speech-api)
- [SherpaOnnxStreamingSpeechToText.java:135-220] — existing SherpaRecognitionStream
- [SpeechCli.java:71-110] — stream command (chunking pattern reference)
- [WavReader.java] — existing PCM-to-float conversion
- [GitHub #183] — Live microphone capture → RecognitionStream
- [D6 in decisions.md] — API shape decision
