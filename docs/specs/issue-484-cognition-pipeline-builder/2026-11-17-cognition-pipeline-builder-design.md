# Assemble CognitionCore through CognitivePipelineBuilder

**Issue:** casehubio/agency#484
**Prerequisites:** neocortex#1055 (EffectiveCognitiveDefaults — closed), neocortex#712 (situational personality — closed)

## Problem

`CognitionCore` is hand-built in three places:

1. **AgencyBeans** (Quarkus CDI) — 18-arg constructor + 4 post-construction participants
2. **AgencyAutoConfiguration** (Spring) — 17-arg constructor + 3 conditional participants
3. **CognitionStack** (agentic-yaml test) — 8-arg constructor at five fidelity levels

All three miss capabilities that `CognitivePipelineBuilder` wires: appraisal, emotion regulation, regulation goals, gut feeling, drive-goal bridge, effective cognitive defaults, entity rendering, cognitive journal, goal-emotion-mood bridge, and cognitive memory retrieval. Production agents run without these.

`CognitionCoreFactory` (CDI `@ApplicationScoped` in neocortex) and `CognitivePipelineBuilder` exist and wire everything correctly — but have zero callers.

## Change

### 1. Neocortex: constructor goes package-private

Make all three `CognitionCore` constructors package-private. The builder (same package: `io.casehub.neocortex.cognition.core`) retains access. External code must go through `CognitivePipelineBuilder` or `CognitionCoreFactory`.

This is a breaking change by design. Any consumer calling `new CognitionCore(...)` gets a compile error — the fix is to route through the builder.

### 2. AgencyBeans (Quarkus): delegate to CognitionCoreFactory

Replace the 18-arg constructor call and the 4 manual `addParticipant`/`chainSectionCustomizer` blocks with:

```java
@Produces @ApplicationScoped
public CognitionAvatarAdapter cognitionAvatarAdapter(
        CognitionCoreFactory coreFactory,
        MoodOrchestrator mood,
        CognitiveGoalConfig cognitiveGoalConfig) {
    var core = coreFactory.create(CognitionConfig.all());

    // Agency-specific: richer EmergentGoalPromptSection with drive-goal awareness
    var cgo = /* resolve CognitiveGoalOrchestrator from core or construct */;
    if (cgo != null) {
        var driveGoalOrch = nullableFrom(goalProposalOrchestratorInstance);
        core.chainSectionCustomizer(sections -> {
            var result = new java.util.ArrayList<>(sections);
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i) instanceof EmergentGoalPromptSection) {
                    result.set(i, new EmergentGoalPromptSection(driveGoalOrch, cgo, cognitiveGoalConfig));
                    return result;
                }
            }
            result.add(new EmergentGoalPromptSection(driveGoalOrch, cgo, cognitiveGoalConfig));
            return result;
        });
    }

    return new CognitionAvatarAdapter(core, nullableFrom(agentRegistryInstance), cgo);
}
```

**What is removed from AgencyBeans:**
- The 18 `Instance<T>` fields for cognitive SPIs (mood, drives, userModel, etc.) — the factory resolves these itself via CDI
- Manual `CognitiveProfileParticipant` creation + `EntityKnowledgePromptSection`/`SocialComparisonPromptSection` customizers — `configureCognitiveProfile()` already does this
- Manual `DomainActivationParticipant` creation + `DomainActivationPromptSection` customizer — `configureDomainActivation()` already does this
- Manual `SubThoughtTickParticipant` wiring — `configureSubThoughts()` already does this
- Manual `CognitiveGoalOrchestrator` construction and `addParticipant` — `configureCognitiveGoalOrchestrator()` does this

**What remains agency-specific:**
- The `EmergentGoalPromptSection` replacement (richer constructor with drive-goal awareness)
- The `CognitionAvatarAdapter` construction (agency's composition root)

**CognitiveGoalOrchestrator as a producer bean:** The issue says CognitiveGoalOrchestrator becomes a producer bean so the factory resolves it. Currently AgencyBeans constructs it inline with a mood-to-PAD lambda. This becomes a `@Produces @ApplicationScoped` method so `CognitionCoreFactory` can inject it.

### 3. AgencyAutoConfiguration (Spring): build through CognitivePipelineBuilder

Replace the 17-arg constructor call with the builder's fluent API:

```java
var builder = CognitivePipelineBuilder.create(mood, drives)
    .userModel(userModel).mentalModel(mentalModel).strategy(strategy)
    .narrative(narrativeOrchestrator.orElse(null))
    .goals(goalProposalOrchestrator.orElse(null))
    .memoryHygiene(hygieneOrchestrator.orElse(null))
    .innerLife(innerLifeOrchestrator.orElse(null))
    .mindMapStore(mindMapStore.orElse(null))
    // ... all remaining optional params
    ;
var core = builder.build();
```

**New `@ConditionalOnMissingBean` beans for SPIs the builder now resolves:**
- `EffectiveCognitiveDefaults` — `@ConditionalOnMissingBean`, returns null or a default
- `SituationalProfileResolver` — `@ConditionalOnMissingBean`
- `SituationalStateService` — `@ConditionalOnMissingBean`
- Other SPIs that `CognitivePipelineBuilder.build()` calls `configure*()` for, when the Spring path previously didn't expose them

**What is removed from AgencyAutoConfiguration:**
- The 17-arg constructor call
- Manual participant/customizer wiring (same as Quarkus — the builder handles it)

**What remains agency-specific:**
- The `EmergentGoalPromptSection` customizer (same pattern as Quarkus)
- `CognitiveGoalOrchestrator` as an explicit `@Bean` (with `@ConditionalOnMissingBean`)

### 4. CognitionStack (test): build through the builder at each stage

Each `Stage` maps to a builder configuration level:

| Stage | Builder calls |
|-------|--------------|
| BASELINE | `create(mood, stubDrives)` — just mood + stub drives |
| SIGNALS | + `.userModel(umo).mentalModel(mmo)` |
| REAL_DRIVES | + `.strategy(slo).memoryHygiene(hygiene)` with real drive sources |
| NARRATIVE | + narrative orchestrator with LLM-backed extraction |
| FULL | + `.goals(gpo)` with drive-goal mappers |

The builder's partial construction (all fields nullable) maps directly to the fidelity levels. At REAL_DRIVES and above, the builder also wires regulation and appraisal — capabilities CognitionStack currently lacks.

**Synthetic CBR priming** (`primeIfNeeded`) and **LLM-backed narrative extraction** stay in CognitionStack — they're test-specific concerns the builder doesn't handle.

### 5. Wiring tests

Two new tests verify the bean graphs boot correctly:

**Quarkus wiring test:** Boot the CDI container with agency + neocortex on the classpath. Assert:
- `CognitionCoreFactory` resolves
- The core it produces has participants registered in FOUNDATION, SOURCE, DERIVED, TERMINAL phases
- Appraisal and regulation participants are present (the capabilities the hand-built path was missing)

**Spring wiring test:** Boot the Spring context with `AgencyAutoConfiguration`. Assert the same participant presence.

## Not in scope

- Exposing new YAML knobs for appraisal/regulation configuration — that's a separate design decision
- Changing the builder's API or adding new hooks to it — agency uses `chainSectionCustomizer` post-construction
- Migrating `EmergentGoalPromptSection` into neocortex — it's agency-specific for now

## References

- CognitionCoreFactory.java — neocortex CDI factory (zero callers)
- CognitivePipelineBuilder.java — builder with 14 configure calls
- CognitionCore.java — 1305-line pipeline with package-scoped configure methods
- AgencyBeans.java:362-447 — current Quarkus hand-build
- AgencyAutoConfiguration.java:314-382 — current Spring hand-build
- CognitionStack.java — test helper with 5 fidelity stages
- casehubio/neocortex#1055 — EffectiveCognitiveDefaults (closed, prerequisite)
- casehubio/neocortex#712 — situational personality (closed, prerequisite)
