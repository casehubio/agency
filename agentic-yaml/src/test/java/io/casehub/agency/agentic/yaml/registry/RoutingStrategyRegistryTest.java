package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.AgentRef;
import io.casehub.agency.core.AgentResult;
import io.casehub.agency.core.RoutingCandidate;
import io.casehub.agency.orchestration.routing.RoutingContext;
import io.casehub.agency.orchestration.routing.RoutingDecision;
import io.casehub.agency.core.yaml.spec.RoutingSpec;
import io.casehub.platform.expression.MvelExpressionEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingStrategyRegistryTest {

    private final RoutingStrategyRegistry registry = new RoutingStrategyRegistry();
    private final MvelExpressionEngine engine = new MvelExpressionEngine();

    @Test
    void firstMatchWithGuardFiltersOnExpression() {
        var spec = new RoutingSpec.FirstMatch("ref.name() == \"specialist\"");
        var strategy = registry.<Object>resolve(spec, engine);

        var specialist = candidate("specialist");
        var generalist = candidate("generalist");
        var ctx = new RoutingContext<>("task", List.of(generalist, specialist), null);

        var decision = strategy.route(ctx);
        assertThat(decision).isInstanceOf(RoutingDecision.Selected.class);
        var selected = (RoutingDecision.Selected) decision;
        assertThat(selected.agents().get(0).name()).isEqualTo("specialist");
    }

    @Test
    void firstMatchWithoutGuardMatchesAll() {
        var spec = new RoutingSpec.FirstMatch(null);
        var strategy = registry.<Object>resolve(spec, null);

        var agent = candidate("any-agent");
        var ctx = new RoutingContext<>("task", List.of(agent), null);

        var decision = strategy.route(ctx);
        assertThat(decision).isInstanceOf(RoutingDecision.Selected.class);
    }

    private static RoutingCandidate candidate(String name) {
        var ref = AgentRef.external(name,
                (Object c) -> CompletableFuture.completedFuture(
                        AgentResult.success(null, "placeholder")));
        return new RoutingCandidate(ref, null);
    }
}
