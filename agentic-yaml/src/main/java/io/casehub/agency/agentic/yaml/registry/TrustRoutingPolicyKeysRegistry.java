package io.casehub.agency.agentic.yaml.registry;

import io.casehub.api.spi.routing.TrustRoutingPolicyKeys;
import io.casehub.agency.agentic.yaml.spec.TrustRoutingPolicyKeysSpec;

public class TrustRoutingPolicyKeysRegistry {

    public TrustRoutingPolicyKeys resolve(TrustRoutingPolicyKeysSpec spec) {
        var keys = TrustRoutingPolicyKeys.create(spec.scopePrefix());
        if (spec.floors() != null) {
            for (var entry : spec.floors().entrySet()) {
                keys = keys.withFloor(entry.getKey(), entry.getValue());
            }
        }
        return keys;
    }
}
