# Speech Configs YAML Surface — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #253 — Speech configs YAML surface
**Issue group:** #248 (branch covers #246 epic)

**Goal:** Add YAML specs for 5 speech config types so YAML-only
application definitions can configure transcription, synthesis, and
model parameters.

**Architecture:** Add provided-scope deps on speech-api + speech-sherpa
to agentic-yaml. 2 types direct-reuse (TranscriptionOptions,
SynthesisOptions), 3 adapted records for Path → String conversion
(SherpaConfigSpec, KokoroConfigSpec, Audio8ConfigSpec) with registries.

**Tech Stack:** Jackson YAML, speech-api (TranscriptionOptions,
SynthesisOptions), speech-sherpa (SherpaConfig, KokoroConfig, Audio8Config)

## Global Constraints

- All spec records in `io.casehub.blocks.agentic.yaml.spec`
- All registries in `io.casehub.blocks.agentic.yaml.registry`
- `Objects.requireNonNull` for non-null fields in compact constructors
- `@Nullable` from `org.jspecify.annotations` for nullable fields
- speech-api and speech-sherpa are `<scope>provided</scope>` deps
- Verify exact constructor signatures via `ide_find_class` + `ide_file_structure`

---

## Batch 1: Specs and registries

### Task 1: Dependencies + direct reuse tests

**Files:**
- Modify: `agentic-yaml/pom.xml` — add 2 provided-scope dependencies
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/SpeechSpecSerializationTest.java`

**Interfaces:**
- Consumes: `TranscriptionOptions` from speech-api, `SynthesisOptions` from speech-api
- Produces: `SpeechSpecSerializationTest` test class (extended by Task 2)

- [ ] **Step 1: Add provided-scope dependencies to agentic-yaml/pom.xml**

Add after the existing `casehub-platform-agent-api` dependency:

```xml
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-speech-api</artifactId>
    <version>${project.version}</version>
    <scope>provided</scope>
</dependency>
<dependency>
    <groupId>io.casehub</groupId>
    <artifactId>casehub-blocks-speech-sherpa</artifactId>
    <version>${project.version}</version>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: Write direct reuse serialization tests**

```java
package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.blocks.speech.TranscriptionOptions;
import io.casehub.blocks.speech.SynthesisOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
    }

    @Nested
    class TranscriptionOptionsSpecs {

        @Test
        void deserialises_all_fields() throws Exception {
            var yaml = """
                    audioFormat: wav
                    languageHint: en
                    modelSize: base.en
                    vocabularyHint: medical
                    """;
            var opts = mapper.readValue(yaml, TranscriptionOptions.class);
            assertThat(opts.audioFormat()).isEqualTo("wav");
            assertThat(opts.languageHint()).isEqualTo("en");
            assertThat(opts.modelSize()).isEqualTo("base.en");
            assertThat(opts.vocabularyHint()).isEqualTo("medical");
        }

        @Test
        void deserialises_without_optional_fields() throws Exception {
            var yaml = """
                    audioFormat: wav
                    modelSize: base.en
                    """;
            var opts = mapper.readValue(yaml, TranscriptionOptions.class);
            assertThat(opts.audioFormat()).isEqualTo("wav");
            assertThat(opts.modelSize()).isEqualTo("base.en");
            assertThat(opts.languageHint()).isNull();
        }
    }

    @Nested
    class SynthesisOptionsSpecs {

        @Test
        void deserialises_all_fields() throws Exception {
            var yaml = """
                    voice: af_heart
                    language: en
                    audioFormat: wav
                    includePhonemes: true
                    """;
            var opts = mapper.readValue(yaml, SynthesisOptions.class);
            assertThat(opts.voice()).isEqualTo("af_heart");
            assertThat(opts.language()).isEqualTo("en");
            assertThat(opts.audioFormat()).isEqualTo("wav");
            assertThat(opts.includePhonemes()).isTrue();
        }

        @Test
        void deserialises_defaults() throws Exception {
            var yaml = """
                    audioFormat: wav
                    includePhonemes: false
                    """;
            var opts = mapper.readValue(yaml, SynthesisOptions.class);
            assertThat(opts.audioFormat()).isEqualTo("wav");
            assertThat(opts.includePhonemes()).isFalse();
        }
    }
}
```

- [ ] **Step 3: Run tests to verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=SpeechSpecSerializationTest`
Expected: PASS. If TranscriptionOptions or SynthesisOptions deserialization
fails (Jackson can't construct from YAML), these types need adapted specs
instead of direct reuse — adjust accordingly.

- [ ] **Step 4: Commit**

```bash
git add agentic-yaml/pom.xml \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/SpeechSpecSerializationTest.java
git commit -m "feat(#253): speech-api/sherpa provided deps + TranscriptionOptions/SynthesisOptions direct reuse tests Refs #253"
```

### Task 2: Sherpa adapted specs + registries

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/SherpaConfigSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/KokoroConfigSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/Audio8ConfigSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/SherpaConfigRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/KokoroConfigRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/Audio8ConfigRegistry.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/SpeechSpecSerializationTest.java`

**Interfaces:**
- Consumes: `SherpaConfig` from speech-sherpa, `KokoroConfig` from speech-sherpa, `Audio8Config` from speech-sherpa
- Produces: `SherpaConfigSpec`, `KokoroConfigSpec`, `Audio8ConfigSpec` records; `SherpaConfigRegistry.resolve()`, `KokoroConfigRegistry.resolve()`, `Audio8ConfigRegistry.resolve()`

- [ ] **Step 1: Verify exact constructor signatures**

Run `ide_find_class` for `SherpaConfig`, `KokoroConfig`, `Audio8Config`.
Then `ide_file_structure` for each. Verify constructor parameters match
the spec design (especially SherpaConfig's convenience constructors and
with-methods).

- [ ] **Step 2: Add failing serialization tests to SpeechSpecSerializationTest**

Add `@Nested` classes for SherpaConfigSpec, KokoroConfigSpec, Audio8ConfigSpec:

```java
// Add imports for the new spec/registry types and Path

@Nested
class SherpaConfigSpecs {

    @Test
    void deserialises_with_all_fields() throws Exception {
        var yaml = """
                modelDir: /models/whisper
                numThreads: 2
                provider: cpu
                punctuationModelDir: /models/punctuation
                """;
        var spec = mapper.readValue(yaml, SherpaConfigSpec.class);
        assertThat(spec.modelDir()).isEqualTo("/models/whisper");
        assertThat(spec.numThreads()).isEqualTo(2);
        assertThat(spec.provider()).isEqualTo("cpu");
        assertThat(spec.punctuationModelDir()).isEqualTo("/models/punctuation");
    }

    @Test
    void deserialises_without_optional_fields() throws Exception {
        var yaml = """
                modelDir: /models/whisper
                numThreads: 2
                provider: cpu
                """;
        var spec = mapper.readValue(yaml, SherpaConfigSpec.class);
        assertThat(spec.punctuationModelDir()).isNull();
    }

    @Test
    void rejects_null_model_dir() {
        assertThatThrownBy(() -> new SherpaConfigSpec(null, 2, "cpu", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void registry_resolves_to_sherpa_config() {
        var spec = new SherpaConfigSpec("/models/whisper", 2, "cpu", null);
        var registry = new SherpaConfigRegistry();
        var result = registry.resolve(spec);
        assertThat(result.modelDir()).isEqualTo(Path.of("/models/whisper"));
        assertThat(result.numThreads()).isEqualTo(2);
        assertThat(result.provider()).isEqualTo("cpu");
    }

    @Test
    void registry_resolves_with_punctuation() {
        var spec = new SherpaConfigSpec("/models/whisper", 2, "cpu",
                "/models/punctuation");
        var registry = new SherpaConfigRegistry();
        var result = registry.resolve(spec);
        assertThat(result.punctuationModelDir()).isEqualTo(Path.of("/models/punctuation"));
    }
}

@Nested
class KokoroConfigSpecs {

    @Test
    void deserialises_all_fields() throws Exception {
        var yaml = """
                modelDir: /models/kokoro
                voiceId: 5
                lengthScale: 1.2
                numThreads: 2
                provider: cpu
                """;
        var spec = mapper.readValue(yaml, KokoroConfigSpec.class);
        assertThat(spec.modelDir()).isEqualTo("/models/kokoro");
        assertThat(spec.voiceId()).isEqualTo(5);
        assertThat(spec.lengthScale()).isEqualTo(1.2f);
    }

    @Test
    void registry_resolves() {
        var spec = new KokoroConfigSpec("/models/kokoro", 0, 1.0f, 2, "cpu");
        var registry = new KokoroConfigRegistry();
        var result = registry.resolve(spec);
        assertThat(result.modelDir()).isEqualTo(Path.of("/models/kokoro"));
        assertThat(result.voiceId()).isEqualTo(0);
    }
}

@Nested
class Audio8ConfigSpecs {

    @Test
    void deserialises_all_fields() throws Exception {
        var yaml = """
                modelDir: /models/audio8
                variant: 0.1b-int8
                numThreads: 2
                provider: cpu
                maxTokens: 2048
                temperature: 0.6
                topP: 0.9
                topK: 50
                """;
        var spec = mapper.readValue(yaml, Audio8ConfigSpec.class);
        assertThat(spec.modelDir()).isEqualTo("/models/audio8");
        assertThat(spec.variant()).isEqualTo("0.1b-int8");
        assertThat(spec.maxTokens()).isEqualTo(2048);
    }

    @Test
    void registry_resolves() {
        var spec = new Audio8ConfigSpec("/models/audio8", "0.1b-int8",
                2, "cpu", 2048, 0.6f, 0.9f, 50);
        var registry = new Audio8ConfigRegistry();
        var result = registry.resolve(spec);
        assertThat(result.modelDir()).isEqualTo(Path.of("/models/audio8"));
        assertThat(result.variant()).isEqualTo("0.1b-int8");
    }
}
```

Add `import static org.assertj.core.api.Assertions.assertThatThrownBy;`
and `import java.nio.file.Path;` to the imports.

- [ ] **Step 3: Run tests to verify fail**

Run: `mvn test -pl agentic-yaml -Dtest=SpeechSpecSerializationTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: compilation error — spec classes not found

- [ ] **Step 4: Implement spec records**

```java
// SherpaConfigSpec.java
package io.casehub.blocks.agentic.yaml.spec;

import org.jspecify.annotations.Nullable;
import java.util.Objects;

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

```java
// KokoroConfigSpec.java
package io.casehub.blocks.agentic.yaml.spec;

import java.util.Objects;

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

```java
// Audio8ConfigSpec.java
package io.casehub.blocks.agentic.yaml.spec;

import java.util.Objects;

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

- [ ] **Step 5: Implement registries**

```java
// SherpaConfigRegistry.java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.SherpaConfigSpec;
import io.casehub.blocks.speech.sherpa.SherpaConfig;
import java.nio.file.Path;

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

```java
// KokoroConfigRegistry.java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.KokoroConfigSpec;
import io.casehub.blocks.speech.sherpa.KokoroConfig;
import java.nio.file.Path;

public class KokoroConfigRegistry {
    public KokoroConfig resolve(KokoroConfigSpec spec) {
        return new KokoroConfig(
                Path.of(spec.modelDir()), spec.voiceId(),
                spec.lengthScale(), spec.numThreads(), spec.provider());
    }
}
```

```java
// Audio8ConfigRegistry.java
package io.casehub.blocks.agentic.yaml.registry;

import io.casehub.blocks.agentic.yaml.spec.Audio8ConfigSpec;
import io.casehub.blocks.speech.sherpa.Audio8Config;
import java.nio.file.Path;

public class Audio8ConfigRegistry {
    public Audio8Config resolve(Audio8ConfigSpec spec) {
        return new Audio8Config(
                Path.of(spec.modelDir()), spec.variant(),
                spec.numThreads(), spec.provider(), spec.maxTokens(),
                spec.temperature(), spec.topP(), spec.topK());
    }
}
```

- [ ] **Step 6: Run all tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=SpeechSpecSerializationTest`
Expected: PASS

- [ ] **Step 7: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/SherpaConfigSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/KokoroConfigSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/Audio8ConfigSpec.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/SherpaConfigRegistry.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/KokoroConfigRegistry.java \
       agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/registry/Audio8ConfigRegistry.java \
       agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/SpeechSpecSerializationTest.java
git commit -m "feat(#253): SherpaConfigSpec, KokoroConfigSpec, Audio8ConfigSpec + registries Refs #253"
```

## Batch 2: Schema and docs

### Task 3: Schema generation + coverage matrix

**Files:**
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/schema/SchemaGenerationTest.java`
- Modify: `docs/yaml-coverage.md`

**Interfaces:**
- Consumes: All spec and direct-reuse types from Tasks 1-2
- Produces: Schema generation tests, updated coverage matrix

- [ ] **Step 1: Add schema generation tests**

Add to `SchemaGenerationTest.java`:

```java
@Test
void sherpaConfigSpecSchemaGenerates() {
    JsonNode schema = generator.generate(SherpaConfigSpec.class);
    assertThat(schema).isNotNull();
    assertThat(schema.toString()).contains("modelDir");
}

@Test
void kokoroConfigSpecSchemaGenerates() {
    JsonNode schema = generator.generate(KokoroConfigSpec.class);
    assertThat(schema).isNotNull();
    assertThat(schema.toString()).contains("modelDir");
}

@Test
void audio8ConfigSpecSchemaGenerates() {
    JsonNode schema = generator.generate(Audio8ConfigSpec.class);
    assertThat(schema).isNotNull();
    assertThat(schema.toString()).contains("variant");
}

@Test
void transcriptionOptionsSchemaGenerates() {
    JsonNode schema = generator.generate(TranscriptionOptions.class);
    assertThat(schema).isNotNull();
    assertThat(schema.toString()).contains("audioFormat");
}

@Test
void synthesisOptionsSchemaGenerates() {
    JsonNode schema = generator.generate(SynthesisOptions.class);
    assertThat(schema).isNotNull();
    assertThat(schema.toString()).contains("voice");
}
```

Add imports for `SherpaConfigSpec`, `KokoroConfigSpec`, `Audio8ConfigSpec`,
`TranscriptionOptions`, `SynthesisOptions`.

- [ ] **Step 2: Run schema tests — verify pass**

Run: `mvn test -pl agentic-yaml -Dtest=SchemaGenerationTest`
Expected: PASS

- [ ] **Step 3: Update coverage matrix**

Edit `docs/yaml-coverage.md`. Add new sections §22, §23, §24 if they
don't exist, or update existing entries:

§22 Speech API:
- TranscriptionOptions → `Done | #253 — direct reuse`
- SynthesisOptions → `Done | #253 — direct reuse`
- CorrectionStrategy → `N/A | @FunctionalInterface, not enum`
- ConversationTurn → `N/A | Runtime data`
- AssembledPrompt → `N/A | Runtime output`
- PromptContext → `N/A | Runtime context`

§23 Speech WebSocket:
- AvatarConfig → `N/A | Already @ConfigMapping via Quarkus YAML`
- VisemeMapping → `N/A | Static utility class`

§24 Speech Sherpa:
- SherpaConfig → `Done | #253 — adapted record`
- KokoroConfig → `Done | #253 — adapted record`
- Audio8Config → `Done | #253 — adapted record`
- GectorConfig → `N/A | Filesystem-derived`

Update the summary count at the bottom.

- [ ] **Step 4: Run full agentic-yaml test suite**

Run: `mvn test -pl agentic-yaml`
Expected: all tests PASS

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/schema/SchemaGenerationTest.java \
       docs/yaml-coverage.md
git commit -m "feat(#253): schema generation, coverage matrix §22 §23 §24 Done Refs #253"
```

## References

- [2026-09-11-speech-configs-yaml-design.md] — design spec
- speech-api: TranscriptionOptions, SynthesisOptions, CleanupConfig
- speech-sherpa: SherpaConfig, KokoroConfig, Audio8Config
- GE-20260904-ebed3c — Kokoro v1.0 requires kokoro-lexicon field
- [GitHub #253](https://github.com/casehubio/blocks/issues/253)
