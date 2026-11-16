package io.casehub.agency.orchestration.distributed;

import io.casehub.agency.orchestration.loop.DecisionOutcome;
import io.casehub.agency.orchestration.loop.ObservationEvent;
import io.casehub.agency.optimisation.prompt.SystemPromptCustomiser;
import io.casehub.platform.agent.AgentEvent;
import io.casehub.platform.agent.AgentProvider;
import io.casehub.platform.agent.AgentSession;
import io.casehub.platform.agent.AgentSessionInit;
import io.smallrye.mutiny.Multi;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedAgentReasonerTest {

    @Test
    void parsesStructuredJsonIntoWriteBack() {
        var agentProvider = returning("""
            {"signals": [{"name": "need-capacity:analysis", "strength": 0.8}],
             "taskClaim": "task-42",
             "synthesis": "Agents converge on root cause",
             "reasoning": "Signal strength indicates capacity gap"}
            """);

        var reasoner = new DistributedAgentReasoner(agentProvider, null);
        var events = List.of(ObservationEvent.of("peer-state-change",
                Map.of("agent", "a1", "state", "EXECUTING")));

        var result = reasoner.summarise(events, null).toCompletableFuture().join();

        assertThat(result).isInstanceOf(DecisionOutcome.WriteBack.class);
        var wb = (DecisionOutcome.WriteBack) result;
        assertThat(wb.signals()).hasSize(1);
        assertThat(wb.signals().get(0).name()).isEqualTo("need-capacity:analysis");
        assertThat(wb.signals().get(0).strength()).isEqualTo(0.8);
    }

    @Test
    void returnsNoOpOnAgentError() {
        var agentProvider = failing();
        var reasoner = new DistributedAgentReasoner(agentProvider, null);
        var events = List.of(ObservationEvent.of("tick", ""));

        var result = reasoner.summarise(events, null).toCompletableFuture().join();

        assertThat(result).isInstanceOf(DecisionOutcome.WriteBack.class);
        var wb = (DecisionOutcome.WriteBack) result;
        assertThat(wb.signals()).isEmpty();
        assertThat(wb.observations()).isEmpty();
    }

    @Test
    void parsesEmptySignalsAsNoOp() {
        var agentProvider = returning("""
            {"signals": [], "reasoning": "Nothing to do"}
            """);
        var reasoner = new DistributedAgentReasoner(agentProvider, null);

        var result = reasoner.summarise(List.of(ObservationEvent.of("tick", "")), null)
                .toCompletableFuture().join();

        var wb = (DecisionOutcome.WriteBack) result;
        assertThat(wb.signals()).isEmpty();
    }

    @Test
    void appliesPromptCustomiser() {
        var agentProvider = returning("""
            {"signals": [], "reasoning": "customised"}
            """);
        SystemPromptCustomiser customiser = (base, sig, slot) -> base + "\nCustom instruction.";
        var reasoner = new DistributedAgentReasoner(agentProvider, customiser);

        var result = reasoner.summarise(List.of(ObservationEvent.of("tick", "")), null)
                .toCompletableFuture().join();

        assertThat(result).isInstanceOf(DecisionOutcome.WriteBack.class);
    }

    @Test
    void parsesSynthesisAsObservation() {
        var agentProvider = returning("""
            {"signals": [], "synthesis": "Root cause identified as memory leak",
             "reasoning": "Multiple peers report OOM"}
            """);
        var reasoner = new DistributedAgentReasoner(agentProvider, null);

        var result = reasoner.summarise(List.of(ObservationEvent.of("tick", "")), null)
                .toCompletableFuture().join();

        var wb = (DecisionOutcome.WriteBack) result;
        assertThat(wb.observations()).hasSize(1);
        assertThat(wb.observations().get(0).patternId()).isEqualTo("synthesis");
    }

    @Test
    void handlesParseError() {
        var agentProvider = returning("this is not json at all");
        var reasoner = new DistributedAgentReasoner(agentProvider, null);

        var result = reasoner.summarise(List.of(ObservationEvent.of("tick", "")), null)
                .toCompletableFuture().join();

        var wb = (DecisionOutcome.WriteBack) result;
        assertThat(wb.signals()).isEmpty();
    }

    // --- test helpers ---

    private static AgentProvider returning(String response) {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().items(
                        new AgentEvent.TextDelta(response),
                        new AgentEvent.InvocationComplete(10, 5, 0, 0, 0, null, 100L, 80L, null, 0, false, null, null));
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }

    private static AgentProvider failing() {
        return new AgentProvider() {
            @Override
            public Multi<AgentEvent> invoke(io.casehub.platform.agent.AgentSessionConfig config) {
                return Multi.createFrom().failure(new RuntimeException("LLM unavailable"));
            }
            @Override
            public AgentSession openSession(AgentSessionInit init) {
                throw new UnsupportedOperationException("test stub");
            }
        };
    }
}
