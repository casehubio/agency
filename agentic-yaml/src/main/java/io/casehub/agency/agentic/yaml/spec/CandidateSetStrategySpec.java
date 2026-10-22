package io.casehub.agency.agentic.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import java.util.Objects;
import java.util.Set;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = CandidateSetStrategySpec.Static.class, name = "static")
})
public sealed interface CandidateSetStrategySpec {

    record Static(Set<String> groups) implements CandidateSetStrategySpec {
        public Static {
            Objects.requireNonNull(groups, "groups");
            if (groups.isEmpty())
                throw new IllegalArgumentException("at least one group required");
            groups = Set.copyOf(groups);
        }
    }
}
