package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.TurnPolicySpec;
import io.casehub.agency.deliberation.conversation.orchestration.AddressedTurnPolicy;
import io.casehub.agency.deliberation.conversation.orchestration.FreeTurnPolicy;
import io.casehub.agency.deliberation.conversation.orchestration.PointAddressedTurnPolicy;
import io.casehub.agency.deliberation.conversation.orchestration.RoundRobinTurnPolicy;
import io.casehub.agency.deliberation.conversation.orchestration.TurnPolicy;

public class TurnPolicyRegistry {
    public TurnPolicy resolve(TurnPolicySpec spec) {
        return switch (spec) {
            case TurnPolicySpec.RoundRobin rr -> new RoundRobinTurnPolicy();
            case TurnPolicySpec.Addressed a -> new AddressedTurnPolicy();
            case TurnPolicySpec.PointAddressed pa -> new PointAddressedTurnPolicy();
            case TurnPolicySpec.Free f -> new FreeTurnPolicy();
        };
    }
}
