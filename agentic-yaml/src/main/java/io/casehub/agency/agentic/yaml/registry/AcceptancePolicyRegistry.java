package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.AcceptancePolicySpec;
import io.casehub.agency.negotiation.AcceptancePolicy;
import io.casehub.agency.negotiation.MajorityAcceptance;
import io.casehub.agency.negotiation.ThresholdAcceptance;
import io.casehub.agency.negotiation.UnanimousAcceptance;

public class AcceptancePolicyRegistry {
    public AcceptancePolicy resolve(AcceptancePolicySpec spec) {
        return switch (spec) {
            case AcceptancePolicySpec.Unanimous u -> new UnanimousAcceptance();
            case AcceptancePolicySpec.Majority m -> new MajorityAcceptance();
            case AcceptancePolicySpec.Threshold t -> new ThresholdAcceptance(t.minAcceptances());
        };
    }
}
