package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.util.List;
import java.util.Objects;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = ConfidenceScorerSpec.Arousal.class, name = "arousal"),
        @Type(value = ConfidenceScorerSpec.Surprise.class, name = "surprise"),
        @Type(value = ConfidenceScorerSpec.Composite.class, name = "composite")
})
public sealed interface ConfidenceScorerSpec {

    record Arousal() implements ConfidenceScorerSpec {}

    record Surprise() implements ConfidenceScorerSpec {}

    record Composite(List<WeightedScorerEntry> scorers) implements ConfidenceScorerSpec {
        public Composite {
            if (scorers == null || scorers.isEmpty())
                throw new IllegalArgumentException("at least one scorer required");
            scorers = List.copyOf(scorers);
        }
    }

    record WeightedScorerEntry(ConfidenceScorerSpec scorer, double weight) {
        public WeightedScorerEntry {
            Objects.requireNonNull(scorer, "scorer");
            if (weight <= 0) throw new IllegalArgumentException("weight must be positive");
        }
    }
}
