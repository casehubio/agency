package io.casehub.agency.core.yaml.spec;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.NAME, property = "type")
@JsonSubTypes({
        @Type(value = DecisionScopeSpec.Reactive.class, name = "reactive"),
        @Type(value = DecisionScopeSpec.TaskAdaptation.class, name = "task-adaptation"),
        @Type(value = DecisionScopeSpec.FullAutonomy.class, name = "full-autonomy")
})
public sealed interface DecisionScopeSpec {

    record Reactive() implements DecisionScopeSpec {}

    record TaskAdaptation() implements DecisionScopeSpec {}

    record FullAutonomy() implements DecisionScopeSpec {}
}
