package io.casehub.agency.orchestration.routing;

import io.casehub.agency.core.AgentRef;
import io.casehub.worker.api.Worker;
import io.casehub.worker.api.WorkerResult;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingDecisionTest {

    @Nested
    class Selected {
        @Test
        void carriesAgentList() {
            var worker = Worker.builder()
                    .name("test")
                    .capabilityNames("test")
                    .function(x -> WorkerResult.of(Map.of()))
                    .build();
            var agent = new AgentRef.WorkerAgent(worker);
            var decision = new RoutingDecision.Selected(List.of(agent));
            assertThat(decision.agents()).containsExactly(agent);
        }

        @Test
        void selectedCarriesReason() {
            var agent = AgentRef.external(x ->
                    java.util.concurrent.CompletableFuture.completedFuture(
                            io.casehub.agency.core.AgentResult.success(null, "ok")));
            var selected = new RoutingDecision.Selected(List.of(agent), "best domain match");
            assertThat(selected.reason()).isEqualTo("best domain match");
            assertThat(selected.agents()).hasSize(1);
        }

        @Test
        void selectedConvenienceConstructorHasNullReason() {
            var agent = AgentRef.external(x ->
                    java.util.concurrent.CompletableFuture.completedFuture(
                            io.casehub.agency.core.AgentResult.success(null, "ok")));
            var selected = new RoutingDecision.Selected(List.of(agent));
            assertThat(selected.reason()).isNull();
        }
    }

    @Nested
    class Unresolvable {
        @Test
        void carriesReason() {
            var decision = new RoutingDecision.Unresolvable("no match");
            assertThat(decision.reason()).isEqualTo("no match");
        }
    }

    @Nested
    class Escalate {
        @Test
        void carriesReason() {
            var decision = new RoutingDecision.Escalate("needs human");
            assertThat(decision.reason()).isEqualTo("needs human");
        }
    }

    @Nested
    class SwarmAssignment {
        @Test
        void defensiveCopy() {
            var agent = AgentRef.external("a1", x ->
                    java.util.concurrent.CompletableFuture.completedFuture(
                            io.casehub.agency.core.AgentResult.success(null, "ok")));
            var agents = new java.util.ArrayList<AgentRef>(List.of(agent));
            var teams = new java.util.HashMap<String, List<AgentRef>>(Map.of("team1", List.of(agent)));
            var assignment = new RoutingDecision.SwarmAssignment(agents, teams, "divide", "test reason");
            agents.clear();
            teams.clear();
            assertThat(assignment.agents()).hasSize(1);
            assertThat(assignment.teams()).hasSize(1);
            assertThat(assignment.strategy()).isEqualTo("divide");
            assertThat(assignment.reason()).isEqualTo("test reason");
        }

        @Test
        void nullableStrategyAndReason() {
            var agent = AgentRef.external("a1", x ->
                    java.util.concurrent.CompletableFuture.completedFuture(
                            io.casehub.agency.core.AgentResult.success(null, "ok")));
            var assignment = new RoutingDecision.SwarmAssignment(List.of(agent), Map.of(), null, null);
            assertThat(assignment.strategy()).isNull();
            assertThat(assignment.reason()).isNull();
        }
    }


    @Test
    void sealedInterfacePermitsThreeVariants() {
        assertThat(RoutingDecision.class.getPermittedSubclasses()).hasSize(4);
    }
}
