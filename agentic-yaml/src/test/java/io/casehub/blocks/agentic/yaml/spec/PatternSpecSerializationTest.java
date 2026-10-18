package io.casehub.blocks.agentic.yaml.spec;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.yaml.jackson.YamlMappers;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PatternSpecSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = YamlMappers.create();
        mapper.registerModule(new JavaTimeModule());
    }

    @ParameterizedTest
    @ValueSource(strings = {"supervisor", "debate", "loop", "parallel",
            "voting", "conditional", "sequence", "htn"})
    void deserializesAllTopologies(String topology) throws IOException {
        var yaml = readFixture(topology);
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec).isNotNull();
        assertThat(spec.agents()).isNotEmpty();
        assertThat(spec.name()).isNotNull();
        assertThat(spec.description()).isNotNull();
        assertThat(spec.task()).isNotNull();
    }

    @Test
    void supervisorFields() throws IOException {
        var spec = loadPattern("supervisor", PatternSpec.Supervisor.class);
        assertThat(spec.name()).isEqualTo("supervised-analysis");
        assertThat(spec.description()).contains("Supervisor coordinates");
        assertThat(spec.task()).isEqualTo("analysis");
        assertThat(spec.routing()).isInstanceOf(RoutingSpec.FirstMatch.class);
        assertThat(((RoutingSpec.FirstMatch) spec.routing()).guard()).isEqualTo("priority > 5");
        assertThat(spec.termination()).hasSize(1);
        assertThat(spec.termination().get(0)).isInstanceOf(TerminationSpec.MaxIterations.class);
        assertThat(spec.aggregation()).isInstanceOf(AggregationSpec.CollectAll.class);
        assertThat(spec.agents()).hasSize(2);
        assertThat(spec.governance()).isNotNull();
        assertThat(spec.governance().oversight()).isNotNull();
        assertThat(spec.governance().oversight().classifier()).isEqualTo("risk-based");
        assertThat(spec.governance().trustRouting()).isNotNull();
        assertThat(spec.governance().trustRouting().threshold()).isEqualTo(0.7);
        assertThat(spec.governance().attestation()).isNotNull();
        assertThat(spec.governance().attestation().observer()).isEqualTo("capability-attestation");
    }

    @Test
    void debateFields() throws IOException {
        var spec = loadPattern("debate", PatternSpec.Debate.class);
        assertThat(spec.name()).isEqualTo("adversarial-review");
        assertThat(spec.task()).isEqualTo("deliberation");
        assertThat(spec.maxRounds()).isEqualTo(7);
        assertThat(spec.judge()).isNotNull();
        assertThat(spec.judge().name()).isEqualTo("arbiter");
        assertThat(spec.judge().description()).isEqualTo("Evaluates arguments from both sides and renders a final verdict");
        assertThat(spec.judge().capabilities()).containsExactly("argument-evaluation", "verdict-rendering");
        assertThat(spec.termination()).hasSize(2);
        assertThat(spec.agents().get(0).description()).isEqualTo("Argues in favour of the proposed position with supporting evidence");
    }

    @Test
    void loopFields() throws IOException {
        var spec = loadPattern("loop", PatternSpec.Loop.class);
        assertThat(spec.name()).isEqualTo("iterative-refinement");
        assertThat(spec.task()).isEqualTo("refinement");
        assertThat(spec.maxIterations()).isEqualTo(15);
        assertThat(spec.exitCondition()).isEqualTo("qualityScore > 0.9");
        assertThat(spec.termination()).hasSize(2);
        assertThat(spec.termination().get(0)).isInstanceOf(TerminationSpec.GoalReached.class);
        assertThat(spec.termination().get(1)).isInstanceOf(TerminationSpec.MaxIterations.class);
        assertThat(spec.judgment()).isNotNull();
        assertThat(spec.judgment().trigger()).isInstanceOf(JudgmentSpec.TriggerSpec.IterationBased.class);
        assertThat(spec.agents().get(0).description()).isEqualTo("Iteratively refines output quality through self-critique and revision");
        assertThat(spec.agents().get(0).capabilities()).containsExactly("content-generation", "self-evaluation", "revision");
    }

    @Test
    void conditionalBranches() throws IOException {
        var spec = loadPattern("conditional", PatternSpec.Conditional.class);
        assertThat(spec.name()).isEqualTo("domain-case-router");
        assertThat(spec.task()).isEqualTo("routing");
        assertThat(spec.branches()).hasSize(3);
        assertThat(spec.branches().get(0).condition()).isEqualTo("category == 'medical'");
        assertThat(spec.branches().get(0).agent().name()).isEqualTo("medical-expert");
        assertThat(spec.branches().get(0).description()).isEqualTo("Routes clinical cases requiring specialist medical review");
        assertThat(spec.branches().get(1).description()).isEqualTo("Routes matters requiring legal analysis and regulatory review");
        assertThat(spec.branches().get(2).condition()).isEqualTo("category == 'financial'");
        assertThat(spec.branches().get(2).agent().name()).isEqualTo("financial-expert");
        assertThat(spec.branches().get(2).description()).isEqualTo("Routes cases requiring financial risk and compliance evaluation");
        assertThat(spec.termination()).hasSize(1);
        assertThat(spec.failurePolicy()).isNotNull();
        assertThat(spec.agents()).hasSize(3);
        assertThat(spec.agents().get(0).description()).isEqualTo("Analyses medical cases with clinical guideline awareness");
        assertThat(spec.agents().get(0).capabilities()).containsExactly("clinical-analysis", "guideline-compliance");
    }

    @Test
    void votingFields() throws IOException {
        var spec = loadPattern("voting", PatternSpec.Voting.class);
        assertThat(spec.name()).isEqualTo("proposal-evaluation");
        assertThat(spec.task()).isEqualTo("evaluation");
        assertThat(spec.aggregation()).isInstanceOf(AggregationSpec.MajorityVote.class);
        assertThat(spec.termination()).hasSize(1);
        assertThat(spec.termination().get(0)).isInstanceOf(TerminationSpec.SinglePass.class);
        assertThat(spec.judgment()).isNotNull();
        assertThat(spec.judgment().trigger()).isInstanceOf(JudgmentSpec.TriggerSpec.ConfidenceThreshold.class);
        assertThat(spec.judgment().caller()).isInstanceOf(JudgmentSpec.CallerSpec.FanOut.class);
        assertThat(spec.failurePolicy()).isNotNull();
        assertThat(spec.agents()).hasSize(3);
        assertThat(spec.agents().get(0).description()).isEqualTo("Assesses technical feasibility and implementation risk");
        assertThat(spec.agents().get(0).capabilities()).containsExactly("technical-assessment", "risk-analysis");
        assertThat(spec.agents().get(2)).isInstanceOf(AgentRefSpec.Human.class);
        assertThat(spec.agents().get(2).name()).isEqualTo("domain-expert");
    }

    @Test
    void sequenceFields() throws IOException {
        var spec = loadPattern("sequence", PatternSpec.Sequence.class);
        assertThat(spec.name()).isEqualTo("data-processing-pipeline");
        assertThat(spec.task()).isEqualTo("data-pipeline");
        assertThat(spec.termination()).hasSize(1);
        assertThat(spec.termination().get(0)).isInstanceOf(TerminationSpec.AgentCount.class);
        assertThat(spec.failurePolicy()).isNotNull();
        assertThat(spec.failurePolicy().agentRetry().maxRetries()).isEqualTo(2);
        assertThat(spec.agents()).hasSize(3);
        assertThat(spec.agents().get(0).name()).isEqualTo("data-collector");
        assertThat(spec.agents().get(0).description()).isEqualTo("Gathers and validates input data from configured sources");
        assertThat(spec.agents().get(1).capabilities()).containsExactly("data-transformation", "enrichment");
    }

    @Test
    void parallelFields() throws IOException {
        var spec = loadPattern("parallel", PatternSpec.Parallel.class);
        assertThat(spec.name()).isEqualTo("multi-source-research");
        assertThat(spec.task()).isEqualTo("research");
        assertThat(spec.aggregation()).isInstanceOf(AggregationSpec.CollectAll.class);
        assertThat(spec.termination()).hasSize(1);
        assertThat(spec.termination().get(0)).isInstanceOf(TerminationSpec.AgentCount.class);
        assertThat(spec.failurePolicy()).isNotNull();
        assertThat(spec.agents()).hasSize(3);
        assertThat(spec.agents().get(0).name()).isEqualTo("web-researcher");
        assertThat(spec.agents().get(0).description()).isEqualTo("Searches public web sources for relevant information");
        assertThat(spec.agents().get(1).name()).isEqualTo("academic-researcher");
        assertThat(spec.agents().get(2)).isInstanceOf(AgentRefSpec.External.class);
        assertThat(spec.agents().get(2).name()).isEqualTo("data-api");
        assertThat(spec.agents().get(2).capabilities()).containsExactly("api-integration", "structured-retrieval");
    }

    @Test
    void htnRootTask() throws IOException {
        var spec = loadPattern("htn", PatternSpec.Htn.class);
        assertThat(spec.name()).isEqualTo("data-processing-hierarchy");
        assertThat(spec.task()).isEqualTo("hierarchical-processing");
        assertThat(spec.rootTask()).isInstanceOf(TaskNodeSpec.Compound.class);
        var root = (TaskNodeSpec.Compound) spec.rootTask();
        assertThat(root.name()).isEqualTo("main-task");
        assertThat(root.subtasks()).hasSize(2);
        var gatherData = (TaskNodeSpec.Primitive) root.subtasks().get(0);
        assertThat(gatherData.description()).isEqualTo("Collects raw data from all configured upstream sources");
        var process = (TaskNodeSpec.Compound) root.subtasks().get(1);
        var clean = (TaskNodeSpec.Primitive) process.subtasks().get(0);
        assertThat(clean.description()).isEqualTo("Normalises, deduplicates, and validates collected data");
        var analyse = (TaskNodeSpec.Primitive) process.subtasks().get(1);
        assertThat(analyse.description()).isEqualTo("Performs statistical analysis and generates insight reports");
    }

    @Test
    void composedAgentWithNestedPattern() throws IOException {
        var yaml = """
                type: supervisor
                agents:
                  - type: composed
                    name: research-team
                    pattern:
                      type: parallel
                      agents:
                        - type: worker
                          name: web-researcher
                        - type: worker
                          name: db-researcher
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        var composed = (AgentRefSpec.Composed) spec.agents().get(0);
        assertThat(composed.pattern()).isInstanceOf(PatternSpec.Parallel.class);
        assertThat(composed.pattern().agents()).hasSize(2);
    }

    @Test
    void judgmentSpec() throws IOException {
        var yaml = """
                type: supervisor
                judgment:
                  trigger:
                    type: iteration-based
                    every: 3
                  caller:
                    type: single
                    callerName: quality-judge
                agents:
                  - type: worker
                    name: worker-1
                """;
        var spec = (PatternSpec.Supervisor) mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.judgment()).isNotNull();
        assertThat(spec.judgment().trigger())
                .isInstanceOf(JudgmentSpec.TriggerSpec.IterationBased.class);
        assertThat(((JudgmentSpec.TriggerSpec.IterationBased) spec.judgment().trigger())
                .every()).isEqualTo(3);
    }

    @Test
    void failurePolicyDeserializes() throws IOException {
        var yaml = """
                type: supervisor
                failurePolicy:
                  onRoutingFailure: RETRY_BROADER
                  onDeadlock: ESCALATE
                agents:
                  - type: worker
                    name: agent-1
                """;
        var spec = (PatternSpec.Supervisor) mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.failurePolicy()).isNotNull();
        assertThat(spec.failurePolicy().onRoutingFailure().name()).isEqualTo("RETRY_BROADER");
    }

    @Test
    void nameAndDescriptionNullWhenOmitted() throws IOException {
        var yaml = """
                type: supervisor
                agents:
                  - type: worker
                    name: agent-1
                """;
        var spec = mapper.readValue(yaml, PatternSpec.class);
        assertThat(spec.name()).isNull();
        assertThat(spec.description()).isNull();
    }

    private <T extends PatternSpec> T loadPattern(String name, Class<T> type) throws IOException {
        var spec = mapper.readValue(readFixture(name), PatternSpec.class);
        assertThat(spec).isInstanceOf(type);
        return type.cast(spec);
    }

    private String readFixture(String name) throws IOException {
        try (var is = getClass().getResourceAsStream("/patterns/" + name + ".yaml")) {
            if (is == null) throw new IOException("Missing fixture: " + name + ".yaml");
            return new String(is.readAllBytes());
        }
    }
}
