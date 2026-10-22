package io.casehub.agency.summarisation.yaml;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineValidatorTest {

    private final SummariserRegistry registry = new SummariserRegistry();

    private PipelineDefinition pipeline(LevelDefinition... levels) {
        return new PipelineDefinition("test",
                new SourceDefinition(null, "io.test.v1", null),
                List.of(levels), null);
    }

    private LevelDefinition level(String name, SummariserDefinition summariser) {
        return new LevelDefinition(name,
                new GroupingDefinition.Windowed(null, 10),
                summariser, null, List.of());
    }

    private SummariserDefinition summariser(String type) {
        return new SummariserDefinition(type, null, Map.of());
    }

    private SummariserDefinition summariser(String type, Map<String, Object> config) {
        return new SummariserDefinition(type, null, config);
    }

    @Test
    void valid_passThroughPipeline() {
        var def = pipeline(level("l1", summariser("pass-through")));
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors).isEmpty();
    }

    @Test
    void rejects_unknownSummariserType() {
        var def = pipeline(level("l1", summariser("nonexistent")));
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors).anyMatch(e -> e.message().contains("nonexistent"));
    }

    @Test
    void rejects_missingInitialState() {
        var config = Map.<String, Object>of(
                "states", List.of("A", "B"),
                "transitions", List.of());
        registry.register("phase-detect", c -> null);
        var def = pipeline(level("l1", summariser("phase-detect", config)));
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors).anyMatch(e -> e.message().contains("initial"));
    }

    @Test
    void rejects_unreachablePhaseDetectState() {
        var config = Map.<String, Object>of(
                "initial", "A",
                "states", List.of("A", "B", "C"),
                "transitions", List.of(
                        Map.of("from", "A", "to", "B", "when", "true")));
        registry.register("phase-detect", c -> null);
        var def = pipeline(level("l1", summariser("phase-detect", config)));
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors).anyMatch(e ->
                e.message().contains("unreachable") && e.message().contains("C"));
    }

    @Test
    void rejects_missingRulesForThresholdClassify() {
        registry.register("threshold-classify", c -> null);
        var def = pipeline(level("l1", summariser("threshold-classify")));
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors).anyMatch(e -> e.message().contains("rules"));
    }

    @Test
    void rejects_missingGrouping() {
        var def = new PipelineDefinition("test",
                new SourceDefinition(null, null, null),
                List.of(new LevelDefinition("l1", null,
                        summariser("pass-through"), null, List.of())),
                null);
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors).anyMatch(e -> e.message().contains("grouping"));
    }

    @Test
    void rejects_emptyLevels() {
        var def = new PipelineDefinition("test",
                new SourceDefinition(null, null, null), List.of(), null);
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors).anyMatch(e -> e.message().contains("level"));
    }

    @Test
    void rejects_missingCategoryFieldForCount() {
        registry.register("count", c -> null);
        var def = pipeline(level("l1", summariser("count")));
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors).anyMatch(e -> e.message().contains("category-field"));
    }

    @Test
    void accepts_validPhaseDetect() {
        var config = Map.<String, Object>of(
                "initial", "A",
                "states", List.of("A", "B"),
                "transitions", List.of(
                        Map.of("from", "A", "to", "B", "when", "true")));
        registry.register("phase-detect", c -> null);
        var def = pipeline(level("l1", summariser("phase-detect", config)));
        var errors = new PipelineValidator().validate(def, registry);
        assertThat(errors.stream().filter(e -> e.level() == PipelineValidator.ValidationError.Level.ERROR))
                .isEmpty();
    }
}
