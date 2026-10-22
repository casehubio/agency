package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.AcceptancePolicySpec;
import io.casehub.agency.deliberation.negotiation.AcceptancePolicy;
import io.casehub.agency.deliberation.negotiation.MajorityAcceptance;
import io.casehub.agency.deliberation.negotiation.ThresholdAcceptance;
import io.casehub.agency.deliberation.negotiation.UnanimousAcceptance;

public class AcceptancePolicyRegistry {
    public AcceptancePolicy resolve(AcceptancePolicySpec spec) {
        return switch (spec) {
            case AcceptancePolicySpec.Unanimous u -> new UnanimousAcceptance();
            case AcceptancePolicySpec.Majority m -> new MajorityAcceptance();
            case AcceptancePolicySpec.Threshold t -> new ThresholdAcceptance(t.minAcceptances());
        };
    }
}
