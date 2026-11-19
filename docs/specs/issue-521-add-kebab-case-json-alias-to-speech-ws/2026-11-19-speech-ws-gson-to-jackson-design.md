# Design: Migrate speech-ws protocol from Gson to Jackson with kebab-case aliases

**Issue:** casehubio/agency#521
**Date:** 2026-11-19
**Branch:** issue-521-add-kebab-case-json-alias-to-speech-ws

## Summary

Replace the hand-written Gson-based `MessageCodec` in speech-ws with Jackson
`ObjectMapper` serialization, and add `@JsonAlias` kebab-case aliases to all
multi-word record components. This brings speech-ws into alignment with
agentic-yaml (#520) and the rest of the CaseHub codebase.

## Scope

**In scope:**
- `AvatarMessage` sealed hierarchy: `@JsonTypeInfo`/`@JsonSubTypes` annotations
- `Cue` sealed hierarchy: same polymorphic annotations
- `VisemeFrame`, `ConversationTurn`: `@JsonAlias` on multi-word components
- `MessageCodec`: rewrite to use Jackson `ObjectMapper`
- `MessageCodecTest`: migrate from Gson to Jackson assertions + kebab-case tests
- `pom.xml`: swap Gson for jackson-databind

**Out of scope:**
- speech-sherpa (separate module, Gson used for different purposes)
- Other speech-ws classes (no Gson usage outside MessageCodec)

## Detailed Changes

### 1. AvatarMessage — polymorphic Jackson annotations

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Start.class, name = "start"),
    @JsonSubTypes.Type(value = Stop.class, name = "stop"),
    @JsonSubTypes.Type(value = Text.class, name = "text"),
    @JsonSubTypes.Type(value = Partial.class, name = "partial"),
    @JsonSubTypes.Type(value = Transcript.class, name = "transcript"),
    @JsonSubTypes.Type(value = Error.class, name = "error"),
    @JsonSubTypes.Type(value = SpeakerPrompt.class, names = {"speakerPrompt", "speaker-prompt"}),
    @JsonSubTypes.Type(value = SpeakerIdentify.class, names = {"speakerIdentify", "speaker-identify"}),
    @JsonSubTypes.Type(value = SpeakerIdentified.class, names = {"speakerIdentified", "speaker-identified"}),
    @JsonSubTypes.Type(value = Segment.class, name = "segment"),
    @JsonSubTypes.Type(value = TurnComplete.class, names = {"turnComplete", "turn-complete"}),
    @JsonSubTypes.Type(value = Gesture.class, name = "gesture"),
    @JsonSubTypes.Type(value = Mood.class, name = "mood"),
    @JsonSubTypes.Type(value = PlaybackComplete.class, names = {"playbackComplete", "playback-complete"}),
    @JsonSubTypes.Type(value = UserActivity.class, names = {"userActivity", "user-activity"})
})
public sealed interface AvatarMessage { ... }
```

Multi-word type names use `names = {...}` (Jackson 2.12+) — first entry is the
canonical serialization name (camelCase), second is the kebab-case alias for
deserialization.

### 2. Field-level @JsonAlias

Multi-word camelCase record components across all protocol records:

| Record | Component | Alias |
|--------|-----------|-------|
| `Start` | `sampleRate` | `sample-rate` |
| `Start` | `llmModel` | `llm-model` |
| `Start` | `ttsModel` | `tts-model` |
| `Text` | `llmModel` | `llm-model` |
| `Text` | `ttsModel` | `tts-model` |
| `Segment` | `turnSeq` | `turn-seq` |
| `Segment` | `hasAudio` | `has-audio` |
| `TurnComplete` | `turnSeq` | `turn-seq` |
| `TurnComplete` | `segmentCount` | `segment-count` |
| `PlaybackComplete` | `turnSeq` | `turn-seq` |
| `GestureCue` | `offsetMs` | `offset-ms` |
| `GestureCue` | `charOffset` | `char-offset` |
| `MoodCue` | `offsetMs` | `offset-ms` |
| `MoodCue` | `charOffset` | `char-offset` |
| `VisemeFrame` | `startMs` | `start-ms` |
| `VisemeFrame` | `endMs` | `end-ms` |

### 3. Cue sealed hierarchy

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = GestureCue.class, name = "gesture"),
    @JsonSubTypes.Type(value = MoodCue.class, name = "mood")
})
public sealed interface Cue { ... }
```

Single-word type names — no kebab aliases needed.

### 4. MessageCodec rewrite

```java
public final class MessageCodec {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String encode(AvatarMessage msg) {
        try {
            return MAPPER.writeValueAsString(msg);
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static AvatarMessage decodeClient(String json) {
        try {
            AvatarMessage msg = MAPPER.readValue(json, AvatarMessage.class);
            return switch (msg) {
                case AvatarMessage.Start _,
                     AvatarMessage.Stop _,
                     AvatarMessage.Text _,
                     AvatarMessage.SpeakerIdentify _,
                     AvatarMessage.PlaybackComplete _,
                     AvatarMessage.UserActivity _ -> msg;
                default -> throw new IllegalArgumentException(
                    "Unknown client message type: " + msg.getClass().getSimpleName());
            };
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid message JSON", e);
        }
    }
}
```

### 5. Start record — sampleRate default

The current hand-written codec defaults `sampleRate` to 16000 when missing.
With Jackson, use `@JsonSetter(nulls = Nulls.SKIP)` won't work for primitive int.
Instead, provide a `@JsonCreator` factory or use a Jackson deserializer default.

Simplest approach: use `@JsonProperty(defaultValue = "16000")` on the component.
If Jackson doesn't honour this for record constructors (it's metadata-only in some
versions), fall back to a custom `@JsonCreator` static factory.

### 6. Wire format impact

**Backward-compatible addition:** `charOffset` now appears in Cue subtypes in
server→client messages (was omitted by hand-written Gson encoder). Value is -1
when not explicitly set. Clients should ignore unknown fields per standard JSON
protocol practice.

**No breaking changes:** All existing camelCase field names and type discriminators
continue to work unchanged. Kebab-case is an additive alias.

### 7. Dependency change

```xml
<!-- Remove -->
<dependency>
    <groupId>com.google.code.gson</groupId>
    <artifactId>gson</artifactId>
    <version>2.12.1</version>
</dependency>

<!-- Add -->
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
</dependency>
```

No version — Quarkus BOM manages Jackson.

### 8. Test plan

- Round-trip encode/decode for all 14 message types (existing coverage, migrated)
- Kebab-case field deserialization for each aliased component
- Kebab-case type discriminator deserialization for multi-word types
- Client-type validation (reject server-only types in decodeClient)
- sampleRate default when field is absent
- Existing test assertions preserved (wire format parity except charOffset addition)

## References

- `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/AvatarMessage.java`
- `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/MessageCodec.java`
- `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/Cue.java`
- `speech-ws/src/main/java/io/casehub/agency/speech/ws/protocol/VisemeFrame.java`
- `speech-ws/src/test/java/io/casehub/agency/speech/ws/protocol/MessageCodecTest.java`
- casehubio/agency#520 — @JsonAlias pattern for agentic-yaml
- casehubio/agency#518 — initial @JsonAlias for AvatarDefinition
