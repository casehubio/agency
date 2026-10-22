# Speech Configs — YAML Surface

**Issue:** casehubio/agency#253
**Date:** 2026-09-11
**Scope:** 5 config-shaped records from coverage matrix §22–§24 → direct reuse + adapted specs

## Problem

Speech transcription, synthesis, and model configuration require Java
construction. YAML-only application definition cannot configure speech
pipeline parameters.

7 types from the original issue are runtime data, already YAML-configured,
utility classes, or misidentified. These are excluded (marked N/A in
coverage matrix). See D1.

## Solution

Add provided-scope dependencies on speech-api and speech-sherpa to
agentic-yaml. 2 types direct-reuse, 3 types get adapted spec records
with registries. No sealed interfaces needed — all types are records.

## Dependencies

Add to `agentic-yaml/pom.xml`:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-agency-speech-api</artifactId>
    <version>${project.version}</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-agency-speech-sherpa</artifactId>
    <version>${project.version}</version>
    <scope>provided</scope>
</dependency>
```

## Direct Reuse

### TranscriptionOptions

Already a Jackson-compatible record with `String audioFormat`,
`String languageHint`, `String modelSize`, `String vocabularyHint`
(4th field nullable via convenience constructor). No adaptation needed.

YAML example:
```yaml
transcription:
  audioFormat: wav
  modelSize: base.en
  languageHint: en
```

### SynthesisOptions

Already a Jackson-compatible record with `String voice`,
`String language`, `String audioFormat`, `boolean includePhonemes`.
No adaptation needed.

YAML example:
```yaml
synthesis:
  voice: af_heart
  language: en
  audioFormat: wav
  includePhonemes: true
```

## Adapted Spec Records

### SherpaConfigSpec

Runtime `SherpaConfig` uses `Path` fields and a nullable `CleanupConfig`
(which contains `TextFilter` SPIs — not YAML-expressible). The spec
adapts Path → String and omits CleanupConfig.

```java
public record SherpaConfigSpec(
        String modelDir,
        int numThreads,
        String provider,
        @Nullable String punctuationModelDir) {

    public SherpaConfigSpec {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads < 1)
            throw new IllegalArgumentException("numThreads must be positive");
    }
}
```

YAML example:
```yaml
sherpa:
  modelDir: /models/whisper-base.en
  numThreads: 2
  provider: cpu
  punctuationModelDir: /models/punctuation
```

### KokoroConfigSpec

Runtime `KokoroConfig` uses `Path modelDir`. The spec adapts Path → String.

```java
public record KokoroConfigSpec(
        String modelDir,
        int voiceId,
        float lengthScale,
        int numThreads,
        String provider) {

    public KokoroConfigSpec {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(provider, "provider");
        if (numThreads < 1)
            throw new IllegalArgumentException("numThreads must be positive");
        if (lengthScale <= 0)
            throw new IllegalArgumentException("lengthScale must be positive");
    }
}
```

YAML example:
```yaml
kokoro:
  modelDir: /models/kokoro-multi-lang-v1_0
  voiceId: 0
  lengthScale: 1.0
  numThreads: 2
  provider: cpu
```

### Audio8ConfigSpec

Runtime `Audio8Config` uses `Path modelDir`. The spec adapts Path → String.

```java
public record Audio8ConfigSpec(
        String modelDir,
        String variant,
        int numThreads,
        String provider,
        int maxTokens,
        float temperature,
        float topP,
        int topK) {

    public Audio8ConfigSpec {
        Objects.requireNonNull(modelDir, "modelDir");
        Objects.requireNonNull(variant, "variant");
        Objects.requireNonNull(provider, "provider");
        if (numThreads < 1)
            throw new IllegalArgumentException("numThreads must be positive");
        if (maxTokens < 1)
            throw new IllegalArgumentException("maxTokens must be positive");
    }
}
```

YAML example:
```yaml
audio8:
  modelDir: /models/audio8-0.1b-int8
  variant: 0.1b-int8
  numThreads: 2
  provider: cpu
  maxTokens: 2048
  temperature: 0.6
  topP: 0.9
  topK: 50
```

## Registries

### SherpaConfigRegistry

```java
public class SherpaConfigRegistry {
    public SherpaConfig resolve(SherpaConfigSpec spec) {
        var config = new SherpaConfig(
                Path.of(spec.modelDir()), spec.numThreads(), spec.provider());
        if (spec.punctuationModelDir() != null) {
            config = config.withPunctuation(Path.of(spec.punctuationModelDir()));
        }
        return config;
    }
}
```

### KokoroConfigRegistry

```java
public class KokoroConfigRegistry {
    public KokoroConfig resolve(KokoroConfigSpec spec) {
        return new KokoroConfig(
                Path.of(spec.modelDir()), spec.voiceId(),
                spec.lengthScale(), spec.numThreads(), spec.provider());
    }
}
```

### Audio8ConfigRegistry

```java
public class Audio8ConfigRegistry {
    public Audio8Config resolve(Audio8ConfigSpec spec) {
        return new Audio8Config(
                Path.of(spec.modelDir()), spec.variant(),
                spec.numThreads(), spec.provider(), spec.maxTokens(),
                spec.temperature(), spec.topP(), spec.topK());
    }
}
```

No registries needed for TranscriptionOptions and SynthesisOptions — direct reuse.

## Schema Generation

Add to schema generation test (non-discriminator):
- `SherpaConfigSpec.class`
- `KokoroConfigSpec.class`
- `Audio8ConfigSpec.class`
- `TranscriptionOptions.class`
- `SynthesisOptions.class`

No discriminator overrides — no sealed interfaces in this issue.

## Coverage Matrix Update

§22 Speech API:
- TranscriptionOptions → Done (direct reuse)
- SynthesisOptions → Done (direct reuse)
- CorrectionStrategy → N/A (@FunctionalInterface, not enum)
- ConversationTurn → N/A (runtime data)
- AssembledPrompt → N/A (runtime output)
- PromptContext → N/A (runtime context)

§23 Speech WebSocket:
- AvatarConfig → N/A (already @ConfigMapping via Quarkus YAML)
- VisemeMapping → N/A (static utility class)

§24 Speech Sherpa:
- SherpaConfig → Done (adapted record)
- KokoroConfig → Done (adapted record)
- Audio8Config → Done (adapted record)
- GectorConfig → N/A (filesystem-derived)

## Test Plan

| Test | What it verifies |
|------|-----------------|
| `SpeechSpecSerializationTest` | Round-trip YAML for all 5 types |
| `SpeechRegistryTest` | All 3 registry resolve methods (Path.of conversion) |

## References

- `speech-api: io.casehub.agency.speech.TranscriptionOptions`
- `speech-api: io.casehub.agency.speech.SynthesisOptions`
- `speech-api: io.casehub.agency.speech.CleanupConfig` — omitted (contains TextFilter SPIs)
- `speech-sherpa: io.casehub.agency.speech.sherpa.SherpaConfig`
- `speech-sherpa: io.casehub.agency.speech.sherpa.KokoroConfig`
- `speech-sherpa: io.casehub.agency.speech.sherpa.Audio8Config`
- GE-20260904-ebed3c — Kokoro v1.0 requires kokoro-lexicon field
- specs/issue-252-trust-routing-oversight/ — prior spec pattern reference
- [GitHub #253](https://github.com/casehubio/agency/issues/253)
