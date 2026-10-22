package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = EpistemicRuleSpec.ExplicitAcknowledgement.class, name = "explicit-acknowledgement"),
        @Type(value = EpistemicRuleSpec.TacitAcceptance.class, name = "tacit-acceptance"),
        @Type(value = EpistemicRuleSpec.CommitmentResolution.class, name = "commitment-resolution")
})
public sealed interface EpistemicRuleSpec {
    record ExplicitAcknowledgement(int minParticipants) implements EpistemicRuleSpec {}
    record TacitAcceptance(int windowRounds) implements EpistemicRuleSpec {}
    record CommitmentResolution() implements EpistemicRuleSpec {}
}
