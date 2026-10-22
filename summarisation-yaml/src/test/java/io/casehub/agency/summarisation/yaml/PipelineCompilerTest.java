package io.casehub.agency.summarisation.yaml;

import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.cloudevents.CloudEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineCompilerTest {

    static final EventLevel INPUT_LEVEL = new EventLevel("input", 0);

    private static ExpressionEngine stubExpressionEngine() {
        return new ExpressionEngine() {
            @Override
            public String type() {return "stub";}

            @SuppressWarnings("unchecked")
            @Override
            public <C, R> CompiledExpression<C, R> compile(String expr, Class<C> ct, Class<R> rt) {
                return new CompiledExpression<>() {
                    @Override
                    public String type() {return "stub";}

                    @Override
                    public R eval(C context) {
                        if (context instanceof Map<?, ?> m) {
                            return (R) m.get(expr);
                        }
                        if (context instanceof List<?> l) {
                            return (R) Boolean.valueOf(l.size() >= 3);
                        }
                        return (R) context;
                    }
                };
            }

            @Override
            public <C, R> CompiledExpression<C, R> compile(String expr, Class<C> ct, Class<R> rt, Map<String, Object> vars) {
                return compile(expr, ct, rt);
            }

            @Override
            public void validate(String expr) {}
        };
    }


    @Test
    void compiles_singleLevelPassThrough() {
        var def = new PipelineDefinition("test",
                new SourceDefinition(null, null, null),
                List.of(new LevelDefinition("out",
                        new GroupingDefinition.Windowed(null, 2),
                        new SummariserDefinition("pass-through", null, Map.of()),
                        null, List.of())),
                null);

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("out").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("b", (Object) 2), 200L, INPUT_LEVEL, null));
        pipeline.tick(300L).toCompletableFuture().join();

        assertThat(pipeline.name()).isEqualTo("test");
        assertThat(output).hasSize(2);
    }

    @Test
    void compiles_multiLevelPipeline() {
        var def = new PipelineDefinition("multi",
                new SourceDefinition(null, null, null),
                List.of(
                        new LevelDefinition("l1",
                                new GroupingDefinition.Windowed(null, 2),
                                new SummariserDefinition("pass-through", null, Map.of()),
                                null, List.of()),
                        new LevelDefinition("l2",
                                new GroupingDefinition.Windowed(null, 2),
                                new SummariserDefinition("pass-through", null, Map.of()),
                                null, List.of())),
                null);

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("l2").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("b", (Object) 2), 200L, INPUT_LEVEL, null));
        pipeline.tick(300L).toCompletableFuture().join();

        // L1 produces 2 events, but L2 needs 2 to trigger — tick again
        pipeline.tick(400L).toCompletableFuture().join();

        assertThat(output).hasSize(2);
    }

    @Test
    void compiles_withCloudEventEmission() {
        var emitted = new ArrayList<CloudEvent>();

        var def = new PipelineDefinition("emitting",
                new SourceDefinition(null, null, null),
                List.of(new LevelDefinition("l1",
                        new GroupingDefinition.Windowed(null, 2),
                        new SummariserDefinition("pass-through", null, Map.of()),
                        new EmitDefinition("io.test.output.v1"),
                        List.of())),
                null);

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry,
                emitted::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("x", (Object) "y"), 100L, INPUT_LEVEL, "t1"));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("z", (Object) "w"), 200L, INPUT_LEVEL, "t1"));
        pipeline.tick(300L).toCompletableFuture().join();

        assertThat(emitted).hasSize(2);
        assertThat(emitted.get(0).getType()).isEqualTo("io.test.output.v1");
        assertThat(emitted.get(0).getExtension("tenancyid")).isEqualTo("t1");
    }

    @Test
    void flush_drainsRemainingEvents() {
        var def = new PipelineDefinition("flush-test",
                new SourceDefinition(null, null, null),
                List.of(new LevelDefinition("out",
                        new GroupingDefinition.Windowed(null, 100),
                        new SummariserDefinition("pass-through", null, Map.of()),
                        null, List.of())),
                null);

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("out").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.tick(200L).toCompletableFuture().join();
        assertThat(output).isEmpty();

        pipeline.flush().toCompletableFuture().join();
        assertThat(output).hasSize(1);
    }

    @Test
    void compiles_windowedWithAge() {
        var def = new PipelineDefinition("aged",
                new SourceDefinition(null, null, null),
                List.of(new LevelDefinition("out",
                        new GroupingDefinition.Windowed(100L, null),
                        new SummariserDefinition("pass-through", null, Map.of()),
                        null, List.of())),
                null);

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null);

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("out").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("a", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.tick(100L).toCompletableFuture().join();
        assertThat(output).isEmpty();

        pipeline.tick(250L).toCompletableFuture().join();
        assertThat(output).hasSize(1);
    }

    @Test
    void compiles_keyedGrouping() {
        var def = new PipelineDefinition("keyed-test",
                                         new SourceDefinition(null, null, null),
                                         List.of(new LevelDefinition("out",
                                                                     new GroupingDefinition.Keyed("category", "done", 5000L),
                                                                     new SummariserDefinition("pass-through", null, Map.of()),
                                                                     null, List.of())),
                                         null);

        var registry = new SummariserRegistry();
        var pipeline = new PipelineCompiler().compile(def, registry, null, stubExpressionEngine());

        var output = new ArrayList<LevelEvent<?>>();
        pipeline.outputBus("out").subscribe(e -> true, output::add);

        pipeline.inputBus().publish(new LevelEvent<>(Map.of("category", (Object) "A", "value", (Object) 1), 100L, INPUT_LEVEL, null));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("category", (Object) "A", "value", (Object) 2), 200L, INPUT_LEVEL, null));
        pipeline.inputBus().publish(new LevelEvent<>(Map.of("category", (Object) "A", "value", (Object) 3, "done", (Object) true), 300L, INPUT_LEVEL, null));
        pipeline.tick(400L).toCompletableFuture().join();

        assertThat(output).isNotEmpty();
    }
}
