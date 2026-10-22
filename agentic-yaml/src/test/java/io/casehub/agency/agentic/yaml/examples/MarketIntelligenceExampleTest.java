package io.casehub.agency.agentic.yaml.examples;

import io.casehub.agency.agentic.AgentRef;
import io.casehub.agency.agentic.FailurePolicy;
import io.casehub.agency.agentic.aggregation.CollectAll;
import io.casehub.agency.agentic.model.PatternType;
import io.casehub.agency.agentic.routing.FirstMatchRouting;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MarketIntelligenceExampleTest extends ExampleTestBase {

    private static final String SCENARIO = "market-intelligence";

    @Test
    void patternCompiles() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);

        assertThat(model.patternType()).isEqualTo(PatternType.SUPERVISOR);
        assertThat(model.routing()).isInstanceOf(FirstMatchRouting.class);
        assertThat(model.aggregation()).isInstanceOf(CollectAll.class);
        assertThat(model.failurePolicy().onDeadlock())
                .isEqualTo(FailurePolicy.AggregationFailureAction.ESCALATE);

        var candidates = model.candidateSupplier().get();
        assertThat(candidates).hasSize(3);

        assertThat(candidates.get(0).ref()).isInstanceOf(AgentRef.ComposedAgent.class);
        assertThat(candidates.get(1).ref()).isInstanceOf(AgentRef.ComposedAgent.class);
        assertThat(candidates.get(2).descriptor().name()).isEqualTo("chief-analyst");
    }

    @Test
    void composedTeamsAreParallel() throws IOException {
        var spec = loadPattern(SCENARIO);
        var model = patternCompiler.compile(spec);
        var candidates = model.candidateSupplier().get();

        var primaryResearch = (AgentRef.ComposedAgent) candidates.get(0).ref();
        assertThat(primaryResearch.model().patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(primaryResearch.model().candidateSupplier().get()).hasSize(3);
        assertThat(primaryResearch.model().candidateSupplier().get().get(0).descriptor().name())
                .isEqualTo("web-analyst");

        var competitiveIntel = (AgentRef.ComposedAgent) candidates.get(1).ref();
        assertThat(competitiveIntel.model().patternType()).isEqualTo(PatternType.PARALLEL);
        assertThat(competitiveIntel.model().candidateSupplier().get()).hasSize(2);
        assertThat(competitiveIntel.model().candidateSupplier().get().get(0).descriptor().name())
                .isEqualTo("patent-analyst");
    }

    @SuppressWarnings("unchecked")
    @Test
    void caseYamlParses() throws IOException {
        var caseMap = load(SCENARIO, "case.yaml", Map.class);

        assertThat(caseMap.get("dsl")).isEqualTo("1.0.0");
        assertThat(caseMap.get("name")).isEqualTo("market-intelligence");
        assertThat(caseMap).containsKey("capabilities");
    }
}
