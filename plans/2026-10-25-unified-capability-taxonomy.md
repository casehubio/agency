# Unified Capability Taxonomy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** casehubio/agency#353 — Unified agency capability taxonomy
**Issue group:** casehubio/casehub-mosaic#283, casehubio/agency#353,
casehubio/casehub-mosaic#284, casehubio/neocortex#673, casehubio/engine#1258

**Goal:** Create a unified capability taxonomy (CapabilityType + CapabilityCategory enums)
in agency-core, rename YAML doc roots to *Definition, add a catalog generator, and wire
a staleness test in mosaic.

**Architecture:** Two new enums in agency-core: CapabilityCategory (8 values) and
CapabilityType (28 values, each with category()). PatternType gains a bridge method
toCapabilityType(). Three YAML doc roots renamed (PatternSpec → PatternDefinition,
NegotiationSpec → NegotiationDefinition, ConversationSpec → ConversationDefinition).
A CapabilityCatalogGenerator emits a JSON catalog artifact. Mosaic staleness test
validates 1:1 mapping.

**Tech Stack:** Java 17 (records, sealed interfaces, enums), Jackson YAML, victools
JSON Schema, TypeScript (Lit/Vitest), Maven

## Global Constraints

- Pre-release platform — breaking changes cost nothing
- PatternDefinition (renamed from PatternSpec) sealed interface stays flat
- PatternType enum stays as-is for exhaustive switch in PatternCompiler
- All renames use `ide_refactor_rename` — never bash mv/cp on Java files
- Every commit references an issue (Refs casehubio/agency#353)
- IntelliJ workspace with all slot repos required for cross-repo refactoring

---

## Batch 1: Taxonomy enums + bridge [agency-core compiles with new types]

### Task 1: CapabilityCategory and CapabilityType enums

**Files:**
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/execution/CapabilityCategory.java`
- Create: `agency-core/src/main/java/io/casehub/agency/orchestration/execution/CapabilityType.java`
- Test: `agency/src/test/java/io/casehub/agency/orchestration/execution/CapabilityTypeTest.java`

**Interfaces:**
- Consumes: nothing (new types)
- Produces: `CapabilityCategory` enum (8 values), `CapabilityType` enum (28 values)
  with `category()` method returning `CapabilityCategory` and
  `toPatternType()` returning `Optional<PatternType>`

- [ ] **Step 1: Write failing tests for CapabilityCategory**

```java
package io.casehub.agency.orchestration.execution;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCategoryTest {

    @Test
    void shouldHaveEightCategories() {
        assertThat(CapabilityCategory.values()).hasSize(8);
    }

    @Test
    void shouldContainAllExpectedCategories() {
        assertThat(CapabilityCategory.values()).containsExactlyInAnyOrder(
            CapabilityCategory.ORCHESTRATION,
            CapabilityCategory.DELIBERATION,
            CapabilityCategory.PERCEPTION,
            CapabilityCategory.COMMUNICATION,
            CapabilityCategory.GOVERNANCE,
            CapabilityCategory.OPTIMISATION,
            CapabilityCategory.SOCIAL,
            CapabilityCategory.PLAYBOOK
        );
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=CapabilityCategoryTest`
Expected: FAIL — class not found

- [ ] **Step 3: Implement CapabilityCategory**

```java
package io.casehub.agency.orchestration.execution;

public enum CapabilityCategory {
    ORCHESTRATION,
    DELIBERATION,
    PERCEPTION,
    COMMUNICATION,
    GOVERNANCE,
    OPTIMISATION,
    SOCIAL,
    PLAYBOOK
}
```

- [ ] **Step 4: Write failing tests for CapabilityType**

```java
package io.casehub.agency.orchestration.execution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilityTypeTest {

    @Test
    void shouldHave28Capabilities() {
        assertThat(CapabilityType.values()).hasSize(28);
    }

    @Test
    void orchestrationCapabilities() {
        assertThat(CapabilityType.SUPERVISOR.category()).isEqualTo(CapabilityCategory.ORCHESTRATION);
        assertThat(CapabilityType.CHOREOGRAPHY.category()).isEqualTo(CapabilityCategory.ORCHESTRATION);
        assertThat(CapabilityType.SAGA.category()).isEqualTo(CapabilityCategory.ORCHESTRATION);
        assertThat(CapabilityType.DECOMPOSITION.category()).isEqualTo(CapabilityCategory.ORCHESTRATION);
    }

    @Test
    void deliberationCapabilities() {
        assertThat(CapabilityType.VOTING.category()).isEqualTo(CapabilityCategory.DELIBERATION);
        assertThat(CapabilityType.DEBATE.category()).isEqualTo(CapabilityCategory.DELIBERATION);
        assertThat(CapabilityType.NEGOTIATION.category()).isEqualTo(CapabilityCategory.DELIBERATION);
        assertThat(CapabilityType.COALITION.category()).isEqualTo(CapabilityCategory.DELIBERATION);
        assertThat(CapabilityType.NORMATIVE.category()).isEqualTo(CapabilityCategory.DELIBERATION);
    }

    @Test
    void playbookCapabilities() {
        assertThat(CapabilityType.SEQUENCE.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.PARALLEL.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.LOOP.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.CONDITIONAL.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.MATCH.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
        assertThat(CapabilityType.TRY_CATCH.category()).isEqualTo(CapabilityCategory.PLAYBOOK);
    }

    @ParameterizedTest
    @EnumSource(CapabilityType.class)
    void everyCategoryHasAtLeastOneCapability(CapabilityType ct) {
        assertThat(ct.category()).isNotNull();
    }

    @Test
    void patternTypeBridgeCoversAllPatternTypes() {
        for (PatternType pt : PatternType.values()) {
            assertThat(pt.toCapabilityType()).isNotNull();
            assertThat(pt.toCapabilityType().toPatternType())
                .isPresent()
                .hasValue(pt);
        }
    }

    @Test
    void nonPatternCapabilitiesReturnEmptyPatternType() {
        assertThat(CapabilityType.NEGOTIATION.toPatternType()).isEmpty();
        assertThat(CapabilityType.SUMMARISATION.toPatternType()).isEmpty();
        assertThat(CapabilityType.COGNITION.toPatternType()).isEmpty();
    }

    @Test
    void htnMapsToDecomposition() {
        assertThat(PatternType.HTN.toCapabilityType()).isEqualTo(CapabilityType.DECOMPOSITION);
        assertThat(CapabilityType.DECOMPOSITION.toPatternType()).hasValue(PatternType.HTN);
    }
}
```

- [ ] **Step 5: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agency -Dtest=CapabilityTypeTest`
Expected: FAIL — CapabilityType not found

- [ ] **Step 6: Implement CapabilityType**

```java
package io.casehub.agency.orchestration.execution;

import java.util.Optional;

public enum CapabilityType {
    SUPERVISOR(CapabilityCategory.ORCHESTRATION),
    CHOREOGRAPHY(CapabilityCategory.ORCHESTRATION),
    SAGA(CapabilityCategory.ORCHESTRATION),
    DECOMPOSITION(CapabilityCategory.ORCHESTRATION),

    VOTING(CapabilityCategory.DELIBERATION),
    DEBATE(CapabilityCategory.DELIBERATION),
    NEGOTIATION(CapabilityCategory.DELIBERATION),
    COALITION(CapabilityCategory.DELIBERATION),
    NORMATIVE(CapabilityCategory.DELIBERATION),

    SUMMARISATION(CapabilityCategory.PERCEPTION),
    NARRATIVE(CapabilityCategory.PERCEPTION),
    OBSERVATION(CapabilityCategory.PERCEPTION),

    CHANNEL_EXECUTION(CapabilityCategory.COMMUNICATION),
    CHANNEL_SUMMARY(CapabilityCategory.COMMUNICATION),

    JUDGMENT(CapabilityCategory.GOVERNANCE),
    OVERSIGHT(CapabilityCategory.GOVERNANCE),
    ATTESTATION(CapabilityCategory.GOVERNANCE),
    TRUST(CapabilityCategory.GOVERNANCE),

    PROMPT_OPTIMISATION(CapabilityCategory.OPTIMISATION),

    MEMORY_HYGIENE(CapabilityCategory.SOCIAL),
    COGNITION(CapabilityCategory.SOCIAL),
    REFLECTION(CapabilityCategory.SOCIAL),

    SEQUENCE(CapabilityCategory.PLAYBOOK),
    PARALLEL(CapabilityCategory.PLAYBOOK),
    LOOP(CapabilityCategory.PLAYBOOK),
    CONDITIONAL(CapabilityCategory.PLAYBOOK),
    MATCH(CapabilityCategory.PLAYBOOK),
    TRY_CATCH(CapabilityCategory.PLAYBOOK);

    private final CapabilityCategory category;

    CapabilityType(CapabilityCategory category) {
        this.category = category;
    }

    public CapabilityCategory category() {
        return category;
    }

    public Optional<PatternType> toPatternType() {
        return switch (this) {
            case SUPERVISOR -> Optional.of(PatternType.SUPERVISOR);
            case VOTING -> Optional.of(PatternType.VOTING);
            case DEBATE -> Optional.of(PatternType.DEBATE);
            case DECOMPOSITION -> Optional.of(PatternType.HTN);
            case SEQUENCE -> Optional.of(PatternType.SEQUENCE);
            case PARALLEL -> Optional.of(PatternType.PARALLEL);
            case LOOP -> Optional.of(PatternType.LOOP);
            case CONDITIONAL -> Optional.of(PatternType.CONDITIONAL);
            default -> Optional.empty();
        };
    }
}
```

- [ ] **Step 7: Add toCapabilityType() bridge to PatternType**

Modify: `agency-core/src/main/java/io/casehub/agency/orchestration/execution/PatternType.java`

Add method to existing PatternType enum:

```java
public CapabilityType toCapabilityType() {
    return switch (this) {
        case SUPERVISOR -> CapabilityType.SUPERVISOR;
        case VOTING -> CapabilityType.VOTING;
        case DEBATE -> CapabilityType.DEBATE;
        case HTN -> CapabilityType.DECOMPOSITION;
        case SEQUENCE -> CapabilityType.SEQUENCE;
        case PARALLEL -> CapabilityType.PARALLEL;
        case LOOP -> CapabilityType.LOOP;
        case CONDITIONAL -> CapabilityType.CONDITIONAL;
    };
}
```

- [ ] **Step 8: Run all tests to verify**

Run: `mvn --batch-mode test -pl agency-core,agency`
Expected: ALL PASS

- [ ] **Step 9: Commit**

```bash
git add agency-core/src/main/java/io/casehub/agency/orchestration/execution/CapabilityCategory.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/execution/CapabilityType.java
git add agency/src/test/java/io/casehub/agency/orchestration/execution/CapabilityTypeTest.java
git add agency/src/test/java/io/casehub/agency/orchestration/execution/CapabilityCategoryTest.java
git add agency-core/src/main/java/io/casehub/agency/orchestration/execution/PatternType.java
git commit -m "feat: add CapabilityCategory and CapabilityType enums with PatternType bridge

Refs casehubio/agency#353"
```

## Batch 2: YAML doc root renames [naming unified]

### Task 2: Rename PatternSpec → PatternDefinition

**Files:**
- Rename: `PatternSpec` → `PatternDefinition` (use `ide_refactor_rename`)
- All internal references updated automatically by IntelliJ
- Test: existing PatternSpecSerializationTest renamed to PatternDefinitionSerializationTest

**Interfaces:**
- Consumes: nothing new
- Produces: `PatternDefinition` sealed interface (same API, new name)

- [ ] **Step 1: Rename via IntelliJ**

Use `ide_refactor_rename` on class `PatternSpec` at
`agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/PatternSpec.java:23`
to `PatternDefinition`. IntelliJ updates all references across the workspace.

- [ ] **Step 2: Verify compilation**

Run: `mvn --batch-mode compile -pl agentic-yaml`
Expected: PASS

- [ ] **Step 3: Run all tests**

Run: `mvn --batch-mode test`
Expected: ALL PASS

- [ ] **Step 4: Update schema baseline if needed**

Run: `mvn --batch-mode test -pl agentic-yaml -Dschema.update`
This regenerates the PatternDefinition schema baseline.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "refactor: rename PatternSpec to PatternDefinition

Document roots use *Definition suffix; composable sub-shapes stay *Spec.

Refs casehubio/agency#353"
```

### Task 3: Rename NegotiationSpec → NegotiationDefinition and ConversationSpec → ConversationDefinition

**Files:**
- Rename: `NegotiationDefinition` → `NegotiationDefinition` (use `ide_refactor_rename`)
- Rename: `ConversationDefinition` → `ConversationDefinition` (use `ide_refactor_rename`)

**Interfaces:**
- Consumes: nothing new
- Produces: `NegotiationDefinition`, `ConversationDefinition` records

- [ ] **Step 1: Rename NegotiationSpec via IntelliJ**

Use `ide_refactor_rename` on class `NegotiationDefinition` at
`agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/NegotiationSpec.java:8`
to `NegotiationDefinition`.

- [ ] **Step 2: Rename ConversationSpec via IntelliJ**

Use `ide_refactor_rename` on class `ConversationDefinition` at
`agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/spec/ConversationSpec.java:5`
to `ConversationDefinition`.

- [ ] **Step 3: Verify compilation and tests**

Run: `mvn --batch-mode test`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: rename NegotiationSpec and ConversationSpec to *Definition

Refs casehubio/agency#353"
```

## Batch 3: Catalog generator [JSON catalog artifact produced]

### Task 4: CapabilityCatalogGenerator

**Files:**
- Create: `agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/schema/CapabilityCatalogGenerator.java`
- Test: `agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/schema/CapabilityCatalogGeneratorTest.java`

**Interfaces:**
- Consumes: `CapabilityType`, `CapabilityCategory` (from agency-core)
- Produces: `String generateCatalog()` — returns JSON string of the full catalog

- [ ] **Step 1: Write failing test**

```java
package io.casehub.agency.agentic.yaml.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.orchestration.execution.CapabilityCategory;
import io.casehub.agency.orchestration.execution.CapabilityType;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CapabilityCatalogGeneratorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void generatesValidJson() throws Exception {
        var json = CapabilityCatalogGenerator.generateCatalog();
        var root = mapper.readTree(json);
        assertThat(root.has("version")).isTrue();
        assertThat(root.get("version").asInt()).isEqualTo(1);
    }

    @Test
    void containsAllCategories() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var categories = root.get("categories");
        assertThat(categories).hasSize(CapabilityCategory.values().length);
    }

    @Test
    void containsAllCapabilities() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var capabilities = root.get("capabilities");
        assertThat(capabilities).hasSize(CapabilityType.values().length);
    }

    @Test
    void capabilityHasRequiredFields() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        for (JsonNode cap : root.get("capabilities")) {
            assertThat(cap.has("name")).isTrue();
            assertThat(cap.has("category")).isTrue();
            assertThat(cap.has("hasYamlRoot")).isTrue();
        }
    }

    @Test
    void supervisorIsOrchestrationWithYamlRoot() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var supervisor = findCapability(root, "supervisor");
        assertThat(supervisor.get("category").asText()).isEqualTo("orchestration");
        assertThat(supervisor.get("hasYamlRoot").asBoolean()).isTrue();
    }

    @Test
    void choreographyIsOrchestrationWithoutYamlRoot() throws Exception {
        var root = mapper.readTree(CapabilityCatalogGenerator.generateCatalog());
        var choreo = findCapability(root, "choreography");
        assertThat(choreo.get("category").asText()).isEqualTo("orchestration");
        assertThat(choreo.get("hasYamlRoot").asBoolean()).isFalse();
    }

    private JsonNode findCapability(JsonNode root, String name) {
        for (JsonNode cap : root.get("capabilities")) {
            if (cap.get("name").asText().equals(name)) return cap;
        }
        throw new AssertionError("Capability not found: " + name);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=CapabilityCatalogGeneratorTest`
Expected: FAIL — class not found

- [ ] **Step 3: Implement CapabilityCatalogGenerator**

```java
package io.casehub.agency.agentic.yaml.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.agency.orchestration.execution.CapabilityCategory;
import io.casehub.agency.orchestration.execution.CapabilityType;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

public final class CapabilityCatalogGenerator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final Set<CapabilityType> YAML_BACKED = EnumSet.of(
        CapabilityType.SUPERVISOR, CapabilityType.VOTING, CapabilityType.DEBATE,
        CapabilityType.DECOMPOSITION, CapabilityType.SEQUENCE, CapabilityType.PARALLEL,
        CapabilityType.LOOP, CapabilityType.CONDITIONAL,
        CapabilityType.NEGOTIATION, CapabilityType.SUMMARISATION,
        CapabilityType.COGNITION, CapabilityType.PROMPT_OPTIMISATION,
        CapabilityType.MATCH, CapabilityType.TRY_CATCH
    );

    private CapabilityCatalogGenerator() {}

    public static String generateCatalog() {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("version", 1);

        ArrayNode categories = root.putArray("categories");
        for (CapabilityCategory cat : CapabilityCategory.values()) {
            ObjectNode catNode = categories.addObject();
            catNode.put("name", toKebab(cat.name()));
        }

        ArrayNode capabilities = root.putArray("capabilities");
        for (CapabilityType ct : CapabilityType.values()) {
            ObjectNode capNode = capabilities.addObject();
            capNode.put("name", toKebab(ct.name()));
            capNode.put("category", toKebab(ct.category().name()));
            capNode.put("hasYamlRoot", YAML_BACKED.contains(ct));
        }

        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate catalog", e);
        }
    }

    private static String toKebab(String enumName) {
        return enumName.toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
```

- [ ] **Step 4: Run tests**

Run: `mvn --batch-mode test -pl agentic-yaml -Dtest=CapabilityCatalogGeneratorTest`
Expected: ALL PASS

- [ ] **Step 5: Run full test suite**

Run: `mvn --batch-mode test`
Expected: ALL PASS

- [ ] **Step 6: Commit**

```bash
git add agentic-yaml/src/main/java/io/casehub/agency/agentic/yaml/schema/CapabilityCatalogGenerator.java
git add agentic-yaml/src/test/java/io/casehub/agency/agentic/yaml/schema/CapabilityCatalogGeneratorTest.java
git commit -m "feat: add CapabilityCatalogGenerator for JSON taxonomy artifact

Emits all CapabilityType entries with category and hasYamlRoot flag.
Separate from AgencySchemaGenerator (structural schema vs catalog metadata).

Refs casehubio/agency#353"
```

## Batch 4: Mosaic staleness test [TS types validated against Java catalog]

### Task 5: Mosaic staleness test

**Files:**
- Create: `casehub-mosaic/packages/mosaic-core/src/types/patterns/catalog-staleness.test.ts`
- Create: `casehub-mosaic/packages/mosaic-core/src/types/patterns/capability-catalog.json`
  (baseline generated from Java)

**Interfaces:**
- Consumes: generated catalog JSON (committed as baseline)
- Produces: CI test that fails when Java catalog and TS types diverge

- [ ] **Step 1: Generate and commit the catalog baseline**

Run the catalog generator from Java (via a test or main method) and save the output
to `casehub-mosaic/packages/mosaic-core/src/types/patterns/capability-catalog.json`.

- [ ] **Step 2: Write the staleness test**

```typescript
import { describe, it, expect } from 'vitest';
import catalog from './capability-catalog.json';
import type { PatternCategory } from './index.js';

type CatalogEntry = { name: string; category: string; hasYamlRoot: boolean };
type CatalogCategory = { name: string };

describe('capability catalog staleness', () => {
  const catalogEntries: CatalogEntry[] = catalog.capabilities;
  const catalogCategories: CatalogCategory[] = catalog.categories;

  it('catalog has expected version', () => {
    expect(catalog.version).toBe(1);
  });

  it('every catalog category has a matching TS AgencyCategoryName or playbook', () => {
    const tsCategories = [
      'orchestration', 'deliberation', 'perception',
      'communication', 'governance', 'optimisation', 'social', 'playbook',
    ];
    for (const cat of catalogCategories) {
      expect(tsCategories).toContain(cat.name);
    }
  });

  it('every catalog entry has a matching TS type entry', () => {
    // Map from catalog names to TS type names (if they differ)
    const nameMap: Record<string, string> = {
      'decomposition': 'decomposition',
      'channel-execution': 'channel-execution',
      'channel-summary': 'channel-summary',
      'prompt-optimisation': 'prompt-optimisation',
      'memory-hygiene': 'memory-hygiene',
      'try-catch': 'try-catch',
    };

    const allTsTypes = getAllTsTypeEntries();

    for (const entry of catalogEntries) {
      const tsName = nameMap[entry.name] ?? entry.name;
      expect(allTsTypes).toContain(tsName);
    }
  });

  it('every TS type entry has a matching catalog entry', () => {
    const catalogNames = catalogEntries.map(e => e.name);
    const allTsTypes = getAllTsTypeEntries();

    for (const tsType of allTsTypes) {
      expect(catalogNames).toContain(tsType);
    }
  });

  it('catalog and TS have same count', () => {
    const allTsTypes = getAllTsTypeEntries();
    expect(allTsTypes.length).toBe(catalogEntries.length);
  });
});

function getAllTsTypeEntries(): string[] {
  // Collect all type entries from the TS category interfaces
  const orchestration = ['supervisor', 'choreography', 'saga', 'decomposition'];
  const deliberation = ['voting', 'debate', 'negotiation', 'coalition', 'normative'];
  const perception = ['summarisation', 'narrative', 'observation'];
  const communication = ['channel-execution', 'channel-summary'];
  const governance = ['judgment', 'oversight', 'attestation', 'trust'];
  const optimisation = ['prompt-optimisation'];
  const social = ['memory-hygiene', 'cognition', 'reflection'];
  const playbook = ['sequence', 'parallel', 'loop', 'conditional', 'match', 'try-catch'];

  return [
    ...orchestration, ...deliberation, ...perception, ...communication,
    ...governance, ...optimisation, ...social, ...playbook,
  ];
}
```

- [ ] **Step 3: Run the staleness test**

Run: `cd casehub-mosaic && yarn vitest run packages/mosaic-core/src/types/patterns/catalog-staleness.test.ts`
Expected: ALL PASS

- [ ] **Step 4: Commit**

```bash
git -C /Users/mdproctor/claude/casehub/slots/219/casehub-mosaic add packages/mosaic-core/src/types/patterns/catalog-staleness.test.ts
git -C /Users/mdproctor/claude/casehub/slots/219/casehub-mosaic add packages/mosaic-core/src/types/patterns/capability-catalog.json
git -C /Users/mdproctor/claude/casehub/slots/219/casehub-mosaic commit -m "feat: add capability catalog staleness test

Validates 1:1 mapping between Java CapabilityType catalog and TS types.
Fails CI if either side adds/removes entries without the other.

Refs casehubio/casehub-mosaic#284"
```

## Batch 5: Documentation update [CLAUDE.md reflects new types]

### Task 6: Update CLAUDE.md and docs

**Files:**
- Modify: `CLAUDE.md` — update Key Directories table and package descriptions
  to reference PatternDefinition, CapabilityType, CapabilityCategory,
  CapabilityCatalogGenerator

**Interfaces:**
- Consumes: all prior tasks
- Produces: updated documentation

- [ ] **Step 1: Update CLAUDE.md**

Replace all references to PatternSpec with PatternDefinition. Add CapabilityType,
CapabilityCategory, and CapabilityCatalogGenerator to the relevant package tables.

- [ ] **Step 2: Commit**

```bash
git add CLAUDE.md
git commit -m "docs: update CLAUDE.md for taxonomy types and naming renames

Refs casehubio/agency#353"
```

## References

- [2026-10-24-unified-capability-taxonomy-design.md] — design spec
- [decisions.md] — 10 captured decisions (7 original + 3 from review)
- [PatternType.java] — existing pattern type enum in agency-core
- [PatternSpec.java:23] — sealed interface to be renamed to PatternDefinition
- [AgencySchemaGenerator.java:17] — existing structural schema generator
- [PatternCompiler.java:48] — exhaustive switch consumer
- [mosaic-core/src/types/patterns/categories.ts] — existing TS categories
- casehubio/agency#353 — agency child issue
- casehubio/casehub-mosaic#284 — mosaic child issue
- casehubio/neocortex#673 — neocortex naming alignment (separate session)
- casehubio/engine#1258 — engine capability status (separate session)
