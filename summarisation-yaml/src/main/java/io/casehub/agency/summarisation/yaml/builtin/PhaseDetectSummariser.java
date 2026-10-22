package io.casehub.agency.summarisation.yaml.builtin;

import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.StatefulSummariser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

public class PhaseDetectSummariser
        implements StatefulSummariser<Map<String, Object>, Map<String, Object>, String> {

    private final String initialState;
    private final List<TransitionRule> transitions;
    private final List<String> aggregateFields;

    private record TransitionRule(String from, String to,
                                   Predicate<BatchContextView> predicate) {}

    private PhaseDetectSummariser(String initialState,
                                   List<TransitionRule> transitions,
                                   List<String> aggregateFields) {
        this.initialState = initialState;
        this.transitions = List.copyOf(transitions);
        this.aggregateFields = List.copyOf(aggregateFields);
    }

    @SuppressWarnings("unchecked")
    public static PhaseDetectSummariser create(Map<String, Object> config,
                                                List<String> aggregateFields) {
        var initial = (String) config.get("initial");
        if (initial == null) {
            throw new IllegalArgumentException("phase-detect requires 'initial'");
        }
        var transitionConfigs = (List<Map<String, Object>>) config.get("transitions");
        if (transitionConfigs == null) {
            throw new IllegalArgumentException("phase-detect requires 'transitions'");
        }
        var rules = new ArrayList<TransitionRule>();
        for (var tc : transitionConfigs) {
            rules.add(new TransitionRule(
                    (String) tc.get("from"),
                    (String) tc.get("to"),
                    buildPredicate(tc)));
        }
        return new PhaseDetectSummariser(initial, rules, aggregateFields);
    }

    private static Predicate<BatchContextView> buildPredicate(Map<String, Object> tc) {
        if (tc.containsKey("min-batch-size")) {
            int minSize = ((Number) tc.get("min-batch-size")).intValue();
            return ctx -> ctx.getSize() >= minSize;
        }
        var countField = (String) tc.get("count-field");
        var countValue = (String) tc.get("count-value");
        var op = (String) tc.getOrDefault("op", ">=");
        var threshold = ((Number) tc.get("threshold")).intValue();
        return ctx -> {
            int actual = ctx.countOf(countField, countValue);
            return switch (op) {
                case ">=" -> actual >= threshold;
                case ">"  -> actual > threshold;
                case "<=" -> actual <= threshold;
                case "<"  -> actual < threshold;
                case "==" -> actual == threshold;
                default -> throw new IllegalArgumentException("Unknown op: " + op);
            };
        };
    }

    @Override
    public CompletionStage<SummariseResult<Map<String, Object>, String>> summarise(
            List<LevelEvent<Map<String, Object>>> batch,
            String previousState) {
        String currentState = previousState != null ? previousState : initialState;

        var payloads = batch.stream()
                .map(LevelEvent::payload)
                .toList();
        var batchCtx = new BatchContextView(BatchContext.compute(payloads, aggregateFields));

        for (var transition : transitions) {
            if (transition.from().equals(currentState) && transition.predicate().test(batchCtx)) {
                var output = new LinkedHashMap<String, Object>();
                output.put("from", currentState);
                output.put("to", transition.to());
                output.put("phase", transition.to());
                return CompletableFuture.completedFuture(
                        new SummariseResult<>(List.of(output), transition.to()));
            }
        }

        return CompletableFuture.completedFuture(
                new SummariseResult<>(List.of(), currentState));
    }
}
