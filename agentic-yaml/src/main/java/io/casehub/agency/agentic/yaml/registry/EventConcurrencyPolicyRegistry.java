package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.orchestration.execution.EventConcurrencyPolicy;
import io.casehub.agency.core.yaml.spec.EventConcurrencyPolicySpec;

public class EventConcurrencyPolicyRegistry {

    public EventConcurrencyPolicy resolve(EventConcurrencyPolicySpec spec) {
        return switch (spec) {
            case EventConcurrencyPolicySpec.Serialize ignored ->
                    EventConcurrencyPolicy.serialize();
            case EventConcurrencyPolicySpec.Coalesce c ->
                    c.window() != null
                            ? EventConcurrencyPolicy.coalesce(c.window())
                            : EventConcurrencyPolicy.coalesce();
            case EventConcurrencyPolicySpec.CoalesceBySource ignored ->
                    EventConcurrencyPolicy.coalesceBySource();
        };
    }
}
