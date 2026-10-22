package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.deliberation.judgment.CallerRef;
import io.casehub.agency.deliberation.judgment.CallerStrategy;
import io.casehub.agency.deliberation.judgment.ConsensusAgreement;
import io.casehub.agency.core.yaml.spec.JudgmentSpec.AgreementSpec;
import io.casehub.agency.core.yaml.spec.JudgmentSpec.CallerSpec;

public class CallerStrategyRegistry {

    public CallerStrategy resolve(CallerSpec spec) {
        return switch (spec) {
            case CallerSpec.Single s ->
                    new CallerStrategy.Single(CallerRef.agent(s.callerName(), null));
            case CallerSpec.FanOut fo ->
                    new CallerStrategy.FanOut(
                            fo.callerNames().stream()
                                    .map(name -> CallerRef.agent(name, null))
                                    .toList(),
                            resolveAgreement(fo.agreement()));
            case CallerSpec.EscalationChain ec ->
                    new CallerStrategy.EscalationChain(
                            ec.callerNames().stream()
                                    .map(name -> CallerRef.agent(name, null))
                                    .toList());
        };
    }

    private ConsensusAgreement resolveAgreement(AgreementSpec spec) {
        return switch (spec) {
            case AgreementSpec.Unanimous u -> ConsensusAgreement.unanimous();
            case AgreementSpec.Majority m -> ConsensusAgreement.majority();
            case AgreementSpec.Threshold t -> ConsensusAgreement.threshold(t.minAgreements());
        };
    }
}
