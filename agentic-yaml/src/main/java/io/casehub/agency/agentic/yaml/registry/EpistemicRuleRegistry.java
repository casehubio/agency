package io.casehub.agency.core.yaml.registry;

import io.casehub.agency.core.yaml.spec.EpistemicRuleSpec;
import io.casehub.agency.deliberation.conversation.EpistemicRule;
import io.casehub.agency.deliberation.conversation.EpistemicRules;

public class EpistemicRuleRegistry {
    public EpistemicRule resolve(EpistemicRuleSpec spec) {
        return switch (spec) {
            case EpistemicRuleSpec.ExplicitAcknowledgement ea ->
                    EpistemicRules.explicitAcknowledgement(ea.minParticipants());
            case EpistemicRuleSpec.TacitAcceptance ta ->
                    EpistemicRules.tacitAcceptance(ta.windowRounds());
            case EpistemicRuleSpec.CommitmentResolution cr ->
                    EpistemicRules.commitmentResolution();
        };
    }
}
