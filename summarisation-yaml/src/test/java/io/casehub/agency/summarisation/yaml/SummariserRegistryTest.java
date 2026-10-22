package io.casehub.agency.summarisation.yaml;

import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.Summariser;
import io.casehub.agency.summarisation.yaml.runtime.SummarisationRecorder;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SummariserRegistryTest {

    static final EventLevel LEVEL = new EventLevel("test", 0);

    private static ExpressionEngine stubExpressionEngine() {
        return new ExpressionEngine() {
            @Override
            public String type() {return "stub";}

            @SuppressWarnings("unchecked")
            @Override
            public <C, R> CompiledExpression<C, R> compile(String expr, Class<C> ct, Class<R> rt) {
                return new CompiledExpression<>() {
                    @Override
                    public String type()     {return "stub";}

                    @Override
                    public R eval(C context) {return (R) context;}
                };
            }

            @Override
            public <C, R> CompiledExpression<C, R> compile(String expr, Class<C> ct, Class<R> rt, java.util.Map<String, Object> vars) {
                return compile(expr, ct, rt);
            }

            @Override
            public void validate(String expr) {}
        };
    }


    @Test
    void registry_createsRegisteredSummariser() {
        var registry = new SummariserRegistry();
        registry.register("custom", config -> Summariser.ofSync(batch ->
                batch.stream().map(e -> (Object) e.payload().toString()).toList()));

        Summariser<Object, Object> s = registry.create("custom", Map.of());
        assertThat(s).isNotNull();
    }

    @Test
    void registry_throwsOnUnknownType() {
        var registry = new SummariserRegistry();
        assertThatThrownBy(() -> registry.create("unknown", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown");
    }

    @Test
    void passThrough_registeredByDefault() {
        var registry = new SummariserRegistry();
        Summariser<Object, Object> s = registry.create("pass-through", Map.of());
        assertThat(s).isNotNull();
    }

    @SuppressWarnings("unchecked")
    @Test
    void passThrough_returnsPayloadsUnchanged() {
        var registry = new SummariserRegistry();
        Summariser<Map<String, Object>, Map<String, Object>> s =
                (Summariser<Map<String, Object>, Map<String, Object>>) (Summariser<?, ?>) registry.create("pass-through", Map.of());

        var batch = List.of(
                new LevelEvent<>(Map.of("a", (Object) 1), 100L, LEVEL, null),
                new LevelEvent<>(Map.of("b", (Object) 2), 200L, LEVEL, null));

        var result = s.summarise(batch).toCompletableFuture().join();
        assertThat(result).containsExactly(Map.of("a", 1), Map.of("b", 2));
    }

    @Test
    void registry_overridesExistingType() {
        var registry = new SummariserRegistry();
        registry.register("pass-through", config -> Summariser.ofSync(batch -> List.of("custom")));

        Summariser<Object, Object> s = registry.create("pass-through", Map.of());
        var batch = List.of(new LevelEvent<>((Object) "x", 100L, LEVEL, null));
        var result = s.summarise(batch).toCompletableFuture().join();
        assertThat(result).containsExactly("custom");
    }

    @Test
    void recorder_registers_fieldExtract() {
        var recorder   = new SummarisationRecorder();
        var registry   = recorder.createRegistry(stubExpressionEngine());
        var summariser = registry.create("field-extract", Map.of("expression", ".name"));
        assertThat(summariser).isNotNull();
    }

    @Test
    void recorder_registers_verbatim() {
        var recorder   = new SummarisationRecorder();
        var registry   = recorder.createRegistry(stubExpressionEngine());
        var summariser = registry.create("verbatim", Map.of("expression", "toString()"));
        assertThat(summariser).isNotNull();
    }
}
