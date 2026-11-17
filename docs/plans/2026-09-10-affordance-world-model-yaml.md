# Affordance World Model YAML Surface Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #248 — feat: Affordance / world model — YAML surface for agent perception
**Issue group:** #248

**Goal:** Create a YAML surface for declaring agent world models — entities, affordances, action vocabularies, observation sections, perception pipelines, and renderer thresholds — via standalone `world.yaml` files.

**Architecture:** Spec records in `agentic-yaml/spec/world/` package mirror runtime affordance types from `blocks/blocks/`. A `WorldCompiler` resolves entity references, compiles polymorphic sections with optional `AnnotatedSection` wrapping, and wires named filters via an `ObservationFilterRegistry`. A deployment `@BuildStep` in `agentic-yaml-deployment` discovers `META-INF/world.yaml` at build time.

**Tech Stack:** Java 17 records, Jackson YAML (`@JsonTypeInfo`/`@JsonSubTypes` for polymorphic sections/filters), JUnit 5, AssertJ

## Global Constraints

- All spec records in package `io.casehub.blocks.agentic.yaml.spec.world`
- Compiler and output in package `io.casehub.blocks.agentic.yaml.compiler`
- Deployment in package `io.casehub.blocks.agentic.yaml.deployment`
- No new compile dependencies — `agentic-yaml` already depends on `casehub-blocks`
- Follow `CognitionDefinition`/`CognitionCompiler` patterns for standalone YAML files
- Follow `PatternSpec` pattern for `@JsonTypeInfo` polymorphic sealed interfaces
- YAML file location: `META-INF/world.yaml` on classpath

---

## Batch 1: Spec Records — the YAML data model

### Task 1: AffordanceSpec and EntitySpec records

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/world/AffordanceSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/world/EntitySpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/world/InlineEntitySpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/world/ActionDescriptorSpec.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/world/WorldSpecSerializationTest.java`

**Interfaces:**
- Produces: `AffordanceSpec(String actionType, @Nullable String label, @Nullable String requiredItem, @Nullable List<String> acceptsItems)`
- Produces: `EntitySpec(String displayName, @Nullable String description, @Nullable List<AffordanceSpec> affordances)`
- Produces: `InlineEntitySpec(String id, String displayName, @Nullable String description, @Nullable List<AffordanceSpec> affordances)`
- Produces: `ActionDescriptorSpec(String type, String description, @Nullable String parameterFormat)`

- [ ] **Step 1: Write failing deserialization tests**

```java
package io.casehub.blocks.agentic.yaml.spec.world;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorldSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
    }

    @Test
    void affordanceSpecAllFields() throws Exception {
        var yaml = """
                actionType: USE
                label: to apply
                requiredItem: key
                acceptsItems:
                  - rat-poison
                  - arsenic
                """;
        var spec = mapper.readValue(yaml, AffordanceSpec.class);
        assertThat(spec.actionType()).isEqualTo("USE");
        assertThat(spec.label()).isEqualTo("to apply");
        assertThat(spec.requiredItem()).isEqualTo("key");
        assertThat(spec.acceptsItems()).containsExactly("rat-poison", "arsenic");
    }

    @Test
    void affordanceSpecMinimal() throws Exception {
        var yaml = "actionType: TAKE";
        var spec = mapper.readValue(yaml, AffordanceSpec.class);
        assertThat(spec.actionType()).isEqualTo("TAKE");
        assertThat(spec.label()).isNull();
        assertThat(spec.requiredItem()).isNull();
        assertThat(spec.acceptsItems()).isNull();
    }

    @Test
    void entitySpecWithAffordances() throws Exception {
        var yaml = """
                displayName: Rat Poison
                description: A dusty bottle
                affordances:
                  - actionType: TAKE
                    label: to pick up
                """;
        var spec = mapper.readValue(yaml, EntitySpec.class);
        assertThat(spec.displayName()).isEqualTo("Rat Poison");
        assertThat(spec.description()).isEqualTo("A dusty bottle");
        assertThat(spec.affordances()).hasSize(1);
        assertThat(spec.affordances().get(0).actionType()).isEqualTo("TAKE");
    }

    @Test
    void entitySpecMinimal() throws Exception {
        var yaml = "displayName: Door";
        var spec = mapper.readValue(yaml, EntitySpec.class);
        assertThat(spec.displayName()).isEqualTo("Door");
        assertThat(spec.description()).isNull();
        assertThat(spec.affordances()).isNull();
    }

    @Test
    void inlineEntitySpecCarriesId() throws Exception {
        var yaml = """
                id: hidden-door
                displayName: Hidden Door
                """;
        var spec = mapper.readValue(yaml, InlineEntitySpec.class);
        assertThat(spec.id()).isEqualTo("hidden-door");
        assertThat(spec.displayName()).isEqualTo("Hidden Door");
    }

    @Test
    void actionDescriptorSpecAllFields() throws Exception {
        var yaml = """
                type: MOVE
                description: Move to an adjacent room
                parameterFormat: <room-id>
                """;
        var spec = mapper.readValue(yaml, ActionDescriptorSpec.class);
        assertThat(spec.type()).isEqualTo("MOVE");
        assertThat(spec.description()).isEqualTo("Move to an adjacent room");
        assertThat(spec.parameterFormat()).isEqualTo("<room-id>");
    }

    @Test
    void actionDescriptorSpecMinimal() throws Exception {
        var yaml = """
                type: LOOK
                description: Look around
                """;
        var spec = mapper.readValue(yaml, ActionDescriptorSpec.class);
        assertThat(spec.parameterFormat()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=WorldSpecSerializationTest`
Expected: Compilation failure — spec classes don't exist yet.

- [ ] **Step 3: Create the four spec records**

`AffordanceSpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;
import java.util.List;

public record AffordanceSpec(
        String actionType,
        @Nullable String label,
        @Nullable String requiredItem,
        @Nullable List<String> acceptsItems) {}
```

`EntitySpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;
import java.util.List;

public record EntitySpec(
        String displayName,
        @Nullable String description,
        @Nullable List<AffordanceSpec> affordances) {}
```

`InlineEntitySpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;
import java.util.List;

public record InlineEntitySpec(
        String id,
        String displayName,
        @Nullable String description,
        @Nullable List<AffordanceSpec> affordances) {}
```

`ActionDescriptorSpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;

public record ActionDescriptorSpec(
        String type,
        String description,
        @Nullable String parameterFormat) {}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=WorldSpecSerializationTest`
Expected: All 7 tests PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#248): AffordanceSpec, EntitySpec, InlineEntitySpec, ActionDescriptorSpec Refs #248"
```

### Task 2: ObservationSectionSpec sealed interface with polymorphic deserialization

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/world/ObservationSectionSpec.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/world/WorldSpecSerializationTest.java`

**Interfaces:**
- Consumes: `EntitySpec`, `InlineEntitySpec` from Task 1
- Produces: `ObservationSectionSpec` sealed interface with `EntityGroupSpec`, `TextBlockSpec`, `ItemListSpec` — each carrying optional annotation properties (`requiredTags`, `resolutions`, `interpretiveFrame`)

- [ ] **Step 1: Write failing polymorphic deserialization tests**

Add to `WorldSpecSerializationTest.java`:

```java
@Test
void entityGroupSpecWithRefs() throws Exception {
    var yaml = """
            type: entity-group
            header: Visible Objects
            emptyMessage: Nothing here.
            entityRefs:
              - poison
              - tea-service
            """;
    var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
    assertThat(spec).isInstanceOf(ObservationSectionSpec.EntityGroupSpec.class);
    var group = (ObservationSectionSpec.EntityGroupSpec) spec;
    assertThat(group.header()).isEqualTo("Visible Objects");
    assertThat(group.emptyMessage()).isEqualTo("Nothing here.");
    assertThat(group.entityRefs()).containsExactly("poison", "tea-service");
    assertThat(group.entities()).isNull();
}

@Test
void entityGroupSpecWithInlineEntities() throws Exception {
    var yaml = """
            type: entity-group
            header: Objects
            entities:
              - id: door
                displayName: Wooden Door
            """;
    var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
    var group = (ObservationSectionSpec.EntityGroupSpec) spec;
    assertThat(group.entities()).hasSize(1);
    assertThat(group.entities().get(0).id()).isEqualTo("door");
}

@Test
void textBlockSpec() throws Exception {
    var yaml = """
            type: text
            header: Current Location
            content: "Kitchen: A large room."
            """;
    var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
    assertThat(spec).isInstanceOf(ObservationSectionSpec.TextBlockSpec.class);
    var text = (ObservationSectionSpec.TextBlockSpec) spec;
    assertThat(text.header()).isEqualTo("Current Location");
    assertThat(text.content()).isEqualTo("Kitchen: A large room.");
}

@Test
void itemListSpec() throws Exception {
    var yaml = """
            type: item-list
            header: Your Goals
            items:
              - "[PRIMARY] Find the Diamond"
              - "[SECONDARY] Solve puzzles"
            """;
    var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
    assertThat(spec).isInstanceOf(ObservationSectionSpec.ItemListSpec.class);
    var list = (ObservationSectionSpec.ItemListSpec) spec;
    assertThat(list.items()).hasSize(2);
}

@Test
void entityGroupSpecWithAnnotationProperties() throws Exception {
    var yaml = """
            type: entity-group
            header: Secret Room
            requiredTags:
              - perception-enhanced
            resolutions:
              REDUCED:
                type: text
                header: Secret Room
                content: You sense something.
            interpretiveFrame: Only visible to enhanced agents
            entityRefs:
              - hidden-door
            """;
    var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
    var group = (ObservationSectionSpec.EntityGroupSpec) spec;
    assertThat(group.requiredTags()).containsExactly("perception-enhanced");
    assertThat(group.resolutions()).containsKey(
            io.casehub.blocks.summarisation.observation.affordance.ResolutionTier.REDUCED);
    var resolution = group.resolutions().get(
            io.casehub.blocks.summarisation.observation.affordance.ResolutionTier.REDUCED);
    assertThat(resolution).isInstanceOf(ObservationSectionSpec.TextBlockSpec.class);
    assertThat(group.interpretiveFrame()).isEqualTo("Only visible to enhanced agents");
}

@Test
void sectionWithoutAnnotationPropertiesHasNulls() throws Exception {
    var yaml = """
            type: text
            header: Location
            content: A room.
            """;
    var spec = mapper.readValue(yaml, ObservationSectionSpec.class);
    var text = (ObservationSectionSpec.TextBlockSpec) spec;
    assertThat(text.requiredTags()).isNull();
    assertThat(text.resolutions()).isNull();
    assertThat(text.interpretiveFrame()).isNull();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=WorldSpecSerializationTest`
Expected: Compilation failure — `ObservationSectionSpec` doesn't exist yet.

- [ ] **Step 3: Create ObservationSectionSpec**

```java
package io.casehub.blocks.agentic.yaml.spec.world;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import io.casehub.blocks.summarisation.observation.affordance.ResolutionTier;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ObservationSectionSpec.EntityGroupSpec.class, name = "entity-group"),
        @Type(value = ObservationSectionSpec.TextBlockSpec.class, name = "text"),
        @Type(value = ObservationSectionSpec.ItemListSpec.class, name = "item-list")
})
public sealed interface ObservationSectionSpec {

    String header();
    @Nullable Set<String> requiredTags();
    @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions();
    @Nullable String interpretiveFrame();

    record EntityGroupSpec(
            String header,
            @Nullable String emptyMessage,
            @Nullable List<String> entityRefs,
            @Nullable List<InlineEntitySpec> entities,
            @Nullable Set<String> requiredTags,
            @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
            @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}

    record TextBlockSpec(
            String header,
            String content,
            @Nullable Set<String> requiredTags,
            @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
            @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}

    record ItemListSpec(
            String header,
            @Nullable String emptyMessage,
            @Nullable List<String> items,
            @Nullable Set<String> requiredTags,
            @Nullable Map<ResolutionTier, ObservationSectionSpec> resolutions,
            @Nullable String interpretiveFrame
    ) implements ObservationSectionSpec {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=WorldSpecSerializationTest`
Expected: All 13 tests PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#248): ObservationSectionSpec sealed interface with polymorphic YAML Refs #248"
```

### Task 3: ObservationFilterSpec, RendererSpec, and WorldDefinition root

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/world/ObservationFilterSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/world/RendererSpec.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/world/WorldDefinition.java`
- Modify: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/spec/world/WorldSpecSerializationTest.java`

**Interfaces:**
- Consumes: `ActionDescriptorSpec`, `EntitySpec`, `ObservationSectionSpec` from Tasks 1-2
- Produces: `ObservationFilterSpec` sealed with `PerceptionSpec`, `RendererSpec`, `WorldDefinition` root record

- [ ] **Step 1: Write failing tests for filter spec, renderer spec, and full world definition**

Add to `WorldSpecSerializationTest.java`:

```java
@Test
void perceptionFilterSpec() throws Exception {
    var yaml = "type: perception";
    var spec = mapper.readValue(yaml, ObservationFilterSpec.class);
    assertThat(spec).isInstanceOf(ObservationFilterSpec.PerceptionSpec.class);
}

@Test
void rendererSpecAllFields() throws Exception {
    var yaml = """
            verbatimThreshold: 5
            groupedThreshold: 20
            """;
    var spec = mapper.readValue(yaml, RendererSpec.class);
    assertThat(spec.verbatimThreshold()).isEqualTo(5);
    assertThat(spec.groupedThreshold()).isEqualTo(20);
}

@Test
void rendererSpecVerbatimOnly() throws Exception {
    var yaml = "verbatimThreshold: 10";
    var spec = mapper.readValue(yaml, RendererSpec.class);
    assertThat(spec.verbatimThreshold()).isEqualTo(10);
    assertThat(spec.groupedThreshold()).isNull();
}

@Test
void worldDefinitionFullYaml() throws Exception {
    var yaml = """
            actions:
              - type: MOVE
                description: Move to a room
              - type: TAKE
                description: Pick up an object
                parameterFormat: <object-id>
            entities:
              poison:
                displayName: Rat Poison
                description: A dusty bottle
                affordances:
                  - actionType: TAKE
                    label: to pick up
              tea-service:
                displayName: Tea Service
                affordances:
                  - actionType: USE
                    acceptsItems:
                      - rat-poison
            sections:
              - type: entity-group
                header: Objects
                entityRefs:
                  - poison
                  - tea-service
              - type: text
                header: Location
                content: A kitchen.
            pipeline:
              - type: perception
            renderer:
              verbatimThreshold: 5
              groupedThreshold: 20
            """;
    var def = mapper.readValue(yaml, WorldDefinition.class);
    assertThat(def.actions()).hasSize(2);
    assertThat(def.entities()).hasSize(2);
    assertThat(def.entities()).containsKey("poison");
    assertThat(def.sections()).hasSize(2);
    assertThat(def.pipeline()).hasSize(1);
    assertThat(def.renderer()).isNotNull();
    assertThat(def.renderer().verbatimThreshold()).isEqualTo(5);
}

@Test
void worldDefinitionEmpty() throws Exception {
    var yaml = "{}";
    var def = mapper.readValue(yaml, WorldDefinition.class);
    assertThat(def.actions()).isNull();
    assertThat(def.entities()).isNull();
    assertThat(def.sections()).isNull();
    assertThat(def.pipeline()).isNull();
    assertThat(def.renderer()).isNull();
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=WorldSpecSerializationTest`
Expected: Compilation failure — new types don't exist yet.

- [ ] **Step 3: Create ObservationFilterSpec, RendererSpec, and WorldDefinition**

`ObservationFilterSpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec.world;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ObservationFilterSpec.PerceptionSpec.class, name = "perception")
})
public sealed interface ObservationFilterSpec {

    record PerceptionSpec() implements ObservationFilterSpec {}
}
```

`RendererSpec.java`:
```java
package io.casehub.blocks.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;

public record RendererSpec(
        @Nullable Integer verbatimThreshold,
        @Nullable Integer groupedThreshold) {}
```

`WorldDefinition.java`:
```java
package io.casehub.blocks.agentic.yaml.spec.world;

import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.Map;

public record WorldDefinition(
        @Nullable List<ActionDescriptorSpec> actions,
        @Nullable Map<String, EntitySpec> entities,
        @Nullable List<ObservationSectionSpec> sections,
        @Nullable List<ObservationFilterSpec> pipeline,
        @Nullable RendererSpec renderer) {}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=WorldSpecSerializationTest`
Expected: All 18 tests PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#248): ObservationFilterSpec, RendererSpec, WorldDefinition root Refs #248"
```

---

## Batch 2: Compiler — from YAML to runtime types

### Task 4: ObservationFilterRegistry and CompiledWorld

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/ObservationFilterRegistry.java`
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/CompiledWorld.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/ObservationFilterRegistryTest.java`

**Interfaces:**
- Produces: `ObservationFilterRegistry` with `register(String, Supplier<ObservationFilter>)` and `resolve(String) → ObservationFilter`. Built-in: `"perception"`.
- Produces: `CompiledWorld(List<ActionDescriptor>, Map<String, ObservableEntity>, List<ObservationSection>, @Nullable ObservationPipeline, @Nullable RendererThresholds)` with inner `RendererThresholds(int verbatimThreshold, @Nullable Integer groupedThreshold)`

- [ ] **Step 1: Write failing tests for ObservationFilterRegistry**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.summarisation.observation.affordance.ObservationFilter;
import io.casehub.blocks.summarisation.observation.affordance.PerceptionFilter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObservationFilterRegistryTest {

    @Test
    void perceptionIsRegisteredByDefault() {
        var registry = new ObservationFilterRegistry();
        ObservationFilter filter = registry.resolve("perception");
        assertThat(filter).isInstanceOf(PerceptionFilter.class);
    }

    @Test
    void unknownTypeThrows() {
        var registry = new ObservationFilterRegistry();
        assertThatThrownBy(() -> registry.resolve("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void customFilterCanBeRegistered() {
        var registry = new ObservationFilterRegistry();
        registry.register("custom", () -> (sections, tags) -> sections);
        ObservationFilter filter = registry.resolve("custom");
        assertThat(filter).isNotNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=ObservationFilterRegistryTest`
Expected: Compilation failure.

- [ ] **Step 3: Create ObservationFilterRegistry and CompiledWorld**

`ObservationFilterRegistry.java`:
```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.summarisation.observation.affordance.ObservationFilter;
import io.casehub.blocks.summarisation.observation.affordance.PerceptionFilter;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ObservationFilterRegistry {

    private final Map<String, Supplier<ObservationFilter>> factories = new HashMap<>();

    public ObservationFilterRegistry() {
        register("perception", PerceptionFilter::new);
    }

    public void register(String name, Supplier<ObservationFilter> factory) {
        factories.put(name, factory);
    }

    public ObservationFilter resolve(String name) {
        var factory = factories.get(name);
        if (factory == null) {
            throw new IllegalArgumentException("Unknown observation filter type: " + name);
        }
        return factory.get();
    }
}
```

`CompiledWorld.java`:
```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.summarisation.observation.affordance.ActionDescriptor;
import io.casehub.blocks.summarisation.observation.affordance.ObservableEntity;
import io.casehub.blocks.summarisation.observation.affordance.ObservationPipeline;
import io.casehub.blocks.summarisation.observation.affordance.ObservationSection;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;

public record CompiledWorld(
        List<ActionDescriptor> actions,
        Map<String, ObservableEntity> entities,
        List<ObservationSection> sections,
        @Nullable ObservationPipeline pipeline,
        @Nullable RendererThresholds rendererThresholds) {

    public record RendererThresholds(
            int verbatimThreshold,
            @Nullable Integer groupedThreshold) {}
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=ObservationFilterRegistryTest`
Expected: All 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#248): ObservationFilterRegistry and CompiledWorld Refs #248"
```

### Task 5: WorldCompiler

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/WorldCompiler.java`
- Test: `agentic-yaml/src/test/java/io/casehub/blocks/agentic/yaml/compiler/WorldCompilerTest.java`

**Interfaces:**
- Consumes: All spec records from Tasks 1-3, `ObservationFilterRegistry` and `CompiledWorld` from Task 4
- Produces: `WorldCompiler(ObservationFilterRegistry)` with `CompiledWorld compile(WorldDefinition)`

- [ ] **Step 1: Write failing tests for WorldCompiler**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.casehub.blocks.summarisation.observation.affordance.*;
import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorldCompilerTest {

    private ObjectMapper mapper;
    private WorldCompiler compiler;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper(new YAMLFactory());
        compiler = new WorldCompiler(new ObservationFilterRegistry());
    }

    @Test
    void emptyDefinitionProducesEmptyWorld() throws Exception {
        var def = mapper.readValue("{}", WorldDefinition.class);
        var world = compiler.compile(def);
        assertThat(world.actions()).isEmpty();
        assertThat(world.entities()).isEmpty();
        assertThat(world.sections()).isEmpty();
        assertThat(world.pipeline()).isNull();
        assertThat(world.rendererThresholds()).isNull();
    }

    @Test
    void compilesActions() throws Exception {
        var yaml = """
                actions:
                  - type: MOVE
                    description: Move to a room
                    parameterFormat: <room-id>
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.actions()).hasSize(1);
        assertThat(world.actions().get(0).actionType()).isEqualTo("MOVE");
        assertThat(world.actions().get(0).description()).isEqualTo("Move to a room");
        assertThat(world.actions().get(0).parameterFormat()).isEqualTo("<room-id>");
    }

    @Test
    void compilesTopLevelEntities() throws Exception {
        var yaml = """
                entities:
                  poison:
                    displayName: Rat Poison
                    description: A dusty bottle
                    affordances:
                      - actionType: TAKE
                        label: to pick up
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.entities()).containsKey("poison");
        var entity = world.entities().get("poison");
        assertThat(entity.id()).isEqualTo("poison");
        assertThat(entity.displayName()).isEqualTo("Rat Poison");
        assertThat(entity.affordances()).hasSize(1);
        assertThat(entity.affordances().get(0).actionType()).isEqualTo("TAKE");
        assertThat(entity.affordances().get(0).label()).isEqualTo("to pick up");
    }

    @Test
    void entityGroupResolvesRefs() throws Exception {
        var yaml = """
                entities:
                  poison:
                    displayName: Rat Poison
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - poison
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.sections()).hasSize(1);
        var group = (ObservationSection.EntityGroup) world.sections().get(0);
        assertThat(group.entities()).hasSize(1);
        assertThat(group.entities().get(0).id()).isEqualTo("poison");
    }

    @Test
    void entityGroupWithInlineEntities() throws Exception {
        var yaml = """
                sections:
                  - type: entity-group
                    header: Objects
                    entities:
                      - id: door
                        displayName: Door
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var group = (ObservationSection.EntityGroup) world.sections().get(0);
        assertThat(group.entities()).hasSize(1);
        assertThat(group.entities().get(0).id()).isEqualTo("door");
    }

    @Test
    void entityGroupMergesRefsAndInline() throws Exception {
        var yaml = """
                entities:
                  poison:
                    displayName: Rat Poison
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - poison
                    entities:
                      - id: door
                        displayName: Door
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var group = (ObservationSection.EntityGroup) world.sections().get(0);
        assertThat(group.entities()).hasSize(2);
    }

    @Test
    void unresolvedEntityRefThrows() throws Exception {
        var yaml = """
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - nonexistent
                """;
        assertThatThrownBy(() -> compiler.compile(
                mapper.readValue(yaml, WorldDefinition.class)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nonexistent");
    }

    @Test
    void textBlockCompiles() throws Exception {
        var yaml = """
                sections:
                  - type: text
                    header: Location
                    content: A kitchen.
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var text = (ObservationSection.TextBlock) world.sections().get(0);
        assertThat(text.header()).isEqualTo("Location");
        assertThat(text.content()).isEqualTo("A kitchen.");
    }

    @Test
    void itemListCompiles() throws Exception {
        var yaml = """
                sections:
                  - type: item-list
                    header: Goals
                    emptyMessage: No goals.
                    items:
                      - Find the diamond
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var list = (ObservationSection.ItemList) world.sections().get(0);
        assertThat(list.items()).containsExactly("Find the diamond");
        assertThat(list.emptyMessage()).isEqualTo("No goals.");
    }

    @Test
    void annotatedSectionWrappedWhenRequiredTagsPresent() throws Exception {
        var yaml = """
                sections:
                  - type: entity-group
                    header: Secret
                    requiredTags:
                      - enhanced
                    entityRefs: []
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.sections().get(0)).isInstanceOf(AnnotatedSection.class);
        var annotated = (AnnotatedSection) world.sections().get(0);
        assertThat(annotated.requiredTags()).containsExactly("enhanced");
    }

    @Test
    void annotatedSectionWithResolutionFallback() throws Exception {
        var yaml = """
                entities:
                  door:
                    displayName: Hidden Door
                sections:
                  - type: entity-group
                    header: Secret Room
                    requiredTags:
                      - perception
                    resolutions:
                      REDUCED:
                        type: text
                        header: Secret Room
                        content: You sense something.
                    entityRefs:
                      - door
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        var annotated = (AnnotatedSection) world.sections().get(0);
        assertThat(annotated.resolutions()).containsKey(ResolutionTier.REDUCED);
        var fallback = (ObservationSection.TextBlock) annotated.resolutions()
                .get(ResolutionTier.REDUCED);
        assertThat(fallback.content()).isEqualTo("You sense something.");
    }

    @Test
    void pipelineCompiles() throws Exception {
        var yaml = """
                pipeline:
                  - type: perception
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.pipeline()).isNotNull();
    }

    @Test
    void rendererThresholdsCompile() throws Exception {
        var yaml = """
                renderer:
                  verbatimThreshold: 5
                  groupedThreshold: 20
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.rendererThresholds()).isNotNull();
        assertThat(world.rendererThresholds().verbatimThreshold()).isEqualTo(5);
        assertThat(world.rendererThresholds().groupedThreshold()).isEqualTo(20);
    }

    @Test
    void rendererValidatesGroupedGreaterThanVerbatim() throws Exception {
        var yaml = """
                renderer:
                  verbatimThreshold: 20
                  groupedThreshold: 5
                """;
        assertThatThrownBy(() -> compiler.compile(
                mapper.readValue(yaml, WorldDefinition.class)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void fullEndToEnd() throws Exception {
        var yaml = """
                actions:
                  - type: MOVE
                    description: Move to a room
                  - type: TAKE
                    description: Pick up an object
                entities:
                  poison:
                    displayName: Rat Poison
                    affordances:
                      - actionType: TAKE
                  tea:
                    displayName: Tea Service
                    affordances:
                      - actionType: USE
                        acceptsItems:
                          - poison
                sections:
                  - type: entity-group
                    header: Objects
                    entityRefs:
                      - poison
                      - tea
                  - type: text
                    header: Location
                    content: A kitchen.
                  - type: item-list
                    header: Goals
                    items:
                      - Find the diamond
                pipeline:
                  - type: perception
                renderer:
                  verbatimThreshold: 5
                """;
        var world = compiler.compile(mapper.readValue(yaml, WorldDefinition.class));
        assertThat(world.actions()).hasSize(2);
        assertThat(world.entities()).hasSize(2);
        assertThat(world.sections()).hasSize(3);
        assertThat(world.pipeline()).isNotNull();
        assertThat(world.rendererThresholds().verbatimThreshold()).isEqualTo(5);
        assertThat(world.rendererThresholds().groupedThreshold()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=WorldCompilerTest`
Expected: Compilation failure — `WorldCompiler` doesn't exist yet.

- [ ] **Step 3: Implement WorldCompiler**

```java
package io.casehub.blocks.agentic.yaml.compiler;

import io.casehub.blocks.summarisation.observation.affordance.*;
import io.casehub.blocks.agentic.yaml.spec.world.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

public class WorldCompiler {

    private final ObservationFilterRegistry filterRegistry;

    public WorldCompiler(ObservationFilterRegistry filterRegistry) {
        this.filterRegistry = filterRegistry;
    }

    public CompiledWorld compile(WorldDefinition definition) {
        var entityMap = compileEntityMap(definition.entities());
        return new CompiledWorld(
                compileActions(definition.actions()),
                entityMap,
                compileSections(definition.sections(), entityMap),
                compilePipeline(definition.pipeline()),
                compileRenderer(definition.renderer()));
    }

    private Map<String, ObservableEntity> compileEntityMap(
            @Nullable Map<String, EntitySpec> specs) {
        if (specs == null) return Map.of();
        var result = new LinkedHashMap<String, ObservableEntity>();
        for (var entry : specs.entrySet()) {
            result.put(entry.getKey(), compileEntity(entry.getKey(), entry.getValue()));
        }
        return Collections.unmodifiableMap(result);
    }

    private ObservableEntity compileEntity(String id, EntitySpec spec) {
        return new ObservableEntity(id, spec.displayName(), spec.description(),
                compileAffordances(spec.affordances()));
    }

    private ObservableEntity compileInlineEntity(InlineEntitySpec spec) {
        return new ObservableEntity(spec.id(), spec.displayName(), spec.description(),
                compileAffordances(spec.affordances()));
    }

    private List<Affordance> compileAffordances(
            @Nullable List<AffordanceSpec> specs) {
        if (specs == null) return List.of();
        return specs.stream()
                .map(s -> new Affordance(s.actionType(), s.label(), s.requiredItem(),
                        s.acceptsItems() != null ? s.acceptsItems() : List.of()))
                .toList();
    }

    private List<ActionDescriptor> compileActions(
            @Nullable List<ActionDescriptorSpec> specs) {
        if (specs == null) return List.of();
        return specs.stream()
                .map(s -> new ActionDescriptor(s.type(), s.description(), s.parameterFormat()))
                .toList();
    }

    private List<ObservationSection> compileSections(
            @Nullable List<ObservationSectionSpec> specs,
            Map<String, ObservableEntity> entityMap) {
        if (specs == null) return List.of();
        return specs.stream()
                .map(s -> compileSection(s, entityMap))
                .toList();
    }

    private ObservationSection compileSection(ObservationSectionSpec spec,
                                               Map<String, ObservableEntity> entityMap) {
        ObservationSection base = switch (spec) {
            case ObservationSectionSpec.EntityGroupSpec g ->
                    compileEntityGroup(g, entityMap);
            case ObservationSectionSpec.TextBlockSpec t ->
                    new ObservationSection.TextBlock(t.header(), t.content());
            case ObservationSectionSpec.ItemListSpec i ->
                    new ObservationSection.ItemList(i.header(), i.emptyMessage(),
                            i.items() != null ? i.items() : List.of());
        };
        return maybeAnnotate(base, spec, entityMap);
    }

    private ObservationSection.EntityGroup compileEntityGroup(
            ObservationSectionSpec.EntityGroupSpec spec,
            Map<String, ObservableEntity> entityMap) {
        var entities = new ArrayList<ObservableEntity>();
        if (spec.entityRefs() != null) {
            for (var ref : spec.entityRefs()) {
                var entity = entityMap.get(ref);
                if (entity == null) {
                    throw new IllegalArgumentException(
                            "Unresolved entity ref '" + ref
                            + "' in section '" + spec.header() + "'");
                }
                entities.add(entity);
            }
        }
        if (spec.entities() != null) {
            for (var inline : spec.entities()) {
                entities.add(compileInlineEntity(inline));
            }
        }
        return new ObservationSection.EntityGroup(
                spec.header(), spec.emptyMessage(), entities);
    }

    private ObservationSection maybeAnnotate(ObservationSection base,
                                              ObservationSectionSpec spec,
                                              Map<String, ObservableEntity> entityMap) {
        if (spec.requiredTags() == null && spec.resolutions() == null
                && spec.interpretiveFrame() == null) {
            return base;
        }
        Map<ResolutionTier, ObservationSection> compiledResolutions = Map.of();
        if (spec.resolutions() != null) {
            compiledResolutions = new LinkedHashMap<>();
            for (var entry : spec.resolutions().entrySet()) {
                compiledResolutions.put(entry.getKey(),
                        compileSection(entry.getValue(), entityMap));
            }
            compiledResolutions = Collections.unmodifiableMap(compiledResolutions);
        }
        return new AnnotatedSection(base,
                spec.requiredTags() != null ? spec.requiredTags() : Set.of(),
                compiledResolutions,
                spec.interpretiveFrame());
    }

    private @Nullable ObservationPipeline compilePipeline(
            @Nullable List<ObservationFilterSpec> specs) {
        if (specs == null || specs.isEmpty()) return null;
        var filters = specs.stream()
                .map(this::compileFilter)
                .toArray(ObservationFilter[]::new);
        return new ObservationPipeline(filters);
    }

    private ObservationFilter compileFilter(ObservationFilterSpec spec) {
        return switch (spec) {
            case ObservationFilterSpec.PerceptionSpec ignored ->
                    filterRegistry.resolve("perception");
        };
    }

    private CompiledWorld.@Nullable RendererThresholds compileRenderer(
            @Nullable RendererSpec spec) {
        if (spec == null) return null;
        int verbatim = spec.verbatimThreshold() != null ? spec.verbatimThreshold() : 5;
        Integer grouped = spec.groupedThreshold();
        if (grouped != null && grouped <= verbatim) {
            throw new IllegalArgumentException(
                    "groupedThreshold (" + grouped
                    + ") must be > verbatimThreshold (" + verbatim + ")");
        }
        return new CompiledWorld.RendererThresholds(verbatim, grouped);
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=WorldCompilerTest`
Expected: All 15 tests PASS.

- [ ] **Step 5: Run all agentic-yaml tests to check for regressions**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All tests PASS.

- [ ] **Step 6: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#248): WorldCompiler — entity refs, annotated sections, pipeline, renderer Refs #248"
```

---

## Batch 3: Deployment + docs

### Task 6: Deployment processor and recorder

**Files:**
- Create: `agentic-yaml-deployment/src/main/java/io/casehub/blocks/agentic/yaml/deployment/WorldConfigBuildItem.java`
- Modify: `agentic-yaml-deployment/src/main/java/io/casehub/blocks/agentic/yaml/deployment/AgenticYamlProcessor.java`

**Interfaces:**
- Consumes: `WorldDefinition`, `WorldCompiler`, `ObservationFilterRegistry`, `CompiledWorld` from Tasks 3-5

- [ ] **Step 1: Create WorldConfigBuildItem**

```java
package io.casehub.blocks.agentic.yaml.deployment;

import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;
import io.quarkus.builder.item.SimpleBuildItem;
import org.jspecify.annotations.Nullable;

public final class WorldConfigBuildItem extends SimpleBuildItem {

    private final @Nullable WorldDefinition definition;

    public WorldConfigBuildItem(@Nullable WorldDefinition definition) {
        this.definition = definition;
    }

    public @Nullable WorldDefinition definition() {
        return definition;
    }
}
```

- [ ] **Step 2: Add discoverWorldConfig build step to AgenticYamlProcessor**

Add the following method to `AgenticYamlProcessor.java`:

```java
@BuildStep
WorldConfigBuildItem discoverWorldConfig() throws IOException {
    ClassLoader cl       = Thread.currentThread().getContextClassLoader();
    URL         resource = cl.getResource("META-INF/world.yaml");
    if (resource == null) {return new WorldConfigBuildItem(null);}
    try (InputStream in = resource.openStream()) {
        var definition = YAML.readValue(in, WorldDefinition.class);
        return new WorldConfigBuildItem(definition);
    }
}
```

Add the import: `import io.casehub.blocks.agentic.yaml.spec.world.WorldDefinition;`

- [ ] **Step 3: Build the deployment module to verify compilation**

Run: `mvn --batch-mode compile -pl agentic-yaml-deployment`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add agentic-yaml-deployment/src/
git -C /Users/mdproctor/claude/casehub/blocks commit -m "feat(#248): WorldConfigBuildItem + deployment discovery Refs #248"
```

### Task 7: Coverage matrix update and full build verification

**Files:**
- Modify: `docs/yaml-coverage.md` — update §15 status from Gap to Done

- [ ] **Step 1: Update coverage matrix §15**

Update the status column for all 7 Gap capabilities to `Done` and `TieredObservationRenderer` note to `Partial (thresholds Done)`. Update the summary table row for "blocks | Affordance (15)" accordingly: Done count increases from 0 to 7, Gap decreases from 7 to 0, Partial stays 1. Update the total row.

- [ ] **Step 2: Run full build**

Run: `mvn --batch-mode install`
Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 3: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/blocks add docs/yaml-coverage.md
git -C /Users/mdproctor/claude/casehub/blocks commit -m "docs(#248): update coverage matrix §15 — affordance world model Done Refs #248"
```

---

## References

- [2026-09-10-affordance-world-model-yaml-design.md] — design spec this plan implements
- [blocks/blocks/src/main/java/io/casehub/blocks/summarisation/observation/affordance/] — runtime affordance types
- [agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/cognition/] — CognitionDefinition pattern precedent
- [agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/compiler/CognitionCompiler.java] — compiler pattern precedent
- [agentic-yaml/src/main/java/io/casehub/blocks/agentic/yaml/spec/PatternSpec.java] — @JsonTypeInfo sealed interface precedent
- [agentic-yaml-deployment/src/main/java/io/casehub/blocks/agentic/yaml/deployment/AgenticYamlProcessor.java] — deployment processor precedent
- [docs/yaml-coverage.md] — capability gap inventory (§15)
- [GitHub #248] — focal issue
