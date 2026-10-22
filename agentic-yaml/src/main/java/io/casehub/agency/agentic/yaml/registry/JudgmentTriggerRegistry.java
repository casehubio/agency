package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.judgment.AlwaysYield;
import io.casehub.agency.agentic.judgment.ConfidenceThreshold;
import io.casehub.agency.agentic.judgment.IterationBased;
import io.casehub.agency.agentic.judgment.JudgmentTrigger;
import io.casehub.agency.agentic.judgment.NeverYield;
import io.casehub.agency.agentic.yaml.spec.JudgmentSpec.TriggerSpec;
import io.casehub.platform.api.expression.ExpressionEngine;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

public class JudgmentTriggerRegistry {

    @SuppressWarnings("unchecked")
    public <T> JudgmentTrigger<T> resolve(TriggerSpec spec,
                                           @Nullable ExpressionEngine engine) {
        return switch (spec) {
            case TriggerSpec.AlwaysYield ay -> new AlwaysYield<>();
            case TriggerSpec.NeverYield ny -> new NeverYield<>();
            case TriggerSpec.IterationBased ib -> new IterationBased<>(ib.every());
            case TriggerSpec.ConfidenceThreshold ct -> {
                if (engine == null) {
                    throw new IllegalStateException(
                            "ConfidenceThreshold requires ExpressionEngine for extractor compilation");
                }
                @SuppressWarnings("unchecked")
                var compiled = engine.compile(ct.extractor(),
                        (Class<Map<String, Object>>) (Class<?>) Map.class, Number.class);
                yield ConfidenceThreshold.below(ct.threshold(), ctx -> {
                    var map = new HashMap<String, Object>();
                    map.put("executionContext", ctx.executionContext());
                    map.put("iterationResults", ctx.iterationResults());
                    map.put("aggregationResult", ctx.aggregationResult());
                    map.put("iteration", ctx.iteration());
                    map.put("previousFeedback", ctx.previousFeedback());
                    return compiled.eval(map).doubleValue();
                });
            }
        };
    }
}
