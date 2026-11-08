# HiveTopologyCompiler Deployment Wiring — Design Spec

**Issue:** casehubio/agency#418
**Parent:** casehubio/agency#416 (Society path maturity)
**Date:** 2026-11-07

## Summary

Wire `HiveTopologyCompiler` into the Quarkus deployment pipeline so
consumers declaring a hive topology in pattern YAML get automatic
compilation and CDI bean registration at runtime.

## Scope

1. `TopologyBuildItem` — carries extracted `TopologySpec.Hive` specs
2. `@BuildStep extractHiveTopologies` — extracts hive topology specs
   from already-parsed patterns
3. `@BuildStep @Record(RUNTIME_INIT) registerHiveTopology` — compiles
   and registers `CompiledHive` beans
4. `AgenticRecorder.compileHiveTopology()` — runtime compilation method

Out of scope:
- Standalone `META-INF/topology.yaml` discovery (YAGNI — no consumer)
- Non-hive topology types (swarm, mesh, many have no compiler yet)

## Components

### TopologyBuildItem

`SimpleBuildItem` in `agentic-yaml-deployment`:

```java
public final class TopologyBuildItem extends SimpleBuildItem {
    private final List<TopologySpec.Hive> hiveTopologies;

    public TopologyBuildItem(List<TopologySpec.Hive> hiveTopologies) {
        this.hiveTopologies = List.copyOf(hiveTopologies);
    }

    public List<TopologySpec.Hive> hiveTopologies() {
        return hiveTopologies;
    }
}
```

### @BuildStep: extractHiveTopologies

Added to `AgenticYamlProcessor`:

```java
@BuildStep
TopologyBuildItem extractHiveTopologies(AgenticPatternsBuildItem patterns) {
    var hiveSpecs = patterns.patterns().stream()
            .filter(p -> p instanceof PatternDefinition.Swarm s
                    && s.topology() instanceof TopologySpec.Hive)
            .map(p -> (TopologySpec.Hive) ((PatternDefinition.Swarm) p).topology())
            .toList();
    return new TopologyBuildItem(hiveSpecs);
}
```

Consumes `AgenticPatternsBuildItem` (already produced by
`discoverAndValidatePatterns()`). Returns empty list when no hive
topologies are found — downstream steps handle the empty case gracefully.

### @BuildStep @Record: registerHiveTopology

Added to `AgenticYamlProcessor`:

```java
@BuildStep
@Record(RUNTIME_INIT)
List<SyntheticBeanBuildItem> registerHiveTopologies(
        AgenticRecorder recorder,
        TopologyBuildItem topologies) {
    return topologies.hiveTopologies().stream()
            .map(spec -> {
                var runtimeValue = recorder.compileHiveTopology(spec);
                return SyntheticBeanBuildItem.configure(CompiledHive.class)
                        .scope(Singleton.class)
                        .runtimeValue(runtimeValue)
                        .done();
            })
            .toList();
}
```

Uses `RUNTIME_INIT` because `HiveTopologyCompiler.compile()` requires
`AgentProvider`, which is only available at runtime.

### AgenticRecorder.compileHiveTopology

New method in the existing `@Recorder`:

```java
public RuntimeValue<CompiledHive> compileHiveTopology(TopologySpec.Hive spec) {
    return new RuntimeValue<>(new HiveTopologyCompiler().compile(spec, null));
}
```

Note: `AgentProvider` injection into recorders requires the Quarkus
`@Inject` pattern on the recorder or a `BeanContainerBuildItem` lookup.
The recorder method accepts the spec and defers `AgentProvider` wiring
to the consumer. The `CompiledHive` carries the topology; consumers
inject `AgentProvider` separately when they need to invoke patterns.

**Alternative if `AgentProvider` is needed at compile time:** The recorder
method can accept it as a parameter if the build step uses
`recorder.compileHiveTopology(spec, agentProviderRuntimeValue)`. This
requires the `AgentProvider` to be produced as a `RuntimeValue` by
another build step. Since `HiveTopologyCompiler.compile()` passes
`AgentProvider` to `HivePatternAdapter` (which is a stub that returns
SUCCESS), passing `null` is safe for now. When pattern execution becomes
real, the recorder wiring will need updating.

## Flow

```
Build time:
  discoverAndValidatePatterns() → AgenticPatternsBuildItem
  extractHiveTopologies(AgenticPatternsBuildItem) → TopologyBuildItem

Runtime (RUNTIME_INIT):
  registerHiveTopologies(recorder, TopologyBuildItem)
    → recorder.compileHiveTopology(spec)
    → SyntheticBeanBuildItem<CompiledHive>
    → CDI @Singleton CompiledHive bean
```

## Test Strategy

Unit test for the extraction logic (the `@BuildStep` annotation wiring
is verified by Quarkus at integration-test time in consumer apps).

| Test case | Scenario |
|-----------|----------|
| Swarm with hive topology | Extract yields one `TopologySpec.Hive` |
| Swarm with swarm topology | Extract yields empty list |
| No swarm patterns | Extract yields empty list |
| Multiple swarm patterns | One hive + one swarm → only hive extracted |
| Null topology on swarm | Filtered out gracefully |

## References

- `AgenticYamlProcessor.java` — existing build steps
- `AgenticPatternsBuildItem.java` — pattern build item
- `AgenticRecorder.java` — existing recorder
- `HiveTopologyCompiler.java` — compiler to wire
- `PatternDefinition.Swarm` — carries `@Nullable TopologySpec topology`
- `SummarisationYamlProcessor.java` — deployment pattern reference
- `TopologySpec.java` — sealed interface with Hive variant
- decisions.md — D4, D5, D6
