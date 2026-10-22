package io.casehub.agency.agentic.yaml.examples;

import io.casehub.agency.agentic.model.PatternType;
import io.casehub.agency.agentic.routing.FirstMatchRouting;
import io.casehub.agency.agentic.yaml.compiler.CompiledWorld;
import io.casehub.agency.summarisation.observation.affordance.AnnotatedSection;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class HubMonitoringExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "hub-monitoring";

    @Test
    void pipelineCompiles() throws IOException {
        var def = loadPipeline(SCENARIO);
        var pipeline = pipelineCompiler.<Object>compile(def, summariserRegistry, null, pipelineExpressionEngine);

        assertThat(pipeline.name()).isEqualTo("hub-monitoring");
        assertThat(def.levels()).hasSize(4);
        assertThat(def.levels().get(0).name()).isEqualTo("per-sensor");
        assertThat(def.levels().get(1).name()).isEqualTo("per-zone");
        assertThat(def.levels().get(2).name()).isEqualTo("zone-health");
        assertThat(def.levels().get(3).name()).isEqualTo("alert-tally");
    }

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.CONDITIONAL);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("safety-officer");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("maintenance-coordinator");
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("logistics-planner");
        assertThat(candidates.get(0).descriptor().capabilities()).hasSize(2);
    }

    @Test
    void worldCompiles() throws IOException {
        var def = loadWorld(SCENARIO);
        var compiled = worldCompiler.compile(def);

        assertThat(compiled.actions()).hasSize(3);
        assertThat(compiled.actions().get(0).actionType()).isEqualTo("INSPECT");
        assertThat(compiled.actions().get(1).actionType()).isEqualTo("ISOLATE");
        assertThat(compiled.actions().get(2).actionType()).isEqualTo("RESET");

        assertThat(compiled.entities()).hasSize(4);
        assertThat(compiled.entities()).containsKey("zone-a");
        assertThat(compiled.entities()).containsKey("conveyor-c1");

        var zoneA = compiled.entities().get("zone-a");
        assertThat(zoneA.displayName()).isEqualTo("Loading Bay A");
        assertThat(zoneA.affordances()).hasSize(2);

        assertThat(compiled.sections()).hasSize(4);
        assertThat(compiled.sections().get(1)).isInstanceOf(AnnotatedSection.class);
        var annotated = (AnnotatedSection) compiled.sections().get(1);
        assertThat(annotated.requiredTags()).containsExactly("maintenance");

        CompiledWorld.RendererThresholds thresholds = compiled.rendererThresholds();
        assertThat(thresholds).isNotNull();
        assertThat(thresholds.verbatimThreshold()).isEqualTo(8);
        assertThat(thresholds.groupedThreshold()).isEqualTo(20);
    }
}
