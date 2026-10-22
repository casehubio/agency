package io.casehub.agency.agentic.yaml.registry;

import io.casehub.agency.agentic.yaml.spec.EpistemicRuleSpec;
import io.casehub.agency.conversation.EpistemicRule;
import io.casehub.agency.conversation.EpistemicRules;

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
