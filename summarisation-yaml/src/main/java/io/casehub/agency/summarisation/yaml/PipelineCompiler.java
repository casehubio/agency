package io.casehub.agency.summarisation.yaml;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.agency.summarisation.EventLevel;
import io.casehub.agency.summarisation.EventStreamBus;
import io.casehub.agency.summarisation.KeyedSummarisationRunner;
import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.SummarisationRunner;
import io.casehub.agency.summarisation.Summariser;
import io.casehub.agency.summarisation.WindowPolicy;
import io.casehub.agency.summarisation.cloudevents.CloudEventEmitter;
import io.casehub.agency.summarisation.cloudevents.EventSink;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngine;
import io.cloudevents.CloudEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

public class PipelineCompiler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @SuppressWarnings("unchecked")
    public <IN> CompiledPipeline<IN> compile(PipelineDefinition definition,
                                             SummariserRegistry registry,
                                             @Nullable EventSink<CloudEvent> emitterSink) {
        return compile(definition, registry, emitterSink, null);
    }

    @SuppressWarnings("unchecked")
    public <IN> CompiledPipeline<IN> compile(PipelineDefinition definition,
                                             SummariserRegistry registry,
                                             @Nullable EventSink<CloudEvent> emitterSink,
                                             @Nullable ExpressionEngine expressionEngine) {
        var inputBus     = new EventStreamBus<Object>();
        var levelRunners = new ArrayList<CompiledPipeline.LevelRunner>();
        var outputBuses  = new LinkedHashMap<String, EventStreamBus<?>>();

        EventStreamBus<Object> currentInput = inputBus;

        for (int i = 0; i < definition.levels().size(); i++) {
            var level       = definition.levels().get(i);
            var outputLevel = new EventLevel(level.name(), i + 1);
            var outputBus   = new EventStreamBus<Object>();

            Summariser<Object, Object> summariser = registry.create(
                    level.summariser().type(), level.summariser().config());

            if (level.grouping() instanceof GroupingDefinition.Keyed keyed) {
                if (expressionEngine == null) {
                    throw new IllegalStateException("Keyed grouping requires ExpressionEngine");
                }
                var keyedRunner = createKeyedRunner(keyed, expressionEngine, summariser, outputBus, outputLevel);
                currentInput.subscribe(e -> true, keyedRunner::collect);
                levelRunners.add(new CompiledPipeline.LevelRunner(
                        now -> keyedRunner.tick(now), keyedRunner::flush));
            } else {
                WindowPolicy policy = toWindowPolicy(level.grouping());
                var          runner = new SummarisationRunner<>(policy, summariser, outputBus, outputLevel);
                currentInput.subscribe(e -> true, runner::collect);
                levelRunners.add(new CompiledPipeline.LevelRunner(
                        now -> runner.tick(now), runner::flush));
            }

            if (level.emit() != null && emitterSink != null) {
                new CloudEventEmitter<>(outputBus, emitterSink,
                                        level.emit().cloudEventType(),
                                        payload -> {
                                            try {
                                                return JSON.writeValueAsBytes(payload);
                                            } catch (JsonProcessingException e) {throw new RuntimeException(e);}
                                        });
            }

            outputBuses.put(level.name(), outputBus);
            currentInput = outputBus;
        }

        return (CompiledPipeline<IN>) (CompiledPipeline<?>) new CompiledPipeline<>(
                definition.name(), inputBus, levelRunners, outputBuses);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private KeyedSummarisationRunner<Object, Object, Object> createKeyedRunner(
            GroupingDefinition.Keyed keyed,
            ExpressionEngine expressionEngine,
            Summariser<Object, Object> summariser,
            EventStreamBus<Object> outputBus,
            EventLevel outputLevel) {
        CompiledExpression<Map, Object> keyExpr =
                expressionEngine.compile(keyed.keyExpression(), Map.class, Object.class);
        CompiledExpression<Map, Boolean> completionExpr =
                expressionEngine.compile(keyed.completionExpression(), Map.class, Boolean.class);

        return new KeyedSummarisationRunner<>(
                (LevelEvent<Object> e) -> {
                    var payload = e.payload();
                    if (payload instanceof Map<?, ?> m) {
                        return keyExpr.eval((Map) m);
                    }
                    return payload.toString();
                },
                group -> group.stream().anyMatch(e -> {
                    var payload = e.payload();
                    if (payload instanceof Map<?, ?> m) {
                        var result = completionExpr.eval((Map) m);
                        return Boolean.TRUE.equals(result);
                    }
                    return false;
                }),
                keyed.staleTimeout(),
                summariser,
                outputBus,
                outputLevel);
    }

    private WindowPolicy toWindowPolicy(GroupingDefinition grouping) {
        if (grouping instanceof GroupingDefinition.Windowed w) {
            if (w.count() != null && w.age() != null) {
                return WindowPolicy.of(w.age(), w.count());
            }
            if (w.count() != null) {
                return WindowPolicy.ofCount(w.count());
            }
            if (w.age() != null) {
                return WindowPolicy.ofAge(w.age());
            }
            throw new IllegalArgumentException("Windowed grouping requires count and/or age");
        }
        throw new IllegalStateException("Unknown grouping type: " + grouping.getClass());
    }
}
