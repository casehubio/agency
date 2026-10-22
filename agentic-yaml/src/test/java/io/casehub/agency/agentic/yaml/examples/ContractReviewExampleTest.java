package io.casehub.agency.agentic.yaml.examples;

import io.casehub.agency.agentic.aggregation.MajorityVote;
import io.casehub.agency.agentic.judgment.JudgmentPolicy;
import io.casehub.agency.agentic.model.PatternType;
import io.casehub.agency.agentic.routing.SelectAllRouting;
import io.casehub.agency.agentic.yaml.compiler.CompiledWorld;
import io.casehub.agency.summarisation.observation.affordance.AnnotatedSection;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ContractReviewExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "contract-review";

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.VOTING);
        assertThat(model.routing()).isInstanceOf(SelectAllRouting.class);
        assertThat(model.aggregation()).isInstanceOf(MajorityVote.class);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(3);
        assertThat(candidates.get(0).descriptor().name()).isEqualTo("legal-analyst");
        assertThat(candidates.get(1).descriptor().name()).isEqualTo("financial-analyst");
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("risk-analyst");

        assertThat(model.judgment()).isNotNull();
        assertThat(model.judgment()).isInstanceOf(JudgmentPolicy.class);
    }

    @Test
    void worldCompiles() throws IOException {
        var def = loadWorld(SCENARIO);
        var compiled = worldCompiler.compile(def);

        assertThat(compiled.actions()).hasSize(5);
        assertThat(compiled.actions().get(0).actionType()).isEqualTo("EXAMINE");
        assertThat(compiled.actions().get(4).actionType()).isEqualTo("REJECT");

        assertThat(compiled.entities()).hasSize(4);
        assertThat(compiled.entities()).containsKey("confidentiality-clause");
        assertThat(compiled.entities()).containsKey("liability-cap");

        assertThat(compiled.sections()).hasSize(4);
        assertThat(compiled.sections().get(1)).isInstanceOf(AnnotatedSection.class);
        var confidential = (AnnotatedSection) compiled.sections().get(1);
        assertThat(confidential.requiredTags()).containsExactly("senior-review");

        assertThat(compiled.sections().get(2)).isInstanceOf(AnnotatedSection.class);
        var financial = (AnnotatedSection) compiled.sections().get(2);
        assertThat(financial.requiredTags()).containsExactly("financial-clearance");

        assertThat(compiled.pipeline()).isNotNull();

        CompiledWorld.RendererThresholds thresholds = compiled.rendererThresholds();
        assertThat(thresholds).isNotNull();
        assertThat(thresholds.verbatimThreshold()).isEqualTo(6);
        assertThat(thresholds.groupedThreshold()).isEqualTo(15);
    }

    @SuppressWarnings("unchecked")
    @Test
    void caseYamlParses() throws IOException {
        var caseMap = load(SCENARIO, "case.yaml", Map.class);

        assertThat(caseMap.get("dsl")).isEqualTo("1.0.0");
        assertThat(caseMap.get("name")).isEqualTo("contract-review");
        assertThat(caseMap).containsKey("capabilities");
    }
}
