# Agency Specs REST Endpoint Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #368 — Expose CognitionDefinition data via REST endpoint for frontend consumption
**Issue group:** #368

**Goal:** Serve all agentic YAML spec data (patterns, cognition, prompt optimisation, world) from a single GET endpoint for frontend consumption.

**Architecture:** A `@BuildStep` in the deployment module consumes the four existing build items, pre-serializes them to JSON at build time, and registers a CDI bean via `SyntheticBeanBuildItem`. A JAX-RS resource injects this bean and returns the pre-serialized JSON string. The `CognitionDefinition.personality` field is annotated with `@JsonProperty("personalityEvolution")` to match the frontend type.

**Tech Stack:** Quarkus deployment/recorder, JAX-RS (jakarta.ws.rs), Jackson, JUnit 5, AssertJ

## Global Constraints

- Package convention: `io.casehub.agency.core.yaml.*` (not `agentic.yaml` — matches existing code)
- No authentication on the endpoint — static, non-sensitive config metadata
- Pre-serialize to JSON at build time — avoids recorder serialization of complex sealed record hierarchies
- `jakarta.ws.rs-api` added as `provided` scope to agentic-yaml module

---

## Batch 1: Endpoint foundation and deployment wiring

### Task 1: @JsonProperty annotation on CognitionDefinition

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/cognition/CognitionDefinition.java`
- Create: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/cognition/CognitionDefinitionJsonPropertyTest.java`

**Interfaces:**
- Consumes: nothing
- Produces: `CognitionDefinition` record serializes `personality` field as `personalityEvolution` in JSON output

- [ ] **Step 1: Write the failing test**

```java
package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CognitionDefinitionJsonPropertyTest {

    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = YamlMappers.create()
            .registerModule(new JavaTimeModule());

    @Test
    void personalitySerializesAsPersonalityEvolutionInJson() throws Exception {
        var yaml = """
                personality:
                  decayFactor: 0.95
                """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);
        var json = jsonMapper.writeValueAsString(definition);

        assertThat(json).contains("\"personalityEvolution\"");
        assertThat(json).doesNotContain("\"personality\"");
    }

    @Test
    void yamlDeserializationStillUsesPersonalityFieldName() throws Exception {
        var yaml = """
                personality:
                  decayFactor: 0.95
                  l2Ceiling: 0.3
                """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);

        assertThat(definition.personality()).isNotNull();
        assertThat(definition.personality().decayFactor()).isEqualTo(0.95);
        assertThat(definition.personality().l2Ceiling()).isEqualTo(0.3);
    }

    @Test
    void jsonRoundTripPreservesData() throws Exception {
        var yaml = """
                drive:
                  changeThreshold: 0.1
                personality:
                  decayFactor: 0.8
                """;
        var definition = yamlMapper.readValue(yaml, CognitionDefinition.class);
        var json = jsonMapper.writeValueAsString(definition);
        var fromJson = jsonMapper.readValue(json, CognitionDefinition.class);

        assertThat(fromJson.drive()).isNotNull();
        assertThat(fromJson.drive().changeThreshold()).isEqualTo(0.1);
        assertThat(fromJson.personality()).isNotNull();
        assertThat(fromJson.personality().decayFactor()).isEqualTo(0.8);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=CognitionDefinitionJsonPropertyTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — `personalitySerializesAsPersonalityEvolutionInJson` fails because the field serializes as `"personality"` without the annotation.

- [ ] **Step 3: Add @JsonProperty annotation to CognitionDefinition**

Modify `CognitionDefinition.java` — add `@JsonProperty("personalityEvolution")` to the `personality` parameter:

```java
package io.casehub.agency.core.yaml.spec.cognition;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

public record CognitionDefinition(
        @Nullable DriveConfigSpec drive,
        @Nullable MoodConfigSpec mood,
        @JsonProperty("personalityEvolution")
        @Nullable PersonalityEvolutionConfigSpec personality,
        @Nullable UserModelConfigSpec userModel,
        @Nullable StrategyLearningConfigSpec strategyLearning,
        @Nullable MentalModelConfigSpec mentalModel,
        @Nullable NarrativeConfigSpec narrative,
        @Nullable GoalProposalConfigSpec goalProposal,
        @Nullable GoalEscalationConfigSpec goalEscalation,
        @Nullable NormDetectionConfigSpec normDetection,
        @Nullable CollectiveGoalConfigSpec collectiveGoal,
        @Nullable RetentionConfigSpec retention,
        @Nullable CognitiveGoalConfigSpec cognitiveGoal) {}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=CognitionDefinitionJsonPropertyTest`
Expected: PASS — all 3 tests green.

- [ ] **Step 5: Run full existing test suite to verify no regressions**

Run: `mvn --batch-mode test -pl agentic-yaml`
Expected: All tests pass. The `@JsonProperty` annotation should not affect YAML deserialization (Jackson YAML uses field names, not JSON property names, unless explicitly configured).

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/cognition/CognitionDefinition.java
git add agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/spec/cognition/CognitionDefinitionJsonPropertyTest.java
git commit -m "feat(#368): add @JsonProperty for personality → personalityEvolution

Refs #368"
```

### Task 2: Runtime classes and JAX-RS resource

**Files:**
- Modify: `agentic-yaml/pom.xml` (add `jakarta.ws.rs-api` provided dependency)
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgencySpecsBean.java`
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgencySpecsResource.java`
- Create: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/runtime/AgencySpecsResourceTest.java`

**Interfaces:**
- Consumes: nothing directly (bean is populated by deployment wiring in Task 3)
- Produces: `AgencySpecsBean` — CDI bean holding pre-serialized JSON string. `AgencySpecsResource` — JAX-RS `@Path("/api/agency/specs")` `@GET` endpoint returning the JSON string.

- [ ] **Step 1: Add jakarta.ws.rs-api dependency to agentic-yaml pom.xml**

Add to `agentic-yaml/pom.xml` in the `<dependencies>` section:

```xml
<dependency>
    <groupId>jakarta.ws.rs</groupId>
    <artifactId>jakarta.ws.rs-api</artifactId>
    <scope>provided</scope>
</dependency>
```

- [ ] **Step 2: Write the failing test**

```java
package io.casehub.agency.core.yaml.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgencySpecsResourceTest {

    @Test
    void returnsPreSerializedJson() {
        var json = """
                {"patterns":null,"cognition":{"drive":{"changeThreshold":0.1},"personalityEvolution":null},"promptOptimisation":null,"world":null}""";
        var bean = new AgencySpecsBean(json);
        var resource = new AgencySpecsResource(bean);

        assertThat(resource.get()).isEqualTo(json);
    }

    @Test
    void returnsEmptyObjectWhenNoSpecsConfigured() {
        var json = """
                {"patterns":null,"cognition":null,"promptOptimisation":null,"world":null}""";
        var bean = new AgencySpecsBean(json);
        var resource = new AgencySpecsResource(bean);

        assertThat(resource.get()).isEqualTo(json);
    }

    @Test
    void beanExposesJsonViaGetter() {
        var json = "{\"patterns\":[]}";
        var bean = new AgencySpecsBean(json);

        assertThat(bean.json()).isEqualTo(json);
    }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=AgencySpecsResourceTest -Dsurefire.failIfNoSpecifiedTests=false`
Expected: FAIL — classes do not exist.

- [ ] **Step 4: Create AgencySpecsBean**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgencySpecsBean.java`:

```java
package io.casehub.agency.core.yaml.runtime;

public class AgencySpecsBean {

    private final String json;

    public AgencySpecsBean(String json) {
        this.json = json;
    }

    public String json() {
        return json;
    }
}
```

- [ ] **Step 5: Create AgencySpecsResource**

Create `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgencySpecsResource.java`:

```java
package io.casehub.agency.core.yaml.runtime;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/agency/specs")
@Produces(MediaType.APPLICATION_JSON)
public class AgencySpecsResource {

    private final AgencySpecsBean specsBean;

    @Inject
    public AgencySpecsResource(AgencySpecsBean specsBean) {
        this.specsBean = specsBean;
    }

    @GET
    public String get() {
        return specsBean.json();
    }
}
```

Constructor injection enables direct unit testing without CDI.

- [ ] **Step 6: Run test to verify it passes**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=AgencySpecsResourceTest`
Expected: PASS — all 3 tests green.

- [ ] **Step 7: Commit**

```bash
git add agentic-yaml/pom.xml
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgencySpecsBean.java
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgencySpecsResource.java
git add agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/runtime/AgencySpecsResourceTest.java
git commit -m "feat(#368): AgencySpecsBean and AgencySpecsResource

JAX-RS GET /api/agency/specs endpoint returning pre-serialized JSON.
Refs #368"
```

### Task 3: Deployment wiring — recorder and build step

**Files:**
- Modify: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgenticRecorder.java`
- Modify: `agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AgenticYamlProcessor.java`

**Interfaces:**
- Consumes: `AgencySpecsBean(String json)` from Task 2. `AgenticPatternsBuildItem`, `CognitionConfigBuildItem`, `PromptOptimisationBuildItem`, `WorldConfigBuildItem` from existing deployment steps.
- Produces: At build time, a `SyntheticBeanBuildItem` that registers `AgencySpecsBean` as `@ApplicationScoped`. An `AdditionalBeanBuildItem` that registers `AgencySpecsResource`.

- [ ] **Step 1: Add createSpecsBean method to AgenticRecorder**

Modify `AgenticRecorder.java` — add `createSpecsBean` method:

```java
public RuntimeValue<AgencySpecsBean> createSpecsBean(String preSerializedJson) {
    return new RuntimeValue<>(new AgencySpecsBean(preSerializedJson));
}
```

Add import:
```java
import io.quarkus.runtime.RuntimeValue;
```

- [ ] **Step 2: Add build step to AgenticYamlProcessor**

Modify `AgenticYamlProcessor.java` — add the build step method and required imports.

Add imports:
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.agency.core.yaml.runtime.AgencySpecsBean;
import io.casehub.agency.core.yaml.runtime.AgencySpecsResource;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.SyntheticBeanBuildItem;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.runtime.RuntimeValue;
import jakarta.enterprise.context.ApplicationScoped;

import static io.quarkus.deployment.annotations.ExecutionTime.STATIC_INIT;
```

Add a JSON ObjectMapper field alongside the existing YAML one:

```java
private static final ObjectMapper JSON = new ObjectMapper();
```

Add the build step methods:

```java
@BuildStep
@Record(STATIC_INIT)
SyntheticBeanBuildItem registerSpecsBean(
        io.casehub.agency.core.yaml.runtime.AgenticRecorder recorder,
        AgenticPatternsBuildItem patterns,
        CognitionConfigBuildItem cognition,
        PromptOptimisationBuildItem promptOpt,
        WorldConfigBuildItem world) throws Exception {

    ObjectNode root = JSON.createObjectNode();

    if (patterns.patterns().isEmpty()) {
        root.putNull("patterns");
    } else {
        ArrayNode arr = root.putArray("patterns");
        for (var p : patterns.patterns()) {
            arr.add(JSON.valueToTree(p));
        }
    }

    root.set("cognition", cognition.definition() != null
            ? JSON.valueToTree(cognition.definition()) : root.nullNode());
    root.set("promptOptimisation", promptOpt.definition() != null
            ? JSON.valueToTree(promptOpt.definition()) : root.nullNode());
    root.set("world", world.definition() != null
            ? JSON.valueToTree(world.definition()) : root.nullNode());

    String json = JSON.writeValueAsString(root);

    RuntimeValue<AgencySpecsBean> runtimeValue = recorder.createSpecsBean(json);
    return SyntheticBeanBuildItem.configure(AgencySpecsBean.class)
            .scope(ApplicationScoped.class)
            .runtimeValue(runtimeValue)
            .done();
}

@BuildStep
AdditionalBeanBuildItem registerSpecsResource() {
    return AdditionalBeanBuildItem.unremovableOf(AgencySpecsResource.class);
}
```

A separate `JSON` ObjectMapper is used (not the `YAML` one) to ensure `writeValueAsString` produces JSON format. `valueToTree` respects `@JsonProperty` annotations, so `personalityEvolution` is correctly output.

- [ ] **Step 3: Verify the full project compiles**

Run: `mvn --batch-mode compile -pl agentic-yaml,agentic-yaml-deployment`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run full test suite**

Run: `mvn --batch-mode test`
Expected: All tests pass across all modules.

- [ ] **Step 5: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/runtime/AgenticRecorder.java
git add agentic-yaml-deployment/src/main/java/io/casehub/agency/agentic/yaml/deployment/AgenticYamlProcessor.java
git commit -m "feat(#368): deployment wiring for /api/agency/specs endpoint

BuildStep consumes all 4 spec build items, pre-serializes to JSON at
build time, registers AgencySpecsBean via SyntheticBeanBuildItem and
AgencySpecsResource via AdditionalBeanBuildItem.
Refs #368"
```

## References

- [2026-10-29-agency-specs-rest-endpoint-design.md] — design spec this plan implements
- `agentic-yaml/spec/cognition/CognitionDefinition.java` — record with @JsonProperty target
- `agentic-yaml/runtime/AgenticRecorder.java` — existing recorder to extend
- `agentic-yaml-deployment/AgenticYamlProcessor.java` — existing build steps to extend
- `CognitionConfigBuildItem.java`, `AgenticPatternsBuildItem.java`, `PromptOptimisationBuildItem.java`, `WorldConfigBuildItem.java` — existing build items consumed
- mosaic-core `types/patterns/social.ts` — frontend CognitionConfig type
- GitHub #368 — focal issue
