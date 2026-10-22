package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.orchestration.routing.FirstMatchRouting;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import io.casehub.agency.orchestration.routing.RoundRobinRouting;
import io.casehub.agency.orchestration.routing.RoutingStrategy;
import io.casehub.agency.orchestration.routing.SelectAllRouting;
import io.casehub.agency.orchestration.routing.SequentialRouting;
import io.casehub.agency.core.yaml.spec.RoutingSpec;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

public class RoutingStrategyRegistry {

    private @Nullable Function<RoutingSpec, @Nullable RoutingStrategy<?>> fallback;

    public void registerFallback(Function<RoutingSpec, @Nullable RoutingStrategy<?>> fallback) {
        this.fallback = fallback;
    }

    @SuppressWarnings("unchecked")
    public <T> RoutingStrategy<T> resolve(RoutingSpec spec, @Nullable ExpressionEngine engine) {
        if (fallback != null) {
            var result = fallback.apply(spec);
            if (result != null) return (RoutingStrategy<T>) result;
        }
        return switch (spec) {
            case RoutingSpec.FirstMatch fm -> {
                if (fm.guard() != null && engine != null) {
                    var compiled = engine.compile(fm.guard(),
                            (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class);
                    yield new FirstMatchRouting<>(c -> {
                        var map = new HashMap<String, Object>();
                        map.put("ref", c.ref());
                        map.put("descriptor", c.descriptor());
                        return Boolean.TRUE.equals(compiled.eval(map));
                    });
                }
                yield new FirstMatchRouting<>(c -> true);
            }
            case RoutingSpec.RoundRobin rr -> new RoundRobinRouting<>();
            case RoutingSpec.Sequential seq -> new SequentialRouting<>();
            case RoutingSpec.LlmSelected llm ->
                    throw new UnsupportedOperationException(
                            "llm-selected routing requires AgentProvider — use CDI injection");
            case RoutingSpec.SelectAll sa -> new SelectAllRouting<>();
        };
    }
}
