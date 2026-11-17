# Avatar Speech WebSocket Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #195 — blocks-ui: Lit-based avatar web component
**Issue group:** #194, #195

**Goal:** Create the speech-ws Maven module in the blocks reactor — the server-side WebSocket endpoint that composes speech SPIs into a real-time avatar speech pipeline.

**Architecture:** New `speech-ws` module depends on speech-api SPIs (not sherpa) and quarkus-websockets-next. The WebSocket accepts PCM audio (binary frames), runs STT + cleanup, generates LLM responses via AgentProvider, produces TTS with phoneme timing, maps phonemes to Oculus visemes, and sends everything back. Protocol DTOs are the contract between this module and the blocks-ui Lit components.

**Tech Stack:** Java 17, Quarkus WebSockets Next, speech-api SPIs, platform-agent-api (AgentProvider), gson, JUnit 5 + Mockito

## Global Constraints

- JDK target: 17 (no FFM — this module uses SPIs, not native bindings)
- No Quarkus container in tests — plain JUnit 5 with Mockito
- speech-ws depends on speech-api interfaces only, never on speech-sherpa
- gson for JSON serialization (already used in speech-sherpa, consistent choice)
- Quarkus WebSockets Next: package is `io.quarkus.websockets.next` (plural — GE-20260813-193670)
- Jandex plugin required for consumer bean discovery

---

## Batch 1: Protocol foundation

### Task 1: speech-ws module scaffold + protocol DTOs + serialization tests

**Files:**
- Create: `speech-ws/pom.xml`
- Modify: `pom.xml` (parent — add module)
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/protocol/VisemeFrame.java`
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/protocol/ConversationTurn.java`
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/protocol/AvatarMessage.java`
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/protocol/MessageCodec.java`
- Test: `speech-ws/src/test/java/io/casehub/blocks/speech/ws/protocol/MessageCodecTest.java`

**Interfaces:**
- Produces: `VisemeFrame(String viseme, long startMs, long endMs)` — used by Task 2 (VisemeMapping) and Task 4 (SpeechWebSocket)
- Produces: `ConversationTurn(String role, String text)` — used by Task 3 (PromptAssembler)
- Produces: `AvatarMessage` sealed hierarchy — used by Task 4 (SpeechWebSocket)
- Produces: `MessageCodec.encode(AvatarMessage) → String` and `MessageCodec.decodeClient(String) → AvatarMessage` — used by Task 4

- [ ] **Step 1: Create speech-ws/pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-parent</artifactId>
    <version>0.2-SNAPSHOT</version>
  </parent>

  <artifactId>casehub-blocks-speech-ws</artifactId>
  <name>casehub-blocks-speech-ws</name>
  <description>WebSocket endpoint for real-time avatar speech pipeline</description>

  <dependencies>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-blocks-speech-api</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>io.casehub</groupId>
      <artifactId>casehub-platform-agent-api</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>io.quarkus</groupId>
      <artifactId>quarkus-websockets-next</artifactId>
      <scope>provided</scope>
    </dependency>
    <dependency>
      <groupId>com.google.code.gson</groupId>
      <artifactId>gson</artifactId>
      <version>2.12.1</version>
    </dependency>
    <dependency>
      <groupId>org.jspecify</groupId>
      <artifactId>jspecify</artifactId>
      <version>1.0.0</version>
    </dependency>

    <!-- Test -->
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>io.smallrye</groupId>
        <artifactId>jandex-maven-plugin</artifactId>
        <executions>
          <execution>
            <id>make-index</id>
            <goals>
              <goal>jandex</goal>
            </goals>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

- [ ] **Step 2: Register module in parent pom.xml**

Add `<module>speech-ws</module>` to the `<modules>` section in the parent pom.xml (after `speech-api`, before the closing `</modules>` tag). This goes in the default modules list, NOT in the jdk22+ profile.

- [ ] **Step 3: Write the protocol DTO records**

Create `VisemeFrame.java`:

```java
package io.casehub.blocks.speech.ws.protocol;

public record VisemeFrame(String viseme, long startMs, long endMs) {
    public VisemeFrame {
        if (viseme == null || viseme.isBlank()) throw new IllegalArgumentException("viseme required");
        if (endMs < startMs) throw new IllegalArgumentException("endMs < startMs");
    }
}
```

Create `ConversationTurn.java`:

```java
package io.casehub.blocks.speech.ws.protocol;

public record ConversationTurn(String role, String text) {
    public ConversationTurn {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role required");
        if (text == null) throw new IllegalArgumentException("text required");
    }
}
```

Create `AvatarMessage.java` — sealed interface with all message variants:

```java
package io.casehub.blocks.speech.ws.protocol;

import java.util.List;

public sealed interface AvatarMessage {

    // Client → Server
    record Start(int sampleRate) implements AvatarMessage {}
    record Stop() implements AvatarMessage {}

    // Server → Client
    record Partial(String text) implements AvatarMessage {}
    record Transcript(String text) implements AvatarMessage {}
    record Response(String text) implements AvatarMessage {}
    record Phonemes(List<VisemeFrame> data) implements AvatarMessage {
        public Phonemes { data = List.copyOf(data); }
    }
    record Error(String message) implements AvatarMessage {}
}
```

- [ ] **Step 4: Write MessageCodec**

Create `MessageCodec.java` — encodes server messages to JSON, decodes client messages from JSON:

```java
package io.casehub.blocks.speech.ws.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.util.List;

public final class MessageCodec {

    private static final Gson GSON = new Gson();

    private MessageCodec() {}

    public static String encode(AvatarMessage msg) {
        var obj = new JsonObject();
        switch (msg) {
            case AvatarMessage.Partial p -> {
                obj.addProperty("type", "partial");
                obj.addProperty("text", p.text());
            }
            case AvatarMessage.Transcript t -> {
                obj.addProperty("type", "transcript");
                obj.addProperty("text", t.text());
            }
            case AvatarMessage.Response r -> {
                obj.addProperty("type", "response");
                obj.addProperty("text", r.text());
            }
            case AvatarMessage.Phonemes ph -> {
                obj.addProperty("type", "phonemes");
                obj.add("data", GSON.toJsonTree(ph.data()));
            }
            case AvatarMessage.Error e -> {
                obj.addProperty("type", "error");
                obj.addProperty("message", e.message());
            }
            case AvatarMessage.Start s -> {
                obj.addProperty("type", "start");
                obj.addProperty("sampleRate", s.sampleRate());
            }
            case AvatarMessage.Stop s -> obj.addProperty("type", "stop");
        }
        return GSON.toJson(obj);
    }

    public static AvatarMessage decodeClient(String json) {
        var obj = JsonParser.parseString(json).getAsJsonObject();
        String type = obj.get("type").getAsString();
        return switch (type) {
            case "start" -> new AvatarMessage.Start(
                    obj.has("sampleRate") ? obj.get("sampleRate").getAsInt() : 16000);
            case "stop" -> new AvatarMessage.Stop();
            default -> throw new IllegalArgumentException("Unknown client message type: " + type);
        };
    }
}
```

- [ ] **Step 5: Write MessageCodecTest**

Create `MessageCodecTest.java`:

```java
package io.casehub.blocks.speech.ws.protocol;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MessageCodecTest {

    @Test
    void encodesPartialMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Partial("hello"));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("partial");
        assertThat(obj.get("text").getAsString()).isEqualTo("hello");
    }

    @Test
    void encodesTranscriptMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Transcript("Hello world."));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("transcript");
        assertThat(obj.get("text").getAsString()).isEqualTo("Hello world.");
    }

    @Test
    void encodesResponseMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Response("I see."));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("response");
    }

    @Test
    void encodesPhonemesMessage() {
        var frames = List.of(
                new VisemeFrame("DD", 0, 80),
                new VisemeFrame("aa", 80, 160));
        String json = MessageCodec.encode(new AvatarMessage.Phonemes(frames));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("phonemes");
        assertThat(obj.getAsJsonArray("data")).hasSize(2);
    }

    @Test
    void encodesErrorMessage() {
        String json = MessageCodec.encode(new AvatarMessage.Error("service down"));
        var obj = JsonParser.parseString(json).getAsJsonObject();
        assertThat(obj.get("type").getAsString()).isEqualTo("error");
        assertThat(obj.get("message").getAsString()).isEqualTo("service down");
    }

    @Test
    void decodesStartMessage() {
        var msg = MessageCodec.decodeClient("{\"type\":\"start\",\"sampleRate\":16000}");
        assertThat(msg).isInstanceOf(AvatarMessage.Start.class);
        assertThat(((AvatarMessage.Start) msg).sampleRate()).isEqualTo(16000);
    }

    @Test
    void decodesStartMessageWithDefaultSampleRate() {
        var msg = MessageCodec.decodeClient("{\"type\":\"start\"}");
        assertThat(((AvatarMessage.Start) msg).sampleRate()).isEqualTo(16000);
    }

    @Test
    void decodesStopMessage() {
        var msg = MessageCodec.decodeClient("{\"type\":\"stop\"}");
        assertThat(msg).isInstanceOf(AvatarMessage.Stop.class);
    }

    @Test
    void rejectsUnknownClientMessageType() {
        assertThatThrownBy(() -> MessageCodec.decodeClient("{\"type\":\"foo\"}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown client message type");
    }
}
```

- [ ] **Step 6: Run tests and verify**

Run: `mvn --batch-mode -pl speech-ws test`
Expected: All 9 tests pass. Module compiles, Jandex index generated.

- [ ] **Step 7: Commit**

```bash
git add speech-ws/ pom.xml
git commit -m "feat(#195): add speech-ws module — protocol DTOs and codec

New Maven module for avatar WebSocket endpoint. Contains:
- AvatarMessage sealed hierarchy (Start, Stop, Partial, Transcript,
  Response, Phonemes, Error)
- VisemeFrame and ConversationTurn records
- MessageCodec (JSON encode/decode via gson)
- Full serialization test suite

Refs #195"
```

---

### Task 2: Viseme mapping (IPA → Oculus)

**Files:**
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/VisemeMapping.java`
- Test: `speech-ws/src/test/java/io/casehub/blocks/speech/ws/VisemeMappingTest.java`

**Interfaces:**
- Consumes: `VisemeFrame(String viseme, long startMs, long endMs)` from Task 1
- Consumes: `PhonemeTiming(String phoneme, long startMs, long endMs)` from speech-api
- Produces: `VisemeMapping.toViseme(String ipaPhoneme) → String` — used by Task 4
- Produces: `VisemeMapping.convert(List<PhonemeTiming>) → List<VisemeFrame>` — used by Task 4

- [ ] **Step 1: Write VisemeMappingTest**

```java
package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.PhonemeTiming;
import io.casehub.blocks.speech.ws.protocol.VisemeFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class VisemeMappingTest {

    @ParameterizedTest
    @CsvSource({
            "p, PP", "b, PP", "m, PP",
            "f, FF", "v, FF",
            "θ, TH", "ð, TH",
            "t, DD", "d, DD", "l, DD",
            "k, kk", "g, kk", "ŋ, kk",
            "s, SS", "z, SS",
            "ʃ, CH", "ʒ, CH", "tʃ, CH", "dʒ, CH",
            "ɹ, RR", "r, RR",
            "n, nn",
            "ɑ, aa", "æ, aa", "ʌ, aa", "a, aa",
            "ɛ, E", "e, E", "ə, E",
            "ɪ, I", "i, I",
            "ɔ, O", "o, O", "ɒ, O",
            "ʊ, U", "u, U", "w, U"
    })
    void mapsIpaToOculusViseme(String ipa, String expectedViseme) {
        assertThat(VisemeMapping.toViseme(ipa)).isEqualTo(expectedViseme);
    }

    @Test
    void unknownPhonemeMappsToSilence() {
        assertThat(VisemeMapping.toViseme("ʔ")).isEqualTo("sil");
        assertThat(VisemeMapping.toViseme("")).isEqualTo("sil");
        assertThat(VisemeMapping.toViseme("xyz")).isEqualTo("sil");
    }

    @Test
    void convertsPhonemesTimingToVisemeFrames() {
        var phonemes = List.of(
                new PhonemeTiming("ð", 0, 60),
                new PhonemeTiming("ə", 60, 120),
                new PhonemeTiming("k", 120, 200));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames).containsExactly(
                new VisemeFrame("TH", 0, 60),
                new VisemeFrame("E", 60, 120),
                new VisemeFrame("kk", 120, 200));
    }

    @Test
    void collapsesConsecutiveIdenticalVisemes() {
        var phonemes = List.of(
                new PhonemeTiming("t", 0, 50),
                new PhonemeTiming("d", 50, 100),
                new PhonemeTiming("ɑ", 100, 200));
        List<VisemeFrame> frames = VisemeMapping.convert(phonemes);
        assertThat(frames).containsExactly(
                new VisemeFrame("DD", 0, 100),
                new VisemeFrame("aa", 100, 200));
    }

    @Test
    void emptyInputReturnsEmptyOutput() {
        assertThat(VisemeMapping.convert(List.of())).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode -pl speech-ws test -Dtest=VisemeMappingTest`
Expected: FAIL — `VisemeMapping` class not found.

- [ ] **Step 3: Implement VisemeMapping**

```java
package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.PhonemeTiming;
import io.casehub.blocks.speech.ws.protocol.VisemeFrame;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class VisemeMapping {

    private static final Map<String, String> IPA_TO_VISEME = Map.ofEntries(
            Map.entry("p", "PP"), Map.entry("b", "PP"), Map.entry("m", "PP"),
            Map.entry("f", "FF"), Map.entry("v", "FF"),
            Map.entry("θ", "TH"), Map.entry("ð", "TH"),
            Map.entry("t", "DD"), Map.entry("d", "DD"), Map.entry("l", "DD"),
            Map.entry("k", "kk"), Map.entry("g", "kk"), Map.entry("ŋ", "kk"),
            Map.entry("s", "SS"), Map.entry("z", "SS"),
            Map.entry("ʃ", "CH"), Map.entry("ʒ", "CH"),
            Map.entry("tʃ", "CH"), Map.entry("dʒ", "CH"),
            Map.entry("ɹ", "RR"), Map.entry("r", "RR"),
            Map.entry("n", "nn"),
            Map.entry("ɑ", "aa"), Map.entry("æ", "aa"),
            Map.entry("ʌ", "aa"), Map.entry("a", "aa"),
            Map.entry("ɛ", "E"), Map.entry("e", "E"), Map.entry("ə", "E"),
            Map.entry("ɪ", "I"), Map.entry("i", "I"),
            Map.entry("ɔ", "O"), Map.entry("o", "O"), Map.entry("ɒ", "O"),
            Map.entry("ʊ", "U"), Map.entry("u", "U"), Map.entry("w", "U"));

    private VisemeMapping() {}

    public static String toViseme(String ipaPhoneme) {
        if (ipaPhoneme == null || ipaPhoneme.isEmpty()) return "sil";
        return IPA_TO_VISEME.getOrDefault(ipaPhoneme, "sil");
    }

    public static List<VisemeFrame> convert(List<PhonemeTiming> phonemes) {
        if (phonemes.isEmpty()) return List.of();
        var result = new ArrayList<VisemeFrame>();
        String currentViseme = null;
        long currentStart = 0;
        long currentEnd = 0;
        for (PhonemeTiming pt : phonemes) {
            String viseme = toViseme(pt.phoneme());
            if (viseme.equals(currentViseme)) {
                currentEnd = pt.endMs();
            } else {
                if (currentViseme != null) {
                    result.add(new VisemeFrame(currentViseme, currentStart, currentEnd));
                }
                currentViseme = viseme;
                currentStart = pt.startMs();
                currentEnd = pt.endMs();
            }
        }
        if (currentViseme != null) {
            result.add(new VisemeFrame(currentViseme, currentStart, currentEnd));
        }
        return List.copyOf(result);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode -pl speech-ws test -Dtest=VisemeMappingTest`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add speech-ws/src/
git commit -m "feat(#195): add VisemeMapping — IPA to Oculus viseme conversion

Static mapping of IPA phonemes to 15 Oculus viseme IDs. Converts
PhonemeTiming lists to VisemeFrame lists with consecutive-viseme
collapsing.

Refs #195"
```

---

## Batch 2: WebSocket endpoint

### Task 3: PromptAssembler + default implementation

**Files:**
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/PromptAssembler.java`
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/DefaultPromptAssembler.java`
- Test: `speech-ws/src/test/java/io/casehub/blocks/speech/ws/DefaultPromptAssemblerTest.java`

**Interfaces:**
- Consumes: `ConversationTurn(String role, String text)` from Task 1
- Produces: `PromptAssembler.assemble(String userMessage, List<ConversationTurn> history) → String` — used by Task 4
- Produces: `DefaultPromptAssembler(String systemPrompt)` — used by Task 4

- [ ] **Step 1: Write DefaultPromptAssemblerTest**

```java
package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.ws.protocol.ConversationTurn;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPromptAssemblerTest {

    @Test
    void assemblesWithSystemPromptAndNoHistory() {
        var assembler = new DefaultPromptAssembler("You are a helpful assistant.");
        String result = assembler.assemble("Hello", List.of());
        assertThat(result).contains("You are a helpful assistant.");
        assertThat(result).contains("Hello");
    }

    @Test
    void includesConversationHistory() {
        var assembler = new DefaultPromptAssembler("Be concise.");
        var history = List.of(
                new ConversationTurn("user", "Hi"),
                new ConversationTurn("assistant", "Hello!"));
        String result = assembler.assemble("How are you?", history);
        assertThat(result).contains("Hi");
        assertThat(result).contains("Hello!");
        assertThat(result).contains("How are you?");
    }

    @Test
    void usesDefaultSystemPromptWhenNull() {
        var assembler = new DefaultPromptAssembler(null);
        String result = assembler.assemble("Test", List.of());
        assertThat(result).isNotBlank();
        assertThat(result).contains("Test");
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode -pl speech-ws test -Dtest=DefaultPromptAssemblerTest`
Expected: FAIL — classes not found.

- [ ] **Step 3: Implement PromptAssembler and DefaultPromptAssembler**

Create `PromptAssembler.java`:

```java
package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.ws.protocol.ConversationTurn;
import java.util.List;

@FunctionalInterface
public interface PromptAssembler {
    String assemble(String userMessage, List<ConversationTurn> history);
}
```

Create `DefaultPromptAssembler.java`:

```java
package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.ws.protocol.ConversationTurn;
import org.jspecify.annotations.Nullable;

import java.util.List;

public class DefaultPromptAssembler implements PromptAssembler {

    private static final String FALLBACK_SYSTEM_PROMPT =
            "You are a conversational assistant. Respond naturally and concisely.";

    private final String systemPrompt;

    public DefaultPromptAssembler(@Nullable String systemPrompt) {
        this.systemPrompt = systemPrompt != null ? systemPrompt : FALLBACK_SYSTEM_PROMPT;
    }

    @Override
    public String assemble(String userMessage, List<ConversationTurn> history) {
        var sb = new StringBuilder();
        sb.append("System: ").append(systemPrompt).append("\n\n");
        for (ConversationTurn turn : history) {
            sb.append(turn.role().equals("user") ? "User" : "Assistant");
            sb.append(": ").append(turn.text()).append("\n");
        }
        sb.append("User: ").append(userMessage).append("\n");
        sb.append("Assistant:");
        return sb.toString();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode -pl speech-ws test -Dtest=DefaultPromptAssemblerTest`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add speech-ws/src/
git commit -m "feat(#195): add PromptAssembler SPI + default implementation

Composable prompt assembly for the avatar speech pipeline. Default
implementation formats system prompt + conversation history + user
message. Tier 2 replaces with personality/mood/drive-enriched assembly.

Refs #195"
```

---

### Task 4: SpeechWebSocket endpoint + AvatarConfig

**Files:**
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/SpeechWebSocket.java`
- Create: `speech-ws/src/main/java/io/casehub/blocks/speech/ws/AvatarConfig.java`
- Test: `speech-ws/src/test/java/io/casehub/blocks/speech/ws/SpeechWebSocketTest.java`

**Interfaces:**
- Consumes: `MessageCodec.encode/decodeClient` from Task 1
- Consumes: `VisemeMapping.convert(List<PhonemeTiming>)` from Task 2
- Consumes: `PromptAssembler.assemble(String, List<ConversationTurn>)` from Task 3
- Consumes: `StreamingSpeechToTextService.startStream(TranscriptionOptions) → RecognitionStream` from speech-api
- Consumes: `TextToSpeechService.synthesise(String, SynthesisOptions) → SynthesisResult` from speech-api
- Consumes: `CleanupConfig.apply(String) → String` from speech-api
- Produces: `SpeechWebSocket` — CDI bean, consumer includes via Jandex indexing

- [ ] **Step 1: Write SpeechWebSocketTest**

Test the business logic extracted into a testable `handleTextMessage` and `handleBinaryMessage` method pattern. Mock all speech services and verify the correct protocol messages are produced.

```java
package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.*;
import io.casehub.blocks.speech.ws.protocol.AvatarMessage;
import io.casehub.blocks.speech.ws.protocol.MessageCodec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SpeechWebSocketTest {

    private StreamingSpeechToTextService sttService;
    private TextToSpeechService ttsService;
    private CleanupConfig cleanupConfig;
    private RecognitionStream recognitionStream;
    private List<String> sentTextMessages;
    private List<byte[]> sentBinaryMessages;
    private SpeechSession session;

    @BeforeEach
    void setUp() {
        sttService = mock(StreamingSpeechToTextService.class);
        ttsService = mock(TextToSpeechService.class);
        cleanupConfig = mock(CleanupConfig.class);
        recognitionStream = mock(RecognitionStream.class);

        when(sttService.startStream(any())).thenReturn(recognitionStream);
        when(cleanupConfig.apply(any())).thenAnswer(inv -> inv.getArgument(0));

        sentTextMessages = new ArrayList<>();
        sentBinaryMessages = new ArrayList<>();
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                new DefaultPromptAssembler(null),
                sentTextMessages::add,
                sentBinaryMessages::add);
    }

    @Test
    void startCreatesRecognitionStream() {
        session.handleStart(new AvatarMessage.Start(16000));
        verify(sttService).startStream(any());
    }

    @Test
    void stopFinalizesAndSendsTranscript() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("hello world", "en", 0.95));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"transcript\""));
    }

    @Test
    void stopWithTtsProducesPhonemesAndAudio() {
        when(recognitionStream.finalResult()).thenReturn(
                new TranscriptionResult("hello", "en", 0.9));
        var phonemes = List.of(new PhonemeTiming("h", 0, 50), new PhonemeTiming("ɛ", 50, 120));
        when(ttsService.synthesise(any(), any())).thenReturn(
                new SynthesisResult(new byte[]{1, 2, 3}, "wav", phonemes));

        session.handleStart(new AvatarMessage.Start(16000));
        session.handleStop();

        assertThat(sentTextMessages).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"phonemes\""));
        assertThat(sentBinaryMessages).hasSize(1);
    }

    @Test
    void audioSamplesAreFedToRecognitionStream() {
        session.handleStart(new AvatarMessage.Start(16000));
        float[] samples = {0.1f, 0.2f, 0.3f};
        session.handleAudio(samples, 16000);
        verify(recognitionStream).acceptSamples(samples, 16000);
    }

    @Test
    void stopWithoutStartSendsError() {
        session.handleStop();
        assertThat(sentTextMessages).anySatisfy(json ->
                assertThat(json).contains("\"type\":\"error\""));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode -pl speech-ws test -Dtest=SpeechWebSocketTest`
Expected: FAIL — `SpeechSession` class not found.

- [ ] **Step 3: Implement SpeechSession**

The business logic class that handles per-connection state, testable without Quarkus:

```java
package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.*;
import io.casehub.blocks.speech.ws.protocol.AvatarMessage;
import io.casehub.blocks.speech.ws.protocol.ConversationTurn;
import io.casehub.blocks.speech.ws.protocol.MessageCodec;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class SpeechSession {

    private final StreamingSpeechToTextService sttService;
    private final TextToSpeechService ttsService;
    private final CleanupConfig cleanupConfig;
    private final @Nullable java.util.function.UnaryOperator<String> responseGenerator;
    private final PromptAssembler promptAssembler;
    private final Consumer<String> textSink;
    private final Consumer<byte[]> binarySink;
    private final List<ConversationTurn> history = new ArrayList<>();

    private @Nullable RecognitionStream stream;

    public SpeechSession(StreamingSpeechToTextService sttService,
                         TextToSpeechService ttsService,
                         CleanupConfig cleanupConfig,
                         @Nullable java.util.function.UnaryOperator<String> responseGenerator,
                         PromptAssembler promptAssembler,
                         Consumer<String> textSink,
                         Consumer<byte[]> binarySink) {
        this.sttService = sttService;
        this.ttsService = ttsService;
        this.cleanupConfig = cleanupConfig;
        this.agentProvider = agentProvider;
        this.promptAssembler = promptAssembler;
        this.textSink = textSink;
        this.binarySink = binarySink;
    }

    public void handleStart(AvatarMessage.Start msg) {
        var options = new TranscriptionOptions(null, null, null);
        stream = sttService.startStream(options);
    }

    public void handleAudio(float[] samples, int sampleRate) {
        if (stream != null) {
            stream.acceptSamples(samples, sampleRate);
        }
    }

    public void handleStop() {
        if (stream == null) {
            send(new AvatarMessage.Error("No active recording session"));
            return;
        }
        try {
            TranscriptionResult result = stream.finalResult();
            stream.close();
            stream = null;

            String cleanText = cleanupConfig.apply(result.text());
            send(new AvatarMessage.Transcript(cleanText));
            history.add(new ConversationTurn("user", cleanText));

            String responseText = generateResponse(cleanText);
            if (responseText != null) {
                send(new AvatarMessage.Response(responseText));
                history.add(new ConversationTurn("assistant", responseText));

                SynthesisResult synthesis = ttsService.synthesise(responseText,
                        new SynthesisOptions(null, null, "wav", true));

                var visemeFrames = VisemeMapping.convert(synthesis.phonemes());
                send(new AvatarMessage.Phonemes(visemeFrames));
                binarySink.accept(synthesis.audioData());
            }
        } catch (Exception e) {
            send(new AvatarMessage.Error(e.getMessage()));
        }
    }

    private @Nullable String generateResponse(String userMessage) {
        if (responseGenerator == null) {
            send(new AvatarMessage.Error("No LLM provider configured"));
            return null;
        }
        String prompt = promptAssembler.assemble(userMessage, List.copyOf(history));
        return responseGenerator.apply(prompt);
    }

    private void send(AvatarMessage msg) {
        textSink.accept(MessageCodec.encode(msg));
    }

    public void close() {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }
}
```

- [ ] **Step 4: Implement AvatarConfig**

```java
package io.casehub.blocks.speech.ws;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

@ConfigMapping(prefix = "casehub.avatar")
public interface AvatarConfig {

    @WithDefault("16000")
    int sampleRate();

    @WithDefault("2")
    int maxDestructiveness();

    Optional<String> systemPrompt();
}
```

- [ ] **Step 5: Implement SpeechWebSocket**

The thin Quarkus adapter that delegates to SpeechSession:

```java
package io.casehub.blocks.speech.ws;

import io.casehub.blocks.speech.CleanupConfig;
import io.casehub.blocks.speech.StreamingSpeechToTextService;
import io.casehub.blocks.speech.TextToSpeechService;
import io.casehub.blocks.speech.ws.protocol.AvatarMessage;
import io.casehub.blocks.speech.ws.protocol.MessageCodec;
import io.quarkus.websockets.next.OnBinaryMessage;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@WebSocket(path = "/ws/avatar")
public class SpeechWebSocket {

    @Inject StreamingSpeechToTextService sttService;
    @Inject TextToSpeechService ttsService;
    @Inject CleanupConfig cleanupConfig;
    @Inject AvatarConfig avatarConfig;

    private SpeechSession session;

    @OnOpen
    public void onOpen(WebSocketConnection connection) {
        var assembler = new DefaultPromptAssembler(
                avatarConfig.systemPrompt().orElse(null));
        session = new SpeechSession(
                sttService, ttsService, cleanupConfig,
                null,
                assembler,
                text -> connection.sendTextAndAwait(text),
                data -> connection.sendBinaryAndAwait(ByteBuffer.wrap(data)));
    }

    @OnTextMessage
    public void onText(String message) {
        AvatarMessage msg = MessageCodec.decodeClient(message);
        switch (msg) {
            case AvatarMessage.Start s -> session.handleStart(s);
            case AvatarMessage.Stop s -> session.handleStop();
            default -> {}
        }
    }

    @OnBinaryMessage
    public void onBinary(ByteBuffer data) {
        var floatBuf = data.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer();
        float[] samples = new float[floatBuf.remaining()];
        floatBuf.get(samples);
        session.handleAudio(samples, avatarConfig.sampleRate());
    }

    @OnClose
    public void onClose() {
        if (session != null) {
            session.close();
        }
    }
}
```

- [ ] **Step 6: Run all tests**

Run: `mvn --batch-mode -pl speech-ws test`
Expected: All tests PASS (MessageCodecTest: 9, VisemeMappingTest: 5, DefaultPromptAssemblerTest: 3, SpeechWebSocketTest: 5 = 22 tests).

- [ ] **Step 7: Commit**

```bash
git add speech-ws/src/
git commit -m "feat(#195): add SpeechWebSocket endpoint + SpeechSession

Quarkus WebSocket endpoint at /ws/avatar. Thin adapter delegates to
SpeechSession (testable without Quarkus). Composes speech-api SPIs:
- StreamingSpeechToTextService for STT
- TextToSpeechService for TTS with phoneme timing
- CleanupConfig for text correction
- VisemeMapping for phoneme-to-viseme conversion

AvatarConfig for sample rate, destructiveness, and system prompt.
AgentProvider integration point prepared for demo app.

Refs #195"
```

---

## Batch 3: Build verification + issues + docs

### Task 5: Full build + create GitHub issues + update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md` (add speech-ws to Key Directories, Dependencies, Module structure)

**Interfaces:**
- None (documentation + issue tracking)

- [ ] **Step 1: Run full reactor build**

Run: `mvn --batch-mode install -DskipTests && mvn --batch-mode test`
Expected: All modules compile, all tests pass (including existing 176 tests + new 22).

- [ ] **Step 2: Create blocks-ui avatar issue**

```bash
gh issue create --repo casehubio/blocks-ui \
  --title "Avatar package: Lit web components for talking avatar" \
  --label "enhancement" \
  --body "$(cat <<'EOF'
## Summary

New `packages/avatar/` package with three Lit web components backed by TalkingHead (Three.js 3D avatar).

## Components

| Component | What it does |
|-----------|-------------|
| `<casehub-avatar>` | 3D avatar face with TalkingHead lip-sync driven by VisemeFrame data |
| `<casehub-transcript>` | Streaming transcript display — partial results replace-in-place with cleaned text |
| `<casehub-speech>` | Mic capture via Web Audio API AudioWorklet, sends PCM binary frames |

## WebSocket Protocol

Connects to `/ws/avatar` (provided by `casehub-blocks-speech-ws`).
- **Client → Server:** `start`/`stop` JSON text frames + PCM binary frames
- **Server → Client:** `partial`/`transcript`/`response`/`phonemes`/`error` JSON + WAV binary

## Dependencies

- `talkinghead` (npm, MIT) — 3D avatar rendering
- `three` (npm, MIT) — Three.js for WebGL
- `casehub-blocks-speech-ws` (Maven SNAPSHOT) — WebSocket endpoint

## Demo App

`examples/avatar-demo/` — thin Quarkus app for integration testing and "clone and run" demo.

## Design Spec

See blocks workspace: `specs/issue-194-talking-avatar-tier1/2026-08-27-avatar-web-component-design.md`

Part of casehubio/blocks#194 — Critical Path: Talking Avatar with Personality
EOF
)"
```

- [ ] **Step 3: Create Tier 2 social cognition issues**

Create one issue per orchestrator on casehubio/blocks:

```bash
gh issue create --repo casehubio/blocks --title "Avatar Tier 2: Personality modulation in speech pipeline" --label "enhancement" --body "Wire PersonalityEvolutionOrchestrator into SpeechWebSocket PromptAssembler. Traits modulate LLM prompt tone and style. Part of #196 (Tier 2: Clean)."

gh issue create --repo casehubio/blocks --title "Avatar Tier 2: Mood-affected responses" --label "enhancement" --body "Wire MoodOrchestrator PAD state into PromptAssembler. Mood modulates word choice and response energy. Part of #196 (Tier 2: Clean)."

gh issue create --repo casehubio/blocks --title "Avatar Tier 2: Drive-influenced proactive speech" --label "enhancement" --body "Wire DriveOrchestrator into SpeechWebSocket. Curiosity/affiliation drives trigger proactive avatar speech. Part of #196 (Tier 2: Clean)."

gh issue create --repo casehubio/blocks --title "Avatar Tier 2: Mental model awareness" --label "enhancement" --body "Wire MentalModelOrchestrator into PromptAssembler. Theory of Mind tracks what user knows, avoids repetition. Part of #196 (Tier 2: Clean)."

gh issue create --repo casehubio/blocks --title "Avatar Tier 2: User model personalization" --label "enhancement" --body "Wire UserModelOrchestrator into PromptAssembler. Remembers user across sessions, adapts responses. Part of #196 (Tier 2: Clean)."

gh issue create --repo casehubio/blocks --title "Avatar Tier 2: Strategy learning" --label "enhancement" --body "Wire StrategyLearningOrchestrator into PromptAssembler. Learns which interaction strategies work with each user. Part of #196 (Tier 2: Clean)."
```

- [ ] **Step 4: Update CLAUDE.md — add speech-ws to Key Directories table**

Add after the speech-sherpa entry in the Key Directories table:

```markdown
| `speech-ws/src/main/java/io/casehub/blocks/speech/ws/` | Avatar WebSocket endpoint — `SpeechWebSocket` (Quarkus WebSocket at `/ws/avatar`), `SpeechSession` (per-connection business logic), `VisemeMapping` (IPA→Oculus viseme), `PromptAssembler` SPI with `DefaultPromptAssembler`, `AvatarConfig` |
| `speech-ws/src/main/java/io/casehub/blocks/speech/ws/protocol/` | WebSocket protocol DTOs — `AvatarMessage` sealed hierarchy (Start, Stop, Partial, Transcript, Response, Phonemes, Error), `VisemeFrame`, `ConversationTurn`, `MessageCodec` (gson JSON encode/decode) |
| `speech-ws/src/test/java/io/casehub/blocks/speech/ws/` | Tests for avatar WebSocket |
```

- [ ] **Step 5: Update CLAUDE.md — add speech-ws module to Dependencies section**

Add a new speech-ws subsection:

```markdown
**speech-ws module:**
**Compile:** `casehub-blocks-speech-api`, `com.google.code.gson:gson`, `org.jspecify:jspecify`
**Provided:** `casehub-platform-agent-api`, `quarkus-websockets-next`
**Compiler target:** Java 17
**Test:** JUnit 5, AssertJ, Mockito
```

- [ ] **Step 6: Commit CLAUDE.md updates**

```bash
git add CLAUDE.md
git commit -m "docs(#195): add speech-ws module to CLAUDE.md

Key Directories, Dependencies, and module structure updated for
the new avatar WebSocket module.

Refs #195"
```

- [ ] **Step 7: Run final full build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS across all modules.

---

## References

- [2026-08-27-avatar-web-component-design.md] — design spec this plan implements
- [speech-api SPIs] — StreamingSpeechToTextService, RecognitionStream, TextToSpeechService, SynthesisResult, PhonemeTiming, TextFilter, CleanupConfig
- [TalkingHead](https://github.com/met4citizen/TalkingHead) — 3D avatar renderer (MIT)
- [Oculus Viseme Reference](https://developer.oculus.com/documentation/unity/audio-ovrlipsync-viseme-reference/) — 15 viseme IDs
- GE-20260813-193670 — quarkus-websockets-next uses plural package name
- GE-20260703-e4a6b0 — WebSockets Next ignores JAX-RS filters; use HttpUpgradeCheck
- GE-20260620-768950 — buildAsync().join() completes before @OnOpen fires
- GitHub #194 — Critical Path: Talking Avatar with Personality
- GitHub #195 — blocks-ui: Lit-based avatar web component
- GitHub #196 — Tier 2: Clean
