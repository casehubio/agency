package io.casehub.agency.core.yaml.deployment;

import io.casehub.agency.core.yaml.spec.PatternDefinition;
import io.casehub.agency.core.yaml.spec.TopologySpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TopologyExtractionTest {

    private final AgenticYamlProcessor processor = new AgenticYamlProcessor();

    private static PatternDefinition.Swarm swarmWithHive() {
        return new PatternDefinition.Swarm(
                "test", "desc", null, null, null, null, null, null,
                "task", List.of(), null, null,
                null, null, null, 0,
                new TopologySpec.Hive(null, List.of("coordinator", "worker"),
                        "supervisor", null),
                null, null);
    }

    private static PatternDefinition.Swarm swarmWithSwarmTopology() {
        return new PatternDefinition.Swarm(
                "test", "desc", null, null, null, null, null, null,
                "task", List.of(), null, null,
                null, null, null, 0,
                new TopologySpec.Swarm(null),
                null, null);
    }

    private static PatternDefinition.Swarm swarmWithNullTopology() {
        return new PatternDefinition.Swarm(
                "test", "desc", null, null, null, null, null, null,
                "task", List.of(), null, null,
                null, null, null, 0,
                null,
                null, null);
    }

    private static PatternDefinition.Supervisor nonSwarmPattern() {
        return new PatternDefinition.Supervisor(
                "test", "desc", null, null, null, null, null, null,
                "task", List.of(), null, null);
    }

    @Test
    void swarmWithHiveTopology_extracted() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(swarmWithHive())));

        assertThat(item.hiveTopologies()).hasSize(1);
        assertThat(item.hiveTopologies().get(0).orchestration()).isEqualTo("supervisor");
        assertThat(item.hiveTopologies().get(0).roles()).containsExactly("coordinator", "worker");
    }

    @Test
    void swarmWithSwarmTopology_notExtracted() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(swarmWithSwarmTopology())));

        assertThat(item.hiveTopologies()).isEmpty();
    }

    @Test
    void noSwarmPatterns_emptyResult() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(nonSwarmPattern())));

        assertThat(item.hiveTopologies()).isEmpty();
    }

    @Test
    void mixedPatterns_onlyHiveExtracted() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(
                        swarmWithHive(), swarmWithSwarmTopology(), nonSwarmPattern())));

        assertThat(item.hiveTopologies()).hasSize(1);
    }

    @Test
    void swarmWithNullTopology_notExtracted() {
        var item = processor.extractHiveTopologies(
                new AgenticPatternsBuildItem(List.of(swarmWithNullTopology())));

        assertThat(item.hiveTopologies()).isEmpty();
    }
}
