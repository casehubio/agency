package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import org.jspecify.annotations.Nullable;

import java.util.List;

public record JudgmentSpec(
        TriggerSpec trigger,
        CallerSpec caller,
        @Nullable AgreementSpec agreement
) {
    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
            @Type(value = TriggerSpec.AlwaysYield.class, name = "always-yield"),
            @Type(value = TriggerSpec.NeverYield.class, name = "never-yield"),
            @Type(value = TriggerSpec.IterationBased.class, name = "iteration-based"),
            @Type(value = TriggerSpec.ConfidenceThreshold.class, name = "confidence-threshold")
    })
    public sealed interface TriggerSpec {
        record AlwaysYield() implements TriggerSpec {}
        record NeverYield() implements TriggerSpec {}
        record IterationBased(int every) implements TriggerSpec {}
        record ConfidenceThreshold(double threshold, String extractor) implements TriggerSpec {}
    }

    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
            @Type(value = CallerSpec.Single.class, name = "single"),
            @Type(value = CallerSpec.FanOut.class, name = "fan-out"),
            @Type(value = CallerSpec.EscalationChain.class, name = "escalation-chain")
    })
    public sealed interface CallerSpec {
        record Single(String callerName) implements CallerSpec {}
        record FanOut(List<String> callerNames, AgreementSpec agreement) implements CallerSpec {}
        record EscalationChain(List<String> callerNames) implements CallerSpec {}
    }

    @JsonTypeInfo(use = Id.NAME, property = "type")
    @JsonSubTypes({
            @Type(value = AgreementSpec.Unanimous.class, name = "unanimous"),
            @Type(value = AgreementSpec.Majority.class, name = "majority"),
            @Type(value = AgreementSpec.Threshold.class, name = "threshold")
    })
    public sealed interface AgreementSpec {
        record Unanimous() implements AgreementSpec {}
        record Majority() implements AgreementSpec {}
        record Threshold(int minAgreements) implements AgreementSpec {}
    }
}
