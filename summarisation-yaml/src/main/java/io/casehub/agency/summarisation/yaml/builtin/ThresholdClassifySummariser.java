package io.casehub.agency.summarisation.yaml.builtin;

import io.casehub.agency.summarisation.LevelEvent;
import io.casehub.agency.summarisation.Summariser;
import io.casehub.platform.api.expression.CompiledExpression;
import io.casehub.platform.api.expression.ExpressionEngine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public class ThresholdClassifySummariser implements Summariser<Map<String, Object>, Map<String, Object>> {

    private final List<CompiledRule> rules;

    private record CompiledRule(String name, String category,
                                CompiledExpression<Map<String, Object>, Boolean> predicate,
                                Map<String, Object> extraFields) {}

    private ThresholdClassifySummariser(List<CompiledRule> rules) {
        this.rules = List.copyOf(rules);
    }

    @SuppressWarnings("unchecked")
    public static ThresholdClassifySummariser create(Map<String, Object> config,
                                                      ExpressionEngine engine) {
        var ruleConfigs = (List<Map<String, Object>>) config.get("rules");
        if (ruleConfigs == null || ruleConfigs.isEmpty()) {
            throw new IllegalArgumentException("threshold-classify requires non-empty 'rules'");
        }
        var compiled = new ArrayList<CompiledRule>();
        for (var ruleConfig : ruleConfigs) {
            var name = (String) ruleConfig.get("name");
            var when = (String) ruleConfig.get("when");
            var category = (String) ruleConfig.get("category");
            var extra = new LinkedHashMap<String, Object>();
            for (var entry : ruleConfig.entrySet()) {
                if (!"name".equals(entry.getKey()) && !"when".equals(entry.getKey())) {
                    extra.put(entry.getKey(), entry.getValue());
                }
            }
            compiled.add(new CompiledRule(name, category,
                    engine.compile(when, (Class<Map<String, Object>>) (Class<?>) Map.class, Boolean.class), extra));
        }
        return new ThresholdClassifySummariser(compiled);
    }

    @Override
    public CompletionStage<List<Map<String, Object>>> summarise(
            List<LevelEvent<Map<String, Object>>> batch) {
        var results = new ArrayList<Map<String, Object>>();
        for (var event : batch) {
            var payload = event.payload();
            for (var rule : rules) {
                if (Boolean.TRUE.equals(rule.predicate().eval(payload))) {
                    var output = new LinkedHashMap<String, Object>();
                    output.putAll(payload);
                    output.putAll(rule.extraFields());
                    output.put("ruleName", rule.name());
                    results.add(output);
                }
            }
        }
        return CompletableFuture.completedFuture(results);
    }
}
